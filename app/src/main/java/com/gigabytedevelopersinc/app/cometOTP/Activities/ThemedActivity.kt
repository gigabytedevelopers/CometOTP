@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools

abstract class ThemedActivity : AppCompatActivity() {
    // lateinit exposes a public field, so Java subclasses keep reading `settings` directly.
    lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings(this)

        setTheme(settings.theme)

        // Set the navigation bar color on older platforms. From Android 15 (API 35) the app is
        // drawn edge-to-edge, the system bars are transparent and this call is a deprecated no-op;
        // the layouts use fitsSystemWindows so their own background shows behind the bars instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            window.navigationBarColor = Tools.getThemeColor(this, R.attr.navigationBarColor)
        }

        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        applySystemBarAppearance()
    }

    /**
     * Keeps the status and navigation bar icons readable. The window takes its icon appearance
     * from whichever theme was set when it was created, which is not necessarily the theme the
     * user chose, so a dark screen could end up with dark icons on it. Deciding from the theme's
     * own background colour gets it right for the light, dark and black themes alike.
     */
    private fun applySystemBarAppearance() {
        val background = Tools.getThemeColor(this, android.R.attr.colorBackground)
        val lightBars = ColorUtils.calculateLuminance(background) > 0.5

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }

    override fun onResume() {
        super.onResume()
    }
}
