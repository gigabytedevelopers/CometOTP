@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The outcome of a credential / encryption change carries the new database key, so it has to
 * reach whichever instance of the screen is current when the change finishes - also when the
 * instance that started it has been recreated in the meantime.
 */
class RetainedJobTest {

    private class Screen(val name: String) {
        val received = mutableListOf<String>()
    }

    /** Background work and main-thread posts are queued and run by hand. */
    private val background = ArrayList<Runnable>()
    private val main = ArrayList<Runnable>()

    private fun newJob() = RetainedJob<Screen>({ background.add(it) }, { main.add(it) })

    private fun runAll(queue: MutableList<Runnable>) {
        while (queue.isNotEmpty())
            queue.removeAt(0).run()
    }

    @Test
    fun outcomeReachesTheResumedInstance() {
        val job = newJob()
        val screen = Screen("a")
        job.onResume(screen)

        job.start { { s: Screen -> s.received.add("key") } }
        assertTrue(job.isBusy)

        runAll(background)
        runAll(main)

        assertEquals(listOf("key"), screen.received)
        assertFalse(job.isBusy)
    }

    @Test
    fun outcomeReachesTheRecreatedInstanceWhenItFinishesAfterRecreation() {
        val job = newJob()
        val old = Screen("old")
        job.onResume(old)
        job.start { { s: Screen -> s.received.add("key") } }

        // Rotation: the old instance pauses and is destroyed, a new one is created and resumes.
        job.onPause(old)
        val recreated = Screen("new")
        job.onResume(recreated)

        runAll(background)
        runAll(main)

        assertEquals(emptyList<String>(), old.received)
        assertEquals(listOf("key"), recreated.received)
        assertFalse(job.isBusy)
    }

    @Test
    fun outcomeWaitsForTheNextInstanceWhenNoneIsResumed() {
        val job = newJob()
        val old = Screen("old")
        job.onResume(old)
        job.start { { s: Screen -> s.received.add("key") } }
        job.onPause(old)

        runAll(background)
        runAll(main)

        // Finished while no instance was resumed: kept, and still reported as busy.
        assertEquals(emptyList<String>(), old.received)
        assertTrue(job.isBusy)

        val recreated = Screen("new")
        job.onResume(recreated)

        assertEquals(listOf("key"), recreated.received)
        assertFalse(job.isBusy)

        // Delivered exactly once.
        job.onPause(recreated)
        job.onResume(recreated)
        assertEquals(listOf("key"), recreated.received)
    }

    @Test
    fun pausingAnOlderInstanceDoesNotForgetTheCurrentOne() {
        val job = newJob()
        val old = Screen("old")
        val recreated = Screen("new")
        job.onResume(old)
        job.start { { s: Screen -> s.received.add("key") } }

        // The new instance resumes before the old one's pause is seen.
        job.onResume(recreated)
        job.onPause(old)

        runAll(background)
        runAll(main)

        assertEquals(listOf("key"), recreated.received)
    }
}
