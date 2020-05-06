package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;

import java.util.Set;

public class PanicResponderActivity extends Activity {
    public static final String PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && PANIC_TRIGGER_ACTION.equals(intent.getAction())) {
            Settings settings = new Settings(this);

            Set<String> response = settings.getPanicResponse();

            if (response.contains("accounts")) {
                DatabaseHelper.wipeDatabase(this);
                KeyStoreHelper.wipeKeys(this);
            }

            if (response.contains("settings"))
                settings.clear(true);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }
}