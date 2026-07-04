# OpenFiles — Architecture & Codebase Documentation

Native Kotlin, Views-based (no Compose, no ViewBinding, no coroutines), single-activity app. Package root: `foss.openfiles.app`. All async work uses `java.util.concurrent.Executors`. Many screens build their UI entirely in code — only 9 layout XMLs exist.

## File tree

```
app/src/main/java/foss/openfiles/app/
├── OpenFilesApp.kt            Application: calls Prefs.init()
├── data/
│   ├── FileItem.kt            FileItem snapshot + FileKind enum (extension → kind classifier)
│   ├── FileRepository.kt      java.io.File directory listing, measure(), uniqueDestination()
│   ├── FileOps.kt             Blocking copy/move/delete/rename/compress/extract (zip-slip guarded)
│   ├── MediaQuery.kt          MediaStore: categories, recents, one-pass categorySizes, indexed search
│   ├── Trash.kt               App recycle bin: .openfiles_trash per volume + index.json, 30-day purge
│   ├── StorageVolumes.kt      Volume discovery (primary + SD/USB), marketing sizes, free space
│   ├── Sorting.kt             Sort comparators (folders always first, case-insensitive)
│   └── Prefs.kt               SharedPreferences singleton wrapper
├── theme/
│   ├── Palettes.kt            7 hardcoded accent palettes (Blue default)
│   └── ThemeManager.kt        Resolves accent/accentStrong/folder; -1 = Material You (API 31+)
├── util/
│   ├── Format.kt              Sizes ("3.36 MB"), dates, relative times, middle-ellipsis
│   ├── Open.kt                Open/share via FileProvider (ACTION_VIEW / ACTION_SEND)
│   └── Thumbs.kt              Thumbnail loader: LruCache + 3-thread pool (images/video/audio art/APK icons)
└── ui/
    ├── main/MainActivity.kt           Single-activity host, push(), BackHandler, permission gate
    ├── permission/PermissionFragment.kt  All-files-access onboarding (code-built)
    ├── home/HomeFragment.kt           Landing: recents carousel, categories, storage, utilities
    ├── home/RecentAdapter.kt          Horizontal recents carousel
    ├── browser/BrowserFragment.kt     THE folder browser (breadcrumb, selection, ops, view modes)
    ├── browser/FileAdapter.kt         List/detailed/grid adapter shared by Browser/Category/Search
    ├── grid/GridAdapter.kt            Square photo/video grid for Category Images/Videos
    ├── category/CategoryFragment.kt   Category listings (grid for media, list otherwise)
    ├── search/SearchFragment.kt       Debounced indexed search + filter chips (code-built)
    ├── recycle/RecycleBinFragment.kt  Trash UI grouped by days-left (code-built)
    ├── storage/ManageStorageFragment.kt  Usage bar + tappable category legend (code-built)
    ├── settings/SettingsFragment.kt   Settings cards (code-built)
    ├── settings/PaletteFragment.kt    Accent palette picker (code-built)
    └── widget/
        ├── Dialogs.kt          Bottom sheets: create/rename/delete/details + runOperation() progress
        ├── SelectFolderSheet.kt  Full-height Move/Copy destination picker
        ├── OneUiMenu.kt        Rounded popup menu (the app's universal context menu)
        ├── PillProgressView.kt  Defines PillProgressDrawable (a Drawable, despite the filename)
        └── FlowLayout.kt       Wrapping ViewGroup for search filter chips
```

## Navigation

- `MainActivity` hosts one `FrameLayout` (`R.id.fragment_container`). Entry screen: `HomeFragment` if storage permission granted, else `PermissionFragment` (re-checked in `onResume` after returning from system settings).
- **Forward:** `MainActivity.push(fragment)` = `replace` + `addToBackStack(null)` + slide/fade animations. Fragments call `(activity as MainActivity).push(...)`.
- **Back:** `onBackPressed()` first offers the press to the current fragment if it implements `MainActivity.BackHandler` (return true = consumed). Only `BrowserFragment` implements it: exits selection mode, else walks up one directory, returning false only at the volume root.
- Flow map:
  - Home → Search, Settings, Category (cells), Browser (volume rows), RecycleBin, ManageStorage
  - Browser → navigates folders **internally** (`navigateTo` + reload, no fragment push); pushes Search/Settings/RecycleBin; home crumb pops the entire back stack
  - Category → Search; tapping a folder pushes a Browser rooted at external storage
  - Search → folder results push a Browser
  - ManageStorage → Category / RecycleBin via legend rows
