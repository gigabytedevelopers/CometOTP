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

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax.ParallaxSlideFragment
import java.util.Arrays

open class SimpleSlide protected constructor(builder: Builder) : Slide, RestorableSlide, ButtonCtaSlide {

    private var _fragment: SimpleSlideFragment = SimpleSlideFragment.newInstance(builder.id, builder.title, builder.titleRes,
            builder.description, builder.descriptionRes, builder.imageRes,
            builder.backgroundRes, builder.layoutRes, builder.permissionsRequestCode)

    private val id: Long = builder.id
    private val title: CharSequence? = builder.title
    @StringRes
    private val titleRes: Int = builder.titleRes
    private val description: CharSequence? = builder.description
    @StringRes
    private val descriptionRes: Int = builder.descriptionRes
    @DrawableRes
    private val imageRes: Int = builder.imageRes
    @LayoutRes
    private val layoutRes: Int = builder.layoutRes


    @ColorRes
    private val backgroundRes: Int = builder.backgroundRes
    @ColorRes
    private val backgroundDarkRes: Int = builder.backgroundDarkRes
    private val canGoForward: Boolean = builder.canGoForward
    private val canGoBackward: Boolean = builder.canGoBackward
    /** Every permission the slide asks for; [permissions] is the part of it not granted yet. */
    private val requestedPermissions: Array<String>? = builder.permissions
    private var permissions: Array<String>? = builder.permissions
    private var permissionsRequestCode: Int = builder.permissionsRequestCode
    private var _buttonCtaLabel: CharSequence? = builder.buttonCtaLabel
    @StringRes
    private var _buttonCtaLabelRes: Int = builder.buttonCtaLabelRes
    private var _buttonCtaClickListener: View.OnClickListener? = builder.buttonCtaClickListener

    init {
        updatePermissions()
    }

    override var fragment: Fragment?
        get() = _fragment
        set(fragment) {
            if (fragment is SimpleSlideFragment)
                this._fragment = fragment
        }

    override val background: Int
        get() = backgroundRes

    override val backgroundDark: Int
        get() = backgroundDarkRes

    override fun canGoForward(): Boolean {
        updatePermissions()
        return canGoForward && permissions == null

    }

    override fun canGoBackward(): Boolean {
        return canGoBackward
    }

    override val buttonCtaClickListener: View.OnClickListener?
        get() {
            updatePermissions()
            if (permissions == null) {
                return _buttonCtaClickListener
            }
            return View.OnClickListener {
                val activity = _fragment.activity
                if (activity != null)
                    ActivityCompat.requestPermissions(activity, permissions!!,
                            permissionsRequestCode)
            }
        }

    override val buttonCtaLabel: CharSequence?
        get() {
            updatePermissions()
            val permissions = permissions ?: return _buttonCtaLabel
            val context = _fragment.context
            if (context != null)
                return context.resources.getQuantityText(
                        R.plurals.mi_label_grant_permission, permissions.size)
            return null
        }

    override val buttonCtaLabelRes: Int
        get() {
            updatePermissions()
            if (permissions == null) {
                return _buttonCtaLabelRes
            }
            return 0
        }

