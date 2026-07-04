package foss.openfiles.app.ui.search

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import foss.openfiles.app.R
import foss.openfiles.app.data.FileItem
import foss.openfiles.app.data.FileKind
import foss.openfiles.app.data.MediaQuery
import foss.openfiles.app.data.Prefs
import foss.openfiles.app.data.StorageVolumes
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.ui.browser.FileAdapter
import foss.openfiles.app.util.Open
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Search screen with One UI style filter chips and recent searches. */
class SearchFragment : Fragment(), FileAdapter.Listener {

    private val executor = Executors.newSingleThreadExecutor()
    private var pending: Future<*>? = null
    private val debounce = android.os.Handler(android.os.Looper.getMainLooper())

    private lateinit var input: EditText
    private lateinit var resultList: RecyclerView
    private lateinit var panel: ScrollView
    private lateinit var adapter: FileAdapter
    private lateinit var resultHeader: TextView
    private lateinit var recentBox: LinearLayout

    private var typeFilter: FileKind? = null
    private var timeFilterDays: Int? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.of_background))
        }

        // Toolbar with input
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

        input = EditText(ctx).apply {
            hint = getString(R.string.search)
            textSize = 24f
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
            setHintTextColor(ThemeManager.accent(ctx))
            background = null
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    commitSearch(text.toString())
                    true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString().orEmpty()
                    debounce.removeCallbacksAndMessages(null)
                    if (q.length >= 2) {
                        debounce.postDelayed({ runSearch(q) }, 250)
                    } else {
                        showPanel()
                    }
                }
            })
        }
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        bar.addView(ImageButton(ctx).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(ContextCompat.getColor(ctx, R.color.of_text_primary))
            background = ContextCompat.getDrawable(ctx, R.drawable.ripple_circle)
            setOnClickListener {
                if (input.text.isEmpty()) requireActivity().onBackPressed()
                else input.setText("")
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(bar)

        // Filters + recent searches panel
        panel = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false }
        panel.addView(buildFilterPanel())
        root.addView(panel, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        // Results
        resultHeader = TextView(ctx).apply {
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
            setPadding(dp(20), dp(10), dp(20), dp(6))
            visibility = View.GONE
        }
        root.addView(resultHeader)

        adapter = FileAdapter(this)
        resultList = RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = this@SearchFragment.adapter
            visibility = View.GONE
        }
        root.addView(resultList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        return root
    }

    private fun buildFilterPanel(): View {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(24))
        }

        box.addView(TextView(ctx).apply {
            text = getString(R.string.filters)
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        })

        // Time chips
        box.addView(sectionLabel(R.string.time))
        val timeRow = chipRow()
        val times = listOf(
            R.string.time_yesterday to 1,
            R.string.time_7_days to 7,
            R.string.time_30_days to 30
        )
        for ((res, days) in times) {
            timeRow.addView(chip(getString(res)) { selected, chipView ->
                timeFilterDays = if (selected) days else null
                deselectSiblings(timeRow, chipView)
                refreshIfActive()
            })
        }
        box.addView(timeRow)

        // Type chips
        box.addView(sectionLabel(R.string.type))
        val typeRow = chipRow()
        val types = listOf(
            R.string.type_image to FileKind.IMAGE,
            R.string.type_video to FileKind.VIDEO,
            R.string.type_audio to FileKind.AUDIO,
            R.string.type_document to FileKind.DOCUMENT,
            R.string.type_apk to FileKind.APK,
            R.string.type_compressed to FileKind.COMPRESSED
        )
        for ((res, kind) in types) {
            typeRow.addView(chip(getString(res)) { selected, chipView ->
                typeFilter = if (selected) kind else null
                deselectSiblings(typeRow, chipView)
                refreshIfActive()
            })
        }
        box.addView(typeRow)

        // Recent searches
        recentBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(recentBox)
        renderRecentSearches()

        return box
    }

    private fun renderRecentSearches() {
        val ctx = requireContext()
        recentBox.removeAllViews()
        val recents = Prefs.recentSearches
        if (recents.isEmpty() || !Prefs.showRecentSearches) return

        val headerRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(28), 0, dp(4))
        }
        headerRow.addView(TextView(ctx).apply {
            text = getString(R.string.recent_searches)
            textSize = 24f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        headerRow.addView(TextView(ctx).apply {
            text = getString(R.string.clear_all)
            textSize = 17f
            setTextColor(ThemeManager.accent(ctx))
            setBackgroundResource(R.drawable.ripple_item)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setOnClickListener {
                Prefs.recentSearches = emptyList()
                renderRecentSearches()
            }
        })
        recentBox.addView(headerRow)

        val row = chipRow()
        for (term in recents) {
            val c = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(ctx, R.drawable.bg_chip)
                setPadding(dp(18), dp(9), dp(12), dp(9))
            }
            c.addView(TextView(ctx).apply {
                text = term
                textSize = 16f
                setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
                setOnClickListener {
                    input.setText(term)
                    input.setSelection(term.length)
                    runSearch(term)
                }
            })
            c.addView(ImageView(ctx).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(ContextCompat.getColor(ctx, R.color.of_text_secondary))
                setOnClickListener {
                    Prefs.recentSearches = Prefs.recentSearches - term
                    renderRecentSearches()
                }
            }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginStart = dp(10) })
            row.addView(c)
        }
        recentBox.addView(row)
    }

    private fun sectionLabel(res: Int): TextView =
        TextView(requireContext()).apply {
            setText(res)
            textSize = 17f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.of_text_secondary))
            setPadding(0, dp(22), 0, dp(10))
        }

    private fun chipRow(): ViewGroup =
        foss.openfiles.app.ui.widget.FlowLayout(requireContext()).apply {
            setPadding(0, dp(4), 0, dp(4))
        }

    private var chipSelectedColor = 0

    private fun chip(label: String, onToggle: (Boolean, View) -> Unit): TextView {
        val ctx = requireContext()
        if (chipSelectedColor == 0) chipSelectedColor = ThemeManager.accentStrong(ctx)
        return TextView(ctx).apply {
            text = label
            textSize = 16f
            tag = false
            setTextColor(ContextCompat.getColor(ctx, R.color.of_text_primary))
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_chip_outline)
            setPadding(dp(18), dp(9), dp(18), dp(9))
            setOnClickListener {
                val selected = !(tag as Boolean)
                setChipState(this, selected)
                onToggle(selected, this)
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun setChipState(chipView: TextView, selected: Boolean) {
        val ctx = requireContext()
        chipView.tag = selected
        chipView.background = ContextCompat.getDrawable(
            ctx, if (selected) R.drawable.bg_chip else R.drawable.bg_chip_outline
        )
        if (selected) chipView.background.setTint(ThemeManager.accentStrong(ctx))
    }

    private fun deselectSiblings(row: ViewGroup, keep: View, exceptRow: Boolean = true) {
        for (i in 0 until row.childCount) {
            val child = row.getChildAt(i)
            if (child !== keep && child is TextView && child.tag == true) {
                setChipState(child, false)
            }
        }
    }

    private fun refreshIfActive() {
        val q = input.text.toString()
        if (q.length >= 2) runSearch(q)
    }

    override fun onDestroyView() {
        debounce.removeCallbacksAndMessages(null)
        pending?.cancel(true)
        super.onDestroyView()
    }

    // ---- Searching -----------------------------------------------------------

    private fun showPanel() {
        panel.visibility = View.VISIBLE
        resultHeader.visibility = View.GONE
        resultList.visibility = View.GONE
    }

    private fun commitSearch(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        Prefs.recentSearches = listOf(t) + (Prefs.recentSearches - t)
        runSearch(t)
    }

    private fun runSearch(query: String) {
        pending?.cancel(true)
        val kinds = typeFilter?.let { setOf(it) }
        val newerThan = timeFilterDays?.let {
            System.currentTimeMillis() - it * 24L * 60 * 60 * 1000
        }
        pending = executor.submit {
            val ctx = context ?: return@submit
            val results = MediaQuery.searchIndexed(
                ctx, query, Prefs.showHidden, kinds, newerThan
            )
            if (Thread.currentThread().isInterrupted) return@submit
            view?.post {
                if (!isAdded) return@post
                panel.visibility = View.GONE
                resultHeader.visibility = View.VISIBLE
                resultHeader.text = getString(R.string.internal_storage) +
                    " (" + results.size + " " +
                    (if (results.size == 1) "item" else "items") + ")"
                resultList.visibility = View.VISIBLE
                adapter.items = results
            }
        }
    }

    // ---- Result interactions ---------------------------------------------------

    override fun onItemClick(item: FileItem) {
        if (item.isDirectory) {
            val activity = requireActivity() as foss.openfiles.app.ui.main.MainActivity
            activity.push(
                foss.openfiles.app.ui.browser.BrowserFragment.newInstance(
                    android.os.Environment.getExternalStorageDirectory().absolutePath,
                    item.path
                )
            )
        } else {
            Open.file(requireActivity(), item.file)
        }
    }

    override fun onItemLongClick(item: FileItem) = Unit
    override fun onSelectionToggled(item: FileItem) = Unit
}
