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
@file:Suppress("PackageName", "DEPRECATION")

package com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextSwitcher
import androidx.activity.OnBackPressedCallback
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import androidx.annotation.InterpolatorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager.widget.ViewPager
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.ButtonCtaSlide
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.Slide
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.SlideAdapter
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.util.AnimUtils
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.util.CheatSheet
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.FadeableViewPager
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.InkPageIndicator
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.view.parallax.Parallaxable

@SuppressLint("Registered")
open class IntroActivity : AppCompatActivity(), IntroNavigation {

    private var activityCreated = false

    private lateinit var miFrame: ConstraintLayout
    private lateinit var miPager: FadeableViewPager
    private lateinit var miPagerIndicator: InkPageIndicator
    private lateinit var miButtonCta: TextSwitcher
    private lateinit var miButtonBack: ImageButton
    private lateinit var miButtonNext: ImageButton

    //Settings constants
    @IntDef(BUTTON_NEXT_FUNCTION_NEXT, BUTTON_NEXT_FUNCTION_NEXT_FINISH)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class ButtonNextFunction

    @IntDef(BUTTON_BACK_FUNCTION_BACK, BUTTON_BACK_FUNCTION_SKIP)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class ButtonBackFunction

    @IntDef(BUTTON_CTA_TINT_MODE_BACKGROUND, BUTTON_CTA_TINT_MODE_TEXT)
    @Retention(AnnotationRetention.SOURCE)
    internal annotation class ButtonCtaTintMode

    private val evaluator = ArgbEvaluator()

    private lateinit var adapter: SlideAdapter

    private val listener = IntroPageChangeListener()

    private var position = 0
    private var positionOffset = 0f

    //Settings
    private var fullscreen = false
    private var buttonCtaVisible = false
    @ButtonNextFunction
    private var buttonNextFunction = BUTTON_NEXT_FUNCTION_NEXT_FINISH
    @ButtonBackFunction
    private var buttonBackFunction = BUTTON_BACK_FUNCTION_SKIP
    @ButtonCtaTintMode
    private var buttonCtaTintMode = BUTTON_CTA_TINT_MODE_BACKGROUND
    private var navigationPolicy: NavigationPolicy? = null
    private val navigationBlockedListeners: MutableList<OnNavigationBlockedListener> = ArrayList()
    private var buttonCtaLabel: CharSequence? = null
    @StringRes
    private var buttonCtaLabelRes = 0
    private var buttonCtaClickListener: View.OnClickListener? = null

    private val autoplayHandler = Handler()
    private var autoplayCallback: Runnable? = null
    private var autoplayCounter = 0
    private var autoplayDelay: Long = 0

