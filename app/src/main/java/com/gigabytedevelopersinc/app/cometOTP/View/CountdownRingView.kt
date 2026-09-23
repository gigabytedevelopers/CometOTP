@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.res.ResourcesCompat
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import java.util.Locale

/**
 * Circular countdown used in the app bar (global TOTP period) and on every time based card.
 *
 * The ring shows the remaining fraction of the period as an arc and the remaining seconds as a
 * label in the centre. The ring turns to the "expiring" colour during the last seconds so that the
 * user knows the token is about to change.
 */
class CountdownRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val DEFAULT_EXPIRING_THRESHOLD_SECONDS = 8
        private const val ANIMATION_DURATION_MS: Long = 1000
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcBounds = RectF()

    private var ringColor = 0
    private var expiringColor = 0
    private var trackColor = 0
    private var textColor = 0
    private var strokeWidth = 0f
    private var showText = true
    private var highlightExpiring = true
    private var expiringThreshold = DEFAULT_EXPIRING_THRESHOLD_SECONDS

    private var period = 30
    /** Remaining fraction of the period, 1 = full, 0 = expired. */
    // setProgress/getProgress are used by ObjectAnimator
    var progress = 1f
        set(value) {
            field = Math.max(0f, Math.min(1f, value))
            invalidate()
        }
    private var remainingSeconds = period

    private var animator: ObjectAnimator? = null

    init {
        ringColor = Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)
        expiringColor = Tools.getThemeColor(context, R.attr.colorExpiring)
        trackColor = Tools.getThemeColor(context, com.google.android.material.R.attr.colorOutlineVariant)
        textColor = Tools.getThemeColor(context, com.google.android.material.R.attr.colorOnSurface)
        strokeWidth = resources.getDimension(R.dimen.countdown_stroke)
        var textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, resources.displayMetrics)

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.CountdownRingView, defStyleAttr, 0)
            ringColor = a.getColor(R.styleable.CountdownRingView_ringColor, ringColor)
            expiringColor = a.getColor(R.styleable.CountdownRingView_ringExpiringColor, expiringColor)
            trackColor = a.getColor(R.styleable.CountdownRingView_ringTrackColor, trackColor)
            textColor = a.getColor(R.styleable.CountdownRingView_ringTextColor, textColor)
            strokeWidth = a.getDimension(R.styleable.CountdownRingView_ringStrokeWidth, strokeWidth)
            textSize = a.getDimension(R.styleable.CountdownRingView_ringTextSize, textSize)
            showText = a.getBoolean(R.styleable.CountdownRingView_ringShowText, showText)
            highlightExpiring = a.getBoolean(R.styleable.CountdownRingView_ringHighlightExpiring, highlightExpiring)
            expiringThreshold = a.getInt(R.styleable.CountdownRingView_ringExpiringThreshold, expiringThreshold)
            a.recycle()
        }

        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = strokeWidth
        trackPaint.color = trackColor

        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeWidth = strokeWidth
        ringPaint.strokeCap = Paint.Cap.ROUND
        ringPaint.color = ringColor

        textPaint.color = textColor
        textPaint.textSize = textSize
        textPaint.textAlign = Paint.Align.CENTER
        val typeface = if (isInEditMode) Typeface.DEFAULT_BOLD else ResourcesCompat.getFont(context, R.font.inter_bold)
        textPaint.typeface = typeface ?: Typeface.DEFAULT_BOLD
    }

    /** Recomputes the remaining time for the given period and animates towards the next second. */
    fun update(newPeriod: Int) {
        period = if (newPeriod <= 0) 30 else newPeriod

        val periodMillis = period * 1000L
        val elapsedMillis = System.currentTimeMillis() % periodMillis
        val remainingMillis = periodMillis - elapsedMillis

        val from = remainingMillis / periodMillis.toFloat()
        val to = Math.max(0f, (remainingMillis - ANIMATION_DURATION_MS) / periodMillis.toFloat())

        remainingSeconds = Math.ceil(remainingMillis / 1000.0).toInt()

        animator?.cancel()

        progress = from
        val animator = ObjectAnimator.ofFloat(this, "progress", from, to)
        this.animator = animator
        animator.duration = ANIMATION_DURATION_MS
        animator.interpolator = LinearInterpolator()
        animator.start()
    }

    /** Sets the ring without animation, e.g. for a static preview. */
    fun setStatic(newPeriod: Int, remaining: Int) {
        period = newPeriod
        remainingSeconds = remaining
        animator?.cancel()
        progress = remaining / newPeriod.toFloat()
    }

    fun stop() {
        val animator = animator
        if (animator != null) {
            animator.cancel()
            this.animator = null
        }
    }

    fun setShowText(show: Boolean) {
        showText = show
        invalidate()
    }

    fun setHighlightExpiring(highlight: Boolean) {
        highlightExpiring = highlight
        invalidate()
    }

    fun setRingColor(color: Int) {
        ringColor = color
        invalidate()
    }

    val isExpiring: Boolean
        get() = highlightExpiring && remainingSeconds <= expiringThreshold

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val inset = strokeWidth / 2f + 1f
        val size = Math.min(width - paddingLeft - paddingRight,
            height - paddingTop - paddingBottom).toFloat()
        val left = paddingLeft + (width - paddingLeft - paddingRight - size) / 2f
        val top = paddingTop + (height - paddingTop - paddingBottom - size) / 2f
        arcBounds.set(left + inset, top + inset, left + size - inset, top + size - inset)

        canvas.drawOval(arcBounds, trackPaint)

        ringPaint.color = if (isExpiring) expiringColor else ringColor
        canvas.drawArc(arcBounds, -90f, -360f * progress, false, ringPaint)

        if (showText) {
            textPaint.color = if (isExpiring) expiringColor else textColor
            val label = String.format(Locale.getDefault(), "%ds", remainingSeconds)
            val baseline = arcBounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(label, arcBounds.centerX(), baseline, textPaint)
        }
    }
}
