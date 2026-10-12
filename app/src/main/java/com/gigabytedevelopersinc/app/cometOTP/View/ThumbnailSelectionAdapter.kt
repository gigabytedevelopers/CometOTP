@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.withTranslation
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail.EntryThumbnails
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import java.util.Locale

/**
 * The icons in the icon picker, under a header for each first letter (# for the names that start
 * with a number), with [current] ringed. Default, the initials tile, comes first, under no header.
 */
class ThumbnailSelectionAdapter(
    private val context: Context,
    private val issuer: String?,
    private val label: String?,
    private val current: EntryThumbnails,
    private val onPicked: (EntryThumbnails) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** A row of the grid: a section's header, or an icon. */
    private sealed class Row {
        class Header(val title: String) : Row()
        class Icon(val thumbnail: EntryThumbnails) : Row()
    }

    private val rows = ArrayList<Row>()
    private val thumbnailSize = Settings(context).thumbnailSize

    init {
        filter("")
    }

    /** Shows the icons whose names contain [filter], under their headers. */
    fun filter(filter: String) {
        val query = filter.lowercase(Locale.getDefault())
        rows.clear()
        var section: String? = null
        for (thumbnail in EntryThumbnails.values()) {
            if (!thumbnail.name.lowercase(Locale.getDefault()).contains(query))
                continue
            val title = sectionOf(thumbnail)
            if (title != null && title != section) {
                rows.add(Row.Header(title))
                section = title
            }
            rows.add(Row.Icon(thumbnail))
        }
        @Suppress("NotifyDataSetChanged") // Every row may change with the filter.
        notifyDataSetChanged()
    }

    /** Whether the row at [position] is a header, which spans the grid's width. */
    fun isHeader(position: Int): Boolean = rows.getOrNull(position) is Row.Header

    /** The header the row at [position] is under, or null for Default's. */
    fun headerFor(position: Int): String? {
        for (i in position downTo 0) {
            val row = rows[i]
            if (row is Row.Header)
                return row.title
        }
        return null
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = if (isHeader(position)) TYPE_HEADER else TYPE_ICON

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_thumbnail_header, parent, false)) {}
        else
            object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_thumbnail_icon, parent, false)) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder.itemView as TextView).text = row.title
            is Row.Icon -> {
                val image = holder.itemView.findViewById<ImageView>(R.id.thumbnail_icon)
                image.layoutParams.width = thumbnailSize
                image.layoutParams.height = thumbnailSize
                image.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context, issuer, label, thumbnailSize, row.thumbnail))
                holder.itemView.contentDescription = row.thumbnail.name
                holder.itemView.isSelected = row.thumbnail == current
                holder.itemView.setOnClickListener { onPicked(row.thumbnail) }
            }
        }
    }

    /**
     * Keeps the header of the section at the top of the grid pinned there, pushed up by the next
     * header as it arrives.
     */
    class StickyHeaders(private val adapter: ThumbnailSelectionAdapter) : RecyclerView.ItemDecoration() {
        private var header: TextView? = null

        override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
            val top = parent.getChildAt(0) ?: return
            val position = parent.getChildAdapterPosition(top)
            if (position == RecyclerView.NO_POSITION)
                return
            val title = adapter.headerFor(position) ?: return

            val view = header ?: (LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_thumbnail_header, parent, false) as TextView).also { header = it }
            if (view.text != title || view.width != parent.width) {
                view.text = title
                // As tall as the headers in the grid, so it lies exactly over them.
                val height = parent.resources.getDimensionPixelSize(R.dimen.thumbnail_header_height)
                view.measure(View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            }

            // The next header, once it reaches the pinned one, pushes it up and out.
            var offset = 0
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                val childPosition = parent.getChildAdapterPosition(child)
                if (childPosition > position && adapter.isHeader(childPosition)) {
                    if (child.top < view.height)
                        offset = child.top - view.height
                    break
                }
            }

            c.withTranslation(0f, offset.toFloat()) { view.draw(this) }
        }
    }

    /** Lets headers span the grid's width, icons one column each. */
    fun spanSizes(layout: GridLayoutManager): GridLayoutManager.SpanSizeLookup {
        return object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = if (isHeader(position)) layout.spanCount else 1
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ICON = 1

        /**
         * The section [thumbnail] is in: # for the services whose names start with a number,
         * which the list has first and spells out (OneAndOne, FiveHundredPX), then the first
         * letter. Default, the initials tile, has none.
         */
        private fun sectionOf(thumbnail: EntryThumbnails): String? {
            if (thumbnail == EntryThumbnails.Default)
                return null
            return if (thumbnail.ordinal < firstLettered) "#"
                else thumbnail.name.substring(0, 1).uppercase(Locale.ROOT)
        }

        /** Where the names that start with a letter begin, after Default and the numbered ones. */
        private val firstLettered: Int by lazy {
            EntryThumbnails.values().firstOrNull { it != EntryThumbnails.Default && it.name.startsWith("A") }?.ordinal ?: 1
        }
    }
}
