package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionChangeHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.SecretKey;

import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod;
import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType;

/**
 * Security screen: choose how CometOTP is locked (PIN, password or the device lock) and set up
 * the chosen credential. Returns the (possibly changed) encryption key to the caller exactly like
 * the settings screen does.
 */
public class SecurityActivity extends BaseActivity {
    private SecretKey encryptionKey = null;
    private boolean encryptionChanged = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private View rowPin;
    private View rowPassword;
    private View rowDevice;
    private MaterialButton removeButton;

    private final ActivityResultLauncher<Intent> setupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();
                if (result.getResultCode() != RESULT_OK || data == null)
                    return;

                String credential = data.getStringExtra(AuthSetupActivity.EXTRA_RESULT_CREDENTIAL);
                String methodName = data.getStringExtra(AuthSetupActivity.EXTRA_METHOD);
                if (credential == null || methodName == null)
                    return;

                applyCredential(AuthMethod.valueOf(methodName), credential);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.security_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_security);
        View v = stub.inflate();

        byte[] keyMaterial = getIntent().getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY);
        if (keyMaterial != null && keyMaterial.length > 0)
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial);

        if (savedInstanceState != null) {
            encryptionChanged = savedInstanceState.getBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false);
            byte[] encKey = savedInstanceState.getByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY);
            if (encKey != null)
                encryptionKey = EncryptionHelper.generateSymmetricKey(encKey);
        }

        rowPin = v.findViewById(R.id.row_pin);
        rowPassword = v.findViewById(R.id.row_password);
        rowDevice = v.findViewById(R.id.row_device);
        removeButton = v.findViewById(R.id.security_remove);

        bindRow(rowPin, R.drawable.ic_dialpad, R.string.security_row_pin, () -> select(AuthMethod.PIN));
        bindRow(rowPassword, R.drawable.ic_lock_outline, R.string.security_row_password, () -> select(AuthMethod.PASSWORD));
        bindRow(rowDevice, R.drawable.ic_fingerprint, R.string.security_row_device, () -> select(AuthMethod.DEVICE));
        removeButton.setOnClickListener(view -> confirmRemove());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithResult();
            }
        });

        refresh();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged);
        if (encryptionKey != null)
            outState.putByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.getEncoded());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finishWithResult();
        return true;
    }

    private void finishWithResult() {
        Intent data = new Intent();
        data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged);
        if (encryptionKey != null)
            data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.getEncoded());
        setResult(RESULT_OK, data);
        finish();
    }

    private void bindRow(View row, int icon, int title, Runnable action) {
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        row.setOnClickListener(view -> action.run());
    }

    private void setSubtitle(View row, int textRes) {
        TextView subtitle = row.findViewById(R.id.row_subtitle);
        if (textRes == 0) {
            subtitle.setVisibility(View.GONE);
        } else {
            subtitle.setText(textRes);
            subtitle.setVisibility(View.VISIBLE);
        }
    }

    private void refresh() {
        AuthMethod current = settings.getAuthMethod();
        setSubtitle(rowPin, current == AuthMethod.PIN ? R.string.security_status_pin : 0);
        setSubtitle(rowPassword, current == AuthMethod.PASSWORD ? R.string.security_status_password : 0);
        setSubtitle(rowDevice, current == AuthMethod.DEVICE ? R.string.security_status_device : R.string.security_row_device_hint);
        removeButton.setVisibility(current == AuthMethod.NONE ? View.GONE : View.VISIBLE);
    }

    private int methodLabel(AuthMethod method) {
        switch (method) {
            case PIN: return R.string.security_row_pin;
            case PASSWORD: return R.string.security_row_password;
            case DEVICE: return R.string.security_row_device;
            default: return R.string.security_row_password;
        }
    }

    private void select(AuthMethod method) {
        AuthMethod current = settings.getAuthMethod();

        if (method == AuthMethod.DEVICE) {
            if (settings.getEncryption() == EncryptionType.PASSWORD) {
                showError(R.string.settings_dialog_msg_auth_invalid_with_encryption);
                return;
            }
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km == null || !km.isKeyguardSecure()) {
                showError(R.string.security_dialog_msg_device_not_secure);
                return;
            }
            if (current == AuthMethod.DEVICE)
                return;
        }

        if (current != AuthMethod.NONE && current != method) {
            ResultDialog.showWarning(this, R.string.security_dialog_title_warning,
                    getString(R.string.security_dialog_msg_switch, getString(methodLabel(current))),
                    R.string.continue_on, () -> proceed(method), android.R.string.cancel, null);
        } else {
            proceed(method);
        }
    }

    private void proceed(AuthMethod method) {
        if (method == AuthMethod.DEVICE) {
            settings.setAuthMethod(AuthMethod.DEVICE);
            refresh();
            ResultDialog.showSuccess(this, R.drawable.ic_fingerprint,
                    R.string.security_result_device_title, R.string.security_result_device_msg,
                    R.string.continue_on, null);
            return;
        }

        Intent intent = new Intent(this, AuthSetupActivity.class);
        intent.putExtra(AuthSetupActivity.EXTRA_METHOD, method.name());
        setupLauncher.launch(intent);
    }

    private void applyCredential(AuthMethod method, String credential) {
        // Deriving the credential hash (PBKDF2) and re-encrypting the database are slow; keep
        // them off the main thread and block the UI with a small progress dialog meanwhile.
        AlertDialog progress = new MaterialAlertDialogBuilder(this)
                .setView(R.layout.dialog_progress)
                .setCancelable(false)
                .show();

        final boolean reEncrypt = settings.getEncryption() == EncryptionType.PASSWORD;
        final SecretKey currentKey = encryptionKey;

        executor.execute(() -> {
            byte[] newKey = settings.setAuthCredentials(credential);
            EncryptionChangeHelper.Result result = reEncrypt
                    ? EncryptionChangeHelper.changeEncryption(this, currentKey, EncryptionType.PASSWORD, newKey)
                    : null;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed())
                    return;
                progress.dismiss();

                if (result != null) {
                    if (result.status != EncryptionChangeHelper.Status.SUCCESS) {
                        int message = result.status == EncryptionChangeHelper.Status.BACKUP_FAILED
                                ? R.string.settings_toast_encryption_backup_failed
                                : R.string.settings_toast_encryption_change_failed;
                        Snackbar.make(findViewById(R.id.container_content), message, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    encryptionKey = result.newKey;
                    encryptionChanged = true;
                }

                settings.setAuthMethod(method);
                refresh();

                boolean isPin = method == AuthMethod.PIN;
                ResultDialog.showSuccessWithSecondary(this, isPin ? R.drawable.ic_dialpad : R.drawable.ic_lock_outline,
                        isPin ? R.string.security_result_pin_title : R.string.security_result_password_title,
                        isPin ? R.string.security_result_pin_msg : R.string.security_result_password_msg,
                        R.string.continue_on, null,
                        isPin ? R.string.security_result_reset_pin : R.string.security_result_reset_password,
                        () -> proceed(method));
            });
        });
    }

    private void confirmRemove() {
        if (settings.getEncryption() == EncryptionType.PASSWORD) {
            showError(R.string.settings_dialog_msg_auth_invalid_with_encryption);
            return;
        }

        ResultDialog.showWarning(this, R.string.security_dialog_title_warning,
                getString(R.string.security_dialog_msg_remove),
                R.string.continue_on, () -> {
                    settings.setAuthMethod(AuthMethod.NONE);
                    refresh();
                }, android.R.string.cancel, null);
    }

    private void showError(int messageRes) {
        ResultDialog.showFailure(this, R.drawable.ic_shield, R.string.settings_dialog_title_error, messageRes,
                android.R.string.ok, null, 0, null);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected boolean shouldDestroyOnScreenOff() {
        return false;
    }
}
