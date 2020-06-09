package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import android.content.Context;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AppCompatDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

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

    private Mode dialogMode;
    private PasswordEnteredCallback callback;

    private TextInputEditText passwordInput;
    private TextInputLayout passwordLayout;
    private EditText passwordConfirm;
    private Button okButton, cancelButton;

    public PasswordEntryDialog(Context context, Mode newMode, boolean blockAccessibility, PasswordEnteredCallback newCallback) {
        super(context, Tools.getThemeResource(context, R.attr.dialogTheme));

        setTitle(R.string.dialog_title_enter_password);
        setContentView(R.layout.dialog_password_entry);
        setCancelable(false);

        passwordLayout = findViewById(R.id.passwordInputLayout);
        passwordInput = findViewById(R.id.passwordInput);
        passwordConfirm = findViewById(R.id.passwordConfirm);

        if (blockAccessibility) {
            passwordLayout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            passwordConfirm.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        okButton = findViewById(R.id.buttonOk);
        cancelButton = findViewById(R.id.buttonCancel);

        okButton.setOnClickListener(this);
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
        if (TextUtils.equals(passwordInput.getEditableText(), passwordConfirm.getEditableText()))
            okButton.setEnabled(true);
        else
            okButton.setEnabled(false);
    }

    public void afterTextChanged(Editable s) {}
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

    // View.OnClickListener
    public void onClick(View view)  {
        if (view.getId() == R.id.buttonOk) {
            if (callback != null)
                callback.onPasswordEntered(Objects.requireNonNull(passwordInput.getText()).toString());
            }

            dismiss();
    }
}