    private var pageScrollInterpolator: Interpolator? = null
    private var pageScrollDuration: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pageScrollInterpolator = AnimationUtils.loadInterpolator(this, android.R.interpolator.accelerate_decelerate)
        pageScrollDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_CURRENT_ITEM)) {
                position = savedInstanceState.getInt(KEY_CURRENT_ITEM, position)
            }
            if (savedInstanceState.containsKey(KEY_FULLSCREEN)) {
                fullscreen = savedInstanceState.getBoolean(KEY_FULLSCREEN, fullscreen)
            }
            if (savedInstanceState.containsKey(KEY_BUTTON_CTA_VISIBLE)) {
                buttonCtaVisible = savedInstanceState.getBoolean(KEY_BUTTON_CTA_VISIBLE, buttonCtaVisible)
            }
        }

        if (fullscreen) {
            setSystemUiFlags(View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN, true)
            updateFullscreen()
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        setContentView(R.layout.mi_activity_intro)
        initViews()

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackNavigation()
            }
        })

        // Edge-to-edge (enforced from Android 15 / API 35): keep the pager and the navigation
        // buttons out of the system bars and above the on-screen keyboard. On older platforms
        // the window still fits the system windows itself and these insets are simply zero.
        ViewCompat.setOnApplyWindowInsetsListener(miFrame) { v, insets ->
            val bars: Insets = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, if (fullscreen) 0 else bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        activityCreated = true

        updateTaskDescription()
        updateButtonNextDrawable()
        updateButtonBackDrawable()
        updateScrollPositions()
        miFrame.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                updateScrollPositions()
                v.removeOnLayoutChangeListener(this)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateFullscreen()
    }

    override fun onUserInteraction() {
        if (isAutoplaying())
            cancelAutoplay()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateButtonCta()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_CURRENT_ITEM, miPager.currentItem)
        outState.putBoolean(KEY_FULLSCREEN, fullscreen)
        outState.putBoolean(KEY_BUTTON_CTA_VISIBLE, buttonCtaVisible)
    }

    /**
     * Called for the system back gesture/button. Returns to the previous slide when there is
     * one, otherwise cancels the intro. Subclasses may override to change this behaviour.
     */
    protected open fun onBackNavigation() {
        if (position > 0) {
            previousSlide()
            return
        }
        val returnIntent = onSendActivityResult(RESULT_CANCELED)
        if (returnIntent != null)
            setResult(RESULT_CANCELED, returnIntent)
        else
            setResult(RESULT_CANCELED)
        finish()
    }

    open fun onSendActivityResult(result: Int): Intent? {
        return null
    }

    override fun onDestroy() {
        if (isAutoplaying()) {
            cancelAutoplay()
        }

        activityCreated = false
        super.onDestroy()
    }

    private fun setSystemUiFlags(flags: Int, value: Boolean) {
        var systemUiVisibility = window.decorView.systemUiVisibility
        if (value) {
            systemUiVisibility = systemUiVisibility or flags
        } else {
            systemUiVisibility = systemUiVisibility and flags.inv()
        }
        window.decorView.systemUiVisibility = systemUiVisibility
    }

    private fun setFullscreenFlags(fullscreen: Boolean) {
        var fullscreenFlags = View.SYSTEM_UI_FLAG_FULLSCREEN
        fullscreenFlags = fullscreenFlags or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        setSystemUiFlags(fullscreenFlags, fullscreen)
    }

    private fun initViews() {
        // bind views
        miFrame = findViewById(R.id.mi_frame)
        miPager = findViewById(R.id.mi_pager)
        miPagerIndicator = findViewById(R.id.mi_pager_indicator)
        miButtonCta = findViewById(R.id.mi_button_cta)
        miButtonBack = findViewById(R.id.mi_button_back)
        miButtonNext = findViewById(R.id.mi_button_next)

        miButtonCta.setInAnimation(this, R.anim.mi_fade_in)
        miButtonCta.setOutAnimation(this, R.anim.mi_fade_out)

        val fragmentManager: FragmentManager = supportFragmentManager
        adapter = SlideAdapter(fragmentManager)

        miPager.adapter = adapter
        miPager.addOnPageChangeListener(listener)
        miPager.setCurrentItem(position, false)

        miPagerIndicator.setViewPager(miPager)

        resetButtonNextOnClickListener()
        resetButtonBackOnClickListener()

        CheatSheet.setup(miButtonNext)
        CheatSheet.setup(miButtonBack)
    }

    open fun setButtonNextOnClickListener(onClickListener: View.OnClickListener?) {
        miButtonNext.setOnClickListener(onClickListener)
    }

    open fun setButtonBackOnClickListener(onClickListener: View.OnClickListener?) {
        miButtonBack.setOnClickListener(onClickListener)
    }

    open fun resetButtonNextOnClickListener() {
        miButtonNext.setOnClickListener { nextSlide() }
    }

    open fun resetButtonBackOnClickListener() {
        miButtonBack.setOnClickListener { performButtonBackPress() }
    }

    private fun smoothScrollPagerTo(position: Int) {
        if (miPager.isFakeDragging)
            return

        val animator = ValueAnimator.ofFloat(miPager.currentItem.toFloat(), position.toFloat())
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (miPager.isFakeDragging)
                    miPager.endFakeDrag()
                miPager.currentItem = position
            }

            override fun onAnimationCancel(animation: Animator) {
                if (miPager.isFakeDragging)
                    miPager.endFakeDrag()
            }
        })
        animator.addUpdateListener(object : ValueAnimator.AnimatorUpdateListener {
            override fun onAnimationUpdate(animation: ValueAnimator) {
                val position = animation.animatedValue as Float

                fakeDragToPosition(position)
            }

            private fun fakeDragToPosition(position: Float): Boolean {
                // The following mimics the underlying calculations in ViewPager
                val scrollX = miPager.scrollX.toFloat()
                val pagerWidth = miPager.width
                val currentPosition = miPager.currentItem

                if (position > currentPosition && Math.floor(position.toDouble()) != currentPosition.toDouble() && position % 1 != 0f) {
                    miPager.setCurrentItem(Math.floor(position.toDouble()).toInt(), false)
                } else if (position < currentPosition && Math.ceil(position.toDouble()) != currentPosition.toDouble() && position % 1 != 0f) {
                    miPager.setCurrentItem(Math.ceil(position.toDouble()).toInt(), false)
                }

                if (!miPager.isFakeDragging && !miPager.beginFakeDrag())
                    return false

                miPager.fakeDragBy(scrollX - pagerWidth * position)
                return true
            }
        })

        val distance = Math.abs(position - miPager.currentItem)

        animator.interpolator = pageScrollInterpolator
        animator.setDuration(calculateScrollDuration(distance))
        animator.start()
    }

    private fun calculateScrollDuration(distance: Int): Long {
        return Math.round(pageScrollDuration * (distance + Math.sqrt(distance.toDouble())) / 2)
    }

    override fun goToSlide(position: Int): Boolean {
        @Suppress("NAME_SHADOWING")
        var position = position
        val lastPosition = miPager.currentItem

        if (lastPosition >= adapter.count) {
            finishIfNeeded()
        }

        var newPosition = lastPosition

        position = Math.max(0, Math.min(position, getCount()))

        if (position > lastPosition) {
            // Go forward
            while (newPosition < position && canGoForward(newPosition, true)) {
                newPosition++
            }
        } else if (position < lastPosition) {
            // Go backward
            while (newPosition > position && canGoBackward(newPosition, true)) {
                newPosition--
            }
        } else {
            // Noting to do here
            return true
        }

        var blocked = false
        if (newPosition != position) {
            // Could not go the complete way to the given position.
            blocked = true

            if (position > lastPosition) {
                AnimUtils.applyShakeAnimation(this, miButtonNext)
            } else {
                AnimUtils.applyShakeAnimation(this, miButtonBack)
            }
        }

        // Scroll to new position
        smoothScrollPagerTo(newPosition)

        return !blocked
    }

    override fun nextSlide(): Boolean {
        val currentItem = miPager.currentItem
        return goToSlide(currentItem + 1)
    }

    private fun nextSlideAuto(): Int {
        var lastPosition = miPager.currentItem
        val count = getCount()

        if (count == 1) {
            return 0
        } else if (miPager.currentItem >= count - 1) {
            while (lastPosition >= 0 && canGoBackward(lastPosition, true)) {
                lastPosition--
            }
            if (autoplayCounter > 0)
                autoplayCounter--
        } else if (canGoForward(lastPosition, true)) {
            lastPosition++
        }

        val distance = Math.abs(lastPosition - miPager.currentItem)

        if (lastPosition == miPager.currentItem)
            return 0

        smoothScrollPagerTo(lastPosition)

        if (autoplayCounter == 0)
            return 0
        return distance

    }

    override fun previousSlide(): Boolean {
        val currentItem = miPager.currentItem
        return goToSlide(currentItem - 1)
    }

    override fun goToLastSlide(): Boolean {
        return goToSlide(getCount() - 1)
    }

    override fun goToFirstSlide(): Boolean {
        return goToSlide(0)
    }

    private fun performButtonBackPress() {
        if (buttonBackFunction == BUTTON_BACK_FUNCTION_SKIP) {
            goToSlide(getCount())
        } else if (buttonBackFunction == BUTTON_BACK_FUNCTION_BACK) {
            previousSlide()
        }
    }

    private fun canGoForward(position: Int, notifyListeners: Boolean): Boolean {
        if (position >= getCount()) {
            return false
        }
        if (position < 0) {
            return true
        }

        if (buttonNextFunction == BUTTON_NEXT_FUNCTION_NEXT && position >= getCount() - 1)
            //Block finishing when button "next" function is not "finish".
            return false

        val canGoForward = (navigationPolicy == null || navigationPolicy!!.canGoForward(position)) &&
                getSlide(position).canGoForward()
        if (!canGoForward && notifyListeners) {
            for (listener in navigationBlockedListeners) {
                listener.onNavigationBlocked(position, OnNavigationBlockedListener.DIRECTION_FORWARD)
            }
        }
        return canGoForward
    }

    private fun canGoBackward(position: Int, notifyListeners: Boolean): Boolean {
        if (position <= 0) {
            return false
        }
        if (position >= getCount()) {
            return true
        }

        val canGoBackward = (navigationPolicy == null || navigationPolicy!!.canGoBackward(position)) &&
                getSlide(position).canGoBackward()
        if (!canGoBackward && notifyListeners) {
            for (listener in navigationBlockedListeners) {
                listener.onNavigationBlocked(position, OnNavigationBlockedListener.DIRECTION_BACKWARD)
            }
        }
        return canGoBackward
    }


    private fun finishIfNeeded(): Boolean {
        if (positionOffset == 0f && position == adapter.count) {
            val returnIntent = onSendActivityResult(RESULT_OK)
            if (returnIntent != null)
                setResult(RESULT_OK, returnIntent)
            else
                setResult(RESULT_OK)
            finish()
            overridePendingTransition(0, 0)
            return true
        }
        return false
    }

    private fun getButtonCta(position: Int): Pair<CharSequence?, out View.OnClickListener?>? {
        if (position < getCount() && getSlide(position) is ButtonCtaSlide) {
            val slide = getSlide(position) as ButtonCtaSlide
            if (slide.buttonCtaClickListener != null &&
                    (slide.buttonCtaLabel != null || slide.buttonCtaLabelRes != 0)) {
                if (slide.buttonCtaLabel != null) {
                    return Pair.create(slide.buttonCtaLabel,
                            slide.buttonCtaClickListener)
                } else {
                    return Pair.create(getString(slide.buttonCtaLabelRes) as CharSequence,
                            slide.buttonCtaClickListener)
                }
            }
        }
        if (buttonCtaVisible) {
            if (buttonCtaLabelRes != 0) {
                return Pair.create(getString(buttonCtaLabelRes) as CharSequence,
                        ButtonCtaClickListener())
            }
            if (!TextUtils.isEmpty(buttonCtaLabel)) {
                return Pair.create(buttonCtaLabel, ButtonCtaClickListener())
            } else {
                return Pair.create(getString(R.string.mi_label_button_cta) as CharSequence,
                        ButtonCtaClickListener())
            }
        }
        return null
    }

    private fun updateTaskDescription() {
        val title = title.toString()
        val iconDrawable: Drawable? = applicationInfo.loadIcon(packageManager)
        val icon: Bitmap? = if (iconDrawable is BitmapDrawable) iconDrawable.bitmap else null
        var colorPrimary: Int
        if (position < getCount()) {
            colorPrimary = try {
                ContextCompat.getColor(this@IntroActivity, getBackgroundDark(position))
            } catch (e: Resources.NotFoundException) {
                ContextCompat.getColor(this@IntroActivity, getBackground(position))
            }
        } else {
            val typedValue = TypedValue()
            // colorPrimary is an AppCompat attribute; R classes are non-transitive since AGP 9
            val a = obtainStyledAttributes(typedValue.data, intArrayOf(androidx.appcompat.R.attr.colorPrimary))
            colorPrimary = a.getColor(0, 0)
            a.recycle()
        }
        colorPrimary = ColorUtils.setAlphaComponent(colorPrimary, 0xFF)

        setTaskDescription(ActivityManager.TaskDescription(title, icon, colorPrimary))
    }

    private fun updateBackground() {
        @ColorInt
        var background: Int
        @ColorInt
        var backgroundNext: Int
        @ColorInt
        var backgroundDark: Int
        @ColorInt
        var backgroundDarkNext: Int

        if (position == getCount()) {
            background = Color.TRANSPARENT
            backgroundNext = Color.TRANSPARENT
            backgroundDark = Color.TRANSPARENT
            backgroundDarkNext = Color.TRANSPARENT
        } else {
            background = ContextCompat.getColor(this@IntroActivity,
                    getBackground(position))
            backgroundNext = ContextCompat.getColor(this@IntroActivity,
                    getBackground(Math.min(position + 1, getCount() - 1)))

            background = ColorUtils.setAlphaComponent(background, 0xFF)
            backgroundNext = ColorUtils.setAlphaComponent(backgroundNext, 0xFF)

            backgroundDark = try {
                ContextCompat.getColor(this@IntroActivity,
                        getBackgroundDark(position))
            } catch (e: Resources.NotFoundException) {
                ContextCompat.getColor(this@IntroActivity,
                        R.color.mi_status_bar_background)
            }
            backgroundDarkNext = try {
                ContextCompat.getColor(this@IntroActivity,
                        getBackgroundDark(Math.min(position + 1, getCount() - 1)))
            } catch (e: Resources.NotFoundException) {
                ContextCompat.getColor(this@IntroActivity,
                        R.color.mi_status_bar_background)
            }
        }

        if (position + positionOffset >= adapter.count - 1) {
            backgroundNext = ColorUtils.setAlphaComponent(background, 0x00)
            backgroundDarkNext = ColorUtils.setAlphaComponent(backgroundDark, 0x00)
        }

        background = evaluator.evaluate(positionOffset, background, backgroundNext) as Int
        backgroundDark = evaluator.evaluate(positionOffset, backgroundDark, backgroundDarkNext) as Int

        miFrame.setBackgroundColor(background)

        val backgroundDarkHsv = FloatArray(3)
        Color.colorToHSV(backgroundDark, backgroundDarkHsv)
        //Slightly darken the background color a bit for more contrast
        backgroundDarkHsv[2] = (backgroundDarkHsv[2] * 0.95).toFloat()
        val backgroundDarker = Color.HSVToColor(backgroundDarkHsv)
        miPagerIndicator.pageIndicatorColor = backgroundDarker
        ViewCompat.setBackgroundTintList(miButtonNext, ColorStateList.valueOf(backgroundDarker))
        ViewCompat.setBackgroundTintList(miButtonBack, ColorStateList.valueOf(backgroundDarker))

        @ColorInt
        val backgroundButtonCta = if (buttonCtaTintMode == BUTTON_CTA_TINT_MODE_TEXT)
            ContextCompat.getColor(this, android.R.color.white) else backgroundDarker
        ViewCompat.setBackgroundTintList(miButtonCta.getChildAt(0), ColorStateList.valueOf(backgroundButtonCta))
        ViewCompat.setBackgroundTintList(miButtonCta.getChildAt(1), ColorStateList.valueOf(backgroundButtonCta))

        val iconColor: Int
        if (ColorUtils.calculateLuminance(backgroundDark) > 0.4) {
            //Light background
            iconColor = ContextCompat.getColor(this, R.color.mi_icon_color_light)
        } else {
            //Dark background
            iconColor = ContextCompat.getColor(this, R.color.mi_icon_color_dark)
        }
        miPagerIndicator.currentPageIndicatorColor = iconColor
        DrawableCompat.setTint(miButtonNext.drawable, iconColor)
        DrawableCompat.setTint(miButtonBack.drawable, iconColor)

        @ColorInt
        val textColorButtonCta = if (buttonCtaTintMode == BUTTON_CTA_TINT_MODE_TEXT)
            backgroundDarker else iconColor
        (miButtonCta.getChildAt(0) as Button).setTextColor(textColorButtonCta)
        (miButtonCta.getChildAt(1) as Button).setTextColor(textColorButtonCta)

        // From Android 15 (API 35) the system bars are transparent (edge-to-edge) and these
        // setters are deprecated no-ops; the frame background already shows behind the bars.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = backgroundDark

            if (position == adapter.count) {
                window.navigationBarColor = Color.TRANSPARENT
            } else if (position + positionOffset >= adapter.count - 1) {
                val typedValue = TypedValue()
                val a = obtainStyledAttributes(typedValue.data, intArrayOf(android.R.attr.navigationBarColor))

                val defaultNavigationBarColor = a.getColor(0, Color.BLACK)

                a.recycle()

                val navigationBarColor = evaluator.evaluate(positionOffset, defaultNavigationBarColor, Color.TRANSPARENT) as Int
                window.navigationBarColor = navigationBarColor
            }
        }

        // Light status bar icons on light backgrounds (no-op below Android 6)
        val insetsController: WindowInsetsControllerCompat = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = ColorUtils.calculateLuminance(backgroundDark) > 0.4
    }

    private fun updateButtonCta() {
        val realPosition = position + positionOffset
        val yOffset = resources.getDimensionPixelSize(R.dimen.mi_y_offset).toFloat()

        if (realPosition < adapter.count) {
            //Before fade
            val button = getButtonCta(position)
            val buttonNext = if (positionOffset == 0f) null else getButtonCta(position + 1)

            if (button == null) {
                if (buttonNext == null) {
                    //Hide button
                    miButtonCta.visibility = View.GONE
                } else {
                    miButtonCta.visibility = View.VISIBLE
                    //Fade in
                    if ((miButtonCta.currentView as Button).text != buttonNext.first)
                        miButtonCta.setText(buttonNext.first)
                    miButtonCta.getChildAt(0).setOnClickListener(buttonNext.second)
                    miButtonCta.getChildAt(1).setOnClickListener(buttonNext.second)
                    miButtonCta.alpha = positionOffset
                    miButtonCta.scaleX = positionOffset
                    miButtonCta.scaleY = positionOffset
                    val layoutParams: ViewGroup.LayoutParams = miButtonCta.layoutParams
                    layoutParams.height = Math.round(resources.getDimensionPixelSize(R.dimen.mi_button_cta_height) * ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation(positionOffset))
                    miButtonCta.layoutParams = layoutParams
                }
            } else {
                miButtonCta.visibility = View.VISIBLE
                if (buttonNext == null) {
                    //Fade out
                    if ((miButtonCta.currentView as Button).text != button.first)
                        miButtonCta.setText(button.first)
                    miButtonCta.getChildAt(0).setOnClickListener(button.second)
                    miButtonCta.getChildAt(1).setOnClickListener(button.second)
                    miButtonCta.alpha = 1 - positionOffset
                    miButtonCta.scaleX = 1 - positionOffset
                    miButtonCta.scaleY = 1 - positionOffset
                    val layoutParams: ViewGroup.LayoutParams = miButtonCta.layoutParams
                    layoutParams.height = Math.round(resources.getDimensionPixelSize(R.dimen.mi_button_cta_height) * ACCELERATE_DECELERATE_INTERPOLATOR.getInterpolation(1 - positionOffset))
                    miButtonCta.layoutParams = layoutParams
                } else {
                    val layoutParams: ViewGroup.LayoutParams = miButtonCta.layoutParams
                    layoutParams.height = resources.getDimensionPixelSize(R.dimen.mi_button_cta_height)
                    miButtonCta.layoutParams = layoutParams
                    //Fade text
                    if (positionOffset >= 0.5f) {
                        if ((miButtonCta.currentView as Button).text != buttonNext.first)
                            miButtonCta.setText(buttonNext.first)
                        miButtonCta.getChildAt(0).setOnClickListener(buttonNext.second)
                        miButtonCta.getChildAt(1).setOnClickListener(buttonNext.second)
                    } else {
                        if ((miButtonCta.currentView as Button).text != button.first)
                            miButtonCta.setText(button.first)
                        miButtonCta.getChildAt(0).setOnClickListener(button.second)
                        miButtonCta.getChildAt(1).setOnClickListener(button.second)
                    }
                }
            }
        }

        if (realPosition < adapter.count - 1) {
            //Reset
            miButtonCta.translationY = 0f
        } else {
            //Hide CTA button
            miButtonCta.translationY = positionOffset * yOffset
        }
    }

    private fun updateButtonBackPosition() {
        val realPosition = position + positionOffset
        val yOffset = resources.getDimensionPixelSize(R.dimen.mi_y_offset).toFloat()

        if (realPosition < 1 && buttonBackFunction == BUTTON_BACK_FUNCTION_BACK) {
            //Hide back button
            miButtonBack.translationY = (1 - positionOffset) * yOffset
        } else if (realPosition < adapter.count - 2) {
            //Reset
            miButtonBack.translationY = 0f
            miButtonBack.translationX = 0f
        } else if (realPosition < adapter.count - 1) {
            //Scroll away skip button
            if (buttonBackFunction == BUTTON_BACK_FUNCTION_SKIP) {
                val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                miButtonBack.translationX = positionOffset * (if (rtl) 1 else -1) * miPager.width
            } else {
                miButtonBack.translationX = 0f
            }
        } else {
            //Keep skip button scrolled away, hide next button
            if (buttonBackFunction == BUTTON_BACK_FUNCTION_SKIP) {
                val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                miButtonBack.translationX = ((if (rtl) 1 else -1) * miPager.width).toFloat()
            } else {
                miButtonBack.translationY = positionOffset * yOffset
            }
        }
    }

    private fun updateButtonNextPosition() {
        val realPosition = position + positionOffset
        val yOffset = resources.getDimensionPixelSize(R.dimen.mi_y_offset).toFloat()

        if (realPosition < adapter.count - 2) {
            //Reset
            miButtonNext.translationY = 0f
        } else if (realPosition < adapter.count - 1) {
            //Reset finish button, hide next icon
            if (buttonNextFunction == BUTTON_NEXT_FUNCTION_NEXT_FINISH) {
                miButtonNext.translationY = 0f
            } else {
                miButtonNext.translationY = positionOffset * yOffset
            }
        } else if (realPosition >= adapter.count - 1) {
            //Hide finish icon, keep next icon hidden
            if (buttonNextFunction == BUTTON_NEXT_FUNCTION_NEXT_FINISH) {
                miButtonNext.translationY = positionOffset * yOffset
            } else {
                miButtonNext.translationY = -yOffset
            }
        }
    }

    private fun updatePagerIndicatorPosition() {
        val realPosition = position + positionOffset
        val yOffset = resources.getDimensionPixelSize(R.dimen.mi_y_offset).toFloat()

        if (realPosition < adapter.count - 1) {
            //Reset
            miPagerIndicator.translationY = 0f
        } else {
            //Hide CTA button
            miPagerIndicator.translationY = positionOffset * yOffset
        }
    }

    private fun updateParallax() {
        if (position == getCount())
            return

        val fragment = getSlide(position).fragment
        val fragmentNext = if (position < getCount() - 1)
            getSlide(position + 1).fragment else null
        if (fragment is Parallaxable) {
            fragment.setOffset(positionOffset)
        }
        if (fragmentNext is Parallaxable) {
            fragmentNext.setOffset(-1 + positionOffset)
        }
    }

    private fun updateFullscreen() {
        if (::adapter.isInitialized && position + positionOffset > adapter.count - 1) {
            setFullscreenFlags(false)
        } else {
            setFullscreenFlags(fullscreen)
        }
    }

    private fun updateBackgroundFade() {
        val realPosition = position + positionOffset

        if (realPosition < adapter.count - 1) {
            //Reset
            miFrame.alpha = 1f
        } else {
            //Fade background
            miFrame.alpha = 1 - (positionOffset * 0.5f)
        }
    }

    private fun updateScrollPositions() {
        updateBackground()
        updateButtonCta()
        updateButtonBackPosition()
        updateButtonNextPosition()
        updatePagerIndicatorPosition()
        updateParallax()
        updateFullscreen()
        updateBackgroundFade()
    }

    private fun updateButtonNextDrawable() {
        val realPosition = position + positionOffset
        var offset = 0f

        if (buttonNextFunction == BUTTON_NEXT_FUNCTION_NEXT_FINISH) {
            if (realPosition >= adapter.count - 1) {
                offset = 1f
            } else if (realPosition >= adapter.count - 2) {
                offset = positionOffset
            }
        }

        if (offset <= 0) {
            miButtonNext.setImageResource(R.drawable.mi_ic_next)
            miButtonNext.drawable.alpha = 0xFF
        } else {
            miButtonNext.setImageResource(R.drawable.mi_ic_next_finish)
            if (miButtonNext.drawable != null && miButtonNext.drawable is LayerDrawable) {
                val drawable = miButtonNext.drawable as LayerDrawable
                drawable.getDrawable(0).alpha = (0xFF * (1 - offset)).toInt()
                drawable.getDrawable(1).alpha = (0xFF * offset).toInt()
            } else {
                miButtonNext.setImageResource(if (offset > 0) R.drawable.mi_ic_finish else R.drawable.mi_ic_next)
            }
        }
    }

    private fun updateButtonBackDrawable() {
        if (buttonBackFunction == BUTTON_BACK_FUNCTION_SKIP) {
            miButtonBack.setImageResource(R.drawable.mi_ic_skip)
        } else {
            miButtonBack.setImageResource(R.drawable.mi_ic_previous)
        }
    }

    open fun autoplay(@IntRange(from = 1) delay: Long, @IntRange(from = -1) repeatCount: Int) {
        autoplayCounter = repeatCount
        autoplayDelay = delay
        autoplayCallback = Runnable {
            if (autoplayCounter == 0) {
                cancelAutoplay()
                return@Runnable
            }
            val distance = nextSlideAuto()
            if (distance != 0)
                autoplayCallback?.let { autoplayHandler.postDelayed(it, autoplayDelay + calculateScrollDuration(distance)) }
        }
        autoplayHandler.postDelayed(autoplayCallback!!, autoplayDelay)
    }

    open fun autoplay(@IntRange(from = 1) delay: Long) {
        autoplay(delay, DEFAULT_AUTOPLAY_REPEAT_COUNT)
    }

    open fun autoplay(@IntRange(from = -1) repeatCount: Int) {
        autoplay(DEFAULT_AUTOPLAY_DELAY.toLong(), repeatCount)
    }

    open fun autoplay() {
        autoplay(DEFAULT_AUTOPLAY_DELAY.toLong(), DEFAULT_AUTOPLAY_REPEAT_COUNT)
    }

    open fun cancelAutoplay() {
        autoplayCallback?.let { autoplayHandler.removeCallbacks(it) }
        autoplayCallback = null
        autoplayCounter = 0
        autoplayDelay = 0
    }

    open fun isAutoplaying(): Boolean {
        return autoplayCallback != null
    }

    open fun getPageScrollDuration(): Long {
        return pageScrollDuration
    }

    open fun setPageScrollDuration(@IntRange(from = 1) pageScrollDuration: Long) {
        this.pageScrollDuration = pageScrollDuration
    }

    open fun getPageScrollInterpolator(): Interpolator? {
        return pageScrollInterpolator
    }

    open fun setPageScrollInterpolator(pageScrollInterpolator: Interpolator?) {
        this.pageScrollInterpolator = pageScrollInterpolator
    }

    open fun setPageScrollInterpolator(@InterpolatorRes interpolatorRes: Int) {
        this.pageScrollInterpolator = AnimationUtils.loadInterpolator(this, interpolatorRes)
    }

    open fun isFullscreen(): Boolean {
        return fullscreen
    }

    open fun setFullscreen(fullscreen: Boolean) {
        this.fullscreen = fullscreen
    }

    open fun isButtonCtaVisible(): Boolean {
        return buttonCtaVisible
    }

    open fun setButtonCtaVisible(buttonCtaVisible: Boolean) {
        this.buttonCtaVisible = buttonCtaVisible
        updateButtonCta()
    }

    @ButtonCtaTintMode
    open fun getButtonCtaTintMode(): Int {
        return buttonCtaTintMode
    }

    open fun setButtonCtaTintMode(@ButtonCtaTintMode buttonCtaTintMode: Int) {
        this.buttonCtaTintMode = buttonCtaTintMode
    }

    @ButtonBackFunction
    open fun getButtonBackFunction(): Int {
        return buttonBackFunction
    }

    open fun setButtonBackFunction(@ButtonBackFunction buttonBackFunction: Int) {
        this.buttonBackFunction = buttonBackFunction
        when (buttonBackFunction) {
            BUTTON_BACK_FUNCTION_BACK -> CheatSheet.setup(miButtonBack, R.string.mi_content_description_back)
            BUTTON_BACK_FUNCTION_SKIP -> CheatSheet.setup(miButtonBack, R.string.mi_content_description_skip)
        }
        updateButtonBackDrawable()
        updateButtonBackPosition()
    }

    @Deprecated("Use getButtonBackFunction() instead.")
    open fun isSkipEnabled(): Boolean {
        return buttonBackFunction == BUTTON_BACK_FUNCTION_SKIP
    }

    @Deprecated("Use setButtonBackFunction(int) instead.")
    open fun setSkipEnabled(skipEnabled: Boolean) {
        setButtonBackFunction(if (skipEnabled) BUTTON_BACK_FUNCTION_SKIP else BUTTON_BACK_FUNCTION_BACK)
    }

    @ButtonNextFunction
    open fun getButtonNextFunction(): Int {
        return buttonNextFunction
    }

    open fun setButtonNextFunction(@ButtonNextFunction buttonNextFunction: Int) {
        this.buttonNextFunction = buttonNextFunction
        when (buttonNextFunction) {
            BUTTON_NEXT_FUNCTION_NEXT_FINISH -> CheatSheet.setup(miButtonNext, R.string.mi_content_description_next_finish)
            BUTTON_NEXT_FUNCTION_NEXT -> CheatSheet.setup(miButtonNext, R.string.mi_content_description_next)
        }
        updateButtonNextDrawable()
        updateButtonNextPosition()
    }

    open fun getContentView(): View? {
        return findViewById(android.R.id.content)
    }

    @Deprecated("Use getButtonNextFunction() instead.")
    open fun isFinishEnabled(): Boolean {
        return buttonNextFunction == BUTTON_NEXT_FUNCTION_NEXT_FINISH
    }

    @Deprecated("Use setButtonNextFunction(int) instead.")
    open fun setFinishEnabled(finishEnabled: Boolean) {
        setButtonNextFunction(if (finishEnabled) BUTTON_NEXT_FUNCTION_NEXT_FINISH else BUTTON_NEXT_FUNCTION_NEXT)
    }

    open fun isButtonBackVisible(): Boolean {
        return miButtonBack.visibility == View.VISIBLE
    }

    open fun setButtonBackVisible(visible: Boolean) {
        miButtonBack.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    open fun isButtonNextVisible(): Boolean {
        return miButtonNext.visibility == View.VISIBLE
    }

    open fun setButtonNextVisible(visible: Boolean) {
        miButtonNext.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    open fun isPagerIndicatorVisible(): Boolean {
        return miPagerIndicator.visibility == View.VISIBLE
    }

    open fun setPagerIndicatorVisible(visible: Boolean) {
        miPagerIndicator.visibility = if (visible) View.VISIBLE else View.GONE
    }

    @Deprecated("Use addOnPageChangeListener(ViewPager.OnPageChangeListener) instead.")
    open fun setOnPageChangeListener(listener: ViewPager.OnPageChangeListener?) {
        miPager.setOnPageChangeListener(listener)
        miPager.addOnPageChangeListener(this.listener)
    }

    open fun addOnPageChangeListener(listener: ViewPager.OnPageChangeListener) {
        miPager.addOnPageChangeListener(listener)
    }

    open fun removeOnPageChangeListener(listener: ViewPager.OnPageChangeListener) {
        if (listener !== this.listener)
            miPager.removeOnPageChangeListener(listener)
    }

    open fun getButtonCtaClickListener(): View.OnClickListener? {
        return buttonCtaClickListener
    }

    open fun setButtonCtaClickListener(buttonCtaClickListener: View.OnClickListener?) {
        this.buttonCtaClickListener = buttonCtaClickListener
        updateButtonCta()
    }

    open fun getButtonCtaLabel(): CharSequence? {
        if (buttonCtaLabel != null)
            return buttonCtaLabel
        return getString(buttonCtaLabelRes)
    }

    open fun setButtonCtaLabel(@StringRes buttonCtaLabelRes: Int) {
        this.buttonCtaLabelRes = buttonCtaLabelRes
        this.buttonCtaLabel = null
        updateButtonCta()
    }

    open fun setButtonCtaLabel(buttonCtaLabel: CharSequence?) {
        this.buttonCtaLabel = buttonCtaLabel
        this.buttonCtaLabelRes = 0
        updateButtonCta()
    }

    open fun setNavigationPolicy(navigationPolicy: NavigationPolicy?) {
        this.navigationPolicy = navigationPolicy
    }

    open fun addOnNavigationBlockedListener(listener: OnNavigationBlockedListener) {
        navigationBlockedListeners.add(listener)
    }

    open fun removeOnNavigationBlockedListener(listener: OnNavigationBlockedListener) {
        navigationBlockedListeners.remove(listener)
    }

    open fun clearOnNavigationBlockedListeners() {
        navigationBlockedListeners.clear()
    }

    open fun lockSwipeIfNeeded() {
        if (position < getCount()) {
            miPager.setSwipeLeftEnabled(canGoForward(position, false))
            miPager.setSwipeRightEnabled(canGoBackward(position, false))
        }
    }

    open fun addSlide(location: Int, `object`: Slide) {
        adapter.addSlide(location, `object`)
        notifyDataSetChanged()
    }

    open fun addSlide(`object`: Slide): Boolean {
        val modified = adapter.addSlide(`object`)
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    open fun addSlides(location: Int, collection: Collection<Slide>): Boolean {
        val modified = adapter.addSlides(location, collection)
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    open fun addSlides(collection: Collection<Slide>): Boolean {
        val modified = adapter.addSlides(collection)
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    open fun clearSlides(): Boolean {
        if (adapter.clearSlides()) {
            notifyDataSetChanged()
            return true
        }
        return false
    }

    open fun containsSlide(`object`: Any?): Boolean {
        return adapter.containsSlide(`object`)
    }

    open fun containsSlides(collection: Collection<*>): Boolean {
        return adapter.containsSlides(collection)
    }

    open fun getSlide(location: Int): Slide {
        return adapter.getSlide(location)
    }

    open fun getSlidePosition(slide: Slide): Int {
        return adapter.getItemPosition(slide)
    }

    open fun getCurrentSlidePosition(): Int {
        return miPager.currentItem
    }

    open fun getItem(position: Int): Fragment {
        return adapter.getItem(position)
    }

    @ColorRes
    open fun getBackground(position: Int): Int {
        return adapter.getBackground(position)
    }

    @ColorRes
    open fun getBackgroundDark(position: Int): Int {
        return adapter.getBackgroundDark(position)
    }

    open fun getSlides(): MutableList<Slide> {
        return adapter.slides
    }

    open fun indexOfSlide(`object`: Any?): Int {
        return adapter.indexOfSlide(`object`)
    }

    open fun isEmpty(): Boolean {
        return adapter.isEmpty()
    }

    open fun getCount(): Int {
        return if (!::adapter.isInitialized) 0 else adapter.count
    }

    open fun lastIndexOfSlide(`object`: Any?): Int {
        return adapter.lastIndexOfSlide(`object`)
    }

    open fun removeSlide(location: Int): Slide {
        val `object` = adapter.removeSlide(location)
        notifyDataSetChanged()
        return `object`
    }

    open fun removeSlide(`object`: Any?): Boolean {
        val modified = adapter.removeSlide(`object`)
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    open fun removeSlides(collection: Collection<*>): Boolean {
        val modified = adapter.removeSlides(collection)
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    open fun retainSlides(collection: Collection<*>): Boolean {
        val modified = adapter.retainSlides(collection)
        if (modified) {
            notifyDataSetChanged()
        }
        return modified
    }

    open fun setSlide(location: Int, `object`: Slide): Slide {
        val oldObject = adapter.setSlide(location, `object`)
        notifyDataSetChanged()
        return oldObject
    }

    open fun setSlides(list: List<Slide>): MutableList<Slide> {
        val oldList = adapter.setSlides(list)
        notifyDataSetChanged()
        return oldList
    }

    open fun setPageTransformer(reverseDrawingOrder: Boolean, transformer: ViewPager.PageTransformer?) {
        miPager.setPageTransformer(reverseDrawingOrder, transformer)
    }

    open fun notifyDataSetChanged() {
        if (!activityCreated) {
            // Don't notify any listener until the activity is created
            return
        }

        val position = this.position
        miPager.adapter = adapter
        miPager.currentItem = position

        if (finishIfNeeded()) {
            return
        }

        updateTaskDescription()
        updateButtonBackDrawable()
        updateButtonNextDrawable()
        updateScrollPositions()
        lockSwipeIfNeeded()
    }

    private inner class IntroPageChangeListener : FadeableViewPager.SimpleOnOverscrollPageChangeListener() {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            this@IntroActivity.position = Math.floor((position + positionOffset).toDouble()).toInt()
            this@IntroActivity.positionOffset = (((position + positionOffset) % 1) + 1) % 1

            if (finishIfNeeded()) {
                return
            }

            //Lock while scrolling a slide near its edges to lock (uncommon) multiple page swipes
            if (Math.abs(positionOffset) < 0.1f) {
                lockSwipeIfNeeded()
            }

            updateButtonNextDrawable()
            updateScrollPositions()
        }

        override fun onPageSelected(position: Int) {
            this@IntroActivity.position = position
            updateTaskDescription()
            lockSwipeIfNeeded()
        }
    }

    private inner class ButtonCtaClickListener : View.OnClickListener {
        override fun onClick(v: View) {
            goToSlide(getCount())
        }
    }

    companion object {
        private const val KEY_CURRENT_ITEM =
                "com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.IntroActivity.KEY_CURRENT_ITEM"
        private const val KEY_FULLSCREEN =
                "com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.IntroActivity.KEY_FULLSCREEN"
        private const val KEY_BUTTON_CTA_VISIBLE =
                "com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.IntroActivity.KEY_BUTTON_CTA_VISIBLE"

        const val BUTTON_NEXT_FUNCTION_NEXT = 1
        const val BUTTON_NEXT_FUNCTION_NEXT_FINISH = 2

        const val BUTTON_BACK_FUNCTION_BACK = 1
        const val BUTTON_BACK_FUNCTION_SKIP = 2

        const val BUTTON_CTA_TINT_MODE_BACKGROUND = 1
        const val BUTTON_CTA_TINT_MODE_TEXT = 2

        const val DEFAULT_AUTOPLAY_DELAY = 1500
        const val INFINITE = -1
        const val DEFAULT_AUTOPLAY_REPEAT_COUNT = INFINITE

        @JvmField
        val ACCELERATE_DECELERATE_INTERPOLATOR: Interpolator = AccelerateDecelerateInterpolator()
    }
}
