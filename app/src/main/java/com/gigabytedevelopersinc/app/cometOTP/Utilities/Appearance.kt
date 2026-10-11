@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.ContextThemeWrapper
import com.gigabytedevelopersinc.app.cometOTP.R
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions

/** The theme the app is drawn in, once "auto" has followed the system's dark mode. */
enum class ThemeChoice { LIGHT, DARK, BLACK }

/**
 * What the theme picker offers. AUTO follows the system's dark mode (Android 10 and later), with
 * the black theme in place of the dark one if "black in auto mode" is on.
 */
enum class ThemePick(val value: String) { AUTO("auto"), LIGHT("light"), DARK("dark"), BLACK("black") }

/**
 * The stored theme settings ([mode] "auto" or "manual", [theme] "light", "dark" or "black") as
 * the theme to draw. Before Android 10 there is no system dark mode to follow, and auto mode
 * falls back to the manual theme, as it always has.
 */
internal fun resolveTheme(mode: String, theme: String, blackInAuto: Boolean, systemDark: Boolean,
                          autoSupported: Boolean): ThemeChoice {
    if (autoSupported && mode == "auto") {
        return when {
            !systemDark -> ThemeChoice.LIGHT
            blackInAuto -> ThemeChoice.BLACK
            else -> ThemeChoice.DARK
        }
    }
    return when (theme) {
        "dark" -> ThemeChoice.DARK
        "black" -> ThemeChoice.BLACK
        else -> ThemeChoice.LIGHT
    }
}

/** The picker's choice for the stored theme settings; see [resolveTheme]. */
internal fun themePickFor(mode: String, theme: String, autoSupported: Boolean): ThemePick {
    if (autoSupported && mode == "auto")
        return ThemePick.AUTO
    return when (theme) {
        "dark" -> ThemePick.DARK
        "black" -> ThemePick.BLACK
        else -> ThemePick.LIGHT
    }
}

/**
 * The list styles: presets for the token size (the label size setting, see
 * EntryViewHolder.setLabelSize) and the icon size, which can still be set one by one.
 */
enum class ListStyle(val labelSize: Int, val thumbnailSize: String) {
    COMPACT(14, "32dp"),
    DEFAULT(18, "46dp"),
    LARGE(22, "64dp");

    companion object {
        /** The style these settings make, or null when they are set one by one to something else. */
        fun of(labelSize: Int, thumbnailSize: String): ListStyle? {
            return entries.firstOrNull { it.labelSize == labelSize && it.thumbnailSize == thumbnailSize }
        }
    }
}

/**
 * Applies the chosen theme, and with dynamic colour the wallpaper's colours on top of it. The
 * light or dark wallpaper palette follows the chosen theme rather than the system, so a light
 * theme picked while the system is dark stays light; the black theme keeps its black surfaces.
 */
object AppTheme {

    fun styleFor(choice: ThemeChoice): Int {
        return when (choice) {
            ThemeChoice.LIGHT -> R.style.AppTheme_NoActionBar
            ThemeChoice.DARK -> R.style.AppTheme_Dark_NoActionBar
            ThemeChoice.BLACK -> R.style.AppTheme_Black_NoActionBar
        }
    }

    /** Whether the device has wallpaper colours to offer (Android 12 and later). */
    val dynamicColorsAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && DynamicColors.isDynamicColorAvailable()

    private fun dynamicOverlayFor(choice: ThemeChoice): Int {
        return if (choice == ThemeChoice.LIGHT)
            com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Light
        else
            com.google.android.material.R.style.ThemeOverlay_Material3_DynamicColors_Dark
    }

    /** Sets [activity]'s theme; call it before super.onCreate(). */
    fun apply(activity: Activity, settings: Settings) {
        val choice = settings.themeChoice
        activity.setTheme(styleFor(choice))
        if (settings.dynamicColors && dynamicColorsAvailable) {
            DynamicColors.applyToActivityIfAvailable(activity,
                DynamicColorsOptions.Builder().setThemeOverlay(dynamicOverlayFor(choice)).build())
            if (choice == ThemeChoice.BLACK)
                activity.theme.applyStyle(R.style.ThemeOverlay_Comet_BlackSurfaces, true)
        }
    }

    /** [context] drawn as [choice], with [dynamic] colour or not: for the theme picker's previews. */
    fun previewContext(context: Context, choice: ThemeChoice, dynamic: Boolean): Context {
        val themed = ContextThemeWrapper(context, styleFor(choice))
        if (dynamic && dynamicColorsAvailable) {
            themed.theme.applyStyle(dynamicOverlayFor(choice), true)
            if (choice == ThemeChoice.BLACK)
                themed.theme.applyStyle(R.style.ThemeOverlay_Comet_BlackSurfaces, true)
        }
        return themed
    }
}
