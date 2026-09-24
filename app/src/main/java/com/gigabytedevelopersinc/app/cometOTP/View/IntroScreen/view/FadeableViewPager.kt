/*
 * MIT License
 *
 * Copyright (c) 2017 Jan Heinrich Reimer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view

import android.content.Context
import android.database.DataSetObserver
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.VisibleForTesting
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

open class FadeableViewPager @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : SwipeBlockableViewPager(context, attrs) {

    /** The wrapper currently set on the pager, released when it is replaced. */
    private var adapterWrapper: PagerAdapterWrapper? = null

    override fun setAdapter(adapter: PagerAdapter?) {
        val oldWrapper = adapterWrapper
        val newWrapper = PagerAdapterWrapper(adapter!!)
        adapterWrapper = newWrapper
        super.setAdapter(newWrapper)
        // IntroActivity sets the same adapter again on every data change; without this each old
        // wrapper would stay registered on it for good.
        oldWrapper?.release()
    }

    override fun getAdapter(): PagerAdapter? {
        val wrapper = super.getAdapter() as PagerAdapterWrapper?
        return wrapper?.adapter
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun setOnPageChangeListener(listener: ViewPager.OnPageChangeListener?) {
        super.setOnPageChangeListener(OnPageChangeListenerWrapper(listener))
    }

    override fun addOnPageChangeListener(listener: ViewPager.OnPageChangeListener) {
        super.addOnPageChangeListener(OnPageChangeListenerWrapper(listener))
    }

    override fun removeOnPageChangeListener(listener: ViewPager.OnPageChangeListener) {
        super.removeOnPageChangeListener(OnPageChangeListenerWrapper(listener))
    }

    override fun setPageTransformer(reverseDrawingOrder: Boolean, transformer: ViewPager.PageTransformer?) {
        // A null transformer removes the current one, so it must reach ViewPager as null rather
        // than as a wrapper around nothing.
        super.setPageTransformer(reverseDrawingOrder,
                if (transformer != null) PageTransformerWrapper(transformer) else null)
    }

    private inner class OnPageChangeListenerWrapper(
            private val listener: ViewPager.OnPageChangeListener?
    ) : ViewPager.OnPageChangeListener {

        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            val count = if (listener is OnOverscrollPageChangeListener)
                super@FadeableViewPager.getAdapter()!!.count else adapter!!.count
            listener!!.onPageScrolled(Math.min(position, count - 1),
                    if (position < count) positionOffset else 0f,
                    if (position < count) positionOffsetPixels else 0)
        }

        override fun onPageSelected(position: Int) {
            val count = if (listener is OnOverscrollPageChangeListener)
                super@FadeableViewPager.getAdapter()!!.count else adapter!!.count
            listener!!.onPageSelected(Math.min(position, count - 1))
        }

        override fun onPageScrollStateChanged(state: Int) {
            listener!!.onPageScrollStateChanged(state)
        }

        // A wrapper equals any other wrapper of the same listener, so that
        // removeOnPageChangeListener, which wraps the listener it is given, finds and removes
        // the wrapper that addOnPageChangeListener registered.
        override fun equals(other: Any?): Boolean {
            return other is OnPageChangeListenerWrapper && other.listener == listener
        }

        override fun hashCode(): Int {
            return listener?.hashCode() ?: 0
        }
    }

    /**
     * Adds one extra, empty page after the wrapped adapter's pages; scrolling onto it fades the
     * intro out.
     *
     * The Java original returned `null` as the object for that extra page. PagerAdapter's
     * parameters and return values are `@NonNull`, which Kotlin enforces, so the Kotlin port
     * uses [FADE_PAGE] instead. ViewPager treats page objects as opaque and never compares them
     * to `null`, so this is invisible to it. Where the original passed the page object on to the
     * wrapped adapter (isViewFromObject and getItemPosition), [FADE_PAGE] is passed instead of
     * `null`.
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @VisibleForTesting
    internal class PagerAdapterWrapper(val adapter: PagerAdapter) : PagerAdapter() {

        private val observer = object : DataSetObserver() {
            override fun onChanged() {
                notifyDataSetChanged()
            }

            override fun onInvalidated() {
                notifyDataSetChanged()
            }
        }

        init {
            adapter.registerDataSetObserver(observer)
        }

        /** Stops following the wrapped adapter; call once this wrapper is no longer in use. */
        fun release() {
            adapter.unregisterDataSetObserver(observer)
        }

        override fun getCount(): Int {
            return adapter.count + 1
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return adapter.isViewFromObject(view, `object`)
        }

        override fun startUpdate(container: ViewGroup) {
            adapter.startUpdate(container)
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            if (position < adapter.count)
                return adapter.instantiateItem(container, position)
            return FADE_PAGE
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            if (position < adapter.count)
                adapter.destroyItem(container, position, `object`)
        }

        override fun setPrimaryItem(container: ViewGroup, position: Int, `object`: Any) {
            if (position < adapter.count)
                adapter.setPrimaryItem(container, position, `object`)
        }

        override fun finishUpdate(container: ViewGroup) {
            adapter.finishUpdate(container)
        }

        @Deprecated("Deprecated in Java")
        override fun startUpdate(container: View) {
            adapter.startUpdate(container)
        }

        @Deprecated("Deprecated in Java")
        override fun instantiateItem(container: View, position: Int): Any {
            if (position < adapter.count)
                return adapter.instantiateItem(container, position)
            return FADE_PAGE
        }

        @Deprecated("Deprecated in Java")
        override fun destroyItem(container: View, position: Int, `object`: Any) {
            if (position < adapter.count)
                adapter.destroyItem(container, position, `object`)
        }

        @Deprecated("Deprecated in Java")
        override fun setPrimaryItem(container: View, position: Int, `object`: Any) {
            if (position < adapter.count)
                adapter.setPrimaryItem(container, position, `object`)
        }

        @Deprecated("Deprecated in Java")
        override fun finishUpdate(container: View) {
            adapter.finishUpdate(container)
        }

        override fun saveState(): Parcelable? {
            return adapter.saveState()
        }

        override fun restoreState(state: Parcelable?, loader: ClassLoader?) {
            adapter.restoreState(state, loader)
        }

        override fun getItemPosition(`object`: Any): Int {
            val position = adapter.getItemPosition(`object`)
            if (position < adapter.count) return position
            return POSITION_NONE
        }

        override fun registerDataSetObserver(observer: DataSetObserver) {
            adapter.registerDataSetObserver(observer)
        }

        override fun unregisterDataSetObserver(observer: DataSetObserver) {
            adapter.unregisterDataSetObserver(observer)
        }

        override fun getPageTitle(position: Int): CharSequence? {
            if (position < adapter.count)
                return adapter.getPageTitle(position)
            return null
        }

        override fun getPageWidth(position: Int): Float {
            if (position < adapter.count)
                return adapter.getPageWidth(position)
            return 1f
        }

        companion object {
            /** Stands in for the `null` page object the Java original used for the extra page. */
            private val FADE_PAGE = Any()
        }
    }

    private inner class PageTransformerWrapper(
            private val pageTransformer: ViewPager.PageTransformer
    ) : ViewPager.PageTransformer {

        override fun transformPage(page: View, position: Float) {
            // Read the adapter now rather than when the transformer was set: it may have been
            // set or replaced since.
            pageTransformer.transformPage(page, Math.min(position, (adapter!!.count - 1).toFloat()))
        }
    }


    interface OnOverscrollPageChangeListener : ViewPager.OnPageChangeListener

    open class SimpleOnOverscrollPageChangeListener : OnOverscrollPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        }

        override fun onPageSelected(position: Int) {
        }

        override fun onPageScrollStateChanged(state: Int) {
        }
    }
}
