/*
 * Created by Emmanuel Nwokoma (Gigabyte)  on 4/12/21 7:44 AM
 * Copyright: All rights reserved Ⓒ 2021
 * Last modified: 4/12/21 7:44 AM
 */
@file:Suppress("PackageName")

package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.view.KeyEvent
import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import android.view.inputmethod.EditorInfo

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Monday, 12
 * Month: April
 * Year: 2021
 * Date: 12 Apr, 2021
 * Time: 7:44 AM
 * Desc: EditorActionHelper
 **/
object EditorActionHelper {

    fun isActionDoneOrKeyboardEnter(actionId: Int, event: KeyEvent?): Boolean {
        var isKeyboardEnterEvent = false
        if (event != null) {
            isKeyboardEnterEvent = event.action == ACTION_DOWN &&
                    (event.keyCode == KEYCODE_ENTER || event.keyCode == KEYCODE_NUMPAD_ENTER)
        }

        return actionId == EditorInfo.IME_ACTION_DONE || isKeyboardEnterEvent
    }

    fun isActionUpKeyboardEnter(event: KeyEvent): Boolean {
        return event.action == ACTION_UP && (event.keyCode == KEYCODE_ENTER || event.keyCode == KEYCODE_NUMPAD_ENTER)
    }
}
