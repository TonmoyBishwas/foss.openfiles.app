package foss.openfiles.app.data

import android.content.Context
import android.content.SharedPreferences

/** Tiny wrapper around SharedPreferences for app-wide settings. */
object Prefs {

    private const val SEP = "\u001F"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("openfiles", Context.MODE_PRIVATE)
    }

    /** Index into the palette list; -1 means dynamic (Material You) when available. */
    var palette: Int
        get() = sp.getInt("palette", 0)
        set(value) = sp.edit().putInt("palette", value).apply()

    var showHidden: Boolean
        get() = sp.getBoolean("show_hidden", false)
        set(value) = sp.edit().putBoolean("show_hidden", value).apply()

    var useTrash: Boolean
        get() = sp.getBoolean("use_trash", true)
        set(value) = sp.edit().putBoolean("use_trash", value).apply()

    var sortMode: Int
        get() = sp.getInt("sort_mode", 0)
        set(value) = sp.edit().putInt("sort_mode", value).apply()

    var sortAscending: Boolean
        get() = sp.getBoolean("sort_asc", true)
        set(value) = sp.edit().putBoolean("sort_asc", value).apply()

    var viewMode: Int
        get() = sp.getInt("view_mode", 0)
        set(value) = sp.edit().putInt("view_mode", value).apply()

    var essentialsFilter: Boolean
        get() = sp.getBoolean("essentials", false)
        set(value) = sp.edit().putBoolean("essentials", value).apply()

    var recentSearches: List<String>
        get() = sp.getString("recent_searches", "")!!.split(SEP).filter { it.isNotEmpty() }
        set(value) = sp.edit()
            .putString("recent_searches", value.take(10).joinToString(SEP)).apply()

    var showRecentSearches: Boolean
        get() = sp.getBoolean("show_recent_searches", true)
        set(value) = sp.edit().putBoolean("show_recent_searches", value).apply()
}
