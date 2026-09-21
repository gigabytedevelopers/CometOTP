package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

public abstract class ThemedActivity extends AppCompatActivity {
    public Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new Settings(this);

        setTheme(settings.getTheme());

        // Set the navigation bar color on older platforms. From Android 15 (API 35) the app is
        // drawn edge-to-edge, the system bars are transparent and this call is a deprecated no-op;
        // the layouts use fitsSystemWindows so their own background shows behind the bars instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            getWindow().setNavigationBarColor(Tools.getThemeColor(this, R.attr.navigationBarColor));
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        applySystemBarAppearance();
    }

    /**
     * Keeps the status and navigation bar icons readable. The window takes its icon appearance
     * from whichever theme was set when it was created, which is not necessarily the theme the
     * user chose, so a dark screen could end up with dark icons on it. Deciding from the theme's
     * own background colour gets it right for the light, dark and black themes alike.
     */
    private void applySystemBarAppearance() {
        int background = Tools.getThemeColor(this, android.R.attr.colorBackground);
        boolean lightBars = ColorUtils.calculateLuminance(background) > 0.5;

        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightBars);
        controller.setAppearanceLightNavigationBars(lightBars);
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
