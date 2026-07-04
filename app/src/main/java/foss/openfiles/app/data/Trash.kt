package foss.openfiles.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TrashEntry(
    val id: String,
    val originalPath: String,
    val trashedAt: Long,
    val stored: File,
    val name: String,
    val isDirectory: Boolean,
    val size: Long
) {
    val daysLeft: Int
        get() {
            val elapsed = System.currentTimeMillis() - trashedAt
            return (Trash.RETENTION_DAYS - elapsed / Trash.DAY_MS).toInt().coerceIn(0, Trash.RETENTION_DAYS)
        }
}

/**
 * App-managed recycle bin. Files are moved into a hidden folder on the same
 * volume with a JSON index; entries are purged after 30 days.
 */
object Trash {

    const val RETENTION_DAYS = 30
    const val DAY_MS = 24L * 60 * 60 * 1000

    private const val DIR_NAME = ".openfiles_trash"
    private const val INDEX = "index.json"

    private fun binDir(context: Context, volumeRoot: File): File =
        File(volumeRoot, DIR_NAME).apply { mkdirs() }

    private fun volumeRootFor(context: Context, file: File): File {
        for (v in StorageVolumes.list(context)) {
            if (file.absolutePath.startsWith(v.root.absolutePath)) return v.root
        }
        return StorageVolumes.list(context).first { it.isPrimary }.root
    }

    fun moveToTrash(context: Context, files: List<File>): Boolean {
        var ok = true
        for (f in files) {
            val root = volumeRootFor(context, f)
            val bin = binDir(context, root)
            val id = "${System.currentTimeMillis()}_${f.name.hashCode()}_${(0..99999).random()}"
            val stored = File(bin, id)
            val measured = FileRepository.measure(f)
            var moved = f.renameTo(stored)
            if (!moved) {
                // Rare fallback (e.g. emulated path quirks): copy into place, then delete.
                moved = copyInto(f, stored) && FileOps.deleteRecursive(f)
                if (!moved) FileOps.deleteRecursive(stored)
            }
            if (!moved) {
                ok = false
                continue
            }
            val entries = readIndex(bin)
            entries.put(JSONObject().apply {
                put("id", id)
                put("path", f.absolutePath)
                put("at", System.currentTimeMillis())
                put("name", f.name)
                put("dir", stored.isDirectory)
                put("size", measured.first)
            })
            writeIndex(bin, entries)
        }
        return ok
    }

    fun list(context: Context): List<TrashEntry> {
        purgeExpired(context)
        val out = mutableListOf<TrashEntry>()
        for (v in StorageVolumes.list(context)) {
            val bin = File(v.root, DIR_NAME)
            if (!bin.isDirectory) continue
            val entries = readIndex(bin)
            for (i in 0 until entries.length()) {
                val o = entries.getJSONObject(i)
                val stored = File(bin, o.getString("id"))
                if (!stored.exists()) continue
                out += TrashEntry(
                    id = o.getString("id"),
                    originalPath = o.getString("path"),
                    trashedAt = o.getLong("at"),
                    stored = stored,
                    name = o.getString("name"),
                    isDirectory = o.optBoolean("dir"),
                    size = o.optLong("size")
                )
            }
        }
        return out.sortedByDescending { it.trashedAt }
    }

    fun restore(context: Context, entry: TrashEntry): Boolean {
        val original = File(entry.originalPath)
        original.parentFile?.mkdirs()
        val dest = FileRepository.uniqueDestination(original)
        val ok = entry.stored.renameTo(dest)
        if (ok) removeFromIndex(entry)
        return ok
    }

    fun deleteForever(context: Context, entry: TrashEntry): Boolean {
        val ok = FileOps.deleteRecursive(entry.stored)
        if (ok) removeFromIndex(entry)
        return ok
    }

    fun purgeExpired(context: Context) {
        for (v in StorageVolumes.list(context)) {
            val bin = File(v.root, DIR_NAME)
            if (!bin.isDirectory) continue
            val entries = readIndex(bin)
            val keep = JSONArray()
            var changed = false
            for (i in 0 until entries.length()) {
                val o = entries.getJSONObject(i)
                val expired = System.currentTimeMillis() - o.getLong("at") > RETENTION_DAYS * DAY_MS
                if (expired) {
                    FileOps.deleteRecursive(File(bin, o.getString("id")))
                    changed = true
                } else {
                    keep.put(o)
                }
            }
            if (changed) writeIndex(bin, keep)
        }
    }

    fun totalSize(context: Context): Long = list(context).sumOf { it.size }

    private fun removeFromIndex(entry: TrashEntry) {
        val bin = entry.stored.parentFile ?: return
        val entries = readIndex(bin)
        val keep = JSONArray()
        for (i in 0 until entries.length()) {
            val o = entries.getJSONObject(i)
            if (o.getString("id") != entry.id) keep.put(o)
        }
        writeIndex(bin, keep)
    }

    /** Copies a file or directory tree to an exact destination path. */
    private fun copyInto(src: File, dest: File): Boolean = runCatching {
        if (src.isDirectory) {
            if (!dest.mkdirs()) return false
            src.listFiles()?.forEach { if (!copyInto(it, File(dest, it.name))) return false }
        } else {
            src.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output, 256 * 1024) }
            }
            dest.setLastModified(src.lastModified())
        }
        true
    }.getOrDefault(false)

    private fun readIndex(bin: File): JSONArray =
        runCatching { JSONArray(File(bin, INDEX).readText()) }.getOrDefault(JSONArray())

    private fun writeIndex(bin: File, arr: JSONArray) {
        runCatching { File(bin, INDEX).writeText(arr.toString()) }
    }
}
