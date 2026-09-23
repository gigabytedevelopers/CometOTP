@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax

import android.os.Bundle
import android.view.View
import androidx.annotation.FloatRange
import androidx.fragment.app.Fragment
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax.util.ParallaxUtil

open class ParallaxFragment : Fragment(), Parallaxable {

    private val parallaxableChildren: MutableList<Parallaxable> = ArrayList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        parallaxableChildren.addAll(ParallaxUtil.findParallaxableChildren(view))
    }

    override fun setOffset(@FloatRange(from = -1.0, to = 1.0) offset: Float) {
        ParallaxUtil.setOffsetToParallaxableList(parallaxableChildren, offset)
    }
}
