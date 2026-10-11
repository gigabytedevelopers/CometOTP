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
 * The docked bottom action bar, raised around the add button.
 *
 * The bar is flush with the bottom of the window and extends behind the navigation bar, so the
 * OTP list can scroll underneath it. Its surface rises in a circle (the cradle, [cradleRadius])
 * centred on its top edge, around the add button that sits there, curving up into it from the
 * edge ([shoulderRadius]); the bar and the cradle, drawn as one shape, cast one soft shadow ([shadowColor], [shadowRadius]) and, where set, one
 * hairline round their top. The view is [raise] taller than the bar, room above the bar's top
 * edge for the cradle and its shadow; that room is transparent and lets touches through.
 */
class CradleBottomBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPath = Path()
    private val linePath = Path()
    private val arcRect = RectF()

    private val cradleRadius: Float
    /** The curves joining the bar's top edge to the cradle, so it rises out of the bar. */
    private val shoulderRadius: Float
    /** How far above the view's top the bar's top edge is: room for the cradle and its shadow. */
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
        var raiseBy = (radius + shadowRadius).toInt()
        var width = density
        var shoulder = 14f * density

        context.withStyledAttributes(attrs, R.styleable.CradleBottomBar, defStyleAttr, 0) {
            barColor = getColor(R.styleable.CradleBottomBar_barColor, barColor)
            lineColor = getColor(R.styleable.CradleBottomBar_barLineColor, lineColor)
            width = getDimension(R.styleable.CradleBottomBar_barLineWidth, width)
            radius = getDimension(R.styleable.CradleBottomBar_barCradleRadius, radius)
            shadowColor = getColor(R.styleable.CradleBottomBar_barShadowColor, shadowColor)
            shadowRadius = getDimension(R.styleable.CradleBottomBar_barShadowRadius, shadowRadius)
            raiseBy = getDimensionPixelSize(R.styleable.CradleBottomBar_barRaise, (radius + shadowRadius).toInt())
            shoulder = getDimension(R.styleable.CradleBottomBar_barShoulderRadius, shoulder)
        }
        cradleRadius = radius
        shoulderRadius = shoulder
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
     * The top outline, left to right: the bar's top edge, a shoulder curving up into the cradle,
     * over the cradle, and a shoulder back down. A shoulder is a circle touching both the edge and
     * the cradle, so the line has no corners.
     */
    private fun traceTop(path: Path, w: Float, top: Float) {
        val cx = w / 2f
        val r = cradleRadius
        val f = shoulderRadius
        // How far either shoulder's centre is from the cradle's, along the edge.
        val d = Math.sqrt(((r + f) * (r + f) - f * f).toDouble()).toFloat()
        // The angle, at the cradle's centre, between the edge and where a shoulder meets it.
        val a = Math.toDegrees(Math.atan2(f.toDouble(), d.toDouble())).toFloat()

        path.moveTo(0f, top)
        path.lineTo(cx - d, top)
        // Angles run clockwise from 3 o'clock.
        arcRect.set(cx - d - f, top - 2 * f, cx - d + f, top)
        path.arcTo(arcRect, 90f, a - 90f, false)
        arcRect.set(cx - r, top - r, cx + r, top + r)
        path.arcTo(arcRect, 180f + a, 180f - 2 * a, false)
        arcRect.set(cx + d - f, top - 2 * f, cx + d + f, top)
        path.arcTo(arcRect, 180f - a, a - 90f, false)
        path.lineTo(w, top)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val top = raise.toFloat()

        // The bar and the cradle as one shape, so they cast one shadow, with no seam between.
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
