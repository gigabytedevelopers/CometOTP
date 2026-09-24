@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.FloatRange
import com.gigabytedevelopersinc.app.cometOTP.R

open class ParallaxLinearLayout @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), Parallaxable {

    @FloatRange(from = -1.0, to = 1.0)
    private var offset = 0f

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is ParallaxLinearLayout.LayoutParams
    }

    override fun generateDefaultLayoutParams(): ParallaxLinearLayout.LayoutParams {
        return ParallaxLinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): ParallaxLinearLayout.LayoutParams {
        return ParallaxLinearLayout.LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): ParallaxLinearLayout.LayoutParams {
        return ParallaxLinearLayout.LayoutParams(p)
    }

    override fun setOffset(@FloatRange(from = -1.0, to = 1.0) offset: Float) {
        this.offset = offset
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            val p = child.layoutParams as ParallaxLinearLayout.LayoutParams
            if (p.parallaxFactor == 0f) continue
            child.translationX = width * -offset * p.parallaxFactor
        }
    }

    open class LayoutParams : LinearLayout.LayoutParams {
        internal var parallaxFactor = 0f

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
            val a = c.obtainStyledAttributes(attrs, R.styleable.ParallaxLayout_Layout)
            parallaxFactor = a.getFloat(R.styleable.ParallaxLayout_Layout_layout_parallaxFactor, parallaxFactor)
            a.recycle()
        }

        constructor(width: Int, height: Int) : super(width, height)

        constructor(width: Int, height: Int, parallaxFactor: Float) : super(width, height) {
            this.parallaxFactor = parallaxFactor
        }

        constructor(width: Int, height: Int, gravity: Int) : super(width, height) {
            this.gravity = gravity
        }

        constructor(width: Int, height: Int, gravity: Int, parallaxFactor: Float) : super(width, height) {
            this.gravity = gravity
            this.parallaxFactor = parallaxFactor
        }

        constructor(source: ViewGroup.LayoutParams?) : super(source)

        constructor(source: ViewGroup.LayoutParams?, parallaxFactor: Float) : super(source) {
            this.parallaxFactor = parallaxFactor
        }

        constructor(source: ViewGroup.MarginLayoutParams?) : super(source)

        constructor(source: ViewGroup.MarginLayoutParams?, parallaxFactor: Float) : super(source) {
            this.parallaxFactor = parallaxFactor
        }
    }
}
