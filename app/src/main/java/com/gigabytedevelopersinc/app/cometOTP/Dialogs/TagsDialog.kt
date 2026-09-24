@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.FrameLayout
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import com.gigabytedevelopersinc.app.cometOTP.View.TagsAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Callable

object TagsDialog {
    fun show(context: Context, tagsAdapter: TagsAdapter, newTagCallable: Callable<*>?, selectedTagsCallable: Callable<*>?) {
        val margin = context.resources.getDimensionPixelSize(R.dimen.activity_margin)
        val marginSmall = context.resources.getDimensionPixelSize(R.dimen.activity_margin_small)

        val tagsSelectionView = ListView(context)
        tagsSelectionView.divider = null
        tagsSelectionView.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        tagsSelectionView.adapter = tagsAdapter
        tagsSelectionView.setOnItemClickListener { _, view, _, _ ->
            val checkedTextView = view as CheckedTextView
            checkedTextView.isChecked = !checkedTextView.isChecked

            tagsAdapter.setTagState(checkedTextView.text.toString(), checkedTextView.isChecked)
        }

        val tagsSelectionLayout = FrameLayout(context)
        tagsSelectionLayout.setPaddingRelative(margin, marginSmall, margin, 0)
        tagsSelectionLayout.addView(tagsSelectionView)

        val tagsSelectorBuilder: AlertDialog.Builder = MaterialAlertDialogBuilder(context)
        tagsSelectorBuilder.setTitle(R.string.label_tags)
                .setView(tagsSelectionLayout)
                .setNegativeButton(android.R.string.cancel) { dialogInterface, _ -> dialogInterface.dismiss() }
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (selectedTagsCallable != null) {
                        try {
                            selectedTagsCallable.call()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                .setNeutralButton(R.string.button_new_tag) { _, _ ->
                    // The same Create Tag sheet the Tags screen uses, so a tag made here gets
                    // a colour and a count setting and is stored the same way.
                    TagEditSheet.show(context, null, tagsAdapter.tags) { _, name, color, showCount ->
                        TagStore.put(context, name, color, showCount)

                        val allTags = tagsAdapter.tagsWithState
                        allTags[name] = true
                        tagsAdapter.setTags(allTags)
                        tagsAdapter.setTagState(name, true)

                        if (newTagCallable != null) {
                            try {
                                newTagCallable.call()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                .setCancelable(false)
                .create()
                .show()

    }
}
