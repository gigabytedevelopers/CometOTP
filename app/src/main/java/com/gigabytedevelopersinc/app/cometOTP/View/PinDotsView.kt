@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools

/**
 * One circle per PIN digit: outlined while empty, filled once the digit is typed. It never shows
 * the digits themselves, to the screen or to accessibility services, only how many were typed.
 *
 * With a fixed length (see [setLength]) there is one circle per digit. Without one the row grows
 * as digits are typed and starts out with as many empty circles as the shortest PIN has digits.
 */
class PinDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val filledColor = Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)
    private val emptyColor = Tools.getThemeColor(context, com.google.android.material.R.attr.colorOutline)
    private val errorColor = Tools.getThemeColor(context, androidx.appcompat.R.attr.colorError)

    private val dotSize = resources.getDimension(R.dimen.pin_dot_size)
    private val dotGap = resources.getDimension(R.dimen.pin_dot_gap)

    private var length: Int? = null
    private var minSlots = 0

    /** How many digits have been typed. */
    var filled = 0
        set(value) {
            val grew = value > field
            field = value.coerceAtLeast(0)
            if (grew)
                popLastDot()
            else
                popScale = 1f
            updateDescription()
            requestLayout()
            invalidate()
        }

    /** Scale of the dot filled last, animated from small to full size. */
    private var popScale = 1f
    private var popAnimator: ValueAnimator? = null

    private var error = false
    private var shakeAnimator: Animator? = null

    private var busy = false
    private var busyAnimator: Animator? = null

    init {
        emptyPaint.style = Paint.Style.STROKE
        emptyPaint.strokeWidth = resources.getDimension(R.dimen.pin_dot_stroke)
        emptyPaint.color = emptyColor
        filledPaint.style = Paint.Style.FILL
        filledPaint.color = filledColor

        // Announce the count as it changes, without stealing focus from the keypad.
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        if (Build.VERSION.SDK_INT >= 34)
            setAccessibilityDataSensitive(ACCESSIBILITY_DATA_SENSITIVE_YES)
        updateDescription()
    }

    /**
     * One circle for each of [length] digits, or a row that grows from [minSlots] circles when the
     * length is not known (null). Clears the digits typed so far.
     */
    fun setLength(length: Int?, minSlots: Int) {
        this.length = length
        this.minSlots = minSlots
        filled = 0
    }

    private val slots: Int
        get() = length ?: maxOf(filled, minSlots)

    /** Pulses all circles, filled, while the PIN is being checked. */
    fun setBusy(busy: Boolean) {
        if (this.busy == busy)
            return
        this.busy = busy
        busyAnimator?.cancel()
        busyAnimator = null
        alpha = 1f
        if (busy) {
            val animator = ObjectAnimator.ofFloat(this, ALPHA, 1f, 0.35f)
            animator.duration = BUSY_PULSE_MS
            animator.repeatMode = ValueAnimator.REVERSE
            animator.repeatCount = ValueAnimator.INFINITE
            animator.start()
            busyAnimator = animator
        }
        invalidate()
    }

    /** Turns the circles to the error colour and shakes them, then clears them and runs [onEnd]. */
    fun showError(onEnd: () -> Unit) {
        setBusy(false)
        shakeAnimator?.cancel()
        error = true
        invalidate()

        val distance = resources.getDimension(R.dimen.pin_dot_shake)
        val animator = ObjectAnimator.ofFloat(this, TRANSLATION_X,
            0f, distance, -distance, distance * 0.66f, -distance * 0.66f, distance * 0.33f, -distance * 0.33f, 0f)
        animator.duration = SHAKE_MS
        animator.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                translationX = 0f
                error = false
                shakeAnimator = null
                if (cancelled)
                    return
                filled = 0
                onEnd()
            }
        })
        shakeAnimator = animator
        animator.start()
    }

    private fun popLastDot() {
        popAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(0.4f, 1f)
        animator.duration = POP_MS
        animator.interpolator = OvershootInterpolator(2.5f)
        animator.addUpdateListener {
            popScale = it.animatedValue as Float
            invalidate()
        }
        popAnimator = animator
        animator.start()
    }

    private fun updateDescription() {
        val length = length
        contentDescription = if (length != null)
            resources.getQuantityString(R.plurals.auth_pin_dots_progress, length, filled, length)
        else
            resources.getQuantityString(R.plurals.auth_pin_dots_count, filled, filled)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Room for the largest row the dots can grow to, so that the layout does not jump.
        val widest = maxOf(slots, minSlots)
        val wantedWidth = (widest * dotSize + (widest - 1).coerceAtLeast(0) * dotGap + paddingLeft + paddingRight).toInt()
        val wantedHeight = (dotSize * POP_OVERSHOOT + paddingTop + paddingBottom).toInt()
        setMeasuredDimension(
            resolveSize(wantedWidth, widthMeasureSpec),
            resolveSize(wantedHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val slots = slots
        if (slots == 0)
            return

        // Shrink the dots (and the gaps between them) when a long PIN would not fit.
        val available = (width - paddingLeft - paddingRight).toFloat()
        val natural = slots * dotSize + (slots - 1) * dotGap
        val scale = if (natural > available && natural > 0f) available / natural else 1f
        val size = dotSize * scale
        val gap = dotGap * scale
        val radius = size / 2f

        val rowWidth = slots * size + (slots - 1) * gap
        var cx = paddingLeft + (available - rowWidth) / 2f + radius
        val cy = paddingTop + (height - paddingTop - paddingBottom) / 2f

        val stroke = emptyPaint.strokeWidth
        val shownFilled = if (busy) slots else filled.coerceAtMost(slots)
        filledPaint.color = if (error) errorColor else filledColor
        emptyPaint.color = if (error) errorColor else emptyColor

        for (i in 0 until slots) {
            if (i < shownFilled) {
                val r = if (i == shownFilled - 1 && !busy) radius * popScale else radius
                canvas.drawCircle(cx, cy, r, filledPaint)
            } else {
                canvas.drawCircle(cx, cy, radius - stroke / 2f, emptyPaint)
            }
            cx += size + gap
        }
    }

    override fun onDetachedFromWindow() {
        popAnimator?.cancel()
        shakeAnimator?.cancel()
        busyAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val POP_MS = 160L
        const val SHAKE_MS = 420L
        const val BUSY_PULSE_MS = 600L
        // The overshoot of the pop animation draws the newest dot a little larger than the rest.
        const val POP_OVERSHOOT = 1.3f
    }
}
