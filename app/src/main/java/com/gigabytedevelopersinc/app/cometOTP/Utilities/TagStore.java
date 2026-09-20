package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.preference.PreferenceManager;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gigabytedevelopersinc.app.cometOTP.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Metadata for tags (colour, whether the item count is shown, and tags created before any entry
 * uses them). Tags themselves remain plain strings on the entries; this store only decorates them.
 * <p>
 * Persisted as a JSON object in the default shared preferences:
 * {@code {"Important": {"color": "#2979FF", "count": true}}}.
 */
public final class TagStore {
    private static final String KEY = "pref_tag_meta";
    private static final String JSON_COLOR = "color";
    private static final String JSON_COUNT = "count";

    public static final class Meta {
        @ColorInt public int color;
        public boolean showCount;

        Meta(@ColorInt int color, boolean showCount) {
            this.color = color;
            this.showCount = showCount;
        }
    }

    private static Map<String, Meta> cache;

    private TagStore() {
    }

    /** All colours offered by the colour picker, in display order. */
    @NonNull
    public static int[] palette(@NonNull Context context) {
        return context.getResources().getIntArray(R.array.tag_palette);
    }

    @NonNull
    public static String[] paletteNames(@NonNull Context context) {
        return context.getResources().getStringArray(R.array.tag_palette_names);
    }

    @ColorInt
    public static int defaultColor(@NonNull Context context) {
        return palette(context)[0];
    }

    @NonNull
    public static String colorName(@NonNull Context context, @ColorInt int color) {
        int[] palette = palette(context);
        String[] names = paletteNames(context);
        for (int i = 0; i < palette.length && i < names.length; i++) {
            if (palette[i] == color)
                return names[i];
        }
        return String.format(Locale.ROOT, "#%06X", 0xFFFFFF & color);
    }

    /** Colour for a tag, falling back to the default colour when the tag has no metadata. */
    @ColorInt
    public static int colorOf(@NonNull Context context, @Nullable String tag) {
        if (tag == null)
            return defaultColor(context);
        Meta meta = load(context).get(tag);
        return meta != null ? meta.color : defaultColor(context);
    }

    public static boolean showsCount(@NonNull Context context, @NonNull String tag) {
        Meta meta = load(context).get(tag);
        return meta != null && meta.showCount;
    }

    @Nullable
    public static Meta metaOf(@NonNull Context context, @NonNull String tag) {
        return load(context).get(tag);
    }

    /** Tags that have metadata, sorted alphabetically. */
    @NonNull
    public static Set<String> knownTags(@NonNull Context context) {
        Set<String> result = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        result.addAll(load(context).keySet());
        return result;
    }

    public static void put(@NonNull Context context, @NonNull String tag, @ColorInt int color, boolean showCount) {
        Map<String, Meta> all = load(context);
        all.put(tag, new Meta(color, showCount));
        save(context, all);
    }

    public static void rename(@NonNull Context context, @NonNull String oldTag, @NonNull String newTag) {
        if (oldTag.equals(newTag))
            return;
        Map<String, Meta> all = load(context);
        Meta meta = all.remove(oldTag);
        if (meta != null)
            all.put(newTag, meta);
        save(context, all);
    }

    public static void remove(@NonNull Context context, @NonNull String tag) {
        Map<String, Meta> all = load(context);
        if (all.remove(tag) != null)
            save(context, all);
    }

    @NonNull
    private static synchronized Map<String, Meta> load(@NonNull Context context) {
        if (cache != null)
            return cache;

        Map<String, Meta> result = new LinkedHashMap<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String json = prefs.getString(KEY, "");
        if (json != null && !json.isEmpty()) {
            try {
                JSONObject root = new JSONObject(json);
                Iterator<String> keys = root.keys();
                while (keys.hasNext()) {
                    String tag = keys.next();
                    JSONObject obj = root.getJSONObject(tag);
                    int color;
                    try {
                        color = Color.parseColor(obj.optString(JSON_COLOR, ""));
                    } catch (IllegalArgumentException e) {
                        color = defaultColor(context);
                    }
                    result.put(tag, new Meta(color, obj.optBoolean(JSON_COUNT, false)));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        cache = result;
        return cache;
    }

    private static synchronized void save(@NonNull Context context, @NonNull Map<String, Meta> all) {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, Meta> entry : all.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put(JSON_COLOR, String.format(Locale.ROOT, "#%06X", 0xFFFFFF & entry.getValue().color));
                obj.put(JSON_COUNT, entry.getValue().showCount);
                root.put(entry.getKey(), obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, root.toString()).apply();
        cache = all;
    }
}
