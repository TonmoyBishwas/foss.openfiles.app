package foss.openfiles.app.ui.settings

import android.graphics.drawable.GradientDrawable
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
import foss.openfiles.app.data.Prefs
import foss.openfiles.app.theme.Palettes
import foss.openfiles.app.theme.ThemeManager

/**
 * Colour palette picker: swatch rows the user can pick manually, mirroring the
 * "Colour palette" concept from device theming — plus a dynamic option on 12+.
 */
class PaletteFragment : Fragment() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private lateinit var listBox: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.of_background))
        }

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
            text = getString(R.string.colour_palette)
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.accent(ctx))
            setPadding(dp(8), 0, 0, 0)
        })
        root.addView(bar)

        listBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(30))
        }
        root.addView(ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox)
        })

        render()
        return root
    }

    private fun render() {
        val ctx = requireContext()
        listBox.removeAllViews()

        if (ThemeManager.dynamicAvailable()) {
            listBox.addView(paletteRow(
                title = getString(R.string.palette_dynamic),
                subtitle = getString(R.string.palette_dynamic_desc),
                colors = intArrayOf(
                    ContextCompat.getColor(ctx, android.R.color.system_accent1_200),
                    ContextCompat.getColor(ctx, android.R.color.system_accent1_400),
                    ContextCompat.getColor(ctx, android.R.color.system_accent1_600)
                ),
                selected = Prefs.palette == ThemeManager.DYNAMIC
            ) {
                Prefs.palette = ThemeManager.DYNAMIC
                render()
            })
        }

        for ((i, p) in Palettes.ALL.withIndex()) {
            listBox.addView(paletteRow(
                title = getString(p.nameRes),
                subtitle = null,
                colors = intArrayOf(p.accent, p.accentStrong, p.folder),
                selected = Prefs.palette == i
            ) {
                Prefs.palette = i
                render()
            })
        }
    }

    private fun paletteRow(
        title: String,
        subtitle: String?,
        colors: IntArray,
        selected: Boolean,
        onClick: () -> Unit
    ): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_card)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF141516.toInt())
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setOnClickListener { onClick() }
        }

        // Vertical swatch stack like the Colour palette preview tile
        val swatch = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0xFF000000.toInt())
            }
        }
        for (c in colors) {
            swatch.addView(View(ctx).apply { setBackgroundColor(c) },
                LinearLayout.LayoutParams(dp(34), dp(15)))
        }
        row.addView(swatch)

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, 0, 0)
        }
        col.addView(TextView(ctx).apply {
            text = title
            textSize = 19f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        })
        if (subtitle != null) {
            col.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 14f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_secondary))
            })
        }
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (selected) {
            row.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_check)
                setColorFilter(ThemeManager.accentStrong(ctx))
            }, LinearLayout.LayoutParams(dp(24), dp(24)))
        }

        (row.layoutParams as? LinearLayout.LayoutParams)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
        row.layoutParams = lp
        return row
    }
}
