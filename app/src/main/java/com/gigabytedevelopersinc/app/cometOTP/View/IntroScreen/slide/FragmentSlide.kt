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

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.ButtonCtaFragment
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.SlideFragment
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax.ParallaxFragment

open class FragmentSlide protected constructor(builder: Builder) : Slide, RestorableSlide, ButtonCtaSlide {

    override var fragment: Fragment? = builder.fragment
    @ColorRes
    override val background: Int = builder.background
    @ColorRes
    override val backgroundDark: Int = builder.backgroundDark
    private val canGoForward: Boolean = builder.canGoForward
    private val canGoBackward: Boolean = builder.canGoBackward
    private var _buttonCtaLabel: CharSequence? = builder.buttonCtaLabel
    @StringRes
    private var _buttonCtaLabelRes: Int = builder.buttonCtaLabelRes
    private var _buttonCtaClickListener: View.OnClickListener? = builder.buttonCtaClickListener

    override fun canGoForward(): Boolean {
        val fragment = fragment
        if (fragment is SlideFragment) {
            return fragment.canGoForward()
        }
        return canGoForward
    }

    override fun canGoBackward(): Boolean {
        val fragment = fragment
        if (fragment is SlideFragment) {
            return fragment.canGoBackward()
        }
        return canGoBackward
    }

    override val buttonCtaClickListener: View.OnClickListener?
        get() {
            val fragment = fragment
            if (fragment is ButtonCtaFragment) {
                return fragment.buttonCtaClickListener
            }
            return _buttonCtaClickListener
        }

    override val buttonCtaLabel: CharSequence?
        get() {
            val fragment = fragment
            if (fragment is ButtonCtaFragment) {
                return fragment.buttonCtaLabel
            }
            return _buttonCtaLabel
        }

    override val buttonCtaLabelRes: Int
        get() {
            val fragment = fragment
            if (fragment is ButtonCtaFragment) {
                return fragment.buttonCtaLabelRes
            }
            return _buttonCtaLabelRes
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as FragmentSlide

        if (background != that.background) return false
        if (backgroundDark != that.backgroundDark) return false
        if (canGoForward != that.canGoForward) return false
        if (canGoBackward != that.canGoBackward) return false
        if (_buttonCtaLabelRes != that._buttonCtaLabelRes) return false
        if (fragment != that.fragment)
            return false
        if (_buttonCtaLabel != that._buttonCtaLabel)
            return false
        return _buttonCtaClickListener == that._buttonCtaClickListener

    }

    override fun hashCode(): Int {
        var result = fragment?.hashCode() ?: 0
        result = 31 * result + background
        result = 31 * result + backgroundDark
        result = 31 * result + if (canGoForward) 1 else 0
        result = 31 * result + if (canGoBackward) 1 else 0
        result = 31 * result + (_buttonCtaLabel?.hashCode() ?: 0)
        result = 31 * result + _buttonCtaLabelRes
        result = 31 * result + (_buttonCtaClickListener?.hashCode() ?: 0)
        return result
    }

    open class Builder {
        internal var fragment: Fragment? = null
        @ColorRes
        internal var background: Int = 0
        @ColorRes
        internal var backgroundDark: Int = 0
        internal var canGoForward: Boolean = true
        internal var canGoBackward: Boolean = true
        internal var buttonCtaLabel: CharSequence? = null
        @StringRes
        internal var buttonCtaLabelRes: Int = 0
        internal var buttonCtaClickListener: View.OnClickListener? = null

        fun fragment(fragment: Fragment?): Builder {
            this.fragment = fragment
            return this
        }

        fun fragment(@LayoutRes layoutRes: Int, @StyleRes themeRes: Int): Builder {
            this.fragment = FragmentSlideFragment.newInstance(layoutRes, themeRes)
            return this
        }

        fun fragment(@LayoutRes layoutRes: Int): Builder {
            this.fragment = FragmentSlideFragment.newInstance(layoutRes)
            return this
        }

        fun background(@ColorRes background: Int): Builder {
            this.background = background
            return this
        }

        fun backgroundDark(@ColorRes backgroundDark: Int): Builder {
            this.backgroundDark = backgroundDark
            return this
        }

        fun canGoForward(canGoForward: Boolean): Builder {
            this.canGoForward = canGoForward
            return this
        }

        fun canGoBackward(canGoBackward: Boolean): Builder {
            this.canGoBackward = canGoBackward
            return this
        }

        fun buttonCtaLabel(buttonCtaLabel: CharSequence?): Builder {
            this.buttonCtaLabel = buttonCtaLabel
            this.buttonCtaLabelRes = 0
            return this
        }

        @Suppress("DEPRECATION")
        fun buttonCtaLabelHtml(buttonCtaLabelHtml: String?): Builder {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                this.buttonCtaLabel = Html.fromHtml(buttonCtaLabelHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                this.buttonCtaLabel = Html.fromHtml(buttonCtaLabelHtml)
            }
            this.buttonCtaLabelRes = 0
            return this
        }

        fun buttonCtaLabel(@StringRes buttonCtaLabelRes: Int): Builder {
            this.buttonCtaLabelRes = buttonCtaLabelRes
            this.buttonCtaLabel = null
            return this
        }

        fun buttonCtaClickListener(buttonCtaClickListener: View.OnClickListener?): Builder {
            this.buttonCtaClickListener = buttonCtaClickListener
            return this
        }

        fun build(): FragmentSlide {
            if (background == 0 || fragment == null)
                throw IllegalArgumentException("You must set at least a fragment and background.")
            return FragmentSlide(this)
        }
    }

    open class FragmentSlideFragment : ParallaxFragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val arguments = arguments!!
            val themeRes = arguments.getInt(ARGUMENT_THEME_RES)
            val contextThemeWrapper: Context? = if (themeRes != 0) {
                ContextThemeWrapper(activity, themeRes)
            } else {
                activity
            }
            val localInflater = inflater.cloneInContext(contextThemeWrapper)

            return localInflater.inflate(arguments.getInt(ARGUMENT_LAYOUT_RES), container, false)
        }

        companion object {
            private const val ARGUMENT_LAYOUT_RES =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_LAYOUT_RES"
            private const val ARGUMENT_THEME_RES =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_THEME_RES"

            fun newInstance(@LayoutRes layoutRes: Int, @StyleRes themeRes: Int): FragmentSlideFragment {
                val arguments = Bundle()
                arguments.putInt(ARGUMENT_LAYOUT_RES, layoutRes)
                arguments.putInt(ARGUMENT_THEME_RES, themeRes)

                val fragment = FragmentSlideFragment()
                fragment.arguments = arguments
                return fragment
            }

            fun newInstance(@LayoutRes layoutRes: Int): FragmentSlideFragment {
                return newInstance(layoutRes, 0)
            }
        }
    }
}
