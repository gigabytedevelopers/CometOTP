@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EditorActionHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Asks for the password an export file was protected with.
 *
 * Unlike [PasswordEntryDialog] this puts no lower bound on the length: the password was chosen
 * in another app, under that app's rules, and a short one still has to be accepted to open the
 * file. Nothing is checked here; the caller tries the file again and comes back with
 * [wrongPassword] set when it did not open.
 */
object ImportPasswordDialog {

    fun show(context: Context, wrongPassword: Boolean, onPassword: (String) -> Unit, onCancel: () -> Unit): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_import_password, null, false)
        val layout = view.findViewById<TextInputLayout>(R.id.importPasswordLayout)
        val input = view.findViewById<TextInputEditText>(R.id.importPassword)

        if (wrongPassword)
            layout.error = context.getString(R.string.import_password_wrong)

        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.import_password_title)
                .setView(view)
                .setPositiveButton(R.string.import_password_open) { _, _ -> onPassword(input.text?.toString() ?: "") }
                .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                .setOnCancelListener { onCancel() }
                .create()

        input.setOnEditorActionListener { _, actionId, event ->
            if (EditorActionHelper.isActionDoneOrKeyboardEnter(actionId, event)) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.performClick()
                true
            } else {
                false
            }
        }

        dialog.show()
        input.requestFocus()
        return dialog
    }
}
