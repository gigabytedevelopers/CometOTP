package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * The selectable launcher icons.
 *
 * <p>Each variant is an activity-alias in the manifest pointing at the main activity. Exactly one
 * alias is enabled at a time, and the launcher shows that alias's icon. The aliases are named
 * against the application's own package so they resolve for both the release and the debug
 * build.</p>
 */
public final class LauncherIcon {

    /** The stored preference value for each variant; also the alias suffix. */
    public static final String BLUE = "blue";
    public static final String MONO = "mono";
    public static final String WHITE = "white";
    public static final String CLASSIC = "classic";

    public static final String DEFAULT = BLUE;

    private static final String[] ALL = {BLUE, MONO, WHITE, CLASSIC};

    private static final String ALIAS_PREFIX = "com.gigabytedevelopersinc.app.cometOTP.Launcher.";

    private LauncherIcon() {
    }

    private static ComponentName aliasFor(Context context, String variant) {
        return new ComponentName(context.getPackageName(),
                ALIAS_PREFIX + Character.toUpperCase(variant.charAt(0)) + variant.substring(1));
    }

    /** The variant whose alias is currently enabled, falling back to the default. */
    public static String current(Context context) {
        PackageManager pm = context.getPackageManager();
        for (String variant : ALL) {
            if (pm.getComponentEnabledSetting(aliasFor(context, variant))
                    == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                return variant;
        }
        return DEFAULT;
    }

    /**
     * Switches the launcher to {@code variant}. The chosen alias is enabled before the others are
     * turned off, so the launcher never sees a moment with no entry at all and the shortcut is not
     * dropped from the home screen.
     */
    public static void apply(Context context, String variant) {
        if (variant == null)
            variant = DEFAULT;

        boolean known = false;
        for (String candidate : ALL)
            known |= candidate.equals(variant);
        if (!known)
            variant = DEFAULT;

        PackageManager pm = context.getPackageManager();
        ComponentName chosen = aliasFor(context, variant);

        if (pm.getComponentEnabledSetting(chosen) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
            return;

        pm.setComponentEnabledSetting(chosen,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);

        for (String candidate : ALL) {
            if (candidate.equals(variant))
                continue;

            pm.setComponentEnabledSetting(aliasFor(context, candidate),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        }
    }
}
