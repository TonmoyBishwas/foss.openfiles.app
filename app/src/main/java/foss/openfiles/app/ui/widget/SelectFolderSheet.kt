package foss.openfiles.app.ui.widget

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import foss.openfiles.app.R
import foss.openfiles.app.data.FileRepository
import foss.openfiles.app.data.Prefs
import foss.openfiles.app.data.Sorting
import foss.openfiles.app.data.StorageVolumes
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.main.MainActivity
import foss.openfiles.app.util.Format
import java.io.File

/**
 * The full-height "Select folder" sheet used by Move/Copy, with a storage chip,
 * breadcrumb, folder list and a bottom Cancel / Move here row.
 */
class SelectFolderSheet(
    private val activity: MainActivity,
    private val itemCount: Int,
    private val isMove: Boolean,
    private val onPick: (File) -> Unit
) {

    private lateinit var dialog: android.app.Dialog
    private lateinit var listBox: LinearLayout
    private lateinit var crumbBar: LinearLayout
    private lateinit var emptyHint: TextView
    private var volumes = StorageVolumes.list(activity)
    private var currentRoot = volumes.first().root
    private var currentDir = currentRoot

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    fun show() {
        val ctx = activity
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_bottom_sheet)
            setPadding(dp(24), dp(24), dp(24), dp(12))
        }

        // Header: title + search/new-folder buttons
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(ctx).apply {
            text = ctx.getString(R.string.select_folder)
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.accent(ctx))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(iconButton(R.drawable.ic_plus) {
            Dialogs.createFolder(ctx, currentDir) { navigate(currentDir) }
        })
        sheet.addView(header)

        // Storage chips
        val chipRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(18), 0, 0)
        }
        for (v in volumes) {
            val label = when {
                v.isPrimary -> ctx.getString(R.string.internal_storage)
                v.isUsb -> ctx.getString(R.string.usb_storage)
                else -> ctx.getString(R.string.sd_card)
            }
            chipRow.addView(TextView(ctx).apply {
                text = label
                textSize = 16f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_chip)
                setPadding(dp(18), dp(10), dp(18), dp(10))
                setOnClickListener {
                    currentRoot = v.root
                    navigate(v.root)
                }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) })
        }
        sheet.addView(chipRow)

        // Breadcrumb
        crumbBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        sheet.addView(HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(crumbBar)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
        ).apply { topMargin = dp(6) })

        // Folder list
        listBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        emptyHint = TextView(ctx).apply {
            text = ctx.getString(R.string.empty_dest_hint)
            textSize = 19f
            gravity = Gravity.CENTER
            setTextColor(ThemeManager.accent(ctx))
            visibility = View.GONE
        }
        val scroller = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
        }
        val listFrame = android.widget.FrameLayout(ctx).apply {
            addView(scroller, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(emptyHint, android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        sheet.addView(listFrame, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Bottom row: count badge + cancel + move here
        val bottom = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(6))
        }
        val badge = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_folder)
                setColorFilter(ThemeManager.folder(ctx))
            }, LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(TextView(ctx).apply {
                text = itemCount.toString()
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
            })
        }
        bottom.addView(badge)
        bottom.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))

        fun bottomButton(label: String, bold: Boolean, onClick: () -> Unit) =
            TextView(ctx).apply {
                text = label
                textSize = 20f
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
                setPadding(dp(20), dp(10), dp(20), dp(10))
                setBackgroundResource(R.drawable.ripple_item)
                setOnClickListener { onClick() }
            }

        bottom.addView(bottomButton(ctx.getString(R.string.cancel), false) { dialog.dismiss() })
        bottom.addView(bottomButton(
            ctx.getString(if (isMove) R.string.move_here else R.string.copy_here), true
        ) {
            dialog.dismiss()
            onPick(currentDir)
        })
        sheet.addView(bottom)

        dialog = android.app.Dialog(ctx).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(sheet)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (ctx.resources.displayMetrics.widthPixels * 0.96).toInt(),
                    (ctx.resources.displayMetrics.heightPixels * 0.92).toInt()
                )
                setGravity(Gravity.BOTTOM)
            }
        }
        navigate(currentDir)
        dialog.show()
    }

    private fun iconButton(iconRes: Int, onClick: () -> Unit): ImageButton =
        ImageButton(activity).apply {
            setImageResource(iconRes)
            setColorFilter(ContextCompat.getColor(activity, R.color.of_text_primary))
            background = ContextCompat.getDrawable(activity, R.drawable.ripple_circle)
            setOnClickListener { onClick() }
        }

    private fun navigate(dir: File) {
        currentDir = dir
        buildCrumbs()
        listBox.removeAllViews()
        val folders = FileRepository.list(
            dir, Prefs.showHidden, false, Sorting.NAME, true
        ).filter { it.isDirectory && it.name != ".openfiles_trash" }

        emptyHint.visibility = if (folders.isEmpty()) View.VISIBLE else View.GONE

        for (f in folders) {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(14), dp(4), dp(14))
                setBackgroundResource(R.drawable.ripple_item)
                setOnClickListener { navigate(f.file) }
            }
            row.addView(ImageView(activity).apply {
                setImageResource(R.drawable.ic_folder)
                setColorFilter(ThemeManager.folder(activity))
            }, LinearLayout.LayoutParams(dp(38), dp(38)))
            val col = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), 0, 0, 0)
            }
            col.addView(TextView(activity).apply {
                text = f.name
                textSize = 19f
                setTextColor(ContextCompat.getColor(activity, R.color.of_text_primary))
            })
            col.addView(TextView(activity).apply {
                text = Format.date(f.lastModified)
                textSize = 14f
                setTextColor(ContextCompat.getColor(activity, R.color.of_text_secondary))
            })
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(activity).apply {
                text = when (f.childCount) {
                    0 -> activity.getString(R.string.no_items)
                    1 -> activity.getString(R.string.item_count_one)
                    else -> activity.getString(R.string.items_count, f.childCount)
                }
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(activity, R.color.of_text_secondary))
            })
            listBox.addView(row)

            listBox.addView(View(activity).apply {
                setBackgroundColor(ContextCompat.getColor(activity, R.color.of_divider))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).apply { marginStart = dp(56) })
        }
    }

    private fun buildCrumbs() {
        crumbBar.removeAllViews()
        val accent = ThemeManager.accent(activity)

        val segments = mutableListOf<File>()
        var f: File? = currentDir
        while (f != null && f.absolutePath.startsWith(currentRoot.absolutePath)) {
            segments.add(0, f)
            if (f == currentRoot) break
            f = f.parentFile
        }

        for ((i, seg) in segments.withIndex()) {
            if (i > 0) {
                crumbBar.addView(ImageView(activity).apply {
                    setImageResource(R.drawable.ic_crumb_arrow)
                    setColorFilter(accent)
                }, LinearLayout.LayoutParams(dp(11), dp(11)).apply {
                    marginStart = dp(7); marginEnd = dp(7)
                })
            }
            val isLast = i == segments.size - 1
            crumbBar.addView(TextView(activity).apply {
                text = if (seg == currentRoot) {
                    when {
                        volumes.first { it.root == currentRoot }.isPrimary ->
                            activity.getString(R.string.internal_storage)
                        else -> activity.getString(R.string.sd_card)
                    }
                } else seg.name
                textSize = 16f
                setTextColor(accent)
                if (isLast) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.ripple_item)
                setPadding(dp(3), dp(5), dp(3), dp(5))
                setOnClickListener { if (!isLast) navigate(seg) }
            })
        }
    }
}
