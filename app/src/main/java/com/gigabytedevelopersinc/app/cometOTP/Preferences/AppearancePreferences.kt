@file:Suppress("PackageName", "DEPRECATION", "OVERRIDE_DEPRECATION")
package com.gigabytedevelopersinc.app.cometOTP.Preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.Preference
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.AppTheme
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ThemeChoice
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ThemePick
import com.gigabytedevelopersinc.app.cometOTP.View.AppearancePreviewView
import com.google.android.material.card.MaterialCardView

/**
 * A row of tiles, each a preview of what it picks, the chosen one outlined. The tiles store what
 * they pick in the settings they stand for; this preference keeps no value of its own, and shows
 * the choice again whenever those settings change.
 */
abstract class TilePickerPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

    protected class Tile(val label: Int, val selected: Boolean, val bindPreview: (AppearancePreviewView) -> Unit,
                         val onPick: () -> Unit)

    protected abstract fun tiles(): List<Tile>

    /** The settings whose change can change the choice shown. */
    protected abstract val watchedKeys: Set<Int>

    // Held here: SharedPreferences keeps only a weak reference to its listeners.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || watchedKeys.any { context.getString(it) == key })
            notifyChanged()
    }

    init {
        layoutResource = R.layout.preference_tile_picker
        isPersistent = false
        isSelectable = false
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onPrepareForRemoval() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        super.onPrepareForRemoval()
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        val row = view.findViewById<LinearLayout>(R.id.tile_row)
        row.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (tile in tiles()) {
            val item = inflater.inflate(R.layout.item_appearance_tile, row, false)
            val frame = item.findViewById<MaterialCardView>(R.id.tile_frame)
            tile.bindPreview(item.findViewById(R.id.tile_preview))
            item.findViewById<TextView>(R.id.tile_label).setText(tile.label)
            frame.strokeWidth = if (tile.selected)
                context.resources.getDimensionPixelSize(R.dimen.tile_selected_stroke) else 0
            item.isSelected = tile.selected
            item.contentDescription = context.getString(tile.label)
            // Read out as one of a set of choices, with the chosen one checked.
            ViewCompat.setAccessibilityDelegate(item, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = RadioButton::class.java.name
                    info.isCheckable = true
                    info.isChecked = tile.selected
                }
            })
            item.setOnClickListener {
                if (!tile.selected)
                    tile.onPick()
            }
            row.addView(item)
        }
    }
}

/**
 * The theme, picked from previews: Auto (Android 10 and later; half light, half dark or black),
 * Light, Dark and Black. With dynamic colour on, the previews show the wallpaper's colours.
 */
class ThemePickerPreference(context: Context, attrs: AttributeSet?) : TilePickerPreference(context, attrs) {

    override val watchedKeys = setOf(R.string.settings_key_theme, R.string.settings_key_theme_mode,
        R.string.settings_key_theme_black_auto, R.string.settings_key_dynamic_colors)

    override fun tiles(): List<Tile> {
        val settings = Settings(context)
        val current = settings.themePick
        val dynamic = settings.dynamicColors
        val darkInAuto = if (settings.getBoolean(R.string.settings_key_theme_black_auto, false)) ThemeChoice.BLACK else ThemeChoice.DARK
        fun preview(choice: ThemeChoice) = AppTheme.previewContext(context, choice, dynamic)

        val tiles = ArrayList<Tile>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            tiles.add(Tile(R.string.settings_theme_auto, current == ThemePick.AUTO,
                { it.setThemes(preview(ThemeChoice.LIGHT), preview(darkInAuto)) }) { settings.themePick = ThemePick.AUTO })
        tiles.add(Tile(R.string.settings_theme_light, current == ThemePick.LIGHT,
            { it.setThemes(preview(ThemeChoice.LIGHT)) }) { settings.themePick = ThemePick.LIGHT })
        tiles.add(Tile(R.string.settings_theme_dark, current == ThemePick.DARK,
            { it.setThemes(preview(ThemeChoice.DARK)) }) { settings.themePick = ThemePick.DARK })
        tiles.add(Tile(R.string.settings_theme_black, current == ThemePick.BLACK,
            { it.setThemes(preview(ThemeChoice.BLACK)) }) { settings.themePick = ThemePick.BLACK })
        return tiles
    }
}
