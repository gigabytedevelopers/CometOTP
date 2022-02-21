/*
 * Created by Emmanuel Nwokoma (Gigabyte)  on 4/12/21 7:44 AM
 * Copyright: All rights reserved Ⓒ 2021
 * Last modified: 4/12/21 7:44 AM
 */

package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_ENTER;
import static android.view.KeyEvent.KEYCODE_NUMPAD_ENTER;

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
public class EditorActionHelper {

    private EditorActionHelper() { /* not allowed */ }

    public static boolean isActionDoneOrKeyboardEnter(int actionId, KeyEvent event) {
        boolean isKeyboardEnterEvent = false;
        if (event != null) {
            isKeyboardEnterEvent = event.getAction() == ACTION_DOWN
                    && (event.getKeyCode() == KEYCODE_ENTER || event.getKeyCode() == KEYCODE_NUMPAD_ENTER);
        }

        return actionId == EditorInfo.IME_ACTION_DONE || isKeyboardEnterEvent;
    }

    public static boolean isActionUpKeyboardEnter(KeyEvent event) {
        return event.getAction() == ACTION_UP && (event.getKeyCode() == KEYCODE_ENTER || event.getKeyCode() == KEYCODE_NUMPAD_ENTER);
    }
}