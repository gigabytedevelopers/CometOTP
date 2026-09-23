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

interface IntroNavigation {
    /**
     * Tries to go to the given position and will stop when `canGoForward()` or
     * `canGoBackward()` returns `false`.
     *
     * @param position The position the pager should go to.
     * @return `true` if the pager was able to go the complete way to the given position,
     * `false` otherwise.
     */
    fun goToSlide(position: Int): Boolean

    /**
     * Tries to go to the next slide if `canGoForward()` returns `true`.
     *
     * @return `true` if the pager was able to go to the next slide, `false` otherwise.
     */
    fun nextSlide(): Boolean


    /**
     * Tries to go to the previous slide if `canGoForward()` returns `true`.
     *
     * @return `true` if the pager was able to go to the previous slide, `false`
     * otherwise.
     */
    fun previousSlide(): Boolean

    /**
     * Tries to go to the last slide and will stop when `canGoForward()` returns
     * `false`.
     *
     * @return `true` if the pager was able to go the complete way to the last slide,
     * `false` otherwise.
     */
    fun goToLastSlide(): Boolean

    /**
     * Tries to go to the first slide and will stop when `canGoBackward()` returns
     * `false`.
     *
     * @return `true` if the pager was able to go the complete way to the first slide,
     * `false` otherwise.
     */
    fun goToFirstSlide(): Boolean
}
