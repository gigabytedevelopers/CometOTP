@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.view.View

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Thursday, 07
 * Month: May
 * Year: 2020
 * Date: 07 May, 2020
 * Time: 1:11 AM
 * Desc: SimpleDoubleClickListener
 **/
abstract class SimpleDoubleClickListener : View.OnClickListener {

    companion object {
        private const val DOUBLE_CLICK_TIME_DELTA: Long = 300 // Milliseconds
    }

    private var lastClickTime: Long = 0
    private var firstTap = true

    override fun onClick(v: View) {
        val clickTime = System.currentTimeMillis()

        if (clickTime - lastClickTime < DOUBLE_CLICK_TIME_DELTA) {
            if (firstTap)
                onDoubleClick(v)

            firstTap = false
        } else {
            firstTap = true

            v.postDelayed({
                if (firstTap)
                    onSingleClick(v)
            }, DOUBLE_CLICK_TIME_DELTA)
        }

        lastClickTime = clickTime
    }

    abstract fun onSingleClick(v: View)
    abstract fun onDoubleClick(v: View)
}
