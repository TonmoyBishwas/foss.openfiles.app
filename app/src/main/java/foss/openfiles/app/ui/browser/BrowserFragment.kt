package foss.openfiles.app.ui.browser

import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import foss.openfiles.app.R
import foss.openfiles.app.data.FileItem
import foss.openfiles.app.data.FileOps
import foss.openfiles.app.data.FileRepository
import foss.openfiles.app.data.Prefs
import foss.openfiles.app.data.Sorting
import foss.openfiles.app.data.Trash
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.main.MainActivity
import foss.openfiles.app.ui.search.SearchFragment
import foss.openfiles.app.ui.settings.SettingsFragment
import foss.openfiles.app.ui.recycle.RecycleBinFragment
import foss.openfiles.app.ui.widget.Dialogs
import foss.openfiles.app.ui.widget.OneUiMenu
import foss.openfiles.app.ui.widget.SelectFolderSheet
import foss.openfiles.app.util.Open
import java.io.File
import java.util.concurrent.Executors

/** The folder browser — the heart of the app. */
class BrowserFragment : Fragment(), MainActivity.BackHandler, FileAdapter.Listener {

    companion object {
        private const val ARG_ROOT = "root"
        private const val ARG_PATH = "path"

        fun newInstance(rootPath: String, path: String = rootPath) = BrowserFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_ROOT, rootPath)
                putString(ARG_PATH, path)
            }
        }
    }

    private lateinit var rootDir: File
    private lateinit var currentDir: File
    private lateinit var adapter: FileAdapter
    private lateinit var list: RecyclerView
    private val executor = Executors.newSingleThreadExecutor()
    private var items: List<FileItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_browser, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        rootDir = File(requireArguments().getString(ARG_ROOT)!!)
        currentDir = File(
            savedInstanceState?.getString(ARG_PATH)
                ?: requireArguments().getString(ARG_PATH)!!
        )

        list = view.findViewById(R.id.fileList)
        applyViewMode()

        view.findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            requireActivity().onBackPressed()
        }
        view.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            (activity as MainActivity).push(SearchFragment())
        }
        view.findViewById<ImageButton>(R.id.btnMore).setOnClickListener { showOverflow(it) }
        view.findViewById<View>(R.id.btnFilter).setOnClickListener { showFilterMenu(it) }
        view.findViewById<View>(R.id.btnSort).setOnClickListener { showSortMenu(it) }
        view.findViewById<ImageButton>(R.id.btnSortDir).setOnClickListener {
            Prefs.sortAscending = !Prefs.sortAscending
            updateSortRow()
            reload()
        }
        view.findViewById<TextView>(R.id.btnCancelSelect).setOnClickListener { exitSelection() }
        view.findViewById<View>(R.id.selectAllBox).setOnClickListener { toggleSelectAll() }

        updateSortRow()
        tintAccents(view)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::currentDir.isInitialized) outState.putString(ARG_PATH, currentDir.absolutePath)
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    private fun tintAccents(view: View) {
        val accent = ThemeManager.accent(requireContext())
        view.findViewById<TextView>(R.id.toolbarTitle).setTextColor(accent)
    }

    // ---- Data -------------------------------------------------------------

    private fun reload() {
        val dir = currentDir
        executor.execute {
            val listData = FileRepository.list(
                dir,
                showHidden = Prefs.showHidden,
                essentialsOnly = Prefs.essentialsFilter && dir == rootDir,
                sortMode = Prefs.sortMode,
                ascending = Prefs.sortAscending
            ).filter { it.name != ".openfiles_trash" }
            view?.post {
                if (!isAdded || currentDir != dir) return@post
                items = listData
                adapter.items = listData
                view?.findViewById<TextView>(R.id.emptyView)?.visibility =
                    if (listData.isEmpty()) View.VISIBLE else View.GONE
                list.visibility = if (listData.isEmpty()) View.GONE else View.VISIBLE
                buildBreadcrumb()
            }
        }
    }

    private fun applyViewMode() {
        val grid = Prefs.viewMode == 2
        adapter = FileAdapter(this, grid)
        list.layoutManager = if (grid) {
            GridLayoutManager(requireContext(), 4)
        } else {
            LinearLayoutManager(requireContext())
        }
        list.adapter = adapter
        adapter.items = items
    }

    // ---- Breadcrumb -------------------------------------------------------

    private fun buildBreadcrumb() {
        val bar = view?.findViewById<LinearLayout>(R.id.breadcrumbBar) ?: return
        bar.removeAllViews()
        val ctx = requireContext()
        val accent = ThemeManager.accent(ctx)
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        // Home icon chip
        val home = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_home_crumb)
            setColorFilter(accent)
            setBackgroundResource(R.drawable.ripple_item)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setOnClickListener {
                exitSelection()
                parentFragmentManager.popBackStack(
                    null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            }
        }
        bar.addView(home, LinearLayout.LayoutParams(dp(34), dp(34)))

        // Path segments from volume root to current
        val segments = mutableListOf<File>()
        var f: File? = currentDir
        while (f != null && f.absolutePath.startsWith(rootDir.absolutePath)) {
            segments.add(0, f)
            if (f == rootDir) break
            f = f.parentFile
        }

        for ((i, seg) in segments.withIndex()) {
            val arrow = ImageView(ctx).apply {
                setImageResource(R.drawable.ic_crumb_arrow)
                setColorFilter(accent)
                alpha = 0.85f
            }
            bar.addView(arrow, LinearLayout.LayoutParams(dp(12), dp(12)).apply {
                marginStart = dp(8); marginEnd = dp(8)
            })

            val isLast = i == segments.size - 1
            val label = TextView(ctx).apply {
                text = if (seg == rootDir) volumeLabel() else seg.name
                textSize = 18f
                setTextColor(accent)
                if (isLast) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.ripple_item)
                setPadding(dp(4), dp(6), dp(4), dp(6))
                if (isLast) {
                    // Long names auto-scroll (marquee) inside the visible width.
                    isSingleLine = true
                    ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                    marqueeRepeatLimit = -1
                    maxWidth = resources.displayMetrics.widthPixels - dp(110)
                    isSelected = true
                }
                setOnClickListener {
                    if (!isLast) navigateTo(seg)
                }
            }
            bar.addView(label)
        }

        // Scroll to the current (last) crumb once the new views have been laid out.
        val scroll = view?.findViewById<HorizontalScrollView>(R.id.breadcrumbScroll)
        scroll?.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, l: Int, t: Int, r: Int, b: Int,
                ol: Int, ot: Int, or2: Int, ob: Int
            ) {
                v.removeOnLayoutChangeListener(this)
                scroll.scrollTo(bar.width, 0)
            }
        })
        scroll?.requestLayout()

        // Filter row only at volume root, like the stock app
        view?.findViewById<TextView>(R.id.filterLabel)?.setText(
            if (Prefs.essentialsFilter) R.string.filter_essentials else R.string.filter_all
        )
    }

    private fun volumeLabel(): String = when {
        rootDir == Environment.getExternalStorageDirectory() ->
            getString(R.string.internal_storage)
        rootDir.absolutePath.contains("usb", true) -> getString(R.string.usb_storage)
        else -> getString(R.string.sd_card)
    }

    private fun navigateTo(dir: File) {
        exitSelection()
        currentDir = dir
        reload()
    }

    // ---- Item interaction ---------------------------------------------------

    override fun onItemClick(item: FileItem) {
        if (item.isDirectory) {
            navigateTo(item.file)
        } else {
            Open.file(requireActivity(), item.file)
        }
    }

    override fun onItemLongClick(item: FileItem) {
        if (!adapter.selectionMode) {
            adapter.selected.add(item.path)
            enterSelection() // selectionMode setter rebinds the list once
        }
    }

    override fun onSelectionToggled(item: FileItem) {
        // The adapter already toggled the item and rebound just that row.
        updateSelectionUi()
    }

    // ---- Selection mode -----------------------------------------------------

    private fun enterSelection() {
        adapter.selectionMode = true
        view?.findViewById<View>(R.id.toolbarNormal)?.visibility = View.GONE
        view?.findViewById<View>(R.id.toolbarSelect)?.visibility = View.VISIBLE
        buildBottomBar()
        view?.findViewById<View>(R.id.bottomBar)?.visibility = View.VISIBLE
        updateSelectionUi()
    }

    private fun exitSelection() {
        if (!adapter.selectionMode) return
        adapter.selected.clear()
        adapter.selectionMode = false
        view?.findViewById<View>(R.id.toolbarNormal)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.toolbarSelect)?.visibility = View.GONE
        view?.findViewById<View>(R.id.bottomBar)?.visibility = View.GONE
    }

    private fun toggleSelectAll() {
        if (adapter.selected.size == items.size) {
            adapter.selected.clear()
        } else {
            adapter.selected.clear()
            adapter.selected.addAll(items.map { it.path })
        }
        adapter.notifyDataSetChanged()
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        val count = adapter.selected.size
        view?.findViewById<TextView>(R.id.selectCount)?.text =
            getString(R.string.n_selected, count)
        val allCheck = view?.findViewById<ImageView>(R.id.selectAllCheck) ?: return
        val all = count == items.size && count > 0
        FileAdapter.bindCheckCircle(allCheck, all)
    }

    private fun selectedFiles(): List<File> = adapter.selected.map { File(it) }

    private fun buildBottomBar() {
        val bar = view?.findViewById<LinearLayout>(R.id.bottomBar) ?: return
        bar.removeAllViews()
        data class Action(val labelRes: Int, val iconRes: Int, val onClick: (View) -> Unit)
        val actions = listOf(
            Action(R.string.move, R.drawable.ic_op_move) { startMoveOrCopy(move = true) },
            Action(R.string.copy, R.drawable.ic_op_copy) { startMoveOrCopy(move = false) },
            Action(R.string.share, R.drawable.ic_op_share) {
                Open.share(requireActivity(), selectedFiles().filter { it.isFile })
            },
            Action(R.string.delete, R.drawable.ic_trash) { confirmDelete() },
            Action(R.string.more, R.drawable.ic_more) { anchor -> showMoreMenu(anchor) }
        )
        for (a in actions) {
            val cell = layoutInflater.inflate(R.layout.item_bottom_action, bar, false)
            cell.findViewById<ImageView>(R.id.icon).setImageResource(a.iconRes)
            cell.findViewById<TextView>(R.id.label).setText(a.labelRes)
            cell.setOnClickListener { a.onClick(it) }
            bar.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    // ---- Menus ----------------------------------------------------------------

    private fun showOverflow(anchor: View) {
        OneUiMenu(requireContext()).apply {
            add(getString(R.string.select)) {
                if (items.isNotEmpty()) enterSelection()
            }
            add(getString(R.string.view)) { showViewMenu(anchor) }
            add(getString(R.string.create_folder)) { promptCreateFolder() }
            add(getString(R.string.recycle_bin), group = 1) {
                (activity as MainActivity).push(RecycleBinFragment())
            }
            add(getString(R.string.settings), group = 1) {
                (activity as MainActivity).push(SettingsFragment())
            }
        }.show(anchor)
    }

    private fun showViewMenu(anchor: View) {
        OneUiMenu(requireContext()).apply {
            add(getString(R.string.view_list), checked = Prefs.viewMode == 0) {
                Prefs.viewMode = 0; applyViewMode(); reload()
            }
            add(getString(R.string.view_detailed_list), checked = Prefs.viewMode == 1) {
                Prefs.viewMode = 1; applyViewMode(); reload()
            }
            add(getString(R.string.view_grid), checked = Prefs.viewMode == 2) {
                Prefs.viewMode = 2; applyViewMode(); reload()
            }
        }.show(anchor)
    }

    private fun showFilterMenu(anchor: View) {
        OneUiMenu(requireContext()).apply {
            add(getString(R.string.filter_essentials), checked = Prefs.essentialsFilter) {
                Prefs.essentialsFilter = true; reload()
            }
            add(getString(R.string.filter_all), checked = !Prefs.essentialsFilter) {
                Prefs.essentialsFilter = false; reload()
            }
        }.show(anchor, alignEnd = false)
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
                    updateSortRow()
                    reload()
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

    private fun showMoreMenu(anchor: View) {
        val files = selectedFiles()
        if (files.isEmpty()) return
        val single = files.size == 1
        OneUiMenu(requireContext()).apply {
            add(getString(R.string.details)) { Dialogs.details(requireContext(), files) }
            if (single) {
                add(getString(R.string.rename)) {
                    Dialogs.rename(requireContext(), files[0]) {
                        exitSelection(); reload()
                    }
                }
            }
            add(getString(R.string.compress)) {
                Dialogs.compressName(requireContext(), currentDir, files) {
                    exitSelection(); reload()
                }
            }
            if (single && files[0].extension.lowercase() == "zip") {
                add(getString(R.string.extract)) {
                    Dialogs.runOperation(
                        requireContext(), getString(R.string.extract)
                    ) { FileOps.extract(files[0], currentDir) }
                        .invokeOnCompletion { exitSelection(); view?.post { reload() } }
                }
            }
        }.show(anchor)
    }

    // ---- Operations -------------------------------------------------------------

    private fun startMoveOrCopy(move: Boolean) {
        val files = selectedFiles()
        if (files.isEmpty()) return
        SelectFolderSheet(requireActivity() as MainActivity, files.size, move) { dest ->
            Dialogs.runOperation(
                requireContext(),
                getString(if (move) R.string.move else R.string.copy)
            ) {
                if (move) FileOps.move(files, dest) else FileOps.copy(files, dest)
            }.invokeOnCompletion {
                exitSelection()
                view?.post { reload() }
            }
        }.show()
    }

    private fun confirmDelete() {
        val files = selectedFiles()
        if (files.isEmpty()) return
        Dialogs.confirmDelete(requireContext(), files.size, Prefs.useTrash) {
            Dialogs.runOperation(requireContext(), getString(R.string.delete)) {
                if (Prefs.useTrash) Trash.moveToTrash(requireContext(), files)
                else files.all { FileOps.deleteRecursive(it) }
            }.invokeOnCompletion {
                exitSelection()
                view?.post { reload() }
            }
        }
    }

    private fun promptCreateFolder() {
        Dialogs.createFolder(requireContext(), currentDir) { reload() }
    }

    // ---- Back handling -------------------------------------------------------------

    override fun onBackPressed(): Boolean {
        if (adapter.selectionMode) {
            exitSelection()
            return true
        }
        if (currentDir != rootDir) {
            currentDir = currentDir.parentFile ?: rootDir
            reload()
            return true
        }
        return false
    }
}
