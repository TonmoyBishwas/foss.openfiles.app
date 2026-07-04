# CLAUDE.md — OpenFiles

Operational guide for Claude Code / contributors. Deep detail lives in `docs/`:
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — full codebase documentation
- [docs/PITFALLS.md](docs/PITFALLS.md) — bugs we've hit, latent risks, mistakes to avoid
- [docs/PLAY-STORE.md](docs/PLAY-STORE.md) — the launch roadmap (the project's main goal)

## What this is

**OpenFiles** (`foss.openfiles.app`) — a FOSS file manager for Android that faithfully recreates the Samsung One UI "My Files" look and feel. GPL-3.0. Native Kotlin + Views (no Compose, no ViewBinding, no coroutines). The only dependencies are androidx core/appcompat/recyclerview/coordinatorlayout/material/documentfile. Release APK is ~1.9 MB and **must stay under ~2 MB** — that's a headline feature.

## Philosophy (non-negotiables)

1. **Pixel-match stock One UI.** The user compares against the real Samsung My Files app on their phone (screenshots in local-only `My Files/` folder). When in doubt, copy the original's spacing, sizing, and behavior.
2. **No network, no ads, no analytics, no cloud.** Files never leave the device. Don't add any permission or dependency that suggests otherwise.
3. **Lightweight over convenient.** No Glide/Coil/Room/DI. Thumbnails (`Thumbs`), settings (`Prefs`), recycle bin (`Trash`) are all hand-rolled. Question every new dependency against APK size.
4. **Many screens build views in code**, not XML (Search, Settings, ManageStorage, RecycleBin, Permission, Palette, all widgets). Match the inline dp/color style of the file you're editing.

## Build & install

System Java is 8 — Gradle needs JDK 17. Always build with (Bash tool):

```bash
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew assembleDebug
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew assembleRelease   # signed APK
JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" ./gradlew bundleRelease     # AAB for Play Store
```

- Release signing reads `keystore.properties` + `release.jks` at repo root (both gitignored — never commit).
- Release build has minify + resource shrinking on; `proguard-rules.pro` is intentionally empty (no reflection in the app).
- Test device: Samsung phone (the One UI reference) over wireless adb, typically `192.168.1.36:40943`:
  `adb install -r app/build/outputs/apk/release/app-release.apk`
- CI (`.github/workflows/build.yml`) builds a debug APK on push/PR only.

## Versioning

Bump **both** `versionCode` and `versionName` in `app/build.gradle.kts` for every release (currently versionCode 2 / 0.1.1).

## Architecture in brief

- Single activity: `ui/main/MainActivity` with one FrameLayout container. Navigation = `MainActivity.push(fragment)` (replace + back stack + slide/fade). Back presses are offered to the current fragment via the `MainActivity.BackHandler` interface first (only `BrowserFragment` implements it — walks up directories / exits selection mode).
- Entry: `PermissionFragment` (All files access gate) → `HomeFragment` → Browser / Category / Search / RecycleBin / ManageStorage / Settings.
- Data layer (`data/`): all **blocking** — every fragment owns a `Executors.newSingleThreadExecutor()` and posts results with `view?.post { if (!isAdded) return@post; ... }`. `FileRepository` (java.io.File browsing), `MediaQuery` (MediaStore for categories/recents/search/sizes), `FileOps` (copy/move/delete/zip), `Trash` (`.openfiles_trash` folder per volume + `index.json`, 30-day purge), `StorageVolumes`, `Prefs` (SharedPreferences singleton, init in `OpenFilesApp`), `Sorting`.
- Theming: `ThemeManager.accent()/accentStrong()/folder()` resolved at runtime from `Prefs.palette` (`-1` = Material You dynamic, API 31+). Applied **imperatively per screen** — never cache accent colors statically.
- `util/Thumbs`: LRU cache (maxMemory/8) + 3-thread pool, key `"$path@$sizePx"`, recycled-view races guarded by `view.tag` check.

## Conventions

- `Prefs.viewMode`: 0 = list, 1 = detailed list, 2 = grid. Grid uses `FileAdapter(listener, grid = true)` + `GridLayoutManager(ctx, 4)` + `item_grid.xml`; list uses `item_file.xml`. Never mix them.
- Search/category queries go through MediaStore (`MediaQuery.searchIndexed`) — **never** recursive filesystem walks on the interactive path. Search input is debounced 250 ms, min 2 chars.
- Popup menus: `ui/widget/OneUiMenu`. Wrapping chip rows: `ui/widget/FlowLayout`. Bottom sheets/progress: `ui/widget/Dialogs`.
- Vector icons: 24-viewport paths, use `<group scaleX/scaleY pivot>` for internal padding instead of redrawing geometry.

## Top mistakes to avoid (details in docs/PITFALLS.md)

- `view.post {}` runs **before** the next layout pass — for scroll-after-layout use a one-shot `OnLayoutChangeListener`.
- `FileItem` default `childCount = -1` renders "-1 items" — build directory items with `FileItem.from(file)`.
- MediaStore `DATE_MODIFIED` is **seconds**; `FileItem.lastModified` is milliseconds (×1000 on read).
- Extension lists exist in both `FileKind` and `MediaQuery` — keep them in sync.
- Every file-listing site must filter out `.openfiles_trash`.
- Kotlin smart-casts fail on mutable properties inside lambdas — capture to a local `val` first.
- Marquee text needs `isSelected = true` and a bounded width or it never scrolls.

## Play Store (the goal)

Follow [docs/PLAY-STORE.md](docs/PLAY-STORE.md). Critical constraints:
- `MANAGE_EXTERNAL_STORAGE` and `REQUEST_INSTALL_PACKAGES` are Play-restricted and require declaration forms — **never add more permissions casually**, and don't remove/rename the file-manager core functionality that justifies them.
- Play requires an **AAB** (`bundleRelease`), a privacy policy URL, and a truthful Data safety form ("no data collected").
- Impersonation policy: the listing must not use Samsung branding; keep the non-affiliation disclaimer.
