package foss.openfiles.app.util

import android.app.Activity
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Opens files in external apps via FileProvider. */
object Open {

    fun mimeOf(file: File): String =
        MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"

    fun file(activity: Activity, file: File) {
        val uri = FileProvider.getUriForFile(
            activity, "${activity.packageName}.provider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeOf(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { activity.startActivity(intent) }
            .onFailure {
                Toast.makeText(activity, "No app can open this file", Toast.LENGTH_SHORT).show()
            }
    }

    fun share(activity: Activity, files: List<File>) {
        val uris = ArrayList(files.map {
            FileProvider.getUriForFile(activity, "${activity.packageName}.provider", it)
        })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeOf(files[0])
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            activity.startActivity(Intent.createChooser(intent, null))
        }
    }
}
