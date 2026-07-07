package foss.openfiles.app.ui.recycle

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
import foss.openfiles.app.data.Trash
import foss.openfiles.app.data.TrashEntry
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.widget.Dialogs
import foss.openfiles.app.ui.widget.OneUiMenu
import foss.openfiles.app.util.Format
import java.util.concurrent.Executors

/** Recycle bin screen: info notice, day-grouped entries, restore / delete. */
class RecycleBinFragment : Fragment() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var listBox: LinearLayout
    private lateinit var countLabel: TextView

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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

        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        titleCol.addView(TextView(ctx).apply {
            text = getString(R.string.recycle_bin)
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.accent(ctx))
        })
        countLabel = TextView(ctx).apply {
            textSize = 15f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        }
        titleCol.addView(countLabel)
        bar.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        bar.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_more)
            setColorFilter(ContextCompat.getColor(ctx, R.color.of_text_primary))
            background = ContextCompat.getDrawable(ctx, R.drawable.ripple_circle)
            setOnClickListener { anchor ->
                OneUiMenu(ctx).apply {
                    add(getString(R.string.empty_bin)) { emptyBin() }
                }.show(anchor)
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar)

        // Notice text
        root.addView(TextView(ctx).apply {
            text = getString(R.string.bin_notice)
            textSize = 17f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
            setPadding(dp(20), dp(16), dp(20), dp(8))
        })

        listBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        root.addView(ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        return root
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun reload() {
        executor.execute {
            val ctx = context ?: return@execute
            val entries = Trash.list(ctx)
            view?.post {
                if (!isAdded) return@post
                render(entries)
            }
        }
    }

    private fun render(entries: List<TrashEntry>) {
        val ctx = requireContext()
        listBox.removeAllViews()
        countLabel.text = when (entries.size) {
            0 -> getString(R.string.no_items)
            1 -> getString(R.string.item_count_one)
            else -> getString(R.string.items_count, entries.size)
        }

        var lastDays = -1
        for (entry in entries) {
            if (entry.daysLeft != lastDays) {
                lastDays = entry.daysLeft
                listBox.addView(sectionHeader(getString(R.string.days_until_deletion, entry.daysLeft)))
            }
            listBox.addView(entryRow(entry))
        }
    }

    private fun sectionHeader(text: String): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(6))
        }
        row.addView(TextView(ctx).apply {
            this.text = text
            textSize = 16f
            setTextColor(ThemeManager.accent(ctx))
        })
        row.addView(DottedLine(ctx), LinearLayout.LayoutParams(0, dp(2), 1f).apply {
            marginStart = dp(14)
        })
        return row
    }

    private fun entryRow(entry: TrashEntry): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setBackgroundResource(R.drawable.ripple_item)
            setOnClickListener {
                OneUiMenu(ctx).apply {
                    add(getString(R.string.restore)) { restore(entry) }
                    add(getString(R.string.delete)) { deleteForever(entry) }
                }.show(it)
            }
        }
        row.addView(ImageView(ctx).apply {
            setImageResource(
                if (entry.isDirectory) R.drawable.ic_folder else R.drawable.ic_file_generic
            )
            setColorFilter(
                if (entry.isDirectory) ThemeManager.folder(ctx) else 0xFF8F9296.toInt()
            )
        }, LinearLayout.LayoutParams(dp(40), dp(40)))

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, 0, 0)
        }
        col.addView(TextView(ctx).apply {
            text = entry.name
            textSize = 19f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        })
        col.addView(TextView(ctx).apply {
            text = Format.date(entry.trashedAt)
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_secondary))
        })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(ctx).apply {
            text = Format.size(entry.size)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_secondary))
        })
        return row
    }

    private fun restore(entry: TrashEntry) {
        executor.execute {
            val ctx = context ?: return@execute
            Trash.restore(ctx, entry)
            view?.post { reload() }
        }
    }

    private fun deleteForever(entry: TrashEntry) {
        Dialogs.confirmDelete(requireContext(), 1, toTrash = false) {
            executor.execute {
                val ctx = context ?: return@execute
                Trash.deleteForever(ctx, entry)
                view?.post { reload() }
            }
        }
    }

    private fun emptyBin() {
        executor.execute {
            val ctx = context ?: return@execute
            val entries = Trash.list(ctx)
            view?.post {
                if (!isAdded || entries.isEmpty()) return@post
                Dialogs.confirmDelete(requireContext(), entries.size, toTrash = false) {
                    executor.execute {
                        val c = context ?: return@execute
                        entries.forEach { Trash.deleteForever(c, it) }
                        view?.post { reload() }
                    }
                }
            }
        }
    }

    /** Dotted horizontal rule used next to section headers. */
    private class DottedLine(context: android.content.Context) : View(context) {
        private val paint = android.graphics.Paint().apply {
            color = 0x44FFFFFF
            strokeWidth = resources.displayMetrics.density * 1.5f
            style = android.graphics.Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(
                floatArrayOf(
                    resources.displayMetrics.density * 2f,
                    resources.displayMetrics.density * 5f
                ), 0f
            )
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val y = height / 2f
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
        }
    }
}
