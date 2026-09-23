@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.core.content.ContextCompat

abstract class BaseActivity : ThemedActivity() {
    private lateinit var screenOffReceiver: ScreenOffReceiver
    private var broadcastReceivedCallback: BroadcastReceivedCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        screenOffReceiver = ScreenOffReceiver()
        // ACTION_SCREEN_OFF is a protected system broadcast, so the receiver does not need to be
        // reachable from other apps. Android 14+ (API 34) requires the export flag to be explicit.
        ContextCompat.registerReceiver(this, screenOffReceiver, screenOffReceiver.filter,
                ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        super.onDestroy()
    }

    fun setBroadcastCallback(cb: BroadcastReceivedCallback?) {
        this.broadcastReceivedCallback = cb
    }

    inner class ScreenOffReceiver : BroadcastReceiver() {
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action!! == Intent.ACTION_SCREEN_OFF) {
                broadcastReceivedCallback?.onReceivedScreenOff()
                if (shouldDestroyOnScreenOff()) {
                    finish()
                }
            }
        }
    }

    protected open fun shouldDestroyOnScreenOff(): Boolean {
        return true
    }

    // Package-private in the Java original; Kotlin has no package visibility and the public
    // setBroadcastCallback() cannot expose a less visible type, so it is public now.
    fun interface BroadcastReceivedCallback {
        fun onReceivedScreenOff()
    }
}
