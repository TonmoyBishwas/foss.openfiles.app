package foss.openfiles.app.data

/** Sort modes matching the One UI sort menu. */
object Sorting {
    const val NAME = 0
    const val DATE = 1
    const val TYPE = 2
    const val SIZE = 3

    /**
     * Natural-ish, case-insensitive comparator. Folders always sort before files,
     * matching the stock file manager.
     */
    fun comparator(mode: Int, ascending: Boolean): Comparator<FileItem> {
        val base: Comparator<FileItem> = when (mode) {
            DATE -> compareBy { it.lastModified }
            TYPE -> compareBy({ it.extension }, { it.name.lowercase() })
            SIZE -> compareBy { it.size }
            else -> compareBy { it.name.lowercase() }
        }
        val directed = if (ascending) base else base.reversed()
        return compareByDescending<FileItem> { it.isDirectory }.then(directed)
    }
}
