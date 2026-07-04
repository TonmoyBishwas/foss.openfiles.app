package foss.openfiles.app.ui.storage

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import foss.openfiles.app.R
import foss.openfiles.app.data.MediaQuery
import foss.openfiles.app.data.StorageVolumes
import foss.openfiles.app.data.Trash
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.category.CategoryFragment
import foss.openfiles.app.ui.main.MainActivity
import foss.openfiles.app.ui.recycle.RecycleBinFragment
import foss.openfiles.app.util.Format
import java.util.concurrent.Executors

/** Manage storage: percent used, segmented bar, per-category sizes. */
class ManageStorageFragment : Fragment() {

    private val executor = Executors.newSingleThreadExecutor()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private data class Seg(
        val labelRes: Int,
        val colorRes: Int,
        var bytes: Long = 0,
        val category: MediaQuery.Category? = null
    )

    private lateinit var segments: List<Seg>
    private lateinit var barView: SegmentBar
    private lateinit var legendBox: LinearLayout
    private lateinit var percentLabel: TextView
    private lateinit var usageLabel: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.of_background))
        }

        // Toolbar
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        bar.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(ContextCompat.getColor(ctx, R.color.of_text_primary))
            background = ContextCompat.getDrawable(ctx, R.drawable.ripple_circle)
            setOnClickListener { requireActivity().onBackPressed() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        bar.addView(TextView(ctx).apply {
            text = getString(R.string.manage_storage)
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.accent(ctx))
            setPadding(dp(8), 0, 0, 0)
        })
        root.addView(bar)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(30))
        }

        content.addView(TextView(ctx).apply {
            text = getString(R.string.internal_storage)
            textSize = 22f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_secondary))
        })

        // "61% used   79.15 GB / 128 GB"
        val usageRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, dp(8), 0, 0)
        }
        percentLabel = TextView(ctx).apply {
            textSize = 38f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        }
        usageRow.addView(percentLabel)
        usageLabel = TextView(ctx).apply {
            textSize = 15f
            maxLines = 1
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
            gravity = Gravity.END or Gravity.BOTTOM
            setPadding(0, 0, 0, dp(7))
        }
        usageRow.addView(usageLabel, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.MATCH_PARENT, 1f
        ))
        content.addView(usageRow)

        // Segmented bar
        segments = listOf(
            Seg(R.string.cat_images, R.color.seg_images, category = MediaQuery.Category.IMAGES),
            Seg(R.string.cat_videos, R.color.seg_videos, category = MediaQuery.Category.VIDEOS),
            Seg(R.string.cat_audio, R.color.seg_audio, category = MediaQuery.Category.AUDIO),
            Seg(R.string.cat_documents, R.color.seg_documents, category = MediaQuery.Category.DOCUMENTS),
            Seg(R.string.cat_apk, R.color.seg_apk, category = MediaQuery.Category.APK),
            Seg(R.string.seg_compressed, R.color.seg_compressed, category = MediaQuery.Category.COMPRESSED),
            Seg(R.string.seg_other, R.color.seg_other),
            Seg(R.string.recycle_bin, R.color.seg_bin)
        )
        barView = SegmentBar(ctx)
        content.addView(barView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(30)
        ).apply { topMargin = dp(18) })

        legendBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        content.addView(legendBox)

        root.addView(ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        load()
        return root
    }

    private fun load() {
        executor.execute {
            val ctx = context ?: return@execute
            val volume = StorageVolumes.list(ctx).first { it.isPrimary }
            val realTotal = StorageVolumes.realTotalOf(volume.root)
            val free = StorageVolumes.freeOf(volume.root)
            val used = realTotal - free
            val marketingTotal = volume.totalBytes

            // Header renders immediately; category sums fill in when the
            // single-pass MediaStore aggregation finishes.
            view?.post {
                if (!isAdded) return@post
                val pct = if (marketingTotal > 0) (used * 100 / marketingTotal).toInt() else 0
                val percentText = android.text.SpannableString(
                    getString(R.string.percent_used, pct)
                ).apply {
                    val split = indexOf(' ')
                    if (split > 0) {
                        setSpan(
                            android.text.style.RelativeSizeSpan(0.45f),
                            split, length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                percentLabel.text = percentText
                usageLabel.text =
                    "${Format.sizeShort(used)} / ${Format.sizeShort(marketingTotal).replace(".0", "")}"
            }

            val usage = MediaQuery.categorySizes(ctx)
            val trashBytes = Trash.totalSize(ctx)
            val known = usage.images + usage.videos + usage.audio + usage.documents +
                usage.apk + usage.compressed + trashBytes
            val other = (used - known).coerceAtLeast(0)
            val values = listOf(
                usage.images, usage.videos, usage.audio, usage.documents,
                usage.apk, usage.compressed, other, trashBytes
            )

            view?.post {
                if (!isAdded) return@post
                for ((i, seg) in segments.withIndex()) seg.bytes = values[i]
                barView.set(segments.map {
                    ContextCompat.getColor(requireContext(), it.colorRes) to it.bytes
                }, marketingTotal)
                renderLegend()
            }
        }
    }

    private fun renderLegend() {
        val ctx = requireContext()
        legendBox.removeAllViews()
        for (seg in segments) {
            if (seg.bytes <= 0 && seg.labelRes != R.string.recycle_bin) continue
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(13), 0, dp(13))
            }
            row.addView(View(ctx).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(ContextCompat.getColor(ctx, seg.colorRes))
                }
            }, LinearLayout.LayoutParams(dp(12), dp(12)))
            row.addView(TextView(ctx).apply {
                setText(seg.labelRes)
                textSize = 19f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
                setPadding(dp(16), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(ctx).apply {
                text = Format.size(seg.bytes)
                textSize = 18f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
            })

            // Category rows open a sortable listing of that category's files,
            // and the Recycle bin row opens the bin — like the stock app.
            val cat = seg.category
            val open: (() -> Unit)? = when {
                cat != null -> {
                    { (activity as MainActivity).push(CategoryFragment.newInstance(cat)) }
                }
                seg.labelRes == R.string.recycle_bin -> {
                    { (activity as MainActivity).push(RecycleBinFragment()) }
                }
                else -> null
            }
            if (open != null) {
                row.addView(ImageView(ctx).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    setColorFilter(ContextCompat.getColor(ctx, R.color.of_text_secondary))
                }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(8) })
                row.setBackgroundResource(R.drawable.ripple_item)
                row.setOnClickListener { open() }
            }
            legendBox.addView(row)
        }
    }

    /** Rounded horizontal bar with colored segments over a gray free-space track. */
    private class SegmentBar(context: android.content.Context) : View(context) {
        private var data: List<Pair<Int, Long>> = emptyList()
        private var total = 1L
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = android.graphics.Path()

        fun set(segments: List<Pair<Int, Long>>, totalBytes: Long) {
            data = segments
            total = totalBytes.coerceAtLeast(1)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            path.reset()
            path.addRoundRect(
                0f, 0f, width.toFloat(), height.toFloat(), r, r, android.graphics.Path.Direction.CW
            )
            canvas.clipPath(path)

            paint.color = 0xFF5A5D61.toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            var x = 0f
            for ((color, bytes) in data) {
                if (bytes <= 0) continue
                val w = width * bytes.toFloat() / total
                paint.color = color
                canvas.drawRect(x, 0f, x + w, height.toFloat(), paint)
                x += w
            }
        }
    }
}
