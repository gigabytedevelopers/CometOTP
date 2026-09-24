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
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.viewpager.widget.ViewPager

open class SwipeBlockableViewPager @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    private var activePointerId = INVALID_POINTER_ID
    private var lastTouchX = 0f

    private var swipeRightEnabled = true
    private var swipeLeftEnabled = true

    private var lockedLeft = false
    private var lockedRight = false

    fun setSwipeRightEnabled(swipeRightEnabled: Boolean) {
        this.swipeRightEnabled = swipeRightEnabled
    }

    fun setSwipeLeftEnabled(swipeLeftEnabled: Boolean) {
        this.swipeLeftEnabled = swipeLeftEnabled
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event) && super.onTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event) && super.onInterceptTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        var allowTouch = false
        val action = event.action
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x

                // Save the ID of this pointer
                activePointerId = event.getPointerId(0)
            }

            MotionEvent.ACTION_MOVE -> {
                // Find the index of the active pointer and fetch its position
                var pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) {
                    // No DOWN was seen for this gesture (or its pointer is gone), so there is no
                    // pointer to follow and getX(-1) would throw. Follow the first pointer from
                    // here on; this move itself counts as no movement.
                    pointerIndex = 0
                    activePointerId = event.getPointerId(0)
                    lastTouchX = event.getX(0)
                }
                val x = event.getX(pointerIndex)

                val dx = x - lastTouchX

                if (dx > 0) {
                    // Swiped right
                    if (!swipeRightEnabled && Math.abs(dx) > SWIPE_LOCK_THRESHOLD) {
                        lockedRight = true
                    }
                    if (!lockedRight) {
                        allowTouch = true
                        if (Math.abs(dx) > SWIPE_UNLOCK_THRESHOLD) {
                            lockedLeft = false
                        }
                    }
                } else if (dx < 0) {
                    // Swiped left
                    if (!swipeLeftEnabled && Math.abs(dx) > SWIPE_LOCK_THRESHOLD) {
                        lockedLeft = true
                    }
                    if (!lockedLeft) {
                        allowTouch = true
                        if (Math.abs(dx) > SWIPE_UNLOCK_THRESHOLD) {
                            lockedRight = false
                        }
                    }
                }

                lastTouchX = x

                invalidate()
            }

            MotionEvent.ACTION_UP,

            MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER_ID
                lockedLeft = false
                lockedRight = false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // Extract the index of the pointer that left the touch sensor
                val pointerIndex = (action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr
                        MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    // This was our active pointer going up. Choose a new
                    // active pointer and adjust accordingly.
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    lastTouchX = event.getX(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
            }
        }

        return (!lockedLeft && !lockedRight) || allowTouch
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        var state = state
        if (state is Bundle) {
            val bundle = state
            swipeRightEnabled = bundle.getBoolean(STATE_SWIPE_RIGHT_ENABLED, true)
            swipeLeftEnabled = bundle.getBoolean(STATE_SWIPE_LEFT_ENABLED, true)
            @Suppress("DEPRECATION")
            state = bundle.getParcelable(STATE_SUPER)
        }
        super.onRestoreInstanceState(state)
    }

    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle(4)
        bundle.putParcelable(STATE_SUPER, super.onSaveInstanceState())
        bundle.putBoolean(STATE_SWIPE_RIGHT_ENABLED, swipeRightEnabled)
        bundle.putBoolean(STATE_SWIPE_LEFT_ENABLED, swipeLeftEnabled)
        return bundle
    }

    companion object {
        private const val SWIPE_LOCK_THRESHOLD = 0
        private const val SWIPE_UNLOCK_THRESHOLD = 0

        private const val STATE_SUPER = "SUPER"
        private const val STATE_SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"
        private const val STATE_SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"

        private const val INVALID_POINTER_ID = -1
    }
}
