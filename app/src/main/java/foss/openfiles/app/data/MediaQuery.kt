package foss.openfiles.app.data

import android.content.Context
import android.provider.MediaStore
import java.io.File

/** Category listings backed by MediaStore, with selection-based queries for speed. */
object MediaQuery {

    enum class Category { IMAGES, VIDEOS, AUDIO, DOCUMENTS, DOWNLOADS, APK, COMPRESSED }

    private val DOC_EXTS = listOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp",
        "csv", "md", "html", "htm", "xml", "json", "epub", "hwp", "log"
    )
    private val APK_EXTS = listOf("apk", "apks", "xapk")
    private val ZIP_EXTS = listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "z")

    fun query(context: Context, category: Category, showHidden: Boolean): List<FileItem> =
        when (category) {
            Category.IMAGES -> mediaStore(
                context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null
            )
            Category.VIDEOS -> mediaStore(
                context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null
            )
            Category.AUDIO -> mediaStore(
                context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null
            )
            Category.DOCUMENTS -> filesByExtension(context, DOC_EXTS)
            Category.APK -> filesByExtension(context, APK_EXTS)
            Category.COMPRESSED -> filesByExtension(context, ZIP_EXTS)
            Category.DOWNLOADS -> downloads(context, showHidden)
        }

    private fun extensionSelection(exts: List<String>): Pair<String, Array<String>> {
        val clause = exts.joinToString(" OR ") {
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        }
        return clause to exts.map { "%.$it" }.toTypedArray()
    }

    private fun mediaStore(
        context: Context,
        uri: android.net.Uri,
        selection: String?,
        args: Array<String>?,
        sortOrder: String? = null,
        limit: Int = Int.MAX_VALUE
    ): List<FileItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val out = mutableListOf<FileItem>()
        runCatching {
            context.contentResolver.query(uri, projection, selection, args, sortOrder)?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext() && out.size < limit) {
                    val path = c.getString(dataCol) ?: continue
                    val f = File(path)
                    if (!f.exists()) continue
                    out += FileItem(
                        path = path,
                        name = f.name,
                        isDirectory = false,
                        size = c.getLong(sizeCol),
                        lastModified = c.getLong(dateCol) * 1000
                    )
                }
            }
        }
        return out
    }

    private fun filesByExtension(context: Context, exts: List<String>): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        val (selection, args) = extensionSelection(exts)
        return mediaStore(context, uri, selection, args)
    }

    private fun downloads(context: Context, showHidden: Boolean): List<FileItem> {
        val dir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        return FileRepository.list(dir, showHidden, false, Sorting.DATE, false)
    }

    /** Most recently modified media/doc files for the home carousel. */
    fun recents(context: Context, limit: Int): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        // Newest first; stop reading once we have enough interesting rows.
        val items = mutableListOf<FileItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        runCatching {
            context.contentResolver.query(
                uri, projection, null, null,
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
            )?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext() && items.size < limit) {
                    val path = c.getString(dataCol) ?: continue
                    if (FileKind.ofExtension(path.substringAfterLast('.', "")) == FileKind.OTHER) {
                        continue
                    }
                    val f = File(path)
                    if (!f.isFile) continue
                    items += FileItem(
                        path = path,
                        name = f.name,
                        isDirectory = false,
                        size = c.getLong(sizeCol),
                        lastModified = c.getLong(dateCol) * 1000
                    )
                }
            }
        }
        return items
    }

    data class CategoryUsage(
        val images: Long = 0,
        val videos: Long = 0,
        val audio: Long = 0,
        val documents: Long = 0,
        val apk: Long = 0,
        val compressed: Long = 0
    )

    /**
     * Total bytes per category in ONE pass over the MediaStore Files table —
     * used by the Manage storage screen.
     */
    fun categorySizes(context: Context): CategoryUsage {
        var images = 0L; var videos = 0L; var audio = 0L
        var documents = 0L; var apk = 0L; var compressed = 0L
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE
        )
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    val size = c.getLong(sizeCol)
                    if (size <= 0) continue
                    when (FileKind.ofExtension(name.substringAfterLast('.', ""))) {
                        FileKind.IMAGE -> images += size
                        FileKind.VIDEO -> videos += size
                        FileKind.AUDIO -> audio += size
                        FileKind.DOCUMENT -> documents += size
                        FileKind.APK -> apk += size
                        FileKind.COMPRESSED -> compressed += size
                        else -> Unit
                    }
                }
            }
        }
        return CategoryUsage(images, videos, audio, documents, apk, compressed)
    }

    /**
     * Fast name search backed by the MediaStore index — a single indexed query
     * instead of walking the whole filesystem. Covers files and folders on all
     * volumes MediaStore knows about.
     */
    fun searchIndexed(
        context: Context,
        query: String,
        showHidden: Boolean,
        kinds: Set<FileKind>?,
        newerThan: Long?,
        limit: Int = 500
    ): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        var selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = mutableListOf("%$query%")
        if (newerThan != null) {
            selection += " AND ${MediaStore.MediaColumns.DATE_MODIFIED} >= ?"
            args += (newerThan / 1000).toString()
        }
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val out = mutableListOf<FileItem>()
        runCatching {
            context.contentResolver.query(
                uri, projection, selection, args.toTypedArray(),
                "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext() && out.size < limit) {
                    if (Thread.currentThread().isInterrupted) return@use
                    val path = c.getString(dataCol) ?: continue
                    if (!showHidden && path.contains("/.")) continue
                    val f = File(path)
                    if (!f.exists()) continue
                    val item = if (f.isDirectory) FileItem.from(f) else FileItem(
                        path = path,
                        name = f.name,
                        isDirectory = false,
                        size = c.getLong(sizeCol),
                        lastModified = c.getLong(dateCol) * 1000
                    )
                    if (kinds != null && (item.isDirectory || FileKind.of(item) !in kinds)) continue
                    out += item
                }
            }
        }
        return out
    }

    /** Recursive filesystem search across a volume. */
    fun search(
        root: File,
        query: String,
        showHidden: Boolean,
        kinds: Set<FileKind>?,
        newerThan: Long?,
        limit: Int = 500
    ): List<FileItem> {
        val q = query.lowercase()
        val out = mutableListOf<FileItem>()
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty() && out.size < limit) {
            val dir = stack.removeLast()
            val kids = dir.listFiles() ?: continue
            for (f in kids) {
                val hidden = f.name.startsWith('.')
                if (hidden && !showHidden) continue
                if (f.isDirectory) {
                    if (f.name != ".openfiles_trash") stack.addLast(f)
                }
                if (!f.name.lowercase().contains(q)) continue
                val item = FileItem.from(f)
                if (kinds != null && FileKind.of(item) !in kinds) continue
                if (newerThan != null && item.lastModified < newerThan) continue
                out += item
                if (out.size >= limit) break
            }
        }
        return out
    }
}