- State: `BrowserFragment` persists `currentDir` in `onSaveInstanceState`; other screens rebuild in `onResume`. There is no broader process-death restoration.

## Threading model

No coroutines anywhere. The universal pattern:

```kotlin
private val executor = Executors.newSingleThreadExecutor()   // one per fragment

executor.execute {
    val ctx = context ?: return@execute
    val data = /* blocking work */
    view?.post {
        if (!isAdded) return@post
        // apply to UI
    }
}
```

- `Thumbs` has its own fixed pool of 3; `Dialogs.runOperation` uses a shared executor and shows an indeterminate progress sheet.
- Cancellation: `SearchFragment` keeps a `Future` and cancels it (plus a 250 ms debounce `Handler`); `MediaQuery.searchIndexed` checks `Thread.isInterrupted`. `BrowserFragment.reload` guards stale results with `currentDir != dir`.
- Executors are never `shutdown()` — the guards above make that safe, but background work can outlive its fragment.

## Data layer

- **FileItem / FileKind** (`data/FileItem.kt`): immutable snapshot (`path, name, isDirectory, size, lastModified, childCount`). Build directory items with `FileItem.from(file)` — the default `childCount = -1` renders "-1 items" otherwise. `FileKind` classifies by extension (FOLDER/IMAGE/VIDEO/AUDIO/DOCUMENT/APK/COMPRESSED/OTHER).
- **FileRepository**: plain `java.io.File` listing with hidden/essentials filters + `Sorting.comparator`. `measure()` = iterative stack walk returning (bytes, files, folders). `uniqueDestination()` yields "name (2).ext" on collision.
- **FileOps**: blocking copy/move/delete/rename/compress/extract. Move falls back to copy+delete across volumes. 256 KB buffers, preserves lastModified. `extract` has zip-slip protection (canonical-path prefix check). `Progress` callback returns false to cancel.
- **MediaQuery**: MediaStore-backed. IMAGES/VIDEOS/AUDIO use dedicated URIs; DOCUMENTS/APK/COMPRESSED use `DISPLAY_NAME LIKE` extension selections; DOWNLOADS lists the Downloads dir via FileRepository. `recents()` feeds the home carousel. `categorySizes()` aggregates per-category bytes in a single cursor pass (Manage storage). `searchIndexed()` is the interactive search (indexed, limit 500, skips `/.` paths unless showHidden); `search()` is the recursive fallback — never use it on the interactive path. **NOTE:** `DATA` column is deprecated but works with All files access; MediaStore timestamps are seconds (converted ×1000).
- **Trash**: hidden `.openfiles_trash` at each volume root + `index.json` (org.json). `moveToTrash` renames (fallback copy+delete), records original path/time/size. 30-day retention, lazily purged on `list()`. Restore uses `uniqueDestination`. Every listing site must filter this folder out (BrowserFragment.reload, SelectFolderSheet.navigate, MediaQuery.search all do).
- **StorageVolumes**: primary external storage + secondary volumes discovered via `getExternalFilesDirs` walking up past `/Android/`; USB detected by name. `marketingSize()` rounds capacity to marketing values (128 GB…).
- **Prefs** keys: `palette` (Int, -1 = dynamic), `showHidden`, `useTrash` (default true), `sortMode` (0 name/1 date/2 type/3 size), `sortAscending`, `viewMode` (0 list/1 detailed/2 grid), `essentialsFilter`, `recentSearches` (max 10, joined by U+001F), `showRecentSearches`.

## Theming

`ThemeManager.accent()/accentStrong()/folder()` resolve from `Prefs.palette`: index into `Palettes.ALL` (7 palettes, Blue default), or `-1` = Material You system accents on API 31+. Colors are applied **imperatively** per screen (`setColorFilter`, `setTextColor`) — there is no activity recreation, so a palette change fully takes effect on the next screen build. Always read colors from ThemeManager at bind time; never cache them in statics. The base theme is `Theme.OpenFiles` (`Theme.Material3.Dark.NoActionBar`) — the app is currently dark-only.

