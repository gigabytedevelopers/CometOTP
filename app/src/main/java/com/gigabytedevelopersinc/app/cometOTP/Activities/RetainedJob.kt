@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.os.Handler
import android.os.Looper

/**
 * Runs one slow job at a time (deriving a credential, re-encrypting the database) on a background
 * thread and hands its outcome to the instance of [A] that is resumed when the job finishes, or
 * else to the next one that resumes.
 *
 * Keep it in a companion object so that a job outlives the activity instance that started it.
 * The outcome carries the new database key: if the activity is recreated (rotation, dark mode,
 * window resize) or destroyed in the background while the job runs, the key must still reach the
 * instance that later returns it to MainActivity. Otherwise MainActivity keeps the old key for a
 * database that is now encrypted with the new one, shows no entries and would overwrite the
 * database under the old key on its next save.
 *
 * Everything except the job itself runs on the main thread. The two parameters exist for tests.
 */
internal class RetainedJob<A : Any>(
    private val runInBackground: (Runnable) -> Unit = { Thread(it).start() },
    private val postToMainThread: (Runnable) -> Unit = { Handler(Looper.getMainLooper()).post(it) }
) {
    private var resumed: A? = null
    private var outcome: ((A) -> Unit)? = null

    /** True from [start] until the outcome has been handed to an activity. */
    var isBusy = false
        private set

    /** Runs [job] in the background; the function it returns is the outcome to deliver. */
    fun start(job: () -> (A) -> Unit) {
        check(!isBusy) { "A job is already running" }
        isBusy = true

        runInBackground(Runnable {
            val result = job()
            postToMainThread(Runnable {
                outcome = result
                resumed?.let { deliver(it) }
            })
        })
    }

    /** Call from onResume(); delivers a finished outcome right away. */
    fun onResume(activity: A) {
        resumed = activity
        deliver(activity)
    }

    /** Call from onPause(). */
    fun onPause(activity: A) {
        if (resumed === activity)
            resumed = null
    }

    private fun deliver(activity: A) {
        val result = outcome ?: return
        outcome = null
        isBusy = false
        result(activity)
    }
}
