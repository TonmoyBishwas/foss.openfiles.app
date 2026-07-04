package foss.openfiles.app.ui.widget

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * The storage usage pill from the home screen: a rounded track with the used
 * fraction filled in the accent colour, matching the stock two-tone pill.
 * Used as a TextView background so it always matches the text size.
 */
class PillProgressDrawable(
    private val trackColor: Int,
    private val fillColor: Int,
    private val fraction: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val r = b.height() / 2f
        clip.reset()
        clip.addRoundRect(
            b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
            r, r, Path.Direction.CW
        )
        canvas.save()
        canvas.clipPath(clip)

        paint.color = trackColor
        canvas.drawRect(b, paint)

        paint.color = fillColor
        canvas.drawRect(
            b.left.toFloat(), b.top.toFloat(),
            b.left + b.width() * fraction.coerceIn(0f, 1f), b.bottom.toFloat(), paint
        )
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
