package foss.openfiles.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import foss.openfiles.app.R
import foss.openfiles.app.data.FileItem
import foss.openfiles.app.data.FileKind
import foss.openfiles.app.util.Format
import foss.openfiles.app.util.Thumbs

class RecentAdapter(
    private val items: List<FileItem>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<RecentAdapter.Holder>() {

    class Holder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val time: TextView = view.findViewById(R.id.time)
        val name: TextView = view.findViewById(R.id.name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recent, parent, false)
        )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.time.text = Format.relative(item.lastModified)
        holder.name.text = Format.middleEllipsis(item.name)
        holder.thumb.setImageResource(R.drawable.ic_file_generic)
        holder.thumb.setColorFilter(0xFF8F9296.toInt())
        val kind = FileKind.of(item)
        if (kind == FileKind.IMAGE || kind == FileKind.VIDEO || kind == FileKind.AUDIO ||
            kind == FileKind.APK
        ) {
            holder.thumb.clearColorFilter()
            Thumbs.load(holder.thumb, item.path, kind, 300)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }
}
