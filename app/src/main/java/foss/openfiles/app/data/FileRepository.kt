package foss.openfiles.app.data

import java.io.File

/** Plain java.io.File based listing — fast and works on every supported API level. */
object FileRepository {

    /** Folder names shown by the "Essentials" filter at a volume root. */
    private val ESSENTIALS = setOf(
        "DCIM", "Documents", "Download", "Downloads", "Movies", "Music",
        "Pictures", "Recordings", "Ringtones", "Alarms", "Notifications", "Audiobooks"
    )

    fun list(
        dir: File,
        showHidden: Boolean,
        essentialsOnly: Boolean,
        sortMode: Int,
        ascending: Boolean
    ): List<FileItem> {
        val children = dir.listFiles() ?: return emptyList()
        val items = children.asSequence()
            .filter { showHidden || !it.name.startsWith('.') }
            .filter { !essentialsOnly || (it.isDirectory && it.name in ESSENTIALS) }
            .map { FileItem.from(it) }
            .toMutableList()
        items.sortWith(Sorting.comparator(sortMode, ascending))
        return items
    }

    /** Recursive size for the details dialog; counts files and folders too. */
    fun measure(file: File): Triple<Long, Int, Int> {
        if (file.isFile) return Triple(file.length(), 1, 0)
        var bytes = 0L
        var files = 0
        var folders = 0
        val stack = ArrayDeque<File>()
        stack.addLast(file)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            val kids = f.listFiles() ?: continue
            for (k in kids) {
                if (k.isDirectory) {
                    folders++
                    stack.addLast(k)
                } else {
                    files++
                    bytes += k.length()
                }
            }
        }
        return Triple(bytes, files, folders)
    }

    /** Unique destination like "name (2).ext" if a collision exists. */
    fun uniqueDestination(dest: File): File {
        if (!dest.exists()) return dest
        val dir = dest.parentFile!!
        val base = dest.nameWithoutExtension
        val ext = dest.extension
        var i = 2
        while (true) {
            val name = if (ext.isEmpty() || dest.isDirectory) "$base ($i)" else "$base ($i).$ext"
            val candidate = File(dir, name)
            if (!candidate.exists()) return candidate
            i++
        }
    }
}
