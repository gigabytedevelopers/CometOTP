package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.widget.Toolbar;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * "Set up Password" / "Set up PIN": the user enters the new credential, then confirms it. The
 * plain credential is handed back to the caller, which stores it and re-encrypts if needed.
 */
public class AuthSetupActivity extends BaseActivity {
    public static final String EXTRA_METHOD = "auth_setup_method";
    public static final String EXTRA_RESULT_CREDENTIAL = "auth_setup_credential";

    private static final String STATE_FIRST = "AuthSetupActivity.first";
    private static final String STATE_CONFIRMING = "AuthSetupActivity.confirming";

    private Constants.AuthMethod method = Constants.AuthMethod.PASSWORD;
    private int minLength = Constants.AUTH_MIN_PASSWORD_LENGTH;

    private String firstEntry = null;

    private TextView title;
    private TextView hint;
    private TextInputLayout layout;
    private TextInputEditText input;
    private MaterialButton continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!settings.getScreenshotsEnabled())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        String methodName = getIntent().getStringExtra(EXTRA_METHOD);
        if (methodName != null)
            method = Constants.AuthMethod.valueOf(methodName);
        boolean isPin = method == Constants.AuthMethod.PIN;
        minLength = isPin ? Constants.AUTH_MIN_PIN_LENGTH : Constants.AUTH_MIN_PASSWORD_LENGTH;

        setTitle(isPin ? R.string.security_setup_title_pin : R.string.security_setup_title_password);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_auth_setup);
        View v = stub.inflate();

        title = v.findViewById(R.id.setupTitle);
        hint = v.findViewById(R.id.setupHint);
        layout = v.findViewById(R.id.setupLayout);
        input = v.findViewById(R.id.setupInput);
        continueButton = v.findViewById(R.id.setupContinue);

        input.setInputType(isPin
                ? (InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD)
                : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        input.setHint(isPin ? R.string.auth_hint_pin : R.string.auth_hint_password);

        if (settings.getBlockAccessibility())
            layout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (settings.getBlockAutofill())
            layout.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        if (savedInstanceState != null) {
            firstEntry = savedInstanceState.getString(STATE_FIRST);
            if (!savedInstanceState.getBoolean(STATE_CONFIRMING, false))
                firstEntry = null;
        }
        showStep();

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                layout.setError(null);
                continueButton.setEnabled(s.length() >= minLength);
            }
        });
        input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && continueButton.isEnabled()) {
                onContinue();
                return true;
            }
            return false;
        });
        continueButton.setOnClickListener(view -> onContinue());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (firstEntry != null) {
                    firstEntry = null;
                    showStep();
                } else {
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        });

        input.requestFocus();
        UIHelper.showKeyboard(this, input);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    @Override
    protected void onSaveInstanceState(android.os.Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_FIRST, firstEntry);
        outState.putBoolean(STATE_CONFIRMING, firstEntry != null);
    }

    private void showStep() {
        boolean isPin = method == Constants.AuthMethod.PIN;
        boolean confirming = firstEntry != null;

        if (confirming) {
            title.setText(isPin ? R.string.security_setup_confirm_pin : R.string.security_setup_confirm_password);
            hint.setText(null);
        } else {
            title.setText(isPin ? R.string.security_setup_enter_pin : R.string.security_setup_enter_password);
            hint.setText(getString(isPin ? R.string.security_setup_hint_pin : R.string.security_setup_hint_password, minLength));
        }

        input.setText("");
        layout.setError(null);
        continueButton.setEnabled(false);
    }

    private void onContinue() {
        Editable text = input.getText();
        String value = text != null ? text.toString() : "";
        if (value.length() < minLength)
            return;

        if (firstEntry == null) {
            firstEntry = value;
            showStep();
            return;
        }

        if (!firstEntry.equals(value)) {
            firstEntry = null;
            showStep();
            layout.setError(getString(R.string.security_setup_mismatch));
            return;
        }

        UIHelper.hideKeyboard(this, input);
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_CREDENTIAL, value);
        data.putExtra(EXTRA_METHOD, method.name());
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected boolean shouldDestroyOnScreenOff() {
        return false;
    }
}
