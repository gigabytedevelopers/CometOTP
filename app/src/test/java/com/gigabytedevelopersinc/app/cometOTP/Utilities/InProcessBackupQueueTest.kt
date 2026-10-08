@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class InProcessBackupQueueTest {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val queue = InProcessBackupQueue(executor, retryDelayMs = 1)

    @After fun close() { executor.shutdownNow() }

    @Test fun aBurstBacksUpOnlyTheLatestEdit() {
        val done = CountDownLatch(1)
        val values = Collections.synchronizedList(mutableListOf<Int>())
        // Block the executor so cancellation is deterministic, without relying on sleep timing.
        val release = CountDownLatch(1)
        executor.execute { release.await() }
        queue.submit(0) { values.add(1); true }
        queue.submit(0) { values.add(2); done.countDown(); true }
        release.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(2), values)
    }

    @Test fun anEditDuringABackupRunsAfterIt() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val values = Collections.synchronizedList(mutableListOf<Int>())
        queue.submit(0) { started.countDown(); release.await(); values.add(1); true }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        queue.submit(0) { values.add(2); done.countDown(); true }
        release.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(1, 2), values)
    }

    @Test fun unmetConstraintsKeepTheBackupPending() {
        val attempts = AtomicInteger()
        val done = CountDownLatch(1)
        queue.submit(0) {
            if (attempts.incrementAndGet() == 1) false
            else { done.countDown(); true }
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(2, attempts.get())
    }

    @Test fun anEditReplacesADeferredBackup() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val done = CountDownLatch(1)
        val attempts = AtomicInteger()
        queue.submit(0) { attempts.incrementAndGet(); started.countDown(); release.await(); false }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        queue.submit(0) { done.countDown(); true }
        release.countDown()
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, attempts.get())
    }
}
