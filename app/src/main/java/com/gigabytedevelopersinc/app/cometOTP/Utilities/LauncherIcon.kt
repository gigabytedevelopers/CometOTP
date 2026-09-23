@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The selectable launcher icons.
 *
 * Each variant is an activity-alias in the manifest pointing at the main activity. Exactly one
 * alias is enabled at a time, and the launcher shows that alias's icon. The aliases are named
 * against the application's own package so they resolve for both the release and the debug
 * build.
 */
object LauncherIcon {

    /** The stored preference value for each variant; also the alias suffix. */
    const val BLUE = "blue"
    const val MONO = "mono"
    const val WHITE = "white"
    const val CLASSIC = "classic"

    const val DEFAULT = BLUE

    private val ALL = arrayOf(BLUE, MONO, WHITE, CLASSIC)

    private const val ALIAS_PREFIX = "com.gigabytedevelopersinc.app.cometOTP.Launcher."

    private fun aliasFor(context: Context, variant: String): ComponentName {
        return ComponentName(context.packageName,
            ALIAS_PREFIX + Character.toUpperCase(variant[0]) + variant.substring(1))
    }

    /** The variant whose alias is currently enabled, falling back to the default. */
    @JvmStatic
    fun current(context: Context): String {
        val pm = context.packageManager
        for (variant in ALL) {
            if (pm.getComponentEnabledSetting(aliasFor(context, variant))
                == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                return variant
        }
        return DEFAULT
    }

    /**
     * Switches the launcher to `variant`. The chosen alias is enabled before the others are
     * turned off, so the launcher never sees a moment with no entry at all and the shortcut is not
     * dropped from the home screen.
     */
    @JvmStatic
    fun apply(context: Context, variant: String?) {
        var chosenVariant: String = variant ?: DEFAULT

        var known = false
        for (candidate in ALL)
            known = known or (candidate == chosenVariant)
        if (!known)
            chosenVariant = DEFAULT

        val pm = context.packageManager
        val chosen = aliasFor(context, chosenVariant)

        if (pm.getComponentEnabledSetting(chosen) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            return

        pm.setComponentEnabledSetting(chosen,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP)

        for (candidate in ALL) {
            if (candidate == chosenVariant)
                continue

            pm.setComponentEnabledSetting(aliasFor(context, candidate),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP)
        }
    }
}
