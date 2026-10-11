@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools

/**
 * A miniature of the home screen, for the theme and list style pickers: an app bar and a few
 * service cards, each with an icon, a token and a label, in the colours of the given theme(s).
 * With two themes (the Auto tile) the left half shows the first and the right half the second.
 * [rows] and [rowHeight] set how dense the cards are, for the list styles.
 */
class AppearancePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private class Palette(themed: Context) {
        val background = Tools.getThemeColor(themed, android.R.attr.colorBackground)
        val card = Tools.getThemeColor(themed, com.google.android.material.R.attr.colorSurfaceContainerHigh)
        val primary = Tools.getThemeColor(themed, androidx.appcompat.R.attr.colorPrimary)
        val text = Tools.getThemeColor(themed, com.google.android.material.R.attr.colorOnSurface)
        val outline = Tools.getThemeColor(themed, com.google.android.material.R.attr.colorOutlineVariant)
    }

    private var left: Palette = Palette(context)
    private var right: Palette? = null

    /** How many cards show, and how tall each is as a share of the preview's height. */
    var rows = 4
        set(value) {
            field = value
            invalidate()
        }
    var rowHeight = 0.16f
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()
    private val density = resources.displayMetrics.density

    /** Draws in [themed]'s colours, or split between it and [second] for the Auto tile. */
    fun setThemes(themed: Context, second: Context? = null) {
        left = Palette(themed)
        right = second?.let { Palette(it) }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val corner = 10 * density

        clip.reset()
        clip.addRoundRect(0f, 0f, w, h, corner, corner, Path.Direction.CW)
        canvas.withClip(clip) {
            drawScreen(this, left, w, h)
            val second = right
            if (second != null)
                withClip(w / 2, 0f, w, h) { drawScreen(this, second, w, h) }
        }

        // A hairline around it, so a preview as dark or light as the screen behind it still shows.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = left.outline
        rect.set(density / 2, density / 2, w - density / 2, h - density / 2)
        canvas.drawRoundRect(rect, corner, corner, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawScreen(canvas: Canvas, p: Palette, w: Float, h: Float) {
        canvas.drawColor(p.background)

        val pad = w * 0.09f
        // App bar
        paint.color = p.card
        rect.set(pad, pad, w - pad, pad + h * 0.07f)
        canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)

        var top = rect.bottom + pad * 0.8f
        val cardHeight = h * rowHeight
        val gap = pad * 0.5f
        repeat(rows) {
            if (top + cardHeight > h - pad / 2)
                return
            paint.color = p.card
            rect.set(pad, top, w - pad, top + cardHeight)
            canvas.drawRoundRect(rect, cardHeight * 0.18f, cardHeight * 0.18f, paint)

            // Icon, token and label
            val iconSize = cardHeight * 0.5f
            val cy = top + cardHeight / 2
            paint.color = p.primary
            canvas.drawCircle(pad * 1.8f + iconSize / 2, cy, iconSize / 2, paint)
            val textLeft = pad * 2.4f + iconSize
            val textRight = w - pad * 1.6f
            paint.color = ColorUtils.setAlphaComponent(p.text, 0xDD)
            rect.set(textLeft, cy - cardHeight * 0.2f, textLeft + (textRight - textLeft) * 0.8f, cy + cardHeight * 0.02f)
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)
            paint.color = ColorUtils.setAlphaComponent(p.text, 0x66)
            rect.set(textLeft, cy + cardHeight * 0.1f, textLeft + (textRight - textLeft) * 0.5f, cy + cardHeight * 0.22f)
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, paint)

            top += cardHeight + gap
        }
    }
}
