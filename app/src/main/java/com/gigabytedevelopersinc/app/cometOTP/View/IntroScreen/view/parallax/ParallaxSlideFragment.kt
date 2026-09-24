@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax

import android.os.Bundle
import android.view.View
import androidx.annotation.FloatRange
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.SlideFragment
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax.util.ParallaxUtil

open class ParallaxSlideFragment : SlideFragment(), Parallaxable {

    private val parallaxableChildren: MutableList<Parallaxable> = ArrayList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Only the current view's children: a fragment's view is recreated every time the pager
        // detaches and reattaches it.
        parallaxableChildren.clear()
        parallaxableChildren.addAll(ParallaxUtil.findParallaxableChildren(view))
    }

    override fun onDestroyView() {
        // Let go of the old view's children along with the view.
        parallaxableChildren.clear()
        super.onDestroyView()
    }

    override fun setOffset(@FloatRange(from = -1.0, to = 1.0) offset: Float) {
        ParallaxUtil.setOffsetToParallaxableList(parallaxableChildren, offset)
    }
}
