/*
 * Created by Emmanuel Nwokoma (Gigabyte)  on 4/12/21 10:21 AM
 * Copyright: All rights reserved Ⓒ 2021
 * Last modified: 4/12/21 10:21 AM
 */
@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.Database

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import java.text.Collator
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Monday, 12
 * Month: April
 * Year: 2021
 * Date: 12 Apr, 2021
 * Time: 10:21 AM
 * Desc: EntryList
 **/
class EntryList {
    private val entryList: ArrayList<Entry> = ArrayList()
    private val currentId = AtomicLong()

    fun addEntry(newEntry: Entry): Boolean {
        return addEntry(newEntry, false)
    }

    fun addEntry(newEntry: Entry, update: Boolean): Boolean {
        if (!entryList.contains(newEntry)) {
            val newId = currentId.incrementAndGet()
            newEntry.listId = newId

            entryList.add(newEntry)

            return true
        } else {
            if (update) {
                val oldIdx = entryList.indexOf(newEntry)
                val oldEntry = entryList[oldIdx]

                newEntry.listId = oldEntry.listId
                entryList[oldIdx] = newEntry
            }
        }

        return false
    }

    fun updateEntries(newEntries: ArrayList<Entry>, update: Boolean) {
        // Remove all items not in the new list
        entryList.retainAll(newEntries)

        // Add new and update existing entries
        for (e in newEntries) {
            addEntry(e, update)
        }
    }

    fun getEntry(pos: Int): Entry {
        return entryList[pos]
    }

    fun swapEntries(fromPosition: Int, toPosition: Int) {
        Collections.swap(entryList, fromPosition, toPosition)
    }

    fun removeEntry(pos: Int) {
        entryList.removeAt(pos)
    }

    fun indexOf(e: Entry?): Int {
        return entryList.indexOf(e)
    }

    fun isEqual(otherEntries: ArrayList<Entry>?): Boolean {
        return entryList == otherEntries
    }

    val entries: ArrayList<Entry>
        get() = ArrayList(entryList)

    fun getEntriesSorted(sortMode: Constants.SortMode?): ArrayList<Entry> {
        return sortEntries(entryList, sortMode)
    }

    val allTags: ArrayList<String>
        get() {
            val tags = HashSet<String>()

            for (entry in entryList) {
                tags.addAll(entry.tags)
            }

            return ArrayList(tags)
        }

    fun getFilteredEntries(constraint: CharSequence?, filterValues: List<Constants.SearchIncludes>, sortMode: Constants.SortMode?): ArrayList<Entry> {
        var filtered = ArrayList<Entry>()

        if (constraint != null && constraint.length != 0) {
            for (i in entryList.indices) {
                if (filterValues.contains(Constants.SearchIncludes.LABEL) && entryList[i].label!!.lowercase(Locale.getDefault()).contains(constraint.toString().lowercase(Locale.getDefault()))) {
                    filtered.add(entryList[i])
                } else if (filterValues.contains(Constants.SearchIncludes.ISSUER) && entryList[i].issuer.lowercase(Locale.getDefault()).contains(constraint.toString().lowercase(Locale.getDefault()))) {
                    filtered.add(entryList[i])
                } else if (filterValues.contains(Constants.SearchIncludes.TAGS)) {
                    val tags = entryList[i].tags
                    for (j in tags.indices) {
                        if (tags[j].lowercase(Locale.getDefault()).contains(constraint.toString().lowercase(Locale.getDefault()))) {
                            filtered.add(entryList[i])
                            break
                        }
                    }
                }
            }
        } else {
            filtered = entryList
        }

        return sortEntries(filtered, sortMode)
    }

    fun getEntriesFilteredByTags(tags: List<String>, noTags: Boolean, tagFunctionality: Constants.TagFunctionality?, sortMode: Constants.SortMode?): ArrayList<Entry> {
        val matchingEntries = ArrayList<Entry>()

        for (e in entryList) {
            // Entries with no tags will always be shown
            var foundMatchingTag = e.tags.isEmpty() && noTags

            if (tagFunctionality == Constants.TagFunctionality.AND) {
                if (e.tags.containsAll(tags)) {
                    foundMatchingTag = true
                }
            } else {
                for (tag in tags) {
                    if (e.tags.contains(tag)) {
                        foundMatchingTag = true
                        break
                    }
                }
            }

            if (foundMatchingTag) {
                matchingEntries.add(e)
            }
        }

        return sortEntries(matchingEntries, sortMode)
    }

    class IssuerComparator internal constructor() : Comparator<Entry> {
        internal var collator: Collator = Collator.getInstance()

        init {
            collator.strength = Collator.PRIMARY
        }

        override fun compare(o1: Entry, o2: Entry): Int {
            return collator.compare(o1.issuer, o2.issuer)
        }
    }

    class LabelComparator internal constructor() : Comparator<Entry> {
        internal var collator: Collator = Collator.getInstance()

        init {
            collator.strength = Collator.PRIMARY
        }

        override fun compare(o1: Entry, o2: Entry): Int {
            return collator.compare(o1.label, o2.label)
        }
    }

    class LastUsedComparator : Comparator<Entry> {
        override fun compare(o1: Entry, o2: Entry): Int {
            return java.lang.Long.compare(o2.lastUsed, o1.lastUsed)
        }
    }

    class MostUsedComparator : Comparator<Entry> {
        override fun compare(o1: Entry, o2: Entry): Int {
            return java.lang.Long.compare(o2.usedFrequency, o1.usedFrequency)
        }
    }

    companion object {
        @JvmStatic
        fun sortEntries(unsortedEntries: ArrayList<Entry>, sortMode: Constants.SortMode?): ArrayList<Entry> {
            val sorted = ArrayList(unsortedEntries)

            if (sortMode == Constants.SortMode.ISSUER) {
                Collections.sort(sorted, IssuerComparator())
            } else if (sortMode == Constants.SortMode.LABEL) {
                Collections.sort(sorted, LabelComparator())
            } else if (sortMode == Constants.SortMode.LAST_USED) {
                Collections.sort(sorted, LastUsedComparator())
            } else if (sortMode == Constants.SortMode.MOST_USED) {
                Collections.sort(sorted, MostUsedComparator())
            }

            return sorted
        }
    }
}
