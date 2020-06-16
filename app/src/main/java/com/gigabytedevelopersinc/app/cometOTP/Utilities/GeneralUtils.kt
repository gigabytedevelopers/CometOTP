@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.os.Build

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Tuesday, 16
 * Month: June
 * Year: 2020
 * Date: 16 Jun, 2020
 * Time: 10:26 PM
 * Desc: GeneralUtils
 **/
object GeneralUtils {
    fun isOreo() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
}