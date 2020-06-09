package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.view.WindowManager;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

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
public class SecureCaptureActivity extends CaptureActivity {
    @Override
    protected DecoratedBarcodeView initializeContent() {
        Settings settings = new Settings(this);

        setTheme(settings.getTheme());

        if (!settings.getScreenshotsEnabled())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);

        return super.initializeContent();
    }
}