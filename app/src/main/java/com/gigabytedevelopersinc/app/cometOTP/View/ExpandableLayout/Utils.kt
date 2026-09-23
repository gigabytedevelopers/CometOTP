@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View.ExpandableLayout

import android.animation.TimeInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.AnticipateOvershootInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.annotation.IntRange
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator

object Utils {

    const val ACCELERATE_DECELERATE_INTERPOLATOR = 0
    const val ACCELERATE_INTERPOLATOR = 1
    const val ANTICIPATE_INTERPOLATOR = 2
    const val ANTICIPATE_OVERSHOOT_INTERPOLATOR = 3
    const val BOUNCE_INTERPOLATOR = 4
    const val DECELERATE_INTERPOLATOR = 5
    const val FAST_OUT_LINEAR_IN_INTERPOLATOR = 6
    const val FAST_OUT_SLOW_IN_INTERPOLATOR = 7
    const val LINEAR_INTERPOLATOR = 8
    const val LINEAR_OUT_SLOW_IN_INTERPOLATOR = 9
    const val OVERSHOOT_INTERPOLATOR = 10

    /**
     * Creates interpolator.
     *
     * @param interpolatorType
     * @return
     */
    @JvmStatic
    fun createInterpolator(@IntRange(from = 0, to = 10) interpolatorType: Int): TimeInterpolator {
        return when (interpolatorType) {
            ACCELERATE_DECELERATE_INTERPOLATOR -> AccelerateDecelerateInterpolator()
            ACCELERATE_INTERPOLATOR -> AccelerateInterpolator()
            ANTICIPATE_INTERPOLATOR -> AnticipateInterpolator()
            ANTICIPATE_OVERSHOOT_INTERPOLATOR -> AnticipateOvershootInterpolator()
            BOUNCE_INTERPOLATOR -> BounceInterpolator()
            DECELERATE_INTERPOLATOR -> DecelerateInterpolator()
            FAST_OUT_LINEAR_IN_INTERPOLATOR -> FastOutLinearInInterpolator()
            FAST_OUT_SLOW_IN_INTERPOLATOR -> FastOutSlowInInterpolator()
            LINEAR_INTERPOLATOR -> LinearInterpolator()
            LINEAR_OUT_SLOW_IN_INTERPOLATOR -> LinearOutSlowInInterpolator()
            OVERSHOOT_INTERPOLATOR -> OvershootInterpolator()
            else -> LinearInterpolator()
        }
    }
}