## Thumbnails (`util/Thumbs`)

LruCache sized `maxMemory/8` KB, key `"$path@$sizePx"` (list = 96 px, grid = 220 px — separate cache entries by design). 3-thread decode pool. Recycled-view races: the view is tagged with the key before dispatch and the bitmap only applied if `view.tag == key`. Decoders: `BitmapFactory` + `inSampleSize` (images), `ThumbnailUtils` (video), `MediaMetadataRetriever.embeddedPicture` (audio art), `PackageManager.getPackageArchiveInfo` (APK icons).

## UI layer

- **XML-inflated screens:** Home (`fragment_home.xml`), Browser + Category (shared `fragment_browser.xml`). Item layouts: `item_file.xml` (list row), `item_grid.xml` (grid tile: 84 dp icon frame, badge at translation 14 dp, name strip), `item_category.xml`, `item_recent.xml`, `item_home_row.xml`, `item_bottom_action.xml`. `activity_main.xml` is a bare container.
- **Code-built screens:** Permission, Search, RecycleBin, ManageStorage, Settings, Palette, and all widgets. They use inline `dp()` helpers and some inline color literals (e.g. `0xFF141516` cards) — match the local style when editing.
- **FileAdapter** (`ui/browser/FileAdapter.kt`): the shared list adapter. Constructor `FileAdapter(listener, grid = false)`; grid mode inflates `item_grid.xml`, sizes icons via `applyGridSizing` (44 dp glyphs vs full-bleed media), and requests 220 px thumbs. Selection state = `LinkedHashSet<String>` of paths. Folder icons get category badge overlays (DCIM, Download, Music…).
- **GridAdapter** (`ui/grid/`): separate square photo grid used only by Category IMAGES/VIDEOS.
- **Custom widgets:** `OneUiMenu` (PopupWindow menu, 16sp rows, dotted group dividers, checkmarks), `FlowLayout` (wrapping chips), `Dialogs` (bottom sheets + `runOperation` progress wrapper returning a `Handle` with `invokeOnCompletion`), `SelectFolderSheet` (move/copy destination picker), `PillProgressDrawable` (home storage pill), `SegmentBar` (inner class of ManageStorageFragment).
- **Drawable conventions:** everything is vector/shape XML (keeps the APK small). Icon glyphs use 24-viewport paths; internal padding is achieved with `<group scaleX/scaleY pivotX/pivotY>` wrappers (see `ic_badge_music.xml`, `ic_badge_download.xml`) — don't redraw geometry to shrink an icon. Key colors: `of_*` (One UI dark palette), `cat_*` (category icons), `seg_*` (storage legend), `grid_tile`/`grid_label`.

## Manifest & permissions

- `READ_EXTERNAL_STORAGE` (maxSdk 32), `WRITE_EXTERNAL_STORAGE` (maxSdk 29), `MANAGE_EXTERNAL_STORAGE` (the core All-files-access permission, `tools:ignore="ScopedStorage"`), `REQUEST_INSTALL_PACKAGES` (opening APKs).
- Permission flow lives in `MainActivity`: API 30+ → `Environment.isExternalStorageManager()` / `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` intent; below → legacy runtime permissions. `requestLegacyExternalStorage="true"` covers API 29.
- `FileProvider` (`${applicationId}.provider`) exposes `external-path` and `root-path` at `"."` — intentionally broad for a file manager; used by `util/Open` for view/share intents.

## Build, signing, CI

- `app/build.gradle.kts`: compileSdk/targetSdk 35, minSdk 21, JVM 17, `buildConfig = true`. versionCode 2 / versionName "0.1.1" — bump both per release.
- Signing: `keystore.properties` + `release.jks` at repo root (gitignored); the release signing config is only created when the properties file exists, so CI builds unsigned.
- Release: minify + shrinkResources with `proguard-android-optimize.txt`; `proguard-rules.pro` intentionally has no rules (no reflection).
- Root: AGP 8.7.3, Kotlin 2.0.21, Gradle wrapper 8.13. **Local machine quirk: system Java is 8 — always set `JAVA_HOME` to Android Studio's JBR (JDK 17) when running Gradle.**
- CI: `.github/workflows/build.yml` — debug APK on push to main / PRs, uploaded as artifact. No release job yet.
