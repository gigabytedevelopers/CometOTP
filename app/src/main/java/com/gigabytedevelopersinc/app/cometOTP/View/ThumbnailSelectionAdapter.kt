@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ImageView
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import java.util.Collections
import java.util.Locale

class ThumbnailSelectionAdapter(context: Context, issuer: String?, label: String?) : BaseAdapter() {
    private val context: Context
    private val items: MutableList<EntryThumbnail.EntryThumbnails>
    private var issuer: String? = "Example"
    private var label: String? = "Example"
    private val settings: Settings

    init {
        items = ArrayList(EntryThumbnail.EntryThumbnails.values().size)
        Collections.addAll(items, *EntryThumbnail.EntryThumbnails.values())
        this.issuer = issuer
        this.label = label
        this.context = context
        settings = Settings(context)
    }

    fun filter(filter: String) {
        items.clear()
        for (thumb in EntryThumbnail.EntryThumbnails.values()) {
            if (thumb.name.lowercase(Locale.getDefault()).contains(filter.lowercase(Locale.getDefault()))) {
                items.add(thumb)
            }
        }
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(i: Int): Any {
        return if (i < count)
            items[i]
        else
            EntryThumbnail.EntryThumbnails.Default
    }

    fun getRealIndex(displayPosition: Int): Int {
        return (getItem(displayPosition) as EntryThumbnail.EntryThumbnails).ordinal
    }

    override fun getItemId(i: Int): Long {
        return (getItem(i) as EntryThumbnail.EntryThumbnails).ordinal.toLong()
    }

    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        val thumbnailSize = settings.thumbnailSize
        val imageView: ImageView
        if (view == null) {
            imageView = ImageView(context)
            // GridView.LayoutParams in the Java version: GridView inherits AbsListView's class.
            imageView.layoutParams = AbsListView.LayoutParams(thumbnailSize, thumbnailSize)
            // Many issuer logos are black, which vanishes against a dark background.
            imageView.setBackgroundResource(R.drawable.bg_thumbnail_tile)
        } else {
            imageView = view as ImageView
        }

        val thumb = getItem(i) as EntryThumbnail.EntryThumbnails

        imageView.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context, issuer, label, thumbnailSize, thumb))
        imageView.contentDescription = thumb.name

        return imageView
    }
}
