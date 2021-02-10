package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import android.text.method.PasswordTransformationMethod;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Wednesday, 10
 * Month: February
 * Year: 2021
 * Date: 10 Feb, 2021
 * Time: 12:42 AM
 * Desc: ConfirmedPasswordTransformationHelper
 **/
public final class ConfirmedPasswordTransformationHelper {

    /** Sets up the specified password views for a toggleable obscure/view password text transformation. */
    public static void setup(TextInputLayout passwordLayout, TextInputEditText passwordInput, EditText passwordConfirmInput) {
        passwordLayout.setEndIconOnClickListener(v -> {
            boolean wasShowingPassword = passwordInput.getTransformationMethod() instanceof PasswordTransformationMethod;
            // Dispatch password visibility change to both password and confirm inputs
            dispatchPasswordVisibilityChange(passwordInput, wasShowingPassword);
            dispatchPasswordVisibilityChange(passwordConfirmInput, wasShowingPassword);
            passwordLayout.refreshDrawableState();
        });
        passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        passwordConfirmInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
    }

    private static void dispatchPasswordVisibilityChange(EditText editText, boolean wasShowingPassword) {
        final int selection = editText.getSelectionEnd();
        PasswordTransformationMethod newMethod = wasShowingPassword ? null : PasswordTransformationMethod.getInstance();
        editText.setTransformationMethod(newMethod);
        if (selection >= 0) {
            editText.setSelection(selection);
        }
    }
}
