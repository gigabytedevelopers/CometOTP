@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools

/**
 * The scanner's viewfinder: four rounded corner brackets around a square in the middle of the
 * view, [WIDTH_SHARE] of its width, in the accent colour. Only a guide for where to hold the
 * code; the whole picture is decoded.
 */
class ViewfinderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = resources.getDimension(R.dimen.scan_corner_stroke)
        color = Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)
    }
    private val arm = resources.getDimension(R.dimen.scan_corner_length)
    private val radius = resources.getDimension(R.dimen.scan_corner_radius)
    private val square = RectF()
    private val corner = RectF()
    private val path = Path()

    /** Where the square ends, from the view's top: for what goes under it. */
    val squareBottom: Float
        get() = (height + minOf(width, height) * WIDTH_SHARE) / 2

    override fun onDraw(canvas: Canvas) {
        val side = minOf(width, height) * WIDTH_SHARE
        val left = (width - side) / 2
        val top = (height - side) / 2
        // The stroke is drawn on the line, half inside: inset so it stays within the square.
        val inset = paint.strokeWidth / 2
        square.set(left + inset, top + inset, left + side - inset, top + side - inset)

        path.reset()
        // Each corner: along the top or bottom, round the corner, then down or up the side.
        for (c in 0 until 4) {
            val right = c == 1 || c == 2
            val bottom = c >= 2
            val x = if (right) square.right else square.left
            val y = if (bottom) square.bottom else square.top
            val dx = if (right) -1 else 1
            val dy = if (bottom) -1 else 1
            corner.set(minOf(x, x + 2 * radius * dx), minOf(y, y + 2 * radius * dy),
                maxOf(x, x + 2 * radius * dx), maxOf(y, y + 2 * radius * dy))
            path.moveTo(x + arm * dx, y)
            path.lineTo(x + radius * dx, y)
            // Angles run clockwise from 3 o'clock: the arc starts at the top (270) or bottom
            // (90) of the corner's circle and turns a quarter towards the side.
            path.arcTo(corner, if (bottom) 90f else 270f, if (right == bottom) -90f else 90f)
            path.lineTo(x, y + arm * dy)
        }
        canvas.drawPath(path, paint)
    }

    companion object {
        /** The square's side, as a share of the view's width (or height, if smaller). */
        const val WIDTH_SHARE = 0.61f
    }
}
