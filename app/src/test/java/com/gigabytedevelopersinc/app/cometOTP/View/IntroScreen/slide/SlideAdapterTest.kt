@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideAdapterTest {

    private class TestSlide(val name: String) : Slide {
        override val fragment: Fragment? = null
        override val background: Int = 0
        override val backgroundDark: Int = 0
        override fun canGoForward(): Boolean = true
        override fun canGoBackward(): Boolean = true
        override fun toString(): String = name
    }

    private val a = TestSlide("a")
    private val b = TestSlide("b")
    private val c = TestSlide("c")
    private val d = TestSlide("d")

    private fun adapterOf(vararg slides: Slide) = SlideAdapter(FragmentManagerStub(), slides.toList())

    private class FragmentManagerStub : FragmentManager()

    @Test
    fun retainSlidesRemovesEverySlideNotInTheCollection() {
        val adapter = adapterOf(a, b, c, d)

        assertTrue(adapter.retainSlides(listOf(a)))

        assertEquals(listOf<Slide>(a), adapter.slides)
    }

    @Test
    fun retainSlidesRemovesAdjacentSlides() {
        val adapter = adapterOf(a, b, c, d)

        assertTrue(adapter.retainSlides(listOf(a, d)))

        assertEquals(listOf<Slide>(a, d), adapter.slides)
    }

    @Test
    fun retainSlidesKeepsEverythingWhenAllAreRetained() {
        val adapter = adapterOf(a, b, c)

        assertFalse(adapter.retainSlides(listOf(a, b, c)))

        assertEquals(listOf<Slide>(a, b, c), adapter.slides)
    }

    @Test
    fun retainSlidesWithAnEmptyCollectionRemovesAll() {
        val adapter = adapterOf(a, b, c)

        assertTrue(adapter.retainSlides(emptyList<Slide>()))

        assertEquals(emptyList<Slide>(), adapter.slides)
    }
}
