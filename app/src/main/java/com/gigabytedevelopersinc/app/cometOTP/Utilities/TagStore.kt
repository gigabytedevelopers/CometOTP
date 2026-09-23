@file:Suppress("PackageName", "DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.graphics.Color
import android.preference.PreferenceManager
import androidx.annotation.ColorInt
import com.gigabytedevelopersinc.app.cometOTP.R
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale
import java.util.TreeSet

/**
 * Metadata for tags (colour, whether the item count is shown, and tags created before any entry
 * uses them). Tags themselves remain plain strings on the entries; this store only decorates them.
 *
 * Persisted as a JSON object in the default shared preferences:
 * `{"Important": {"color": "#2979FF", "count": true}}`.
 */
object TagStore {
    private const val KEY = "pref_tag_meta"
    private const val JSON_COLOR = "color"
    private const val JSON_COUNT = "count"

    class Meta internal constructor(@ColorInt color: Int, showCount: Boolean) {
        @ColorInt
        @JvmField
        var color: Int = color
        @JvmField
        var showCount: Boolean = showCount
    }

    private var cache: MutableMap<String, Meta>? = null

    /** All colours offered by the colour picker, in display order. */
    @JvmStatic
    fun palette(context: Context): IntArray {
        return context.resources.getIntArray(R.array.tag_palette)
    }

    @JvmStatic
    fun paletteNames(context: Context): Array<String> {
        return context.resources.getStringArray(R.array.tag_palette_names)
    }

    @ColorInt
    @JvmStatic
    fun defaultColor(context: Context): Int {
        return palette(context)[0]
    }

    @JvmStatic
    fun colorName(context: Context, @ColorInt color: Int): String {
        val palette = palette(context)
        val names = paletteNames(context)
        var i = 0
        while (i < palette.size && i < names.size) {
            if (palette[i] == color)
                return names[i]
            i++
        }
        return String.format(Locale.ROOT, "#%06X", 0xFFFFFF and color)
    }

    /** Colour for a tag, falling back to the default colour when the tag has no metadata. */
    @ColorInt
    @JvmStatic
    fun colorOf(context: Context, tag: String?): Int {
        if (tag == null)
            return defaultColor(context)
        val meta = load(context)[tag]
        return meta?.color ?: defaultColor(context)
    }

    @JvmStatic
    fun showsCount(context: Context, tag: String): Boolean {
        val meta = load(context)[tag]
        return meta != null && meta.showCount
    }

    @JvmStatic
    fun metaOf(context: Context, tag: String): Meta? {
        return load(context)[tag]
    }

    /** Tags that have metadata, sorted alphabetically. */
    @JvmStatic
    fun knownTags(context: Context): Set<String> {
        val result: MutableSet<String> = TreeSet(String.CASE_INSENSITIVE_ORDER)
        result.addAll(load(context).keys)
        return result
    }

    @JvmStatic
    fun put(context: Context, tag: String, @ColorInt color: Int, showCount: Boolean) {
        val all = load(context)
        all[tag] = Meta(color, showCount)
        save(context, all)
    }

    @JvmStatic
    fun rename(context: Context, oldTag: String, newTag: String) {
        if (oldTag == newTag)
            return
        val all = load(context)
        val meta = all.remove(oldTag)
        if (meta != null)
            all[newTag] = meta
        save(context, all)
    }

    @JvmStatic
    fun remove(context: Context, tag: String) {
        val all = load(context)
        if (all.remove(tag) != null)
            save(context, all)
    }

    @Synchronized
    private fun load(context: Context): MutableMap<String, Meta> {
        cache?.let { return it }

        val result: MutableMap<String, Meta> = LinkedHashMap()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val json = prefs.getString(KEY, "")
        if (json != null && json.isNotEmpty()) {
            try {
                val root = JSONObject(json)
                val keys = root.keys()
                while (keys.hasNext()) {
                    val tag = keys.next()
                    val obj = root.getJSONObject(tag)
                    var color: Int
                    try {
                        color = Color.parseColor(obj.optString(JSON_COLOR, ""))
                    } catch (e: IllegalArgumentException) {
                        color = defaultColor(context)
                    }
                    result[tag] = Meta(color, obj.optBoolean(JSON_COUNT, false))
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        cache = result
        return result
    }

    @Synchronized
    private fun save(context: Context, all: MutableMap<String, Meta>) {
        val root = JSONObject()
        try {
            for ((key, value) in all) {
                val obj = JSONObject()
                obj.put(JSON_COLOR, String.format(Locale.ROOT, "#%06X", 0xFFFFFF and value.color))
                obj.put(JSON_COUNT, value.showCount)
                root.put(key, obj)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, root.toString()).apply()
        cache = all
    }
}
