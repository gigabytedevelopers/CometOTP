@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax.util

import android.view.View
import android.view.ViewGroup
import androidx.annotation.FloatRange
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax.Parallaxable
import java.util.LinkedList
import java.util.Queue

object ParallaxUtil {

    fun findParallaxableChildren(root: View): MutableList<Parallaxable> {
        val parallaxableChildrenFound: MutableList<Parallaxable> = LinkedList()
        val queue: Queue<View> = LinkedList()
        queue.add(root)
        while (!queue.isEmpty()) {
            val child = queue.remove()
            if (child is Parallaxable) {
                parallaxableChildrenFound.add(child)
            } else if (child is ViewGroup) {
                for (i in child.childCount - 1 downTo 0) {
                    queue.add(child.getChildAt(i))
                }
            }
        }
        return parallaxableChildrenFound
    }

    /**
     * Set the provided offset to a list of parallaxable items.
     *
     * @param parallaxableChildren The list of parallaxable items to set the offset to.
     * @param offset The offset to assign.
     */
    fun setOffsetToParallaxableList(
            parallaxableChildren: List<Parallaxable>,
            @FloatRange(from = -1.0, to = 1.0) offset: Float
    ) {
        if (!parallaxableChildren.isEmpty()) {
            for (parallaxable in parallaxableChildren) {
                parallaxable.setOffset(offset)
            }
        }
    }
}
