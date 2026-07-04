package foss.openfiles.app.data

import java.io.File

/** Immutable snapshot of a file or folder shown in a list. */
data class FileItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val childCount: Int = -1
) {
    val file: File get() = File(path)
    val extension: String get() = name.substringAfterLast('.', "").lowercase()

    val isHidden: Boolean get() = name.startsWith('.')

    companion object {
        fun from(f: File): FileItem {
            val dir = f.isDirectory
            return FileItem(
                path = f.absolutePath,
                name = f.name,
                isDirectory = dir,
                size = if (dir) 0 else f.length(),
                lastModified = f.lastModified(),
                childCount = if (dir) f.list()?.size ?: 0 else -1
            )
        }
    }
}

/** File kind used for icons, categories and search filters. */
enum class FileKind {
    FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, APK, COMPRESSED, OTHER;

    companion object {
        private val IMAGE_EXT = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "ico", "tiff", "raw", "dng"
        )
        private val VIDEO_EXT = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "wmv", "flv", "3gp", "m4v", "ts", "mpg", "mpeg"
        )
        private val AUDIO_EXT = setOf(
            "mp3", "wav", "flac", "m4a", "aac", "ogg", "opus", "wma", "mid", "amr", "aiff"
        )
        private val DOC_EXT = setOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp",
            "csv", "md", "html", "htm", "xml", "json", "epub", "hwp", "log"
        )
        private val ZIP_EXT = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "z")

        fun of(item: FileItem): FileKind = if (item.isDirectory) FOLDER else ofExtension(item.extension)

        fun ofExtension(ext: String): FileKind = when (ext.lowercase()) {
            in IMAGE_EXT -> IMAGE
            in VIDEO_EXT -> VIDEO
            in AUDIO_EXT -> AUDIO
            in DOC_EXT -> DOCUMENT
            "apk", "apks", "xapk" -> APK
            in ZIP_EXT -> COMPRESSED
            else -> OTHER
        }
    }
}
