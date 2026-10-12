@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * The docked bottom action bar, notched for the add button.
 *
 * The bar is flush with the bottom of the window and extends behind the navigation bar, so the
 * OTP list can scroll underneath it. A circle ([notchRadius]) centred on its top edge is cut out
 * of it for the add button that sits there, and the edge rounds down into the notch
 * ([notchCornerRadius]) rather than meeting it at a corner. The bar casts a soft shadow
 * ([shadowColor], [shadowRadius]) and, where set, a hairline runs round its top. The view is
 * [raise] taller than the bar, room above the bar's top edge for the shadow; that room is
 * transparent and lets touches through.
 */
class NotchedBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPath = Path()
    private val linePath = Path()
    private val arcRect = RectF()

    private val notchRadius: Float
    /** The curves joining the bar's top edge to the notch, so the edge has no corners. */
    private val notchCornerRadius: Float
    /** How far below the view's top the bar's top edge is: room for its shadow. */
    val raise: Int
    private val lineWidth: Float

    init {
        setWillNotDraw(false)

        val density = resources.displayMetrics.density
        var barColor = 0xFFFFFFFF.toInt()
        var lineColor = 0
        var shadowColor = 0x29000000
        var shadowRadius = 12f * density
        var radius = 38f * density
        var raiseBy = (shadowRadius + density).toInt()
        var width = density
        var corner = 16f * density

        context.withStyledAttributes(attrs, R.styleable.NotchedBottomBar, defStyleAttr, 0) {
            barColor = getColor(R.styleable.NotchedBottomBar_barColor, barColor)
            lineColor = getColor(R.styleable.NotchedBottomBar_barLineColor, lineColor)
            width = getDimension(R.styleable.NotchedBottomBar_barLineWidth, width)
            radius = getDimension(R.styleable.NotchedBottomBar_barNotchRadius, radius)
            shadowColor = getColor(R.styleable.NotchedBottomBar_barShadowColor, shadowColor)
            shadowRadius = getDimension(R.styleable.NotchedBottomBar_barShadowRadius, shadowRadius)
            raiseBy = getDimensionPixelSize(R.styleable.NotchedBottomBar_barRaise, (shadowRadius + density).toInt())
            corner = getDimension(R.styleable.NotchedBottomBar_barNotchCornerRadius, corner)
        }
        notchRadius = radius
        notchCornerRadius = corner
        raise = raiseBy
        lineWidth = width

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = barColor
        // Shadows on shapes other than text are drawn by the GPU from Android 9 on only.
        fillPaint.setShadowLayer(shadowRadius, 0f, -density, shadowColor)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            setLayerType(LAYER_TYPE_SOFTWARE, null)

        linePaint.style = Paint.Style.STROKE
        linePaint.color = lineColor
        linePaint.strokeWidth = lineWidth
    }

    /**
     * The top outline, left to right: the bar's top edge, a corner rounding down into the notch,
     * round the bottom of the notch, and a corner back up. A corner is a circle under the edge
     * touching both it and the notch, so the line has no corners.
     */
    private fun traceTop(path: Path, w: Float, top: Float) {
        val cx = w / 2f
        val r = notchRadius
        val f = notchCornerRadius
        // How far either corner's centre is from the notch's, along the edge.
        val d = Math.sqrt(((r + f) * (r + f) - f * f).toDouble()).toFloat()
        // The angle, at the notch's centre, between the edge and where a corner meets the notch.
        val a = Math.toDegrees(Math.atan2(f.toDouble(), d.toDouble())).toFloat()

        path.moveTo(0f, top)
        path.lineTo(cx - d, top)
        // Angles run clockwise from 3 o'clock.
        arcRect.set(cx - d - f, top, cx - d + f, top + 2 * f)
        path.arcTo(arcRect, 270f, 90f - a, false)
        arcRect.set(cx - r, top - r, cx + r, top + r)
        path.arcTo(arcRect, 180f - a, 2 * a - 180f, false)
        arcRect.set(cx + d - f, top, cx + d + f, top + 2 * f)
        path.arcTo(arcRect, 180f + a, 90f - a, false)
        path.lineTo(w, top)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val top = raise.toFloat()

        fillPath.reset()
        traceTop(fillPath, w, top)
        fillPath.lineTo(w, h)
        fillPath.lineTo(0f, h)
        fillPath.close()
        canvas.drawPath(fillPath, fillPaint)

        if (linePaint.alpha != 0) {
            linePath.reset()
            traceTop(linePath, w, top)
            canvas.drawPath(linePath, linePaint)
        }

        super.onDraw(canvas)
    }
}
