package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.core.content.ContextCompat;

import java.util.Objects;

public abstract class BaseActivity extends ThemedActivity {
    private ScreenOffReceiver screenOffReceiver;
    private BroadcastReceivedCallback broadcastReceivedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        screenOffReceiver = new ScreenOffReceiver();
        // ACTION_SCREEN_OFF is a protected system broadcast, so the receiver does not need to be
        // reachable from other apps. Android 14+ (API 34) requires the export flag to be explicit.
        ContextCompat.registerReceiver(this, screenOffReceiver, screenOffReceiver.filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(screenOffReceiver);
        super.onDestroy();
    }

    public void setBroadcastCallback(BroadcastReceivedCallback cb) {
        this.broadcastReceivedCallback = cb;
    }

    public class ScreenOffReceiver extends BroadcastReceiver {
        public IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);

        @Override
        public void onReceive(Context context, Intent intent) {
            if (Objects.requireNonNull(intent.getAction()).equals(Intent.ACTION_SCREEN_OFF)) {
                if (broadcastReceivedCallback != null) {
                    broadcastReceivedCallback.onReceivedScreenOff();
                }
                if (shouldDestroyOnScreenOff()) {
                    finish();
                }
            }
        }
    }

    protected boolean shouldDestroyOnScreenOff() {
        return true;
    }

    interface BroadcastReceivedCallback {
        void onReceivedScreenOff();
    }
}
