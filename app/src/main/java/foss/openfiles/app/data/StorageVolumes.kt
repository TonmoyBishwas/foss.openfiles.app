package foss.openfiles.app.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File

data class VolumeInfo(
    val root: File,
    val isPrimary: Boolean,
    val isUsb: Boolean,
    val totalBytes: Long,
    val usedBytes: Long
)

/** Detects internal + removable storage volumes without any cloud/network paths. */
object StorageVolumes {

    fun list(context: Context): List<VolumeInfo> {
        val volumes = mutableListOf<VolumeInfo>()
        val primaryRoot = Environment.getExternalStorageDirectory()
        volumes += info(primaryRoot, isPrimary = true, isUsb = false)

        // Secondary volumes (SD card, USB drives) via app-dirs trick: works API 19+.
        val seen = mutableSetOf(primaryRoot.absolutePath)
        for (dir in ContextCompat.getExternalFilesDirs(context, null).filterNotNull()) {
            val root = volumeRootOf(dir) ?: continue
            if (!seen.add(root.absolutePath)) continue
            val usb = root.name.startsWith("usb", ignoreCase = true) ||
                root.absolutePath.contains("usb", ignoreCase = true)
            volumes += info(root, isPrimary = false, isUsb = usb)
        }
        return volumes
    }

    /** /storage/XXXX-XXXX/Android/data/pkg/files -> /storage/XXXX-XXXX */
    private fun volumeRootOf(appFilesDir: File): File? {
        var f: File? = appFilesDir
        while (f != null && f.name != "Android") f = f.parentFile
        return f?.parentFile
    }

    private fun info(root: File, isPrimary: Boolean, isUsb: Boolean): VolumeInfo {
        val stat = runCatching { StatFs(root.absolutePath) }.getOrNull()
        val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0
        val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0
        // Round advertised capacity up to a marketing size (128 GB etc) like the stock app.
        return VolumeInfo(root, isPrimary, isUsb, marketingSize(total), total - free)
    }

    fun marketingSize(realTotal: Long): Long {
        if (realTotal <= 0) return 0
        val steps = longArrayOf(
            4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096
        ).map { it * 1024L * 1024L * 1024L }
        for (s in steps) if (realTotal <= s) return s
        return realTotal
    }

    fun freeOf(root: File): Long {
        val stat = runCatching { StatFs(root.absolutePath) }.getOrNull() ?: return 0
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun realTotalOf(root: File): Long {
        val stat = runCatching { StatFs(root.absolutePath) }.getOrNull() ?: return 0
        return stat.blockCountLong * stat.blockSizeLong
    }
}
