@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View.ExpandableLayout

import android.animation.TimeInterpolator
import androidx.annotation.IntDef

interface ExpandableLayout {

    companion object {
        /**
         * Duration of expand animation
         */
        const val DEFAULT_DURATION = 300
        /**
         * Visibility of the layout when the layout attaches
         */
        const val DEFAULT_EXPANDED = false
        /**
         * Orientation of child views
         */
        const val HORIZONTAL = 0
        /**
         * Orientation of child views
         */
        const val VERTICAL = 1
    }

    /**
     * Orientation of layout
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(HORIZONTAL, VERTICAL)
    annotation class Orientation

    /**
     * Starts animation the state of the view to the inverse of its current state.
     */
    fun toggle()

    /**
     * Starts animation the state of the view to the inverse of its current state.
     *
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    fun toggle(duration: Long, interpolator: TimeInterpolator?)

    /**
     * Starts expand animation.
     */
    fun expand()

    /**
     * Starts expand animation.
     *
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    fun expand(duration: Long, interpolator: TimeInterpolator?)

    /**
     * Starts collapse animation.
     */
    fun collapse()

    /**
     * Starts collapse animation.
     *
     * @param duration
     * @param interpolator use the default interpolator if the argument is null.
     */
    fun collapse(duration: Long, interpolator: TimeInterpolator?)

    /**
     * Sets the expandable layout listener.
     *
     * @param listener ExpandableLayoutListener
     */
    fun setListener(listener: ExpandableLayoutListener)

    /**
     * Sets the length of the animation.
     * The default duration is 300 milliseconds.
     *
     * @param duration
     */
    fun setDuration(duration: Int)

    /**
     * Sets state of expanse.
     *
     * @param expanded The layout is visible if expanded is true
     */
    fun setExpanded(expanded: Boolean)

    /**
     * Gets state of expanse.
     *
     * @return true if the layout is visible
     */
    fun isExpanded(): Boolean

    /**
     * The time interpolator used in calculating the elapsed fraction of this animation. The
     * interpolator determines whether the animation runs with linear or non-linear motion,
     * such as acceleration and deceleration.
     * The default value is  [android.view.animation.AccelerateDecelerateInterpolator]
     *
     * @param interpolator
     */
    fun setInterpolator(interpolator: TimeInterpolator)
}
