package foss.openfiles.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import foss.openfiles.app.data.FileKind
import java.io.File
import java.util.concurrent.Executors

/**
 * Tiny thumbnail loader (no third-party image library) — LRU memory cache +
 * a small decode thread pool. Keeps the APK lightweight like a native app.
 */
object Thumbs {

    private val executor = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    private val cache = object : LruCache<String, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 1024) / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun load(view: ImageView, path: String, kind: FileKind, sizePx: Int) {
        val key = "$path@$sizePx"
        view.tag = key
        cache.get(key)?.let {
            view.setImageBitmap(it)
            return
        }
        executor.execute {
            val file = File(path)
            val stamp = file.lastModified()
            val bmp = runCatching { decode(view.context, file, kind, sizePx) }.getOrNull()
            if (bmp != null) {
                cache.put(key, bmp)
                main.post { if (view.tag == key) view.setImageBitmap(bmp) }
            }
        }
    }

    fun cached(path: String, sizePx: Int): Bitmap? = cache.get("$path@$sizePx")

    private fun decode(context: Context, file: File, kind: FileKind, sizePx: Int): Bitmap? =
        when (kind) {
            FileKind.IMAGE -> decodeImage(file, sizePx)
            FileKind.VIDEO -> decodeVideo(file, sizePx)
            FileKind.AUDIO -> decodeAlbumArt(file, sizePx)
            FileKind.APK -> decodeApkIcon(context, file, sizePx)
            else -> null
        }

    private fun decodeImage(file: File, sizePx: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= sizePx && opts.outHeight / (sample * 2) >= sizePx) {
            sample *= 2
        }
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, real)
    }

    private fun decodeVideo(file: File, sizePx: Int): Bitmap? =
        runCatching {
            @Suppress("DEPRECATION")
            ThumbnailUtils.createVideoThumbnail(
                file.absolutePath,
                android.provider.MediaStore.Images.Thumbnails.MINI_KIND
            )
        }.getOrNull()

    private fun decodeAlbumArt(file: File, sizePx: Int): Bitmap? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            val art = mmr.embeddedPicture ?: return null
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(art, 0, art.size, opts)
            var sample = 1
            while (opts.outWidth / (sample * 2) >= sizePx) sample *= 2
            BitmapFactory.decodeByteArray(
                art, 0, art.size, BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    private fun decodeApkIcon(context: Context, file: File, sizePx: Int): Bitmap? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = file.absolutePath
        app.publicSourceDir = file.absolutePath
        return drawableToBitmap(app.loadIcon(pm), sizePx)
    }

    private fun drawableToBitmap(d: Drawable, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(canvas)
        return bmp
    }
}
