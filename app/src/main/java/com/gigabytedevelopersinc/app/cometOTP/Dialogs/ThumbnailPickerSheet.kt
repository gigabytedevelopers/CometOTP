@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.GridView
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.View.ThumbnailSelectionAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Searchable icon picker shown as an expanded bottom sheet. Used when creating a service and from
 * the card overflow menu.
 */
object ThumbnailPickerSheet {

    fun interface Callback {
        fun onThumbnailPicked(thumbnail: EntryThumbnail.EntryThumbnails)
    }

    @JvmStatic
    fun show(context: Context, issuer: String?, label: String?, callback: Callback) {
        val sheet = BottomSheetDialog(context)
        sheet.setContentView(R.layout.sheet_thumbnail_picker)

        val content = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (content != null) {
            content.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            val behavior = BottomSheetBehavior.from(content)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

        val adapter = ThumbnailSelectionAdapter(context, issuer, label)
        val grid = sheet.findViewById<GridView>(R.id.thumbnail_grid)
        val search = sheet.findViewById<EditText>(R.id.thumbnail_search)
        val close = sheet.findViewById<View>(R.id.sheetClose)

        if (grid == null || search == null)
            return

        val thumbnailSize = Settings(context).thumbnailSize
        grid.columnWidth = Math.max(thumbnailSize, context.resources.getDimensionPixelSize(R.dimen.issuer_icon_size))
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            var thumbnail = EntryThumbnail.EntryThumbnails.Default
            try {
                thumbnail = EntryThumbnail.EntryThumbnails.values()[adapter.getRealIndex(position)]
            } catch (e: Exception) {
                e.printStackTrace()
            }
            sheet.dismiss()
            callback.onThumbnailPicked(thumbnail)
        }

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
