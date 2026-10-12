@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.View.ThumbnailSelectionAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Searchable icon picker shown as an expanded bottom sheet, the icons under a header for each
 * letter. Used when creating or editing a service and from the card's options.
 */
object ThumbnailPickerSheet {

    fun interface Callback {
        fun onThumbnailPicked(thumbnail: EntryThumbnail.EntryThumbnails)
    }

    /** Shows the picker, with [current], the icon now in use, ringed. */
    fun show(context: Context, issuer: String?, label: String?,
             current: EntryThumbnail.EntryThumbnails = EntryThumbnail.EntryThumbnails.Default, callback: Callback) {
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(R.layout.sheet_thumbnail_picker)

        val content = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (content != null) {
            content.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            val behavior = BottomSheetBehavior.from(content)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        val grid = sheet.findViewById<RecyclerView>(R.id.thumbnail_grid)
        val search = sheet.findViewById<EditText>(R.id.thumbnail_search)
        val close = sheet.findViewById<View>(R.id.sheetClose)

        if (grid == null || search == null)
            return

        val adapter = ThumbnailSelectionAdapter(context, issuer, label, current) { thumbnail ->
            sheet.dismiss()
            callback.onThumbnailPicked(thumbnail)
        }
        val layout = GridLayoutManager(context, 4)
        layout.spanSizeLookup = adapter.spanSizes(layout)
        grid.layoutManager = layout
        grid.adapter = adapter
        grid.addItemDecoration(ThumbnailSelectionAdapter.StickyHeaders(adapter))

        // As many columns as fit, each an icon in its ring with room around it.
        val res = context.resources
        val column = Math.max(Settings(context).thumbnailSize, res.getDimensionPixelSize(R.dimen.issuer_icon_size)) +
                res.getDimensionPixelSize(R.dimen.thumbnail_ring_padding) * 2 + res.getDimensionPixelSize(R.dimen.space_md)
        grid.doOnLayout { layout.spanCount = Math.max(1, it.width / column) }

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(editable: Editable) {
                adapter.filter(editable.toString())
            }
        })

        close?.setOnClickListener { sheet.dismiss() }

        sheet.show()
    }
}
