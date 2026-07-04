# OpenFiles — Pitfalls, Known Bugs & Mistakes to Avoid

This file records bugs that actually happened (so they don't regress), structural risks that will likely bite later, and hard rules. Read it before touching the corresponding areas.

## Bugs we already hit (do not regress)

1. **Grid view rendered broken rows.** Cause: `GridLayoutManager(4)` was paired with the full-width `item_file.xml` row layout. Fix: grid mode is a first-class mode of `FileAdapter` (`FileAdapter(listener, grid = true)`) inflating `item_grid.xml`. Never mix a grid layout manager with the list item layout, and always recreate the adapter when `Prefs.viewMode` changes (see `BrowserFragment.applyViewMode`).

2. **Breadcrumb didn't scroll to the newest crumb.** Cause: `view.post { fullScroll(...) }` runs **before** the Choreographer layout pass has measured the newly added crumb views. Fix: a one-shot `addOnLayoutChangeListener` that scrolls after layout, then removes itself. General rule: `view.post` is not "after layout" — anything that depends on new views' measured sizes needs `OnLayoutChangeListener` or `doOnNextLayout`.

3. **Marquee text stuck.** A `TextView` marquee needs all of: `isSingleLine = true`, `ellipsize = MARQUEE`, `marqueeRepeatLimit = -1`, **`isSelected = true`**, and a bounded width (`maxWidth` cap) — otherwise it silently never scrolls.

4. **Directories showed "-1 items".** `FileItem` defaults `childCount = -1`; cursor-built items skip it. Always construct directory items with `FileItem.from(file)` (done in `MediaQuery.searchIndexed`).

5. **Search was slow and unreliable.** Original implementation walked the filesystem recursively on every keystroke. Fix: `MediaQuery.searchIndexed` (single indexed MediaStore `LIKE` query) + 250 ms debounce + minimum 2 chars + `Future` cancellation. Never reintroduce recursive walking on the interactive path; `MediaQuery.search` exists only as a non-interactive fallback.

6. **Kotlin smart-cast failure inside lambdas.** Accessing a mutable/nullable property (e.g. `seg.category`) inside a click-listener lambda fails to smart-cast. Capture to a local first: `val cat = seg.category`.

7. **Gradle build fails with "requires JVM 11+ / this build uses Java 8".** The machine's default Java is 8. Always run Gradle with `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"`.

8. **Badge icons looked cropped/malformed.** Vector badges must keep the 24-viewport geometry and gain padding via a `<group scaleX/scaleY pivotX/pivotY>` wrapper (see `ic_badge_download.xml`, `ic_badge_music.xml`) — don't hand-shrink path coordinates.

9. **MediaStore `DATE_MODIFIED` is in seconds.** `FileItem.lastModified` and `java.io.File.lastModified()` are milliseconds. Every cursor read multiplies by 1000; every `newerThan` filter divides by 1000. Getting this wrong makes date filters/grouping silently wrong by a factor of 1000.

## Contracts that are easy to break

- **`.openfiles_trash` filtering.** The recycle bin is a real folder at each volume root. `BrowserFragment.reload`, `SelectFolderSheet.navigate`, and `MediaQuery.search` all filter it out explicitly. Any *new* file-listing surface must do the same, or the bin leaks into the UI (and worse, users can delete the index).
- **Duplicated extension lists.** `FileKind` (classification) and `MediaQuery` (`DOC_EXTS`/`APK_EXTS`/`ZIP_EXTS` selections) both hardcode extension sets. If they drift, category listings and Manage-storage sums disagree with icons/filters. Adding an extension means updating both.
- **Threading pattern.** Every background result must be applied via `view?.post { if (!isAdded) return@post; ... }` with `context ?: return@execute` at the top of the task. Skipping the guards crashes on rotation/back navigation.
- **Theme colors are runtime values.** Read `ThemeManager.accent()` etc. at bind/build time. There's no activity recreation on palette change, so cached colors go stale; screens rebuilt on navigation pick up the new palette naturally.
- **Version bumps.** Every release bumps `versionCode` *and* `versionName` in `app/build.gradle.kts`. Play rejects re-used versionCodes.

## Latent risks (watch these; likely sources of future bugs)

- **`Trash.index.json` writes are not atomic.** A crash mid-write can corrupt/lose the bin index (files remain on disk under `.openfiles_trash/<id>` but become orphaned). If the bin gains importance, write to a temp file + rename. Trash IDs use `timeMillis + hashCode + random(0..99999)` — collision is improbable but nothing dedupes.
- **`onBackPressed()` is deprecated.** The `BackHandler` interface hangs off the deprecated override in `MainActivity`. Migrating to `OnBackPressedDispatcher` will be needed for predictive back / future targetSdk pressure — migrate the whole contract at once, not piecemeal.
- **MediaStore `DATA` column is deprecated.** It works because the app holds All files access and does raw `java.io.File` I/O. Do **not** half-migrate to scoped-storage APIs (SAF, `MediaStore` write APIs) — the app's model assumes raw path access everywhere.
- **MediaStore staleness.** Files created by other apps (or over USB) may not appear in categories/search until the media scanner indexes them. Browser (FileRepository) always sees them; Category/Search may not. If users report "file missing from search", this is why — consider `MediaScannerConnection.scanFile` after our own file operations.
- **No process-death restoration.** Only `BrowserFragment` saves `currentDir`. Back-stack fragments recreate with their original arguments; anything passed outside `arguments` is lost. Keep fragment inputs in `arguments` (serializable/primitives).
- **`commitAllowingStateLoss`** in `MainActivity.showEntryScreen` — deliberate (permission return path), but don't copy the pattern elsewhere without reason.
- **`CategoryFragment.buildBreadcrumb`** contains an unused, fragile lookup: `bar.parent.parent as LinearLayout`. It does nothing today; remove it rather than build on it.
- **Search hidden-file rule**: `searchIndexed` hides any path containing `/.` — that also hides files *inside* dotted directories (e.g. `.thumbnails/`), not just dotted files. This matches user expectation but differs subtly from the browser's `name.startsWith('.')` rule.
- **Executor lifecycle.** Per-fragment executors are never shut down; long file operations continue after the fragment dies (`Dialogs.runOperation` progress sheet holds its own lifecycle). Bounded, but don't add unbounded work sources.
- **Thumbs cache keys include the pixel size** (96 px list / 220 px grid = two cache entries per file). Fine today; adding more sizes multiplies memory. Reuse an existing size when possible.
- **`PillProgressView.kt` defines a `Drawable`,** not a View. Rename it if you touch it; don't be misled.

## Hard rules

1. **APK size is a feature.** No new dependencies without strong justification (currently ~1.9 MB; keep under ~2 MB). No Glide/Coil/Room/DI/analytics — ever, per project philosophy.
2. **No file I/O on the main thread.** Everything goes through the executor + post pattern.
3. **No network permissions.** The privacy story ("your files never leave your device") is a core promise and part of the Play listing.
4. **Permissions are frozen.** `MANAGE_EXTERNAL_STORAGE` and `REQUEST_INSTALL_PACKAGES` are already Play-policy-sensitive (see docs/PLAY-STORE.md). Adding any new permission risks re-review; adding a sensitive one risks rejection.
5. **Verify on the Samsung reference device** (wireless adb) — it's the ground truth for One UI fidelity. Reference screenshots live in the local-only `My Files/` folder.
6. **Match the original.** UI changes should be justified by a comparison against stock One UI My Files, not personal taste.
