@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.content.DialogInterface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.TreeSet

/**
 * Tags for several entries at once (manage mode). Each tag shows whether all, some or none of
 * the entries carry it. A tap adds it to all of them, then takes it off all of them, and for a
 * tag only some carry, comes back to leaving it as it is.
 */
object BulkTagsDialog {

    /** What OK does with a tag. */
    internal enum class Change { ADD, REMOVE, KEEP }

    /** The tag's state before any change: what [Change.KEEP] leaves it at. */
    internal fun initialState(carriedBy: Int, of: Int): Int {
        return when (carriedBy) {
            0 -> MaterialCheckBox.STATE_UNCHECKED
            of -> MaterialCheckBox.STATE_CHECKED
            else -> MaterialCheckBox.STATE_INDETERMINATE
        }
    }

    /** The state a tap moves a tag to, from [current]; only a tag some carry comes back to "some". */
    internal fun nextState(current: Int, initial: Int): Int {
        return when (current) {
            MaterialCheckBox.STATE_INDETERMINATE -> MaterialCheckBox.STATE_CHECKED
            MaterialCheckBox.STATE_CHECKED -> MaterialCheckBox.STATE_UNCHECKED
            else -> if (initial == MaterialCheckBox.STATE_INDETERMINATE) MaterialCheckBox.STATE_INDETERMINATE
                    else MaterialCheckBox.STATE_CHECKED
        }
    }

    internal fun changeFor(state: Int): Change {
        return when (state) {
            MaterialCheckBox.STATE_CHECKED -> Change.ADD
            MaterialCheckBox.STATE_UNCHECKED -> Change.REMOVE
            else -> Change.KEEP
        }
    }

    /** Applies [changes] (tag to what to do with it) to [entries]; true if any entry changed. */
    internal fun apply(entries: List<Entry>, changes: Map<String, Change>): Boolean {
        var changed = false
        for (entry in entries) {
            for ((tag, change) in changes) {
                if (change == Change.ADD && !entry.tags.contains(tag)) {
                    entry.tags.add(tag)
                    changed = true
                } else if (change == Change.REMOVE && entry.tags.remove(tag)) {
                    changed = true
                }
            }
        }
        return changed
    }

    /**
     * @param entries the selected entries, changed in place
     * @param tagsInUse the tags entries carry now; tags made on the Tags screen are added
     * @param onChanged runs after OK changed at least one entry, to save them
     */
    fun show(context: Context, entries: List<Entry>, tagsInUse: List<String>, onChanged: () -> Unit) {
        val names: MutableSet<String> = TreeSet(String.CASE_INSENSITIVE_ORDER)
        names.addAll(tagsInUse)
        names.addAll(TagStore.knownTags(context))

        val margin = context.resources.getDimensionPixelSize(R.dimen.activity_margin)
        val marginSmall = context.resources.getDimensionPixelSize(R.dimen.activity_margin_small)

        val rows = LinearLayout(context)
        rows.orientation = LinearLayout.VERTICAL
        rows.setPaddingRelative(margin, marginSmall, margin, 0)

        val boxes = LinkedHashMap<String, MaterialCheckBox>()
        fun addRow(tag: String, state: Int, initial: Int) {
            val box = MaterialCheckBox(context)
            box.text = tag
            box.minHeight = context.resources.getDimensionPixelSize(R.dimen.nav_row_height)
            box.checkedState = state
            // The box toggles itself before this runs; the state is then set to the next one.
            var current = state
            box.setOnClickListener {
                current = nextState(current, initial)
                box.checkedState = current
            }
            rows.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            boxes[tag] = box
        }

        for (tag in names) {
            val initial = initialState(entries.count { it.tags.contains(tag) }, entries.size)
            addRow(tag, initial, initial)
        }

        val scroll = ScrollView(context)
        scroll.addView(rows)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.resources.getQuantityString(R.plurals.dialog_title_tag_selected, entries.size, entries.size))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val changes = boxes.mapValues { changeFor(it.value.checkedState) }
                if (apply(entries, changes))
                    onChanged()
            }
            .setNeutralButton(R.string.button_new_tag, null)
            .create()

        dialog.setOnShowListener {
            // Kept open: the new tag joins the list, ticked for every entry, with the other
            // choices still pending.
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                TagEditSheet.show(context, null, boxes.keys.toList()) { _, name, color, showCount ->
                    TagStore.put(context, name, color, showCount)
                    addRow(name, MaterialCheckBox.STATE_CHECKED, MaterialCheckBox.STATE_UNCHECKED)
                }
            }
        }
        dialog.show()
    }
}
