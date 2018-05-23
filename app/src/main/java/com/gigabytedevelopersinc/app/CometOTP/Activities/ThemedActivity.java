package com.gigabytedevelopersinc.app.CometOTP.Activities;

import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.gigabytedevelopersinc.app.CometOTP.R;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Settings;

import java.util.Locale;

public abstract class ThemedActivity extends AppCompatActivity {
    public Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = new Settings(this);

        setTheme();
        setLocale();

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        setLocale();

        super.onResume();
    }

    public void setTheme() {
        String theme = settings.getTheme();

        if (theme.equals("light")) {
            setTheme(R.style.AppTheme_NoActionBar);
        } else if (theme.equals("dark")) {
            setTheme(R.style.AppTheme_Dark_NoActionBar);
        } else if (theme.equals("black")) {
            setTheme(R.style.AppTheme_Black_NoActionBar);
        }
    }

    public void setLocale() {
        Locale locale = settings.getLocale();
        Locale.setDefault(locale);

        Configuration config = new Configuration();
        config.locale = locale;

        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
    }
}
