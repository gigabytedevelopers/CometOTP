@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Resources
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.View
import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity
import com.gigabytedevelopersinc.app.cometOTP.R
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.DateFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Tools {
    @Suppress("unused")
    private const val CSS_RGBA_FORMAT = "rgba(%1\$d,%2\$d,%3\$d,%4\$1f)"

    /* Checks if external storage is available for read and write */
    @JvmStatic
    fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    /* Checks if external storage is available to at least read */
    @JvmStatic
    fun isExternalStorageReadable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
    }

    /* Get a color based on the current theme */
    @JvmStatic
    fun getThemeColor(context: Context, colorAttr: Int): Int {
        val theme = context.theme
        val arr = theme.obtainStyledAttributes(intArrayOf(colorAttr))

        val colorValue = arr.getColor(0, -1)
        arr.recycle()

        return colorValue
    }

    @JvmStatic
    fun getThemeResource(context: Context, styleAttr: Int): Int {
        val theme = context.theme
        val arr = theme.obtainStyledAttributes(intArrayOf(styleAttr))

        val styleValue = arr.getResourceId(0, -1)
        arr.recycle()

        return styleValue
    }

    /* Create a ColorFilter based on the current theme */
    @JvmStatic
    fun getThemeColorFilter(context: Context, colorAttr: Int): ColorFilter {
        return PorterDuffColorFilter(getThemeColor(context, colorAttr), PorterDuff.Mode.SRC_IN)
    }

    @JvmStatic
    fun buildUri(base: String?, name: String): Uri {
        return Uri.fromFile(File(base, name))
    }

    @JvmStatic
    fun mkdir(path: String): Boolean {
        val dir = File(path)
        return dir.exists() || dir.mkdirs()
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun getSystemLocale(): Locale {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Resources.getSystem().configuration.locales.get(0)
        } else {
            return Resources.getSystem().configuration.locale
        }
    }

    @JvmStatic
    fun formatTokenString(token: Int, digits: Int): String {
        val numberFormat = NumberFormat.getInstance(Locale.ENGLISH)
        numberFormat.minimumIntegerDigits = digits
        numberFormat.isGroupingUsed = false

        return numberFormat.format(token.toLong())
    }


    @JvmStatic
    fun formatToken(s: String?, chunkSize: Int): String? {
        if (chunkSize == 0 || s == null)
            return s

        val ret = StringBuilder("")
        var index = s.length
        while (index > 0) {
            ret.insert(0, s.substring(Math.max(index - chunkSize, 0), index))
            ret.insert(0, " ")
            index = index - chunkSize
        }
        // trim { it <= ' ' } is java.lang.String.trim(); Kotlin's trim() strips Unicode whitespace.
        return ret.toString().trim { it <= ' ' }
    }

    @JvmStatic
    fun getDateTimeString(): String {
        val df: DateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ENGLISH)
        val now = Calendar.getInstance().time
        return df.format(now)
    }

    @JvmStatic
    fun copyToClipboard(context: Context, text: String?) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(context.getString(R.string.label_clipboard_content), text)
        clipboard.setPrimaryClip(clip)

        Snackbar.make((context as MainActivity).findViewById<View>(R.id.main_content), R.string.toast_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
    }
}
