@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object UIHelper {
    fun showGenericDialog(context: Context, titleId: Int, messageId: Int) {
        showGenericDialog(context, titleId, messageId, null)
    }

    fun showGenericDialog(context: Context, titleId: Int, messageId: Int, onOk: Runnable?) {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(titleId)
            .setMessage(messageId)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (onOk != null)
                    onOk.run()
            }
            .create()
            .show()
    }

    fun showKeyboard(context: Context, view: View?) {
        showKeyboard(context, view, false)
    }

    @Suppress("DEPRECATION")
    fun showKeyboard(context: Context, view: View?, showForced: Boolean) {
        if (view != null) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (showForced)
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
            else
                imm.showSoftInput(view, 0)
        }
    }

    fun hideKeyboard(context: Context, view: View?) {
        if (view != null) {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
