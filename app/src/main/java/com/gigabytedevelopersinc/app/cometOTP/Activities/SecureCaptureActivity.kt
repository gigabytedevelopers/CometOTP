@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.view.WindowManager
import com.gigabytedevelopersinc.app.cometOTP.Utilities.AppTheme
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ScreenshotGuard
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Tuesday, 24
 * Month: December
 * Year: 2019
 * Date: 24 Dec, 2019
 * Time: 3:30 AM
 * Desc: SecureCaptureActivity
 **/
class SecureCaptureActivity : CaptureActivity() {
    // Not a ThemedActivity, so it keeps its own; see ThemedActivity.protectsScreen.
    private val screenshotGuard = ScreenshotGuard(this)

    override fun initializeContent(): DecoratedBarcodeView {
        val settings = Settings(this)

        AppTheme.apply(this, settings)

        screenshotGuard.update()

        @Suppress("DEPRECATION")
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)

        return super.initializeContent()
    }

    override fun onResume() {
        super.onResume()
        screenshotGuard.update()
    }

    override fun onDestroy() {
        screenshotGuard.stop()
        super.onDestroy()
    }
}
