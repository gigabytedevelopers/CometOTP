@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore

class TagsAdapter(context: Context, tags: HashMap<String, Boolean>) :
    ArrayAdapter<String>(context, layoutResourceId, ArrayList(tags.keys)) {
    // The Java version kept its own copy of the constructor's Context; ArrayAdapter.getContext()
    // returns that same object, so it is used instead of a second field.
    private var tagsOrder: MutableList<String>
    private var tagsState: HashMap<String, Boolean>

    init {
        this.tagsState = tags
        this.tagsOrder = ArrayList(tagsState.keys)
        this.tagsOrder.sort()
    }

    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        val checkedTextView: CheckedTextView = if (view == null) {
            LayoutInflater.from(context).inflate(layoutResourceId, viewGroup, false) as CheckedTextView
        } else {
            view as CheckedTextView
        }
        checkedTextView.text = tagsOrder[i]
        checkedTextView.isChecked = tagsState[tagsOrder[i]]!!

        return checkedTextView
    }

    val tags: List<String>
        get() = tagsOrder

    fun getTagState(tag: String): Boolean? {
        if (tagsState.containsKey(tag))
            return tagsState[tag]
        return false
    }

    fun setTagState(tag: String, state: Boolean) {
        if (tagsState.containsKey(tag))
            tagsState[tag] = state
        notifyDataSetChanged()
    }

    val activeTags: MutableList<String>
        get() {
            val tagsList: MutableList<String> = ArrayList()
            for (tag in tagsOrder) {
                if (tagsState[tag]!!) {
                    tagsList.add(tag)
                }
            }
            return tagsList
        }

    fun allTagsActive(): Boolean {
        for (key in tagsState.keys)
            if (!tagsState[key]!!)
                return false

        return true
    }

    val tagsWithState: HashMap<String, Boolean>
        get() = HashMap(tagsState)

    fun setTags(tags: HashMap<String, Boolean>) {
        this.tagsState = tags
        this.tagsOrder = ArrayList(tagsState.keys)
        this.tagsOrder.sort()

        this.clear()
        this.addAll(this.tags)
        notifyDataSetChanged()
    }

    companion object {
        private const val layoutResourceId = android.R.layout.simple_list_item_multiple_choice

        @JvmStatic
        fun createTagsMap(context: Context, entries: ArrayList<Entry>, settings: Settings): HashMap<String, Boolean> {
            val tagsHashMap = HashMap<String, Boolean>()

            for (entry in entries) {
                for (tag in entry.tags)
                    tagsHashMap[tag] = settings.getTagToggle(tag)
            }

            // Tags created on the Tags screen that nothing carries yet still belong in the filter.
            for (tag in TagStore.knownTags(context))
                tagsHashMap[tag] = settings.getTagToggle(tag)

            return tagsHashMap
        }
    }
}
