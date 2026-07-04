package foss.openfiles.app.ui.grid

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import foss.openfiles.app.R
import foss.openfiles.app.data.FileItem
import foss.openfiles.app.data.FileKind
import foss.openfiles.app.util.Format
import foss.openfiles.app.util.Thumbs

/**
 * Square photo/video grid with the filename overlaid on a dark strip at the
 * bottom of each tile and a play glyph for videos.
 */
class GridAdapter(
    private val items: List<FileItem>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<GridAdapter.Holder>() {

    class Holder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame) {
        lateinit var image: ImageView
        lateinit var label: TextView
        lateinit var play: ImageView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val ctx = parent.context
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val frame = FrameLayout(ctx)
        frame.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val holder = Holder(frame)

        holder.image = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xFF17181A.toInt())
        }
        frame.addView(holder.image, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        holder.play = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_badge_video)
            alpha = 0.95f
            visibility = View.GONE
        }
        frame.addView(holder.play, FrameLayout.LayoutParams(dp(22), dp(22)).apply {
            gravity = Gravity.START or Gravity.BOTTOM
            setMargins(dp(6), 0, 0, dp(26))
        })

        holder.label = TextView(ctx).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            maxLines = 1
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0x66000000)
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        frame.addView(holder.label, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM })

        // Square tiles
        frame.viewTreeObserver.addOnGlobalLayoutListener {
            if (frame.width > 0 && frame.layoutParams.height != frame.width) {
                frame.layoutParams = frame.layoutParams.apply { height = frame.width }
            }
        }
        return holder
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val kind = FileKind.of(item)
        holder.label.text = Format.middleEllipsis(item.name, 12)
        holder.play.visibility = if (kind == FileKind.VIDEO) View.VISIBLE else View.GONE
        holder.image.setImageDrawable(null)
        Thumbs.load(holder.image, item.path, kind, 220)
        holder.frame.setOnClickListener { onClick(item) }
    }
}
