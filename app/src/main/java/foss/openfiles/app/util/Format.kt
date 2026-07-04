package foss.openfiles.app.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Size/date formatting that matches the stock file manager's style. */
object Format {

    private val oneDp = DecimalFormat("#,##0.0#")
    private val twoDp = DecimalFormat("#,##0.00")
    private val whole = DecimalFormat("#,##0")

    /** e.g. 3.36 MB, 659 MB, 118.7 GB, 800 KB */
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1000) return "${whole.format(kb)} KB"
        val mb = kb / 1024.0
        if (mb < 100) return "${twoDp.format(mb)} MB"
        if (mb < 1000) return "${whole.format(mb)} MB"
        val gb = mb / 1024.0
        return "${twoDp.format(gb)} GB"
    }

    /** Short form used in the storage pill: 118.7 GB */
    fun sizeShort(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1) return "${oneDp.format(gb)} GB"
        val mb = bytes / (1024.0 * 1024.0)
        if (mb >= 1) return "${whole.format(mb)} MB"
        return "${whole.format(bytes / 1024.0)} KB"
    }

    /**
     * e.g. "4 Jul 7:24 am" for this year, "14 Nov 2023 4:18 pm" for other years —
     * the same shape as the screenshots.
     */
    fun date(millis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val pattern = if (sameYear) "d MMM h:mm a" else "d MMM yyyy h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault())
            .format(Date(millis))
            .replace("AM", "am").replace("PM", "pm")
    }

    /** Relative label for the recents carousel: Just now / N minutes ago / N hours ago / date. */
    fun relative(millis: Long): String {
        val diff = System.currentTimeMillis() - millis
        val min = diff / 60000
        return when {
            min < 1 -> "Just now"
            min < 60 -> "$min minute${if (min == 1L) "" else "s"} ago"
            min < 24 * 60 -> "${min / 60} hour${if (min / 60 == 1L) "" else "s"} ago"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
        }
    }

    /** Middle-ellipsized name like "Scree...les.jpg" used under recents thumbnails. */
    fun middleEllipsis(name: String, max: Int = 14): String {
        if (name.length <= max) return name
        val keep = max - 3
        val head = keep * 2 / 3
        val tail = keep - head
        return name.take(head) + "..." + name.takeLast(tail)
    }
}
