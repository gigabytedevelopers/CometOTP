package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

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
    public void onResume() {
        super.onResume();
    }
}
