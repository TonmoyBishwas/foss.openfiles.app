package foss.openfiles.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import foss.openfiles.app.R
import foss.openfiles.app.data.MediaQuery
import foss.openfiles.app.data.Prefs
import foss.openfiles.app.data.StorageVolumes
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.browser.BrowserFragment
import foss.openfiles.app.ui.category.CategoryFragment
import foss.openfiles.app.ui.main.MainActivity
import foss.openfiles.app.ui.recycle.RecycleBinFragment
import foss.openfiles.app.ui.search.SearchFragment
import foss.openfiles.app.ui.settings.SettingsFragment
import foss.openfiles.app.ui.storage.ManageStorageFragment
import foss.openfiles.app.ui.widget.OneUiMenu
import foss.openfiles.app.util.Format
import java.util.concurrent.Executors

class HomeFragment : Fragment() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            (activity as MainActivity).push(SearchFragment())
        }
        view.findViewById<ImageButton>(R.id.btnMore).setOnClickListener { anchor ->
            OneUiMenu(requireContext()).apply {
                add(getString(R.string.settings)) {
                    (activity as MainActivity).push(SettingsFragment())
                }
            }.show(anchor)
        }
        buildCategories(view)
        buildUtilities(view)
    }

    override fun onResume() {
        super.onResume()
        val v = view ?: return
        loadRecents(v)
        buildStorage(v)
    }

    // ---- Recents carousel -------------------------------------------------

    private fun loadRecents(root: View) {
        val list = root.findViewById<RecyclerView>(R.id.recentList)
        list.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        executor.execute {
            val ctx = context ?: return@execute
            val recents = MediaQuery.recents(ctx, 12)
            val dayAgo = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            val newCount = recents.count { it.lastModified >= dayAgo }
            list.post {
                if (!isAdded) return@post
                val title = root.findViewById<TextView>(R.id.recentTitle)
                val dot = root.findViewById<View>(R.id.newDot)
                if (newCount > 0) {
                    title.text = resources.getQuantityString(
                        R.plurals.new_files_added, newCount, newCount
                    )
                    dot.visibility = View.VISIBLE
                } else {
                    title.text = getString(R.string.recent_files)
                    dot.visibility = View.GONE
                }
                list.adapter = RecentAdapter(recents) { item ->
                    activity?.let { foss.openfiles.app.util.Open.file(it, item.file) }
                }
            }
        }
    }

    // ---- Categories grid --------------------------------------------------

    private data class Cat(
        val labelRes: Int, val iconRes: Int, val colorRes: Int,
        val category: MediaQuery.Category
    )

    private fun buildCategories(root: View) {
        val grid = root.findViewById<GridLayout>(R.id.categoryGrid)
        grid.removeAllViews()
        val cats = listOf(
            Cat(R.string.cat_images, R.drawable.ic_cat_image, R.color.cat_images,
                MediaQuery.Category.IMAGES),
            Cat(R.string.cat_videos, R.drawable.ic_cat_video, R.color.cat_videos,
                MediaQuery.Category.VIDEOS),
            Cat(R.string.cat_audio, R.drawable.ic_cat_audio, R.color.cat_audio,
                MediaQuery.Category.AUDIO),
            Cat(R.string.cat_documents, R.drawable.ic_cat_document, R.color.cat_documents,
                MediaQuery.Category.DOCUMENTS),
            Cat(R.string.cat_downloads, R.drawable.ic_cat_download, R.color.cat_downloads,
                MediaQuery.Category.DOWNLOADS),
            Cat(R.string.cat_apk, R.drawable.ic_cat_apk, R.color.cat_apk,
                MediaQuery.Category.APK)
        )
        val inflater = layoutInflater
        val density = resources.displayMetrics.density
        for ((i, cat) in cats.withIndex()) {
            val cell = inflater.inflate(R.layout.item_category, grid, false)
            cell.findViewById<ImageView>(R.id.icon).apply {
                setImageResource(cat.iconRes)
                setColorFilter(ContextCompat.getColor(requireContext(), cat.colorRes))
            }
            cell.findViewById<TextView>(R.id.label).setText(cat.labelRes)
            cell.setOnClickListener {
                (activity as MainActivity).push(CategoryFragment.newInstance(cat.category))
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                // Fixed height keeps all six cells identical even when a label
                // ("Installation files") wraps to two lines.
                height = (96 * density).toInt()
                columnSpec = GridLayout.spec(i % 3, 1f)
                rowSpec = GridLayout.spec(i / 3)
                setMargins(
                    (4 * density).toInt(), (4 * density).toInt(),
                    (4 * density).toInt(), (4 * density).toInt()
                )
            }
            grid.addView(cell, lp)
        }
    }

    // ---- Storage + Utilities ----------------------------------------------

    private fun buildStorage(root: View) {
        // Volume detection touches StatFs/app dirs — off the main thread.
        executor.execute {
            val ctx = context ?: return@execute
            val volumes = StorageVolumes.list(ctx)
            root.post {
                if (isAdded) renderStorage(root, volumes)
            }
        }
    }

    private fun renderStorage(root: View, volumes: List<foss.openfiles.app.data.VolumeInfo>) {
        val list = root.findViewById<LinearLayout>(R.id.storageList)
        list.removeAllViews()
        val ctx = requireContext()
        for (volume in volumes) {
            val row = layoutInflater.inflate(R.layout.item_home_row, list, false)
            row.findViewById<ImageView>(R.id.icon).setImageResource(
                when {
                    volume.isPrimary -> R.drawable.ic_phone
                    volume.isUsb -> R.drawable.ic_usb
                    else -> R.drawable.ic_sd_card
                }
            )
            row.findViewById<TextView>(R.id.title).setText(
                when {
                    volume.isPrimary -> R.string.internal_storage
                    volume.isUsb -> R.string.usb_storage
                    else -> R.string.sd_card
                }
            )
            row.findViewById<android.widget.FrameLayout>(R.id.pillBox).apply {
                visibility = View.VISIBLE
                removeAllViews()
                val density = resources.displayMetrics.density
                fun dp(v: Int) = (v * density).toInt()

                val used = Format.sizeShort(volume.usedBytes)
                val total = Format.sizeShort(volume.totalBytes).replace(".0", "")
                val span = android.text.SpannableString("$used / $total").apply {
                    setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        0, used.length + 2,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    setSpan(
                        android.text.style.ForegroundColorSpan(0xCCFFFFFF.toInt()),
                        used.length + 2, length,
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                addView(TextView(ctx).apply {
                    text = span
                    textSize = 13f
                    maxLines = 1
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(dp(13), dp(5), dp(13), dp(5))
                    background = foss.openfiles.app.ui.widget.PillProgressDrawable(
                        trackColor = 0xFF3F4247.toInt(),
                        fillColor = ThemeManager.accentStrong(ctx),
                        fraction = if (volume.totalBytes > 0)
                            volume.usedBytes.toFloat() / volume.totalBytes else 0f
                    )
                }, android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.CENTER
                ))
            }
            row.setOnClickListener {
                (activity as MainActivity).push(BrowserFragment.newInstance(volume.root.absolutePath))
            }
            list.addView(row)
            addDivider(list)
        }
        if (list.childCount > 0) list.removeViewAt(list.childCount - 1)
    }

    private fun buildUtilities(root: View) {
        val list = root.findViewById<LinearLayout>(R.id.utilityList)
        list.removeAllViews()

        val bin = layoutInflater.inflate(R.layout.item_home_row, list, false)
        bin.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_trash)
        bin.findViewById<TextView>(R.id.title).setText(R.string.recycle_bin)
        bin.setOnClickListener {
            (activity as MainActivity).push(RecycleBinFragment())
        }
        list.addView(bin)
        addDivider(list)

        val manage = layoutInflater.inflate(R.layout.item_home_row, list, false)
        manage.findViewById<ImageView>(R.id.icon).setImageResource(R.drawable.ic_manage_storage)
        manage.findViewById<TextView>(R.id.title).setText(R.string.manage_storage)
        manage.setOnClickListener {
            (activity as MainActivity).push(ManageStorageFragment())
        }
        list.addView(manage)
    }

    private fun addDivider(parent: LinearLayout) {
        val density = resources.displayMetrics.density
        val divider = View(requireContext()).apply {
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.of_divider))
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).apply {
            marginStart = (64 * density).toInt()
            marginEnd = (16 * density).toInt()
        }
        parent.addView(divider, lp)
    }
}
