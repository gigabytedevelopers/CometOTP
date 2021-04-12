package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import android.content.Context;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.ConfirmedPasswordTransformationHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GeneralUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AppCompatDialog;

import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

import java.util.Objects;

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 7/15/2018
 **/
public class PasswordEntryDialog extends AppCompatDialog
        implements View.OnClickListener, TextWatcher {

    public enum Mode { ENTER, UPDATE }

    public interface PasswordEnteredCallback {
        void onPasswordEntered(String newPassword);
    }

    private final Mode dialogMode;
    private final PasswordEnteredCallback callback;

    private final TextInputEditText passwordInput;
    private final EditText passwordConfirm;
    private final Button okButton;
    private final TextView tooShortWarning;

    public PasswordEntryDialog(Context context, Mode newMode, boolean blockAccessibility, boolean blockAutofill, PasswordEnteredCallback newCallback) {
        super(context, Tools.getThemeResource(context, R.attr.dialogTheme));

        setTitle(R.string.dialog_title_enter_password);
        setContentView(R.layout.dialog_password_entry);

        TextInputLayout passwordLayout = findViewById(R.id.passwordInputLayout);
        passwordInput = findViewById(R.id.passwordInput);
        passwordConfirm = findViewById(R.id.passwordConfirm);
        tooShortWarning = findViewById(R.id.tooShortWarning);
        tooShortWarning.setText(getContext().getString(R.string.settings_label_short_password, Constants.AUTH_MIN_PASSWORD_LENGTH));
        ConfirmedPasswordTransformationHelper.setup(passwordLayout, passwordInput, passwordConfirm);

        if (blockAccessibility) {
            passwordLayout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            passwordConfirm.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && blockAutofill) {
            passwordLayout.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            passwordConfirm.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        okButton = findViewById(R.id.buttonOk);
        okButton.setOnClickListener(this);
        okButton.setEnabled(false);

        Button cancelButton = findViewById(R.id.buttonCancel);
        cancelButton.setOnClickListener(this);

        this.callback = newCallback;

        this.dialogMode = newMode;

        if (this.dialogMode == Mode.UPDATE) {
            passwordConfirm.setVisibility(View.VISIBLE);
            passwordInput.addTextChangedListener(this);
            passwordConfirm.addTextChangedListener(this);
        } else if (this.dialogMode == Mode.ENTER) {
            passwordConfirm.setVisibility(View.GONE);
        }
    }

    // TextWatcher
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (passwordInput.getEditableText().length() >= Constants.AUTH_MIN_PASSWORD_LENGTH) {
            tooShortWarning.setVisibility(View.GONE);
            okButton.setEnabled(TextUtils.equals(passwordInput.getEditableText(), passwordConfirm.getEditableText()));
        }
        else {
            tooShortWarning.setVisibility(View.VISIBLE);
            okButton.setEnabled(false);
        }
    }

    public void afterTextChanged(Editable s) {}
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    // View.OnClickListener
    public void onClick(View view)  {
        if (view.getId() == R.id.buttonOk) {
            if (callback != null)
                callback.onPasswordEntered(passwordInput.getText().toString());
        }

        dismiss();
    }
}