package foss.openfiles.app.theme

import foss.openfiles.app.R

/**
 * A selectable accent palette. Mirrors how One UI derives its accents from the
 * wallpaper colour palette — here the user picks one manually so it works on
 * every Android version and device.
 */
data class Palette(
    val nameRes: Int,
    /** Light pastel accent used for titles, links and breadcrumbs. */
    val accent: Int,
    /** Stronger accent used for pills, checkboxes and switches. */
    val accentStrong: Int,
    /** Folder icon tint. */
    val folder: Int
)

object Palettes {

    val ALL = listOf(
        Palette(R.string.palette_blue, 0xFF95AFD4.toInt(), 0xFF4C86C8.toInt(), 0xFF8FA8C7.toInt()),
        Palette(R.string.palette_green, 0xFFA3C9A8.toInt(), 0xFF4C9A58.toInt(), 0xFF9BBCA0.toInt()),
        Palette(R.string.palette_purple, 0xFFB9A8D9.toInt(), 0xFF7D5BC0.toInt(), 0xFFAFA0CC.toInt()),
        Palette(R.string.palette_pink, 0xFFD9A8BC.toInt(), 0xFFC05B85.toInt(), 0xFFCCA0B2.toInt()),
        Palette(R.string.palette_orange, 0xFFD9BCA0.toInt(), 0xFFC08145.toInt(), 0xFFCCB29B.toInt()),
        Palette(R.string.palette_mint, 0xFF9CCFC6.toInt(), 0xFF3F9E8F.toInt(), 0xFF95C2BA.toInt()),
        Palette(R.string.palette_gray, 0xFFB5B9BE.toInt(), 0xFF6E747B.toInt(), 0xFFA9AEB4.toInt())
    )

    fun current(index: Int): Palette = ALL.getOrElse(index) { ALL[0] }
}
