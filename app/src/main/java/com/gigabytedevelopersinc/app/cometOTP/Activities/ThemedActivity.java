package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

import java.util.Locale;

public abstract class ThemedActivity extends AppCompatActivity {
    public Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new Settings(this);

        setTheme(settings.getTheme());

        //Set navigation bar color
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(Tools.getThemeColor(this, R.attr.navigationBarColor));
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
