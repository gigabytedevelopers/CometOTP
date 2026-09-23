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
@file:Suppress("PackageName", "DEPRECATION")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide

import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.SlideFragment

@Suppress("UNCHECKED_CAST")
open class SlideAdapter : FragmentPagerAdapter {
    private var data: MutableList<Slide>
    private val fragmentManager: FragmentManager

    constructor(fragmentManager: FragmentManager) : super(fragmentManager) {
        this.fragmentManager = fragmentManager
        data = ArrayList()
    }

    constructor(fragmentManager: FragmentManager, collection: Collection<Slide>) : super(fragmentManager) {
        this.fragmentManager = fragmentManager
        data = ArrayList(collection)
    }

    fun addSlide(location: Int, `object`: Slide) {
        if (!data.contains(`object`)) {
            data.add(location, `object`)
        }
    }

    fun addSlide(`object`: Slide): Boolean {
        if (data.contains(`object`)) {
            return false
        }
        val modified = data.add(`object`)
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    fun addSlides(location: Int, collection: Collection<Slide>): Boolean {
        var modified = false
        var i = 0
        for (slide in collection) {
            if (!data.contains(slide)) {
                data.add(location + i, slide)
                i++
                modified = true
            }
        }
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    fun addSlides(collection: Collection<Slide>): Boolean {
        var modified = false
        for (slide in collection) {
            if (!data.contains(slide)) {
                data.add(slide)
                modified = true
            }
        }
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    fun clearSlides(): Boolean {
        if (!data.isEmpty()) {
            data.clear()
            return true
        }
        return false
    }

    fun containsSlide(`object`: Any?): Boolean {
        return `object` is Slide && data.contains(`object`)
    }

    fun containsSlides(collection: Collection<*>): Boolean {
        return (data as MutableList<Any?>).containsAll(collection)
    }

    fun getSlide(location: Int): Slide {
        return data[location]
    }

    override fun getItem(position: Int): Fragment {
        return data[position].fragment!!
    }

    override fun getItemPosition(`object`: Any): Int {
        if (`object` is Fragment) {
            fragmentManager.beginTransaction()
                    .detach(`object`)
                    .attach(`object`)
                    .commit()
        }
        return super.getItemPosition(`object`)
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = getItem(position)
        if (fragment.isAdded) {
            return fragment
        }

        val instantiatedFragment = super.instantiateItem(container, position) as Fragment
        val slide = data[position]
        if (slide is RestorableSlide) {
            //Load old fragment from fragment manager
            slide.fragment = instantiatedFragment
            data[position] = slide
            if (instantiatedFragment is SlideFragment && instantiatedFragment.isAdded) {
                instantiatedFragment.updateNavigation()
            }
        }
        return instantiatedFragment
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        @Suppress("UNUSED_VARIABLE")
        val fragment = `object` as Fragment
        super.destroyItem(container, position, `object`)
    }

    @ColorRes
    fun getBackground(position: Int): Int {
        return data[position].background
    }

    @ColorRes
    fun getBackgroundDark(position: Int): Int {
        return data[position].backgroundDark
    }

    val slides: MutableList<Slide>
        get() = data

    fun indexOfSlide(`object`: Any?): Int {
        return (data as MutableList<Any?>).indexOf(`object`)
    }

    fun isEmpty(): Boolean {
        return data.isEmpty()
    }

    override fun getCount(): Int {
        return data.size
    }

    fun lastIndexOfSlide(`object`: Any?): Int {
        return (data as MutableList<Any?>).lastIndexOf(`object`)
    }

    fun removeSlide(location: Int): Slide {
        return data.removeAt(location)
    }

    fun removeSlide(`object`: Any?): Boolean {
        val locationToRemove = (data as MutableList<Any?>).indexOf(`object`)
        if (locationToRemove >= 0) {
            data.removeAt(locationToRemove)
            return true
        }
        return false
    }

    fun removeSlides(collection: Collection<*>): Boolean {
        var modified = false
        for (`object` in collection) {
            val locationToRemove = (data as MutableList<Any?>).indexOf(`object`)
            if (locationToRemove >= 0) {
                data.removeAt(locationToRemove)
                modified = true
            }
        }
        return modified
    }

    fun retainSlides(collection: Collection<*>): Boolean {
        var modified = false
        var i = data.size - 1
        while (i >= 0) {
            if (!(collection as Collection<Any?>).contains(data[i])) {
                data.removeAt(i)
                modified = true
                i--
            }
            i--
        }
        return modified
    }

    fun setSlide(location: Int, `object`: Slide): Slide {
        return data.set(location, `object`)
    }

    fun setSlides(list: List<Slide>): MutableList<Slide> {
        val oldList: MutableList<Slide> = ArrayList(data)
        data = ArrayList(list)
        return oldList
    }

    override fun notifyDataSetChanged() {
        super.notifyDataSetChanged()
    }
}
