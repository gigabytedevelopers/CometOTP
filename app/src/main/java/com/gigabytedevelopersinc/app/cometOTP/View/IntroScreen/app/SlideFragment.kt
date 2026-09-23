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

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app

import android.view.View
import androidx.fragment.app.Fragment


open class SlideFragment : Fragment(), IntroNavigation {

    open fun canGoForward(): Boolean {
        return true
    }

    open fun canGoBackward(): Boolean {
        return true
    }

    open val introActivity: IntroActivity
        get() {
            val activity = activity
            if (activity is IntroActivity) {
                return activity
            } else {
                throw IllegalStateException("SlideFragment's must be attached to an IntroActivity.")
            }
        }

    open fun updateNavigation() {
        introActivity.lockSwipeIfNeeded()
    }

    open fun addOnNavigationBlockedListener(listener: OnNavigationBlockedListener) {
        introActivity.addOnNavigationBlockedListener(listener)
    }

    open fun removeOnNavigationBlockedListener(listener: OnNavigationBlockedListener) {
        introActivity.removeOnNavigationBlockedListener(listener)
    }

    override fun goToSlide(position: Int): Boolean {
        return introActivity.goToSlide(position)
    }

    override fun nextSlide(): Boolean {
        return introActivity.nextSlide()
    }

    override fun previousSlide(): Boolean {
        return introActivity.previousSlide()
    }

    override fun goToLastSlide(): Boolean {
        return introActivity.goToLastSlide()
    }

    override fun goToFirstSlide(): Boolean {
        return introActivity.goToFirstSlide()
    }

    /**
     * @deprecated
     */
    open val contentView: View?
        get() = requireActivity().findViewById(android.R.id.content)
}
