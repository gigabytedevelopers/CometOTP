@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Images shared from another app reach the main screen through a forwarded intent that clears
 * whatever was open above it, including the lock screen and the setup wizard, both of which used
 * to close the app whenever they came back cancelled.
 */
class MainActivityShareTest {

    @Test
    fun aCancelledScreenStillClosesTheAppWhenNothingArrived() {
        assertEquals(CancelledScreenOutcome.CLOSE, cancelledScreenOutcome(newIntentArrived = false))
    }

    @Test
    fun aScreenClearedForANewIntentIsShownAgain() {
        assertEquals(CancelledScreenOutcome.SHOW_AGAIN, cancelledScreenOutcome(newIntentArrived = true))
    }

    @Test
    fun readsTheOneImageOfASingleShare() {
        assertEquals(listOf("a"), sharedItems("a", null, emptyList()))
    }

    @Test
    fun readsEveryImageOfAMultipleShare() {
        assertEquals(listOf("a", "b", "c"), sharedItems(null, listOf("a", "b", "c"), emptyList()))
    }

    @Test
    fun readsAnImageTheSystemCopiedIntoTheClipDataOnce() {
        assertEquals(listOf("a"), sharedItems("a", null, listOf("a")))
        assertEquals(listOf("a", "b"), sharedItems(null, listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun fallsBackToTheClipDataWhenTheExtraIsMissing() {
        assertEquals(listOf("a", "b"), sharedItems(null, null, listOf("a", "b")))
    }

    @Test
    fun keepsTheOrderAndDropsEmptyItems() {
        assertEquals(listOf("b", "a", "c"), sharedItems("b", listOf(null, "a"), listOf(null, "c", "b")))
    }

    @Test
    fun readsNothingFromAnEmptyShare() {
        assertEquals(emptyList<String>(), sharedItems(null, null, emptyList()))
        assertEquals(emptyList<String>(), sharedItems(null, listOf(null), listOf(null)))
    }
}
