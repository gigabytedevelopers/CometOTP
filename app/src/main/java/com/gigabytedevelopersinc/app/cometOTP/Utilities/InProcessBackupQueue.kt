@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Debounces backups that need an unlocked database key, independently of an activity's timers. */
internal class InProcessBackupQueue(
    private val executor: ScheduledExecutorService,
    private val retryDelayMs: Long = 30_000L
) {
    private class Pending(val task: () -> Boolean) {
        var future: ScheduledFuture<*>? = null
    }
    private var pending: Pending? = null
    private var running = false

    val isBusy: Boolean
        @Synchronized get() = running || pending != null

    /** A false result means constraints are unmet; keep the task until they allow a backup. */
    @Synchronized
    fun submit(delayMs: Long, task: () -> Boolean) {
        pending?.future?.cancel(false)
        enqueue(delayMs, task)
    }

    private fun enqueue(delayMs: Long, task: () -> Boolean) {
        val work = Pending(task)
        pending = work
        work.future = executor.schedule({
            synchronized(this) {
                // A canceled timer may already be waiting for this lock; it must not clear a
                // newer edit's timer or make a stale backup.
                if (pending !== work)
                    return@schedule
                pending = null
                running = true
            }
            var completed = true
            try {
                completed = task()
            } finally {
                synchronized(this) {
                    running = false
                    // An edit during this run already queued a newer snapshot; leave it alone.
                    if (!completed && pending == null)
                        enqueue(retryDelayMs, task)
                }
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }
}
