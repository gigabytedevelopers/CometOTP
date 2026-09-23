@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.google.android.material.button.MaterialButton

/**
 * The onboarding coach marks: a dimmed overlay that spotlights one control at a time and explains
 * it with a tooltip. Added on top of the activity's content view and removed when the user
 * finishes the tour.
 */
class CoachMarkOverlay(context: Context) : FrameLayout(context) {

    /** One step of the tour: the control to spotlight and the copy that explains it. */
    class Step(
        internal val target: View,
        internal val title: String,
        internal val body: String
    )

    companion object {
        private const val SCRIM_COLOR = 0xB3000000.toInt()
        private const val HOLE_PADDING_DP = 8f

        /** Starts the tour inside `root`; the overlay removes itself when it finishes. */
        @JvmStatic
        fun show(root: ViewGroup, steps: List<Step>, onFinished: Runnable?): CoachMarkOverlay {
            val overlay = CoachMarkOverlay(root.context)
            overlay.steps.addAll(steps)
            overlay.onFinished = onFinished
            overlay.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            root.addView(overlay)
            overlay.buildDots()
            overlay.bindStep()
            return overlay
        }
    }

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hole = RectF()
    private val location = IntArray(2)

    private val steps: MutableList<Step> = ArrayList()
    private var current = 0

    private lateinit var tooltip: View
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var dots: LinearLayout
    private lateinit var next: MaterialButton
    private lateinit var caretTop: ImageView
    private lateinit var caretBottom: ImageView

    private var onFinished: Runnable? = null

    init {
        init()
    }

    private fun init() {
        setWillNotDraw(false)
        // The hole is punched out of the scrim, which needs a software layer
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        isFocusable = true

        scrimPaint.color = SCRIM_COLOR
        holePaint.color = Color.TRANSPARENT
        holePaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

        LayoutInflater.from(context).inflate(R.layout.view_coach_mark, this, true)
        tooltip = findViewById(R.id.coach_container)
        title = findViewById(R.id.coach_title)
        body = findViewById(R.id.coach_body)
        dots = findViewById(R.id.coach_dots)
        next = findViewById(R.id.coach_next)
        caretTop = findViewById(R.id.coach_caret_top)
        caretBottom = findViewById(R.id.coach_caret_bottom)

        next.setOnClickListener { advance() }
    }

    private fun buildDots() {
        dots.removeAllViews()
        val size = dp(6f)
        val gap = dp(3f)
        for (i in 0 until steps.size) {
            val dot = View(context)
            val lp = LinearLayout.LayoutParams(size, size)
            lp.marginEnd = gap
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.bg_coach_dot)
            dots.addView(dot)
        }
    }

    private fun updateDots() {
        for (i in 0 until dots.childCount) {
            val dot = dots.getChildAt(i)
            val active = i == current
            val lp = dot.layoutParams as LinearLayout.LayoutParams
            lp.width = if (active) dp(16f) else dp(6f)
            dot.layoutParams = lp
            dot.setBackgroundResource(if (active) R.drawable.bg_coach_dot_active else R.drawable.bg_coach_dot)
        }
    }

    private fun bindStep() {
        val step = steps[current]
        title.text = step.title
        body.text = step.body
        next.setText(if (current == steps.size - 1) R.string.coach_finish else R.string.coach_next)
        updateDots()

        // The target may not be laid out yet on the first pass
        post { positionTooltip() }
        invalidate()
    }

    private fun advance() {
        if (current < steps.size - 1) {
            current++
            bindStep()
        } else {
            finish()
        }
    }

    private fun finish() {
        val parent = parent as ViewGroup?
        parent?.removeView(this)
        onFinished?.run()
    }

    private fun computeHole() {
        val step = steps[current]
        val target = step.target

        target.getLocationInWindow(location)
        val own = IntArray(2)
        getLocationInWindow(own)

        val padding = dp(HOLE_PADDING_DP).toFloat()
        val left = location[0] - own[0] - padding
        val top = location[1] - own[1] - padding
        hole.set(left, top, left + target.width + padding * 2, top + target.height + padding * 2)
    }

    private fun positionTooltip() {
        computeHole()

        val gap = dp(10f)
        val margin = dp(16f)

        tooltip.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
        val tooltipWidth = tooltip.measuredWidth
        val tooltipHeight = tooltip.measuredHeight

        // Prefer placing the tooltip above the spotlight; fall back to below when there is no room
        val above = hole.top - tooltipHeight - gap > margin
        caretTop.visibility = if (above) View.GONE else View.VISIBLE
        caretBottom.visibility = if (above) View.VISIBLE else View.GONE

        var top = if (above)
            (hole.top - tooltipHeight - gap).toInt()
        else
            (hole.bottom + gap).toInt()
        top = Math.max(margin, Math.min(top, height - tooltipHeight - margin))

        val desiredLeft = (hole.centerX() - tooltipWidth / 2f).toInt()
        val left = Math.max(margin, Math.min(desiredLeft, width - tooltipWidth - margin))

        val lp = tooltip.layoutParams as FrameLayout.LayoutParams
        lp.gravity = Gravity.START or Gravity.TOP
        lp.leftMargin = left
        lp.topMargin = top
        tooltip.layoutParams = lp

        // Point the caret at the centre of the spotlight
        val caret = if (above) caretBottom else caretTop
        val caretParams = caret.layoutParams as LinearLayout.LayoutParams
        val caretLeft = (hole.centerX() - left - caret.layoutParams.width / 2f).toInt()
        caretParams.marginStart = Math.max(dp(12f), Math.min(caretLeft, tooltipWidth - dp(30f)))
        caret.layoutParams = caretParams
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { positionTooltip() }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        computeHole()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val radius = Math.max(hole.width(), hole.height()) / 2f
        canvas.drawRoundRect(hole, radius, radius, holePaint)
    }

    private fun dp(value: Float): Int {
        return Math.round(value * resources.displayMetrics.density)
    }

    @Suppress("unused")
    private fun themeColor(attr: Int): Int {
        return Tools.getThemeColor(context, attr)
    }
}
