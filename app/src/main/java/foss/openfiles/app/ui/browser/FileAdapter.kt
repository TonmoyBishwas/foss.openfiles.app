package foss.openfiles.app.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import foss.openfiles.app.R
import foss.openfiles.app.data.FileItem
import foss.openfiles.app.data.FileKind
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.util.Format
import foss.openfiles.app.util.Thumbs

/**
 * File list adapter used by the browser and category screens.
 * Supports selection mode with leading check circles, folder badge icons and
 * a thin inset divider under each row like the stock list.
 */
class FileAdapter(
    private val listener: Listener,
    private val grid: Boolean = false
) : RecyclerView.Adapter<FileAdapter.Holder>() {

    interface Listener {
        fun onItemClick(item: FileItem)
        fun onItemLongClick(item: FileItem)
        fun onSelectionToggled(item: FileItem)
    }

    var items: List<FileItem> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var selectionMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    val selected = LinkedHashSet<String>()

    /** Optional per-item metadata (e.g. source domain in Downloads). */
    var subtitleProvider: ((FileItem) -> String?)? = null

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: ImageView = view.findViewById(R.id.checkbox)
        val icon: ImageView = view.findViewById(R.id.icon)
        val badge: ImageView = view.findViewById(R.id.badge)
        val name: TextView = view.findViewById(R.id.name)
        val date: TextView? = view.findViewById(R.id.date)
        val meta: TextView? = view.findViewById(R.id.meta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            LayoutInflater.from(parent.context).inflate(
                if (grid) R.layout.item_grid else R.layout.item_file, parent, false
            )
        )

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val ctx = holder.itemView.context
        val item = items[position]
        val kind = FileKind.of(item)

        holder.name.text = item.name
        holder.date?.text = Format.date(item.lastModified)
        holder.meta?.text = when {
            item.isDirectory && item.childCount == 0 -> ctx.getString(R.string.no_items)
            item.isDirectory && item.childCount == 1 -> ctx.getString(R.string.item_count_one)
            item.isDirectory -> ctx.getString(R.string.items_count, item.childCount)
            else -> Format.size(item.size)
        }

        bindIcon(holder, item, kind)

        holder.checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        if (selectionMode) {
            bindCheckCircle(holder.checkbox, item.path in selected)
        }

        holder.itemView.setOnClickListener {
            if (selectionMode) listener.onSelectionToggled(item)
            else listener.onItemClick(item)
        }
        holder.itemView.setOnLongClickListener {
            listener.onItemLongClick(item)
            true
        }

        val custom = subtitleProvider?.invoke(item)
        if (custom != null) {
            holder.date?.text = custom
        }
    }

    companion object {
        /** Blue filled circle + white check when selected; gray outline otherwise. */
        fun bindCheckCircle(view: ImageView, isSelected: Boolean) {
            val ctx = view.context
            val pad = (4 * ctx.resources.displayMetrics.density).toInt()
            if (isSelected) {
                view.setBackgroundResource(R.drawable.bg_check_circle)
                view.background.setTint(ThemeManager.accentStrong(ctx))
                view.setImageResource(R.drawable.ic_check)
                view.setColorFilter(0xFFFFFFFF.toInt())
                view.setPadding(pad, pad, pad, pad)
            } else {
                view.background = null
                view.setImageResource(R.drawable.ic_check_circle_off)
                view.clearColorFilter()
                view.setPadding(0, 0, 0, 0)
            }
        }
    }

    /** Thumbnail decode size — bigger in grid mode where tiles are full-bleed. */
    private val thumbPx: Int get() = if (grid) 220 else 96

    private fun bindIcon(holder: Holder, item: FileItem, kind: FileKind) {
        val ctx = holder.itemView.context
        holder.badge.visibility = View.GONE
        holder.icon.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.icon.background = null
        holder.icon.clipToOutline = false
        holder.icon.clearColorFilter()

        fun tint(color: Int?) {
            holder.icon.imageTintList =
                color?.let { android.content.res.ColorStateList.valueOf(it) }
        }

        if (item.isDirectory) {
            holder.icon.setImageResource(R.drawable.ic_folder)
            tint(ThemeManager.folder(ctx))
            badgeFor(item.name)?.let {
                holder.badge.visibility = View.VISIBLE
                holder.badge.setImageResource(it)
                holder.badge.setColorFilter(0xFFFFFFFF.toInt())
                holder.badge.background =
                    ContextCompat.getDrawable(ctx, R.drawable.bg_badge)
                holder.badge.background.setTint(darker(ThemeManager.folder(ctx)))
            }
            applyGridSizing(holder, fullBleed = false)
            return
        }

        when (kind) {
            FileKind.IMAGE, FileKind.VIDEO -> {
                tint(null)
                holder.icon.setImageDrawable(null)
                holder.icon.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.icon.background = ContextCompat.getDrawable(ctx, R.drawable.bg_thumb)
                holder.icon.clipToOutline = true
                Thumbs.load(holder.icon, item.path, kind, thumbPx)
            }
            FileKind.AUDIO -> {
                tint(null)
                holder.icon.scaleType = ImageView.ScaleType.CENTER_CROP
                holder.icon.background = ContextCompat.getDrawable(ctx, R.drawable.bg_thumb)
                holder.icon.clipToOutline = true
                holder.icon.setImageResource(R.drawable.ic_audio_tile)
                Thumbs.load(holder.icon, item.path, kind, thumbPx)
            }
            FileKind.APK -> {
                tint(null)
                holder.icon.setImageResource(R.drawable.ic_file_generic)
                holder.icon.imageTintList =
                    android.content.res.ColorStateList.valueOf(0xFF6E7378.toInt())
                Thumbs.cached(item.path, thumbPx)?.let {
                    tint(null)
                    holder.icon.setImageBitmap(it)
                } ?: run {
                    tint(null)
                    Thumbs.load(holder.icon, item.path, kind, thumbPx)
                }
            }
            else -> {
                holder.icon.setImageResource(iconFor(kind, item.extension))
                tint(colorFor(ctx, kind))
            }
        }

        applyGridSizing(
            holder,
            fullBleed = kind == FileKind.IMAGE || kind == FileKind.VIDEO || kind == FileKind.AUDIO
        )
    }

    /** In grid mode, media thumbnails fill the whole tile; everything else stays a centred glyph. */
    private fun applyGridSizing(holder: Holder, fullBleed: Boolean) {
        if (!grid) return
        val lp = holder.icon.layoutParams as android.widget.FrameLayout.LayoutParams
        if (fullBleed) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            holder.icon.background = null
            holder.icon.clipToOutline = false
        } else {
            val d = holder.itemView.resources.displayMetrics.density
            lp.width = (44 * d).toInt()
            lp.height = (44 * d).toInt()
        }
        holder.icon.layoutParams = lp
    }

    private fun badgeFor(folderName: String): Int? = when (folderName.lowercase()) {
        "dcim", "camera" -> R.drawable.ic_badge_camera
        "download", "downloads" -> R.drawable.ic_badge_download
        "documents" -> R.drawable.ic_badge_doc
        "music", "audiobooks", "ringtones" -> R.drawable.ic_badge_music
        "movies" -> R.drawable.ic_badge_video
        "pictures", "screenshots" -> R.drawable.ic_badge_image
        "android" -> R.drawable.ic_badge_settings
        else -> null
    }

    private fun iconFor(kind: FileKind, ext: String): Int = when (kind) {
        FileKind.DOCUMENT -> R.drawable.ic_cat_document
        FileKind.COMPRESSED -> R.drawable.ic_file_zip
        else -> R.drawable.ic_file_generic
    }

    private fun colorFor(ctx: android.content.Context, kind: FileKind): Int = when (kind) {
        FileKind.DOCUMENT -> ContextCompat.getColor(ctx, R.color.cat_documents)
        FileKind.COMPRESSED -> ContextCompat.getColor(ctx, R.color.seg_compressed)
        else -> 0xFF8F9296.toInt()
    }

    private fun darker(color: Int): Int {
        val f = 0.75f
        val r = ((color shr 16 and 0xFF) * f).toInt()
        val g = ((color shr 8 and 0xFF) * f).toInt()
        val b = ((color and 0xFF) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
