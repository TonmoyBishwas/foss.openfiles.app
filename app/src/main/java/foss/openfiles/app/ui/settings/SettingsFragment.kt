package foss.openfiles.app.ui.settings

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import foss.openfiles.app.BuildConfig
import androidx.fragment.app.Fragment
import foss.openfiles.app.R
import foss.openfiles.app.data.Prefs
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.main.MainActivity

/** OpenFiles settings screen with rounded One UI card groups. */
class SettingsFragment : Fragment() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
            text = getString(R.string.of_settings)
            textSize = 26f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ThemeManager.accent(ctx))
            setPadding(dp(8), 0, 0, 0)
        })
        root.addView(bar)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(30))
        }

        // Appearance group
        content.addView(groupLabel(R.string.appearance))
        content.addView(card().also { card ->
            card.addView(navRow(getString(R.string.colour_palette),
                getString(R.string.colour_palette_desc)) {
                (activity as MainActivity).push(PaletteFragment())
            })
        })

        // File management group
        content.addView(groupLabel(R.string.file_management))
        content.addView(card().also { card ->
            card.addView(switchRow(getString(R.string.show_hidden), Prefs.showHidden) {
                Prefs.showHidden = it
            })
            card.addView(divider())
            card.addView(switchRow(getString(R.string.trash_setting), Prefs.useTrash) {
                Prefs.useTrash = it
            })
        })

        // About group
        content.addView(card().also { card ->
            card.addView(navRow(getString(R.string.about),
                getString(R.string.version, BuildConfig.VERSION_NAME)) {
                openGitHub()
            })
            card.addView(divider())
            card.addView(navRow(getString(R.string.open_source_licenses), null) {
                openGitHub()
            })
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(22) })

        root.addView(ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = false
            addView(content)
        })
        return root
    }

    private fun openGitHub() {
        runCatching {
            startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/TonmoyBishwas/foss.openfiles.app")
                )
            )
        }
    }

    private fun groupLabel(res: Int): TextView =
        TextView(requireContext()).apply {
            setText(res)
            textSize = 15f
            setTextColor(ThemeManager.accent(requireContext()))
            setPadding(dp(14), dp(22), dp(14), dp(10))
        }

    private fun card(): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_card)
            backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF141516.toInt())
        }

    private fun divider(): View =
        View(requireContext()).apply {
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.of_divider))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).apply { marginStart = dp(22); marginEnd = dp(22) }
        }

    private fun navRow(title: String, subtitle: String?, onClick: () -> Unit): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            setBackgroundResource(R.drawable.ripple_item)
            setOnClickListener { onClick() }
        }
        row.addView(TextView(ctx).apply {
            text = title
            textSize = 19f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        })
        if (subtitle != null) {
            row.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 15f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_secondary))
                setPadding(0, dp(3), 0, 0)
            })
        }
        return row
    }

    private fun switchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(14))
        }
        row.addView(TextView(ctx).apply {
            text = title
            textSize = 19f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(ctx).apply {
            isChecked = checked
            thumbTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFBBBBBB.toInt())
            )
            trackTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ThemeManager.accentStrong(ctx), 0xFF55585C.toInt())
            )
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }
        row.addView(sw)
        return row
    }
}
