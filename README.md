# OpenFiles

**OpenFiles** (`foss.openfiles.app`) is a free and open-source file manager for Android, designed to look and feel like the stock One UI "My Files" experience — clean, fast, and lightweight.

> OpenFiles is an independent open-source project. It is not affiliated with, endorsed by, or connected to Samsung Electronics Co., Ltd. All UI code and assets in this repository are original work.

## Features

- 📁 Browse **internal storage** and **external storage devices** (SD card, USB drives, SSDs)
- 🗂️ Category views: Images, Videos, Audio, Documents, Downloads, APKs
- 🕘 Recent files
- 🗑️ Trash (recycle bin) with restore
- ⭐ Favorites / shortcuts to folders
- 🔍 Search
- ✂️ Full file operations: copy, move, rename, delete, share, compress/extract, details
- 🎨 **Manual color palette picker** — pick your accent palette in-app (mimics dynamic device theming, works on all Android versions)
- 🌙 Light & dark theme
- 📦 No network features, no cloud, no ads, no analytics — your files stay on your device
- 🪶 Lightweight, native Android (Kotlin + Views), supports older devices

## Download

Grab the latest APK from the [Releases page](https://github.com/TonmoyBishwas/foss.openfiles.app/releases).

- **Requires:** Android 5.0 (API 21) or newer
- **APK size:** under 2 MB — as light as a native file manager
- On Android 11+ the app asks for *All files access* so it can manage your storage; below that it uses the classic storage permission.

## Building

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/`.

## License

Licensed under the [GNU General Public License v3.0](LICENSE).
