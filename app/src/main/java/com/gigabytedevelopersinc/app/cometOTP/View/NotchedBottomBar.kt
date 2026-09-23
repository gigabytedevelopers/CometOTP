@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * The docked bottom action bar from the redesign.
 *
 * The bar is flush with the bottom of the window and extends behind the navigation bar, so the
 * OTP list can scroll underneath it. A circular notch is punched out of the top edge for the add
 * button, which is centred on that edge, and the 1dp top hairline follows the notch around.
 */
class NotchedBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPath = Path()
    private val notchPath = Path()
    private val linePath = Path()
    private val notchRect = RectF()

    // Backing field for notchRadius: the constructor assigns it directly, without the
    // invalidate() that the public setter performs.
    private var mNotchRadius = 0f
    private var lineWidth = 0f

    init {
        setWillNotDraw(false)

        val density = resources.displayMetrics.density
        var barColor = 0xFFFFFFFF.toInt()
        var lineColor = 0x1F000000
        mNotchRadius = 38f * density
        lineWidth = density

        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.NotchedBottomBar, defStyleAttr, 0)
            barColor = a.getColor(R.styleable.NotchedBottomBar_barColor, barColor)
            lineColor = a.getColor(R.styleable.NotchedBottomBar_barLineColor, lineColor)
            mNotchRadius = a.getDimension(R.styleable.NotchedBottomBar_barNotchRadius, mNotchRadius)
            lineWidth = a.getDimension(R.styleable.NotchedBottomBar_barLineWidth, lineWidth)
            a.recycle()
        }

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = barColor

        linePaint.style = Paint.Style.STROKE
        linePaint.color = lineColor
        linePaint.strokeWidth = lineWidth
    }

    /** The radius of the circle cut out of the top edge, including the gap around the button. */
    var notchRadius: Float
        get() = mNotchRadius
        set(radius) {
            mNotchRadius = radius
            invalidate()
        }

    fun setBarColor(@ColorInt color: Int) {
        fillPaint.color = color
        invalidate()
    }

    fun setBarLineColor(@ColorInt color: Int) {
        linePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2f
        // Inset by half the stroke so the hairline sits fully inside the view.
        val top = lineWidth / 2f

        fillPath.reset()
        fillPath.addRect(0f, top, w, h, Path.Direction.CW)
        notchPath.reset()
        notchPath.addCircle(cx, top, mNotchRadius, Path.Direction.CW)
        fillPath.op(notchPath, Path.Op.DIFFERENCE)
        canvas.drawPath(fillPath, fillPaint)

        notchRect.set(cx - mNotchRadius, top - mNotchRadius, cx + mNotchRadius, top + mNotchRadius)
        linePath.reset()
        linePath.moveTo(0f, top)
        linePath.lineTo(cx - mNotchRadius, top)
        // Sweep from the left of the notch, down through its lowest point, back up to the right.
        linePath.arcTo(notchRect, 180f, -180f, false)
        linePath.lineTo(w, top)
        canvas.drawPath(linePath, linePaint)

        super.onDraw(canvas)
    }
}
