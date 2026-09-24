@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.util.TypedValue
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * Original http://stackoverflow.com/questions/23122088/colored-boxed-with-letters-a-la-gmail
 * Used to create a [Bitmap] that contains a letter used in the English
 * alphabet or digit, if there is no letter or digit available, a default image
 * is shown instead.
 *
 * Only English language supported.
 */
internal class LetterBitmap {

    /**
     * The [TextPaint] used to draw the letter onto the tile
     */
    private val mPaint = TextPaint()
    /**
     * The bounds that enclose the letter
     */
    private val mBounds = Rect()
    /**
     * The [Canvas] to draw on
     */
    private val mCanvas = Canvas()
    /**
     * The first char of the name being displayed
     */
    private val mFirstChar = CharArray(1)

    /**
     * The background colors of the tile, copied out of the resource array (which is recycled as
     * soon as it has been read, so every tile can use the colors)
     */
    private val mColors: IntArray
    /**
     * The font size used to display the letter
     */
    private val mTileLetterFontSizeScale: Float

    /**
     * Constructor for `LetterTileProvider`
     *
     * @param context The [Context] to use
     */
    constructor(context: Context) {
        val res = context.resources

        mPaint.typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
        mPaint.color = Color.WHITE
        mPaint.textAlign = Paint.Align.CENTER
        mPaint.isAntiAlias = true

        val colors = res.obtainTypedArray(R.array.letter_tile_colors)
        mColors = try {
            IntArray(NUM_OF_TILE_COLORS) { colors.getColor(it, Color.BLACK) }
        } finally {
            colors.recycle()
        }
        val typedValue = TypedValue()
        res.getValue(R.dimen.tile_letter_font_size_scale, typedValue, true)
        mTileLetterFontSizeScale = typedValue.float
    }

    /**
     * @param displayName The name used to create the letter for the tile
     * @param key         The key used to generate the background color for the tile
     * @param width       The desired width of the tile
     * @param height      The desired height of the tile
     * @return A [Bitmap] that contains a letter used in the English
     * alphabet or digit, if there is no letter or digit available, a
     * default image is shown instead
     */
    fun getLetterTile(displayName: String, key: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var firstChar = '?'

        if (displayName.isNotEmpty() && startsWithAlphabeticOrDigit(displayName)) {
            firstChar = displayName[0]
        }

        val c = mCanvas
        c.setBitmap(bitmap)
        c.drawColor(pickColor(key))

        /*if (!isEnglishLetterOrDigit(firstChar)) {
            firstChar = '?';
        }*/
        mFirstChar[0] = Character.toUpperCase(firstChar)
        mPaint.textSize = mTileLetterFontSizeScale * height
        mPaint.getTextBounds(mFirstChar, 0, 1, mBounds)
        // Integer halves, as in the Java original, then widened to float.
        c.drawText(mFirstChar, 0, 1, (width / 2).toFloat(), (height / 2 +
                (mBounds.bottom - mBounds.top) / 2).toFloat(), mPaint)
        return bitmap
    }

    /**
     * @param key The key used to generate the tile color
     * @return A new or previously chosen color for `key` used as the
     * tile background color
     */
    private fun pickColor(key: String): Int {
        // String.hashCode() is not supposed to change across java versions, so
        // this should guarantee the same key always maps to the same color
        val color = Math.abs(key.hashCode()) % NUM_OF_TILE_COLORS
        return mColors[color]
    }

    companion object {
        /**
         * The number of available tile colors
         */
        private const val NUM_OF_TILE_COLORS = 8

        /**
         * @param string The string to check
         *      * @return True if `string` starts with an alphabetic letter or a digit,
         * false otherwise
         */
        /*private static boolean isEnglishLetterOrDigit(char c) {
            return 'A' <= c && c <= 'Z' || 'a' <= c && c <= 'z' || '0' <= c && c <= '9';
        }*/
        private fun startsWithAlphabeticOrDigit(string: String): Boolean {
            return Character.isAlphabetic(string.codePointAt(0)) ||
                    Character.isDigit(string[0])
        }
    }
}
