package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

public abstract class BaseActivity extends ThemedActivity {
    private ScreenOffReceiver screenOffReceiver;
    private BroadcastReceivedCallback broadcastReceivedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        screenOffReceiver = new ScreenOffReceiver();
        registerReceiver(screenOffReceiver, screenOffReceiver.filter);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(screenOffReceiver);

        super.onDestroy();
    }

    private void destroyIfNotMain() {
        if (getClass() != MainActivity.class)
            finish();
    }

    public void setBroadcastCallback(BroadcastReceivedCallback cb) {
        this.broadcastReceivedCallback = cb;
    }

    public class ScreenOffReceiver extends BroadcastReceiver {
        public IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (broadcastReceivedCallback != null)
                    broadcastReceivedCallback.onReceivedScreenOff();

                destroyIfNotMain();
            }
        }
    }

    interface BroadcastReceivedCallback {
        void onReceivedScreenOff();
    }
}
