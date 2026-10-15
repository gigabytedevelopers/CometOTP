@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import org.junit.Assert.assertEquals
import org.junit.Test

/** What the support screens show, with and without browser sync ([Faq]). */
class FaqTest {

    private val titles = listOf("What is CometOTP", "What is OTP")
    private val bodies = listOf("Never leaves it.", "A short code.")

    private fun texts(browserSync: Boolean) =
        Faq.texts(titles, bodies, browserSync, "Can I use my codes on a computer", "Yes.", "Unless you connect a browser.")

    @Test
    fun withoutBrowserSyncTheFaqIsAsWritten() {
        val faq = texts(browserSync = false)
        assertEquals(titles, faq.titles)
        assertEquals(bodies, faq.bodies)
    }

    @Test
    fun withBrowserSyncTheQuestionOnBrowsersComesLastAndTheFirstAnswerMentionsThem() {
        val faq = texts(browserSync = true)
        assertEquals(titles + "Can I use my codes on a computer", faq.titles)
        assertEquals(listOf("Unless you connect a browser.", "A short code.", "Yes."), faq.bodies)
    }
}
