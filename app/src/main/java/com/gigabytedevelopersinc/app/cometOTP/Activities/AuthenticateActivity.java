package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.Intent;
import android.os.Bundle;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.GeneralUtils;
import com.gigabytedevelopersinc.app.cometOTP.View.AutoFillable.AutoFillableTextInputEditText;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.widget.Toolbar;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;

import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod;

public class AuthenticateActivity extends ThemedActivity
    implements EditText.OnEditorActionListener, View.OnClickListener {
    private String password;
    private final AutoFillableTextInputEditText.AutoFillTextListener autoFillTextListener = text -> checkPassword(text.toString());

    AuthMethod authMethod;
    String newEncryption = "";
    boolean oldPassword = false;

    AutoFillableTextInputEditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.auth_activity_title);

        if (!settings.getScreenshotsEnabled())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        toolbar.setNavigationIcon(null);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_authenticate);
        View v = stub.inflate();

        Intent callingIntent = getIntent();
        int labelMsg = callingIntent.getIntExtra(Constants.EXTRA_AUTH_MESSAGE, R.string.auth_msg_authenticate);
        newEncryption = callingIntent.getStringExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION);

        TextView passwordLabel = v.findViewById(R.id.passwordLabel);
        TextInputLayout passwordLayout = v.findViewById(R.id.passwordLayout);
        passwordInput = v.findViewById(R.id.passwordEdit);

        if (settings.getBlockAccessibility())
            passwordLayout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        if (GeneralUtils.INSTANCE.isOreo() && settings.getBlockAutofill())
            passwordLayout.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);

        passwordLabel.setText(labelMsg);

        authMethod = settings.getAuthMethod();
        password = settings.getAuthCredentials();

        if (password.isEmpty()) {
            password = settings.getOldCredentials(authMethod);
            oldPassword = true;
        }

        if (authMethod == AuthMethod.PASSWORD) {
            if (password.isEmpty()) {
                Snackbar.make(findViewById(R.id.authenticate), R.string.auth_toast_password_missing, Snackbar.LENGTH_LONG).show();
                finishWithResult(true, null);
            } else {
                passwordLayout.setHint(getString(R.string.auth_hint_password));
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
        } else if (authMethod == AuthMethod.PIN) {
            if (password.isEmpty()) {
                Snackbar.make(findViewById(R.id.authenticate), R.string.auth_toast_pin_missing, Snackbar.LENGTH_LONG).show();
                finishWithResult(true, null);
            } else {
                passwordLayout.setHint(getString(R.string.auth_hint_pin));
                passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            }
        } else {
            finishWithResult(true, null);
        }

        passwordInput.setTransformationMethod(new PasswordTransformationMethod());
        passwordInput.setOnEditorActionListener(this);

        Button unlockButton = v.findViewById(R.id.buttonUnlock);
        unlockButton.setOnClickListener(this);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Override
    public void onClick(View view) {
        checkPassword(Objects.requireNonNull(passwordInput.getText()).toString());
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            checkPassword(v.getText().toString());
            return true;
        }

        return false;
    }

    public void checkPassword(String plainPassword) {
        if (! oldPassword) {
            try {
                EncryptionHelper.PBKDF2Credentials credentials = EncryptionHelper.generatePBKDF2Credentials(plainPassword, settings.getSalt(), settings.getIterations());
                byte[] passwordArray = Base64.decode(password, Base64.URL_SAFE);

                if (Arrays.equals(passwordArray, credentials.password)) {
                    finishWithResult(true, credentials.key);
                } else {
                    finishWithResult(false, null);
                }
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
                e.printStackTrace();
                finishWithResult(false, null);
            }
        } else {
            String hashedPassword = new String(Hex.encodeHex(DigestUtils.sha256(plainPassword)));

            if (hashedPassword.equals(password)) {
                byte[] key = settings.setAuthCredentials(plainPassword);

                if (key == null)
                    Snackbar.make(findViewById(R.id.authenticate), R.string.settings_toast_auth_upgrade_failed, Snackbar.LENGTH_LONG).show();

                if (authMethod == AuthMethod.PASSWORD)
                    settings.removeAuthPasswordHash();
                else if (authMethod == AuthMethod.PIN)
                    settings.removeAuthPINHash();

                finishWithResult(true, key);
            } else {
                finishWithResult(false, null);
            }
        }
    }

    // End with a result
    public void finishWithResult(boolean success, byte[] key) {
        Intent data = new Intent();

        if (newEncryption != null && ! newEncryption.isEmpty())
            data.putExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION, newEncryption);

        if (key != null)
            data.putExtra(Constants.EXTRA_AUTH_PASSWORD_KEY, key);

        if (success)
            setResult(RESULT_OK, data);

        finish();
    }

    // Go back to the main activity
    @Override
    public void onBackPressed() {
        finishWithResult(false, null);
        super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (settings.getAutoUnlockAfterAutofill()) {
            passwordInput.setAutoFillTextListener(autoFillTextListener);
        }
    }

    @Override
    protected void onStop() {
        passwordInput.setAutoFillTextListener(null);
        super.onStop();
    }
}
