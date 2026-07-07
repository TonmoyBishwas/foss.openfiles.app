package foss.openfiles.app.ui.widget

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import foss.openfiles.app.R
import foss.openfiles.app.data.FileItem
import foss.openfiles.app.data.FileKind
import foss.openfiles.app.data.FileOps
import foss.openfiles.app.data.FileRepository
import foss.openfiles.app.theme.ThemeManager
import foss.openfiles.app.util.Format
import java.io.File
import java.util.concurrent.Executors

/** One UI style dialogs: rounded dark sheets with large typography. */
object Dialogs {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    class Handle {
        private var done = false
        private var callback: (() -> Unit)? = null

        internal fun complete() {
            done = true
            callback?.invoke()
        }

        fun invokeOnCompletion(cb: () -> Unit) {
            if (done) cb() else callback = cb
        }
    }

    private fun dp(context: Context, v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()

    private fun baseDialog(context: Context, content: ViewGroup): Dialog =
        Dialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(
                    (context.resources.displayMetrics.widthPixels * 0.94).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setGravity(Gravity.BOTTOM)
                attributes = attributes.apply { y = dp(context, 12) }
            }
        }

    private fun sheet(context: Context): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_bottom_sheet)
            setPadding(dp(context, 26), dp(context, 26), dp(context, 26), dp(context, 14))
        }

    private fun title(context: Context, text: String): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.of_text_primary))
        }

    private fun buttonRow(
        context: Context,
        negative: String,
        positive: String,
        onNegative: () -> Unit,
        onPositive: () -> Unit
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(context, 18), 0, 0)

        fun textButton(label: String, bold: Boolean, onClick: () -> Unit): TextView =
            TextView(context).apply {
                text = label
                textSize = 19f
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.of_text_primary))
                gravity = Gravity.CENTER
                setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
                setBackgroundResource(R.drawable.ripple_item)
                setOnClickListener { onClick() }
            }

        addView(textButton(negative, false, onNegative),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(android.view.View(context).apply {
            setBackgroundColor(0x26FFFFFF)
        }, LinearLayout.LayoutParams(dp(context, 1), dp(context, 22)))
        addView(textButton(positive, true, onPositive),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    // ---- Create folder / rename ------------------------------------------------

    fun createFolder(context: Context, parent: File, onDone: () -> Unit) {
        inputDialog(
            context,
            context.getString(R.string.create_folder),
            context.getString(R.string.folder_hint),
            context.getString(R.string.create)
        ) { name ->
            val target = File(parent, name)
            if (target.exists() || !target.mkdirs()) {
                Toast.makeText(context, "Couldn't create folder", Toast.LENGTH_SHORT).show()
            }
            onDone()
        }
    }

    fun rename(context: Context, file: File, onDone: () -> Unit) {
        inputDialog(
            context,
            context.getString(R.string.rename),
            file.name,
            context.getString(R.string.rename),
            prefill = file.name
        ) { name ->
            if (name != file.name && !FileOps.rename(file, name)) {
                Toast.makeText(context, "Couldn't rename", Toast.LENGTH_SHORT).show()
            }
            onDone()
        }
    }

    fun compressName(context: Context, dir: File, sources: List<File>, onDone: () -> Unit) {
        val defaultName = (sources.firstOrNull()?.nameWithoutExtension ?: "Archive") + ".zip"
        inputDialog(
            context,
            context.getString(R.string.compress),
            defaultName,
            context.getString(R.string.compress),
            prefill = defaultName
        ) { name ->
            val zipName = if (name.endsWith(".zip", true)) name else "$name.zip"
            val target = FileRepository.uniqueDestination(File(dir, zipName))
            runOperation(context, context.getString(R.string.compress)) {
                FileOps.compress(sources, target)
            }.invokeOnCompletion { onDone() }
        }
    }

    private fun inputDialog(
        context: Context,
        heading: String,
        hint: String,
        positiveLabel: String,
        prefill: String? = null,
        onSubmit: (String) -> Unit
    ) {
        val box = sheet(context)
        box.addView(title(context, heading))

        val input = EditText(context).apply {
            this.hint = hint
            textSize = 19f
            setTextColor(ContextCompat.getColor(context, R.color.of_text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.of_text_secondary))
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ThemeManager.accentStrong(context)
            )
            if (prefill != null) {
                setText(prefill)
                val dot = prefill.lastIndexOf('.')
                setSelection(0, if (dot > 0) dot else prefill.length)
            }
        }
        box.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 20) })

        lateinit var dialog: Dialog
        box.addView(buttonRow(
            context,
            context.getString(R.string.cancel),
            positiveLabel,
            onNegative = { dialog.dismiss() },
            onPositive = {
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && !name.contains('/')) {
                    dialog.dismiss()
                    onSubmit(name)
                }
            }
        ))
        dialog = baseDialog(context, box)
        dialog.show()
        input.requestFocus()
        dialog.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
    }

    // ---- Delete confirmation ------------------------------------------------

    fun confirmDelete(context: Context, count: Int, toTrash: Boolean, onConfirm: () -> Unit) {
        val box = sheet(context)
        val message = when {
            toTrash && count == 1 -> context.getString(R.string.move_to_bin_question_one)
            toTrash -> context.getString(R.string.move_to_bin_question, count)
            count == 1 -> context.getString(R.string.delete_permanently_question_one)
            else -> context.getString(R.string.delete_permanently_question, count)
        }
        box.addView(TextView(context).apply {
            text = message
            textSize = 19f
            setTextColor(ContextCompat.getColor(context, R.color.of_text_primary))
        })
        lateinit var dialog: Dialog
        box.addView(buttonRow(
            context,
            context.getString(R.string.cancel),
            context.getString(
                if (toTrash) R.string.move_to_recycle_bin else R.string.delete
            ),
            onNegative = { dialog.dismiss() },
            onPositive = { dialog.dismiss(); onConfirm() }
        ))
        dialog = baseDialog(context, box)
        dialog.show()
    }

    // ---- Details ------------------------------------------------

    fun details(context: Context, files: List<File>) {
        val box = sheet(context)
        box.addView(title(context, context.getString(R.string.details)))

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(context, 22), 0, 0)
        }
        val icon = ImageView(context).apply {
            if (files.size == 1 && files[0].isDirectory) {
                setImageResource(R.drawable.ic_folder)
                setColorFilter(ThemeManager.folder(context))
            } else {
                setImageResource(R.drawable.ic_file_generic)
                setColorFilter(0xFF8F9296.toInt())
            }
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(context, 40), dp(context, 40)))
        header.addView(TextView(context).apply {
            text = if (files.size == 1) files[0].name
            else context.getString(R.string.n_selected, files.size)
            textSize = 21f
            setTextColor(ContextCompat.getColor(context, R.color.of_text_primary))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(context, 18) })
        box.addView(header)

        fun row(label: String): Pair<LinearLayout, TextView> {
            val col = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(context, 20), 0, 0)
            }
            col.addView(TextView(context).apply {
                text = label
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.of_text_secondary))
            })
            val value = TextView(context).apply {
                textSize = 19f
                setTextColor(ContextCompat.getColor(context, R.color.of_text_primary))
                text = "…"
            }
            col.addView(value)
            box.addView(col)
            return col to value
        }

        val (_, sizeVal) = row(context.getString(R.string.size))
        val (_, modVal) = row(context.getString(R.string.last_modified))
        val containsRow = if (files.size == 1 && files[0].isDirectory)
            row(context.getString(R.string.contains)) else null
        val (_, pathVal) = row(context.getString(R.string.path))

        pathVal.text = displayPath(context, files[0])

        lateinit var dialog: Dialog
        box.addView(buttonRow(
            context, "", context.getString(R.string.ok),
            onNegative = {}, onPositive = { dialog.dismiss() }
        ).also { rowView ->
            // Only OK button — hide the empty negative side.
            (rowView.getChildAt(0) as TextView).visibility = android.view.View.GONE
            rowView.getChildAt(1).visibility = android.view.View.GONE
        })

        dialog = baseDialog(context, box)
        dialog.show()

        executor.execute {
            var bytes = 0L
            var fileCount = 0
            var folderCount = 0
            for (f in files) {
                val (b, fc, dc) = FileRepository.measure(f)
                bytes += b; fileCount += fc; folderCount += dc
            }
            val newest = files.maxOf { it.lastModified() }
            main.post {
                sizeVal.text = Format.size(bytes)
                modVal.text = Format.date(newest)
                containsRow?.second?.text = "$fileCount files, $folderCount folder" +
                    (if (folderCount == 1) "" else "s")
            }
        }
    }

    private fun displayPath(context: Context, file: File): String {
        val internal = android.os.Environment.getExternalStorageDirectory().absolutePath
        val p = (if (file.isDirectory) file else file.parentFile ?: file).absolutePath
        return if (p.startsWith(internal)) {
            "/" + context.getString(R.string.internal_storage) + p.removePrefix(internal)
        } else p
    }

    // ---- Progress operation ------------------------------------------------

    fun runOperation(context: Context, label: String, work: () -> Boolean): Handle {
        val handle = Handle()
        val box = sheet(context)
        box.addView(title(context, label))
        val bar = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ThemeManager.accentStrong(context)
            )
        }
        box.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(context, 22); bottomMargin = dp(context, 12) })

        val dialog = baseDialog(context, box)
        dialog.setCancelable(false)
        dialog.show()

        executor.execute {
            val ok = runCatching { work() }.getOrDefault(false)
            main.post {
                runCatching { dialog.dismiss() }
                if (!ok) {
                    Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show()
                }
                handle.complete()
            }
        }
        return handle
    }
}