    @Synchronized
    private fun updatePermissions() {
        // Checked against the full request every time, not only what was missing last time, so a
        // permission revoked while the intro is open (see SimpleSlideFragment.onResume) is asked
        // for again.
        val permissions = requestedPermissions
        if (permissions != null) {
            val permissionsNotGranted: MutableList<String> = ArrayList()
            for (permission in permissions) {
                val context = _fragment.context
                if (context == null ||
                        ContextCompat.checkSelfPermission(context, permission) !=
                        PackageManager.PERMISSION_GRANTED) {
                    permissionsNotGranted.add(permission)
                }
            }

            if (permissionsNotGranted.size > 0) {
                this.permissions = permissionsNotGranted.toTypedArray()
            } else {
                this.permissions = null
            }
        } else {
            this.permissions = null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as SimpleSlide

        if (id != that.id) return false
        if (titleRes != that.titleRes) return false
        if (descriptionRes != that.descriptionRes) return false
        if (imageRes != that.imageRes) return false
        if (layoutRes != that.layoutRes) return false
        if (backgroundRes != that.backgroundRes) return false
        if (backgroundDarkRes != that.backgroundDarkRes) return false
        if (canGoForward != that.canGoForward) return false
        if (canGoBackward != that.canGoBackward) return false
        if (permissionsRequestCode != that.permissionsRequestCode) return false
        if (_buttonCtaLabelRes != that._buttonCtaLabelRes) return false
        if (_fragment != that._fragment)
            return false
        if (title != that.title) return false
        if (description != that.description)
            return false
        if (!Arrays.equals(permissions, that.permissions)) return false
        if (_buttonCtaLabel != that._buttonCtaLabel)
            return false
        return _buttonCtaClickListener == that._buttonCtaClickListener

    }

    override fun hashCode(): Int {
        var result = _fragment.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + titleRes
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + descriptionRes
        result = 31 * result + imageRes
        result = 31 * result + layoutRes
        result = 31 * result + backgroundRes
        result = 31 * result + backgroundDarkRes
        result = 31 * result + if (canGoForward) 1 else 0
        result = 31 * result + if (canGoBackward) 1 else 0
        result = 31 * result + Arrays.hashCode(permissions)
        result = 31 * result + permissionsRequestCode
        result = 31 * result + (_buttonCtaLabel?.hashCode() ?: 0)
        result = 31 * result + _buttonCtaLabelRes
        result = 31 * result + (_buttonCtaClickListener?.hashCode() ?: 0)
        return result
    }

    open class Builder {
        @ColorRes
        internal var backgroundRes: Int = 0
        internal var id: Long = 0
        @ColorRes
        internal var backgroundDarkRes: Int = 0
        internal var title: CharSequence? = null
        @StringRes
        internal var titleRes: Int = 0
        internal var description: CharSequence? = null
        @StringRes
        internal var descriptionRes: Int = 0
        @DrawableRes
        internal var imageRes: Int = 0
        @LayoutRes
        internal var layoutRes: Int = R.layout.mi_fragment_simple_slide
        internal var canGoForward: Boolean = true
        internal var canGoBackward: Boolean = true
        internal var permissions: Array<String>? = null
        internal var buttonCtaLabel: CharSequence? = null
        @StringRes
        internal var buttonCtaLabelRes: Int = 0
        internal var buttonCtaClickListener: View.OnClickListener? = null

        internal var permissionsRequestCode: Int = DEFAULT_PERMISSIONS_REQUEST_CODE

        fun background(@ColorRes backgroundRes: Int): Builder {
            this.backgroundRes = backgroundRes
            return this
        }

        fun backgroundDark(@ColorRes backgroundDarkRes: Int): Builder {
            this.backgroundDarkRes = backgroundDarkRes
            return this
        }

        fun title(title: CharSequence?): Builder {
            this.title = title
            this.titleRes = 0
            return this
        }

        @Suppress("DEPRECATION")
        fun titleHtml(titleHtml: String?): Builder {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                this.title = Html.fromHtml(titleHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                this.title = Html.fromHtml(titleHtml)
            }
            this.titleRes = 0
            return this
        }

        fun id(id: Long): Builder {
            this.id = id
            return this
        }

        fun title(@StringRes titleRes: Int): Builder {
            this.titleRes = titleRes
            this.title = null
            return this
        }

        fun description(description: CharSequence?): Builder {
            this.description = description
            this.descriptionRes = 0
            return this
        }

        @Suppress("DEPRECATION")
        fun descriptionHtml(descriptionHtml: String?): Builder {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                this.description = Html.fromHtml(descriptionHtml, Html.FROM_HTML_MODE_LEGACY)
            } else {
                this.description = Html.fromHtml(descriptionHtml)
            }
            this.descriptionRes = 0
            return this
        }

        fun description(@StringRes descriptionRes: Int): Builder {
            this.descriptionRes = descriptionRes
            this.description = null
            return this
        }

        fun image(@DrawableRes imageRes: Int): Builder {
            this.imageRes = imageRes
            return this
        }

        fun layout(@LayoutRes layoutRes: Int): Builder {
            this.layoutRes = layoutRes
            return this
        }

        fun scrollable(scrollable: Boolean): Builder {
            this.layoutRes = if (scrollable) R.layout.mi_fragment_simple_slide_scrollable else
                R.layout.mi_fragment_simple_slide
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

        fun permissions(permissions: Array<String>?): Builder {
            this.permissions = permissions
            return this
        }

        fun permission(permission: String): Builder {
            this.permissions = arrayOf(permission)
            return this
        }

        fun permissionsRequestCode(permissionsRequestCode: Int): Builder {
            this.permissionsRequestCode = permissionsRequestCode
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

        fun build(): SimpleSlide {
            if (backgroundRes == 0)
                throw IllegalArgumentException("You must set a background.")
            return SimpleSlide(this)
        }
    }

    open class SimpleSlideFragment : ParallaxSlideFragment() {

        var titleView: TextView? = null
            private set
        var descriptionView: TextView? = null
            private set
        var imageView: ImageView? = null
            private set

        @Suppress("DEPRECATION")
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            retainInstance = true
            updateNavigation()
        }

        override fun onResume() {
            super.onResume()
            //Lock scroll for the case that users revoke accepted permission settings while in the intro
            updateNavigation()
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View? {
            val arguments = arguments!!

            val fragment = inflater.inflate(arguments.getInt(ARGUMENT_LAYOUT_RES,
                    R.layout.mi_fragment_simple_slide), container, false)

            titleView = fragment.findViewById(R.id.mi_title)
            descriptionView = fragment.findViewById(R.id.mi_description)
            imageView = fragment.findViewById(R.id.mi_image)

            val id = arguments.getLong(ARGUMENT_ID)
            val title = arguments.getCharSequence(ARGUMENT_TITLE)
            val titleRes = arguments.getInt(ARGUMENT_TITLE_RES)
            val description = arguments.getCharSequence(ARGUMENT_DESCRIPTION)
            val descriptionRes = arguments.getInt(ARGUMENT_DESCRIPTION_RES)
            val imageRes = arguments.getInt(ARGUMENT_IMAGE_RES)
            val backgroundRes = arguments.getInt(ARGUMENT_BACKGROUND_RES)

            //Title
            titleView?.let { titleView ->
                if (title != null) {
                    titleView.text = title
                    titleView.visibility = View.VISIBLE
                } else if (titleRes != 0) {
                    titleView.setText(titleRes)
                    titleView.visibility = View.VISIBLE
                } else {
                    titleView.visibility = View.GONE
                }
            }

            //Description
            descriptionView?.let { descriptionView ->
                if (description != null) {
                    descriptionView.text = description
                    descriptionView.visibility = View.VISIBLE
                } else if (descriptionRes != 0) {
                    descriptionView.setText(descriptionRes)
                    descriptionView.visibility = View.VISIBLE
                } else {
                    descriptionView.visibility = View.GONE
                }
            }

            //Image
            imageView?.let { imageView ->
                if (imageRes != 0) {
                    imageView.setImageResource(imageRes)
                    imageView.visibility = View.VISIBLE
                } else {
                    imageView.visibility = View.GONE
                }
            }

            @ColorInt
            val textColorPrimary: Int
            @ColorInt
            val textColorSecondary: Int

            if (backgroundRes != 0 &&
                    ColorUtils.calculateLuminance(ContextCompat.getColor(requireContext(), backgroundRes)) < 0.6) {
                //Use light text color
                textColorPrimary = ContextCompat.getColor(requireContext(), R.color.mi_text_color_primary_dark)
                textColorSecondary = ContextCompat.getColor(requireContext(), R.color.mi_text_color_secondary_dark)
            } else {
                //Use dark text color
                textColorPrimary = ContextCompat.getColor(requireContext(), R.color.mi_text_color_primary_light)
                textColorSecondary = ContextCompat.getColor(requireContext(), R.color.mi_text_color_secondary_light)
            }

            titleView?.setTextColor(textColorPrimary)
            descriptionView?.setTextColor(textColorSecondary)

            val activity = activity
            if (activity is SimpleSlideActivity) {
                activity.onSlideViewCreated(this, fragment, id)
            }

            return fragment
        }

        override fun onDestroyView() {
            val activity = activity
            if (activity is SimpleSlideActivity) {
                val id = arguments!!.getLong(ARGUMENT_ID)
                activity.onSlideDestroyView(this, view, id)
            }
            titleView = null
            descriptionView = null
            imageView = null
            super.onDestroyView()
        }

        val slideId: Long
            get() = arguments!!.getLong(ARGUMENT_ID)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                                grantResults: IntArray) {
            val arguments = arguments
            val permissionsRequestCode = if (arguments == null) DEFAULT_PERMISSIONS_REQUEST_CODE else
                arguments.getInt(ARGUMENT_PERMISSIONS_REQUEST_CODE,
                        DEFAULT_PERMISSIONS_REQUEST_CODE)
            if (requestCode == permissionsRequestCode) {
                updateNavigation()
            }
        }

        companion object {
            private const val ARGUMENT_ID =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_ID"
            private const val ARGUMENT_TITLE =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_TITLE"
            private const val ARGUMENT_TITLE_RES =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_TITLE_RES"
            private const val ARGUMENT_DESCRIPTION =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_DESCRIPTION"
            private const val ARGUMENT_DESCRIPTION_RES =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_DESCRIPTION_RES"
            private const val ARGUMENT_IMAGE_RES =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_IMAGE_RES"
            private const val ARGUMENT_BACKGROUND_RES =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_BACKGROUND_RES"
            private const val ARGUMENT_LAYOUT_RES =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_LAYOUT_RES"

            private const val ARGUMENT_PERMISSIONS_REQUEST_CODE =
                    "com.gigabytedevelopersinc.app.cometOTP.SimpleFragment.ARGUMENT_PERMISSIONS_REQUEST_CODE"

            @JvmStatic
            fun newInstance(id: Long, title: CharSequence?, @StringRes titleRes: Int,
                            description: CharSequence?, @StringRes descriptionRes: Int,
                            @DrawableRes imageRes: Int, @ColorRes backgroundRes: Int,
                            @LayoutRes layout: Int, permissionsRequestCode: Int): SimpleSlideFragment {
                val arguments = Bundle()
                arguments.putLong(ARGUMENT_ID, id)
                arguments.putCharSequence(ARGUMENT_TITLE, title)
                arguments.putInt(ARGUMENT_TITLE_RES, titleRes)
                arguments.putCharSequence(ARGUMENT_DESCRIPTION, description)
                arguments.putInt(ARGUMENT_DESCRIPTION_RES, descriptionRes)
                arguments.putInt(ARGUMENT_IMAGE_RES, imageRes)
                arguments.putInt(ARGUMENT_BACKGROUND_RES, backgroundRes)
                arguments.putInt(ARGUMENT_LAYOUT_RES, layout)
                arguments.putInt(ARGUMENT_PERMISSIONS_REQUEST_CODE, permissionsRequestCode)

                val fragment = SimpleSlideFragment()
                fragment.arguments = arguments

                return fragment
            }
        }
    }

    companion object {
        private const val DEFAULT_PERMISSIONS_REQUEST_CODE = 34 //Random number
    }
}
