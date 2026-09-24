@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view

import android.database.DataSetObserver
import android.view.View
import androidx.viewpager.widget.PagerAdapter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FadeableViewPager wraps every adapter it is given, and IntroActivity hands it the same adapter
 * again on every data change. Each wrapper registers an observer on the wrapped adapter, so a
 * wrapper that is replaced has to take its observer off again.
 */
class PagerAdapterWrapperTest {

    /** Records observers itself, since the unit-test android.jar's DataSetObservable does nothing. */
    private class TrackingAdapter : PagerAdapter() {
        val observers = mutableListOf<DataSetObserver>()

        override fun getCount(): Int = 0
        override fun isViewFromObject(view: View, `object`: Any): Boolean = false

        override fun registerDataSetObserver(observer: DataSetObserver) {
            observers.add(observer)
        }

        override fun unregisterDataSetObserver(observer: DataSetObserver) {
            observers.remove(observer)
        }
    }

    @Test
    fun releaseUnregistersTheWrapperFromTheWrappedAdapter() {
        val inner = TrackingAdapter()

        val wrapper = FadeableViewPager.PagerAdapterWrapper(inner)
        assertEquals(1, inner.observers.size)

        wrapper.release()
        assertEquals(0, inner.observers.size)
    }

    @Test
    fun rewrappingTheSameAdapterDoesNotAccumulateObservers() {
        val inner = TrackingAdapter()

        // What FadeableViewPager.setAdapter now does each time the same adapter is set again.
        var current = FadeableViewPager.PagerAdapterWrapper(inner)
        repeat(5) {
            val next = FadeableViewPager.PagerAdapterWrapper(inner)
            current.release()
            current = next
        }

        assertEquals(1, inner.observers.size)
    }
}
