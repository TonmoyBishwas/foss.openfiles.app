package foss.openfiles.app.ui.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Minimal flow layout: children keep their natural width and wrap onto the
 * next line when a row is full — used for the search filter chips so they
 * never get squeezed like a weighted row would.
 */
class FlowLayout(
    context: Context,
    private val hSpacingDp: Int = 8,
    private val vSpacingDp: Int = 10
) : ViewGroup(context) {

    private val hSpace get() = (hSpacingDp * resources.displayMetrics.density).toInt()
    private val vSpace get() = (vSpacingDp * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            )
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += rowHeight + vSpace
                rowHeight = 0
            }
            x += child.measuredWidth + hSpace
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            y + rowHeight + paddingTop + paddingBottom
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l - paddingLeft - paddingRight
        var x = 0
        var y = 0
        var rowHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            if (x > 0 && x + child.measuredWidth > width) {
                x = 0
                y += rowHeight + vSpace
                rowHeight = 0
            }
            child.layout(
                paddingLeft + x,
                paddingTop + y,
                paddingLeft + x + child.measuredWidth,
                paddingTop + y + child.measuredHeight
            )
            x += child.measuredWidth + hSpace
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
    }
}
