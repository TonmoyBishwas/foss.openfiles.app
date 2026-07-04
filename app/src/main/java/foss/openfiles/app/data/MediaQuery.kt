package foss.openfiles.app.data

import android.content.Context
import android.provider.MediaStore
import java.io.File

/** Category listings backed by MediaStore, with a filesystem fallback. */
object MediaQuery {

    enum class Category { IMAGES, VIDEOS, AUDIO, DOCUMENTS, DOWNLOADS, APK }

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
            Category.DOCUMENTS -> filesByKind(context, FileKind.DOCUMENT)
            Category.APK -> filesByExtension(context, listOf("apk", "apks", "xapk"))
            Category.DOWNLOADS -> downloads(context, showHidden)
        }

    /** Total bytes per category for the Manage storage screen. */
    fun categorySize(context: Context, category: Category): Long =
        query(context, category, showHidden = false).sumOf { it.size }

    private fun mediaStore(
        context: Context,
        uri: android.net.Uri,
        selection: String?,
        args: Array<String>?
    ): List<FileItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val out = mutableListOf<FileItem>()
        runCatching {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
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

    private fun filesByKind(context: Context, kind: FileKind): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        return mediaStore(context, uri, null, null).filter {
            FileKind.ofExtension(it.extension) == kind
        }
    }

    private fun filesByExtension(context: Context, exts: List<String>): List<FileItem> {
        val uri = MediaStore.Files.getContentUri("external")
        return mediaStore(context, uri, null, null).filter { it.extension in exts }
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
        val all = mediaStore(context, uri, null, null)
        return all.asSequence()
            .filter { FileKind.ofExtension(it.extension) != FileKind.OTHER }
            .sortedByDescending { it.lastModified }
            .take(limit)
            .toList()
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
