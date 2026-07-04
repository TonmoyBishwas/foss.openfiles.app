package foss.openfiles.app.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Blocking file operations; callers run these on a background thread. */
object FileOps {

    interface Progress {
        /** Return false to cancel. */
        fun onProgress(done: Long, total: Long, currentName: String): Boolean
    }

    fun totalBytes(sources: List<File>): Long =
        sources.sumOf { FileRepository.measure(it).first }

    fun copy(sources: List<File>, destDir: File, progress: Progress? = null): Boolean {
        val total = totalBytes(sources)
        var done = 0L
        for (src in sources) {
            val dest = FileRepository.uniqueDestination(File(destDir, src.name))
            done = copyRecursive(src, dest, done, total, progress) ?: return false
        }
        return true
    }

    fun move(sources: List<File>, destDir: File, progress: Progress? = null): Boolean {
        for (src in sources) {
            val dest = FileRepository.uniqueDestination(File(destDir, src.name))
            if (src.renameTo(dest)) continue
            // Different volume: fall back to copy + delete.
            val total = FileRepository.measure(src).first
            copyRecursive(src, dest, 0, total, progress) ?: return false
            deleteRecursive(src)
        }
        return true
    }

    fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    fun rename(file: File, newName: String): Boolean {
        val dest = File(file.parentFile, newName)
        if (dest.exists()) return false
        return file.renameTo(dest)
    }

    /** Copies src (file or dir) to dest. Returns new done counter, or null if cancelled. */
    private fun copyRecursive(
        src: File, dest: File, doneStart: Long, total: Long, progress: Progress?
    ): Long? {
        var done = doneStart
        if (src.isDirectory) {
            if (!dest.exists() && !dest.mkdirs()) return null
            val kids = src.listFiles() ?: return done
            for (k in kids) {
                done = copyRecursive(k, File(dest, k.name), done, total, progress) ?: return null
            }
            dest.setLastModified(src.lastModified())
            return done
        }
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (progress?.onProgress(done, total, src.name) == false) {
                        output.close()
                        dest.delete()
                        return null
                    }
                }
            }
        }
        dest.setLastModified(src.lastModified())
        return done
    }

    /** Zips [sources] into [zipFile]. */
    fun compress(sources: List<File>, zipFile: File, progress: Progress? = null): Boolean {
        val total = totalBytes(sources)
        var done = 0L
        ZipOutputStream(FileOutputStream(zipFile).buffered()).use { zos ->
            for (src in sources) {
                val base = src.parentFile!!.absolutePath.length + 1
                val stack = ArrayDeque<File>()
                stack.addLast(src)
                while (stack.isNotEmpty()) {
                    val f = stack.removeLast()
                    val rel = f.absolutePath.substring(base).replace('\\', '/')
                    if (f.isDirectory) {
                        zos.putNextEntry(ZipEntry("$rel/"))
                        zos.closeEntry()
                        f.listFiles()?.forEach { stack.addLast(it) }
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        FileInputStream(f).use { input ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                zos.write(buf, 0, n)
                                done += n
                                if (progress?.onProgress(done, total, f.name) == false) {
                                    zos.closeEntry()
                                    return false
                                }
                            }
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
        return true
    }

    /** Extracts a zip into destDir/zipName/. Rejects entries escaping the target (zip-slip). */
    fun extract(zip: File, destDir: File, progress: Progress? = null): Boolean {
        val target = FileRepository.uniqueDestination(File(destDir, zip.nameWithoutExtension))
        if (!target.mkdirs()) return false
        val canonicalTarget = target.canonicalPath + File.separator
        val total = zip.length()
        var done = 0L
        ZipInputStream(FileInputStream(zip).buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(canonicalTarget)) {
                    zis.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { output ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                        }
                    }
                }
                done += entry.compressedSize.coerceAtLeast(0)
                if (progress?.onProgress(done.coerceAtMost(total), total, entry.name) == false) {
                    return false
                }
                zis.closeEntry()
            }
        }
        return true
    }
}
