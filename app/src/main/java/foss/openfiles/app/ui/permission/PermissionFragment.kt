package foss.openfiles.app.ui.permission

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import foss.openfiles.app.R
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.main.MainActivity

/** Simple onboarding screen asking for storage access. */
class PermissionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.of_background))
            setPadding(dp(40), 0, dp(40), 0)

            addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_folder)
                setColorFilter(ThemeManager.folder(ctx))
            }, LinearLayout.LayoutParams(dp(96), dp(96)))

            addView(TextView(ctx).apply {
                text = getString(R.string.perm_title)
                textSize = 24f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
                gravity = Gravity.CENTER
                setPadding(0, dp(28), 0, 0)
            })

            addView(TextView(ctx).apply {
                text = getString(R.string.perm_message)
                textSize = 16f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_secondary))
                gravity = Gravity.CENTER
                setPadding(0, dp(14), 0, 0)
            })

            addView(Button(ctx).apply {
                text = getString(R.string.allow)
                textSize = 17f
                isAllCaps = false
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_pill)
                backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ThemeManager.accentStrong(ctx)
                )
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(dp(48), dp(14), dp(48), dp(14))
                setOnClickListener {
                    (activity as? MainActivity)?.requestStoragePermission()
                }
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(36) })
        }
    }
}
