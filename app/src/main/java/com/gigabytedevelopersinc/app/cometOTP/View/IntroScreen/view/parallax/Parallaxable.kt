@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax

import androidx.annotation.FloatRange

interface Parallaxable {
    fun setOffset(@FloatRange(from = -1.0, to = 1.0) offset: Float)
}
