package foss.openfiles.app.theme

import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import foss.openfiles.app.data.Prefs

/**
 * Resolves the active accent colours. The user can pick a fixed palette or,
 * on Android 12+, follow the device (Material You) colours — mimicking how the
 * stock file manager follows the wallpaper palette.
 */
object ThemeManager {

    const val DYNAMIC = -1

    fun accent(context: Context): Int = resolve(context).accent

    fun accentStrong(context: Context): Int = resolve(context).accentStrong

    fun folder(context: Context): Int = resolve(context).folder

    fun dynamicAvailable(): Boolean = Build.VERSION.SDK_INT >= 31

    private fun resolve(context: Context): Palette {
        val idx = Prefs.palette
        if (idx == DYNAMIC && dynamicAvailable()) {
            return Palette(
                nameRes = 0,
                accent = ContextCompat.getColor(context, android.R.color.system_accent1_200),
                accentStrong = ContextCompat.getColor(context, android.R.color.system_accent1_400),
                folder = ContextCompat.getColor(context, android.R.color.system_accent1_300)
            )
        }
        return Palettes.current(if (idx < 0) 0 else idx)
    }
}
