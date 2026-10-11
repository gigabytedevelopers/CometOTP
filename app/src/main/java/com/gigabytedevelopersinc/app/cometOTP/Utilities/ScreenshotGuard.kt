@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager

private const val MINUTE_MS = 60_000L

/**
 * When screenshots allowed [now] for [minutes] stop being allowed, in milliseconds since the
 * epoch; 0 (no end) for 0 minutes or less, "until I turn it off".
 */
internal fun screenshotWindowEnd(minutes: Int, now: Long): Long {
    return if (minutes <= 0) 0 else now + minutes * MINUTE_MS
}

/** Whether screenshots switched on ([enabled]) until [until] (0: no end) are still allowed at [now]. */
internal fun screenshotsAllowedAt(enabled: Boolean, until: Long, now: Long): Boolean {
    return enabled && (until <= 0 || now < until)
}

/**
 * Keeps [activity]'s window out of screenshots, screen recordings and the recent apps preview
 * (FLAG_SECURE) unless screenshots are allowed in Settings → Security, and only for as long as
 * they were allowed: when that time is up the window is protected again, even while it stays
 * open. [onChange] hears whether screenshots are allowed, and until when (0: no end), each time
 * it is checked.
 */
class ScreenshotGuard(private val activity: Activity, private val onChange: ((Boolean, Long) -> Unit)? = null) {
    private val handler = Handler(Looper.getMainLooper())
    private val expire = Runnable { update() }

    /** Applies the setting as it is now; call it when the window is created and on resume. */
    fun update() {
        handler.removeCallbacks(expire)

        val settings = Settings(activity)
        val allowed = settings.screenshotsAllowed()
        if (allowed)
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Also while the screen is in the background, so its preview in recent apps is covered.
        val until = if (allowed) settings.screenshotsAllowedUntil else 0
        if (until > 0)
            handler.postDelayed(expire, until - System.currentTimeMillis())

        onChange?.invoke(allowed, until)
    }

    /** Call when the activity is destroyed. */
    fun stop() {
        handler.removeCallbacks(expire)
    }

    companion object {
        /**
         * Keeps a dialog's or sheet's [window] out of screenshots unless they are allowed: it is a
         * window of its own, which its screen's FLAG_SECURE does not cover. For those showing
         * codes, secrets or the camera.
         */
        fun protect(window: Window?, settings: Settings) {
            if (window != null && !settings.screenshotsAllowed())
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
