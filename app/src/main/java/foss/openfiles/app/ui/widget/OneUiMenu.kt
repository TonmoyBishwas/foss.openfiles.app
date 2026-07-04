package foss.openfiles.app.ui.widget

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import foss.openfiles.app.R
import foss.openfiles.app.theme.ThemeManager

/**
 * Rounded dark popup menu in the One UI style: large text, generous padding,
 * dotted divider between groups, checkmark on the selected entry.
 */
class OneUiMenu(private val context: Context) {

    data class Item(
        val title: String,
        val checked: Boolean = false,
        val group: Int = 0,
        val action: () -> Unit
    )

    private val items = mutableListOf<Item>()

    fun add(title: String, checked: Boolean = false, group: Int = 0, action: () -> Unit) {
        items += Item(title, checked, group, action)
    }

    fun show(anchor: View, alignEnd: Boolean = true) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_popup)
            elevation = dp(8).toFloat()
            setPadding(0, dp(10), 0, dp(10))
        }

        lateinit var popup: PopupWindow
        var lastGroup = items.firstOrNull()?.group ?: 0
        val anyChecked = items.any { it.checked }

        for (item in items) {
            if (item.group != lastGroup) {
                container.addView(DottedDivider(context), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
                ).apply { setMargins(dp(22), dp(8), dp(22), dp(8)) })
                lastGroup = item.group
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(26), dp(13), dp(22), dp(13))
                setBackgroundResource(R.drawable.ripple_item)
                addView(TextView(context).apply {
                    text = item.title
                    textSize = 19f
                    setTextColor(
                        if (item.checked) ThemeManager.accent(context)
                        else ContextCompat.getColor(context, R.color.of_text_primary)
                    )
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (item.checked) {
                    addView(ImageView(context).apply {
                        setImageResource(R.drawable.ic_check)
                        setColorFilter(ThemeManager.accent(context))
                    }, LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                        marginStart = dp(24)
                    })
                } else if (anyChecked) {
                    addView(View(context), LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                        marginStart = dp(24)
                    })
                }
                setOnClickListener {
                    popup.dismiss()
                    item.action()
                }
            }
            container.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        popup = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(10).toFloat()
        }

        container.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val x = if (alignEnd) anchor.width - container.measuredWidth else 0
        popup.showAsDropDown(anchor, x, -anchor.height / 3)
    }

    /** Horizontal dotted line used as a menu group separator. */
    private class DottedDivider(context: Context) : View(context) {
        private val paint = android.graphics.Paint().apply {
            color = 0x55FFFFFF
            strokeWidth = resources.displayMetrics.density * 1.5f
            style = android.graphics.Paint.Style.STROKE
            pathEffect = android.graphics.DashPathEffect(
                floatArrayOf(resources.displayMetrics.density * 2f, resources.displayMetrics.density * 4f), 0f
            )
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val y = height / 2f
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
        }
    }
}
