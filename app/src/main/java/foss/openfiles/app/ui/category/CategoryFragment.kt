package foss.openfiles.app.ui.category

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import foss.openfiles.app.R
import foss.openfiles.app.data.FileItem
import foss.openfiles.app.data.MediaQuery
import foss.openfiles.app.data.Prefs
import foss.openfiles.app.data.Sorting
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.browser.FileAdapter
import foss.openfiles.app.ui.grid.GridAdapter
import foss.openfiles.app.ui.main.MainActivity
import foss.openfiles.app.ui.search.SearchFragment
import foss.openfiles.app.ui.widget.OneUiMenu
import foss.openfiles.app.util.Format
import foss.openfiles.app.util.Open
import java.util.concurrent.Executors

/** Category browser: Images/Videos as a grid, others as a list, like the stock app. */
class CategoryFragment : Fragment(), FileAdapter.Listener {

    companion object {
        private const val ARG_CATEGORY = "category"

        fun newInstance(category: MediaQuery.Category) = CategoryFragment().apply {
            arguments = Bundle().apply { putString(ARG_CATEGORY, category.name) }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var category: MediaQuery.Category
    private var items: List<FileItem> = emptyList()

    private val isGrid: Boolean
        get() = category == MediaQuery.Category.IMAGES || category == MediaQuery.Category.VIDEOS

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        category = MediaQuery.Category.valueOf(requireArguments().getString(ARG_CATEGORY)!!)

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressed()
        }
        view.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            (activity as MainActivity).push(SearchFragment())
        }
        view.findViewById<ImageButton>(R.id.btnMore).visibility = View.GONE
        view.findViewById<View>(R.id.btnFilter).visibility = View.INVISIBLE
        view.findViewById<View>(R.id.btnSort).setOnClickListener { showSortMenu(it) }
        view.findViewById<ImageButton>(R.id.btnSortDir).setOnClickListener {
            Prefs.sortAscending = !Prefs.sortAscending
            updateSortRow(); load()
        }

        buildBreadcrumb(view)
        updateSortRow()
        load()
    }

    private fun titleRes(): Int = when (category) {
        MediaQuery.Category.IMAGES -> R.string.cat_images
        MediaQuery.Category.VIDEOS -> R.string.cat_videos
        MediaQuery.Category.AUDIO -> R.string.cat_audio
        MediaQuery.Category.DOCUMENTS -> R.string.cat_documents
        MediaQuery.Category.DOWNLOADS -> R.string.cat_downloads
        MediaQuery.Category.APK -> R.string.cat_apk
    }

    private fun buildBreadcrumb(view: View) {
        val bar = view.findViewById<LinearLayout>(R.id.breadcrumbBar)
        bar.removeAllViews()
        val ctx = requireContext()
        val accent = ThemeManager.accent(ctx)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val home = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_home_crumb)
            setColorFilter(accent)
            setOnClickListener { requireActivity().onBackPressed() }
        }
        bar.addView(home, LinearLayout.LayoutParams(dp(34), dp(34)))

        bar.addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_crumb_arrow)
            setColorFilter(accent)
        }, LinearLayout.LayoutParams(dp(12), dp(12)).apply {
            marginStart = dp(8); marginEnd = dp(8)
        })

        bar.addView(TextView(ctx).apply {
            setText(titleRes())
            textSize = 18f
            setTextColor(accent)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        // Right side: total size
        val totalLabel = TextView(ctx).apply {
            textSize = 18f
            setTextColor(accent)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            tag = "totalSize"
        }
        val parent = bar.parent.parent as LinearLayout // breadcrumbScroll's parent row
        // Keep simple: append into breadcrumb bar with padding.
        bar.addView(totalLabel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(24) })
    }

    private fun load() {
        val list = view?.findViewById<RecyclerView>(R.id.fileList) ?: return
        executor.execute {
            val ctx = context ?: return@execute
            var data = MediaQuery.query(ctx, category, Prefs.showHidden)
            val comparator = Sorting.comparator(Prefs.sortMode, Prefs.sortAscending)
            data = data.sortedWith(comparator)
            if (category == MediaQuery.Category.DOWNLOADS) {
                // Keep natural date grouping for downloads
                data = data.sortedByDescending { it.lastModified }
            }
            view?.post {
                if (!isAdded) return@post
                items = data
                view?.findViewById<TextView>(R.id.emptyView)?.visibility =
                    if (data.isEmpty()) View.VISIBLE else View.GONE

                val totalBytes = data.sumOf { it.size }
                view?.findViewWithTag<TextView>("totalSize")?.text = Format.size(totalBytes)

                if (isGrid) {
                    list.layoutManager = GridLayoutManager(requireContext(), 4)
                    list.adapter = GridAdapter(data) { item ->
                        Open.file(requireActivity(), item.file)
                    }
                } else {
                    list.layoutManager = LinearLayoutManager(requireContext())
                    val adapter = FileAdapter(this)
                    adapter.items = data
                    list.adapter = adapter
                }
            }
        }
    }

    private fun showSortMenu(anchor: View) {
        val labels = listOf(
            R.string.sort_name to Sorting.NAME,
            R.string.sort_date to Sorting.DATE,
            R.string.sort_type to Sorting.TYPE,
            R.string.sort_size to Sorting.SIZE
        )
        OneUiMenu(requireContext()).apply {
            for ((res, mode) in labels) {
                add(getString(res), checked = Prefs.sortMode == mode) {
                    Prefs.sortMode = mode
                    updateSortRow(); load()
                }
            }
        }.show(anchor)
    }

    private fun updateSortRow() {
        val v = view ?: return
        v.findViewById<TextView>(R.id.sortLabel).setText(
            when (Prefs.sortMode) {
                Sorting.DATE -> R.string.sort_date
                Sorting.TYPE -> R.string.sort_type
                Sorting.SIZE -> R.string.sort_size
                else -> R.string.sort_name
            }
        )
        v.findViewById<ImageButton>(R.id.btnSortDir).rotation =
            if (Prefs.sortAscending) 0f else 180f
    }

    // List item interactions (non-grid categories)
    override fun onItemClick(item: FileItem) {
        Open.file(requireActivity(), item.file)
    }

    override fun onItemLongClick(item: FileItem) = Unit
    override fun onSelectionToggled(item: FileItem) = Unit
}
