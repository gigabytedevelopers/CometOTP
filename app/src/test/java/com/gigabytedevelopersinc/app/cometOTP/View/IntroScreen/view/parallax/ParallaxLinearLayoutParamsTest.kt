@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The unit-test android.jar has no-op framework constructors, so these only check what
 * ParallaxLinearLayout.LayoutParams itself does with its arguments: the gravity argument must
 * end up in [android.widget.LinearLayout.LayoutParams.gravity], not in the weight.
 */
class ParallaxLinearLayoutParamsTest {

    @Test
    fun gravityConstructorSetsGravity() {
        val params = ParallaxLinearLayout.LayoutParams(10, 20, Gravity.CENTER)

        assertEquals(Gravity.CENTER, params.gravity)
        assertEquals(0f, params.weight, 0f)
        assertEquals(0f, params.parallaxFactor, 0f)
    }

    @Test
    fun gravityAndParallaxFactorConstructorSetsBoth() {
        val params = ParallaxLinearLayout.LayoutParams(10, 20, Gravity.END, 0.5f)

        assertEquals(Gravity.END, params.gravity)
        assertEquals(0f, params.weight, 0f)
        assertEquals(0.5f, params.parallaxFactor, 0f)
    }
}
