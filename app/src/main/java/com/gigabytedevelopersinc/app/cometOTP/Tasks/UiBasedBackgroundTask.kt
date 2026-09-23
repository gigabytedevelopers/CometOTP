@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Tasks

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Encapsulates a background task that needs to communicate back to the UI (on the main thread) to
 * provide a result. */
abstract class UiBasedBackgroundTask<Result : Any>
/** @param failedResult The result to return if the task fails (throws an exception or returns null). */
constructor(private val failedResult: Result) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    private val callbackLock = Any()
    private var callback: UiCallback<Result>? = null
    private var awaitedResult: Result? = null

    @Volatile
    @get:AnyThread
    var isCanceled = false
        private set

    /** @param callback If null, any results which may arrive from a currently executing task will
     *                   be stored until a new callback is set. */
    fun setCallback(callback: UiCallback<Result>?) {
        synchronized(callbackLock) {
            // Don't bother doing anything if the task was canceled.
            if (isCanceled) {
                return
            }
            this.callback = callback
            // If we have an awaited result and are setting a new callback, publish the result immediately.
            val result = awaitedResult
            if (result != null && callback != null) {
                emitResultOnMainThread(callback, result)
            }
        }
    }

    private fun emitResultOnMainThread(callback: UiCallback<Result>, result: Result) {
        mainThreadHandler.post { callback.onResult(result) }
        this.callback = null
        this.awaitedResult = null
    }

    /** Executed the task on a background thread. Safe to call from the main thread. */
    @AnyThread
    fun execute() {
        executor.execute { runTask() }
    }

    private fun runTask() {
        var result = failedResult
        try {
            result = doInBackground()
        } catch (e: Exception) {
            Log.e("UiBasedBackgroundTask", "Problem running background task", e)
        }

        synchronized(callbackLock) {
            // Don't bother issuing callback or storing result if this task is canceled.
            if (isCanceled) {
                return
            }
            val currentCallback = callback
            if (currentCallback != null) {
                emitResultOnMainThread(currentCallback, result)
            } else {
                awaitedResult = result
            }
        }
    }

    /** Work to be done in a background thread.
     * @return Return the result from this task's execution.
     * @throws Exception If an Exception is thrown from this task's execution, it will be logged
     *         and the provided default Result will be returned. */
    protected abstract fun doInBackground(): Result

    @AnyThread
    fun cancel() {
        isCanceled = true
    }

    fun interface UiCallback<Result : Any> {
        @MainThread
        fun onResult(result: Result)
    }
}
