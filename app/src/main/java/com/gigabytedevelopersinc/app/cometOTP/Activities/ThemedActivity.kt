@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.AppTheme
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ScreenshotGuard
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools

abstract class ThemedActivity : AppCompatActivity() {
    // lateinit exposes a public field, so Java subclasses keep reading `settings` directly.
    lateinit var settings: Settings

    /** Whether this screen shows codes or secrets, and is kept out of screenshots; see [ScreenshotGuard]. */
    protected open val protectsScreen: Boolean
        get() = false

    private var screenshotGuard: ScreenshotGuard? = null

    /** Whether screenshots of this protected screen are allowed now, and until when (0: no end). */
    protected open fun onScreenshotsChanged(allowed: Boolean, until: Long) {}

    /** Applies the screenshot setting again, e.g. right after it was changed here. */
    protected fun updateScreenshotGuard() {
        screenshotGuard?.update()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = Settings(this)

        AppTheme.apply(this, settings)

        if (protectsScreen)
            screenshotGuard = ScreenshotGuard(this) { allowed, until -> onScreenshotsChanged(allowed, until) }
                .also { it.update() }

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
        screenshotGuard?.update()
    }

    override fun onDestroy() {
        screenshotGuard?.stop()
        super.onDestroy()
    }
}
