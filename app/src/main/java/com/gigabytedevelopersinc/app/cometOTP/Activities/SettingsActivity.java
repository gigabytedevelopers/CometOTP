package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.backup.BackupManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.ViewStub;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Preferences.CredentialsPreference;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GeneralUtils;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper;
import com.google.android.material.snackbar.Snackbar;

import org.openintents.openpgp.util.OpenPgpAppPreference;
import org.openintents.openpgp.util.OpenPgpKeyPreference;

import java.util.ArrayList;
import java.util.Locale;

import javax.crypto.SecretKey;

import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod;
import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType;

public class SettingsActivity extends BaseActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener{
    SettingsFragment fragment;

    SecretKey encryptionKey = null;
    boolean encryptionChanged = false;

    /* Activity result launchers (replace the request-code based onActivityResult()). */
    private final ActivityResultLauncher<Intent> authenticateLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();

                if (result.getResultCode() == RESULT_OK && data != null) {
                    byte[] authKey = data.getByteArrayExtra(Constants.EXTRA_AUTH_PASSWORD_KEY);
                    String newEnc = data.getStringExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION);

                    if (authKey != null && authKey.length > 0 && newEnc != null && !newEnc.isEmpty()) {
                        EncryptionType newEncType = EncryptionType.valueOf(newEnc);
                        tryEncryptionChange(newEncType, authKey);
                    } else {
                        Snackbar.make(findViewById(R.id.container_content), R.string.settings_toast_encryption_no_key, Snackbar.LENGTH_LONG).show();
                    }
                } else {
                    Snackbar.make(findViewById(R.id.container_content), R.string.settings_toast_encryption_auth_failed, Snackbar.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Intent> backupLocationLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent data = result.getData();

                if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null) {
                    Uri treeUri = data.getData();
                    // Both flags were requested in requestBackupAccess(); persist exactly those.
                    final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(treeUri, takeFlags);
                    settings.setBackupLocation(treeUri);
                }
            });

    // Android 13+ (API 33): the results of broadcast-triggered backups are reported through
    // notifications, which need the POST_NOTIFICATIONS runtime permission.
    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (!granted)
                    Snackbar.make(findViewById(R.id.container_content), R.string.settings_toast_notifications_denied, Snackbar.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.settings_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.inflate();

        Intent callingIntent = getIntent();
        byte[] keyMaterial = callingIntent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY);
        if (keyMaterial != null && keyMaterial.length > 0)
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial);

        if (savedInstanceState != null) {
            encryptionChanged = savedInstanceState.getBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false);

            byte[] encKey = savedInstanceState.getByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY);
            if (encKey != null) {
                encryptionKey = EncryptionHelper.generateSymmetricKey(encKey);
            }
        }

        fragment = new SettingsFragment();

        getFragmentManager().beginTransaction()
                .replace(R.id.container_content, fragment)
                .commit();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishWithResult();
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged);
        if (encryptionKey != null) {
            outState.putByteArray(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.getEncoded());
        }
    }

    public void finishWithResult() {
        Intent data = new Intent();

        data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, encryptionChanged);
        if (encryptionKey != null)
            data.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, encryptionKey.getEncoded());

        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finishWithResult();
        return true;
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        BackupManager backupManager = new BackupManager(this);
        backupManager.dataChanged();

        if (key == null)
            return;

        if (key.equals(getString(R.string.settings_key_theme)) ||
                key.equals(getString(R.string.settings_key_special_features)) ||
                key.equals(getString(R.string.settings_key_backup_location)) ||
                key.equals(getString(R.string.settings_key_theme_mode)) ||
                key.equals(getString(R.string.settings_key_theme_black_auto))) {
            recreate();
        } else if(key.equals(getString(R.string.settings_key_encryption))) {
            if (settings.getEncryption() != EncryptionType.PASSWORD) {
                if (settings.getAndroidBackupServiceEnabled()) {
                    UIHelper.showGenericDialog(this,
                        R.string.settings_dialog_title_android_sync,
                        R.string.settings_dialog_msg_android_sync_disabled_encryption
                    );
                }

                settings.setAndroidBackupServiceEnabled(false);
                if (fragment.useAndroidSync != null) {
                    fragment.useAndroidSync.setEnabled(false);
                    fragment.useAndroidSync.setChecked(false);
                }
            } else {
                if (fragment.useAndroidSync != null)
                    fragment.useAndroidSync.setEnabled(true);
            }
        } else if(key.equals(getString(R.string.settings_key_enable_android_backup_service))) {
            Log.d(SettingsActivity.class.getSimpleName(),
                    "onSharedPreferenceChanged called modifying settings_key_enable_android_backup_service service is now: " +
                    (settings.getAndroidBackupServiceEnabled() ? "enabled" : "disabled"));

            int message = settings.getAndroidBackupServiceEnabled() ? R.string.settings_toast_android_sync_enabled : R.string.settings_toast_android_sync_disabled;
            Snackbar.make(findViewById(R.id.container_content), message, Snackbar.LENGTH_SHORT).show();
        } else if (key.equals(getString(R.string.settings_key_backup_broadcasts))) {
            if (!settings.getBackupBroadcasts().isEmpty())
                ensureNotificationPermission();
        }
        fragment.updateAutoBackup();
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void generateNewEncryptionKey() {
        if (settings.getEncryption() == EncryptionType.KEYSTORE) {
            encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(this, false);
            encryptionChanged = true;
        }
    }

    private void tryEncryptionChangeWithAuth(EncryptionType newEnc) {
        Intent authIntent = new Intent(this, AuthenticateActivity.class);
        authIntent.putExtra(Constants.EXTRA_AUTH_NEW_ENCRYPTION, newEnc.name());
        authIntent.putExtra(Constants.EXTRA_AUTH_MESSAGE, R.string.auth_msg_confirm_encryption);
        authenticateLauncher.launch(authIntent);
    }

    private boolean tryEncryptionChange(EncryptionType newEnc, byte[] newKey) {
        Snackbar upgrading = Snackbar.make(findViewById(R.id.container_content), R.string.settings_toast_encryption_changing, Snackbar.LENGTH_LONG);
        //Toast upgrading = Toast.makeText(this, R.string.settings_toast_encryption_changing, Toast.LENGTH_LONG);
        upgrading.show();

        if (DatabaseHelper.backupDatabase(this)) {
            ArrayList<Entry> entries;

            if (encryptionKey != null)
                entries = DatabaseHelper.loadDatabase(this, encryptionKey);
            else
                entries = new ArrayList<>();

            SecretKey newEncryptionKey;

            if (newEnc == EncryptionType.KEYSTORE) {
                newEncryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(this, true);
            } else if (newKey != null && newKey.length > 0) {
                newEncryptionKey = EncryptionHelper.generateSymmetricKey(newKey);
            } else {
                upgrading.dismiss();
                DatabaseHelper.restoreDatabaseBackup(this);
                return false;
            }

            if (DatabaseHelper.saveDatabase(this, entries, newEncryptionKey)) {
                encryptionKey = newEncryptionKey;
                encryptionChanged = true;

                fragment.encryption.setValue(newEnc.name().toLowerCase());

                upgrading.dismiss();
                Snackbar.make(findViewById(R.id.container_content), R.string.settings_toast_encryption_change_success, Snackbar.LENGTH_LONG).show();

                return true;
            }

            DatabaseHelper.restoreDatabaseBackup(this);

            upgrading.dismiss();
            Snackbar.make(findViewById(R.id.container_content), R.string.settings_toast_encryption_change_failed, Snackbar.LENGTH_LONG).show();
        } else {
            upgrading.dismiss();
            Snackbar.make(findViewById(R.id.container_content), R.string.settings_toast_encryption_backup_failed, Snackbar.LENGTH_LONG).show();
        }

        return false;
    }

    private void requestBackupAccess() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        if (GeneralUtils.INSTANCE.isOreo() && settings.isBackupLocationSet())
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, settings.getBackupLocation());

        backupLocationLauncher.launch(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // OpenPgpKeyPreference (openpgp-api) still starts its key chooser with the request-code
        // based API, so this is the only result that has to be routed by hand.
        if (fragment != null && fragment.pgpSigningKey != null)
            fragment.pgpSigningKey.handleOnActivityResult(requestCode, resultCode, data);
    }

    public static class SettingsFragment extends PreferenceFragment {
        PreferenceCategory catUI;

        Settings settings;
        ListPreference encryption;
        ListPreference useAutoBackup;
        CheckBoxPreference useAndroidSync;

        EditTextPreference pgpEncryptionKey;
        OpenPgpKeyPreference pgpSigningKey;
        ListPreference themeMode;
        CheckBoxPreference themeBlack;
        ListPreference theme;

        public void encryptionChangeWithDialog(final EncryptionType encryptionType) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.settings_dialog_title_warning)
                    .setMessage(R.string.settings_dialog_msg_encryption_change)
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        if (encryptionType == EncryptionType.PASSWORD)
                            ((SettingsActivity) getActivity()).tryEncryptionChangeWithAuth(encryptionType);
                        else if (encryptionType == EncryptionType.KEYSTORE)
                            ((SettingsActivity) getActivity()).tryEncryptionChange(encryptionType, null);
                    })
                    .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
                    })
                    .setCancelable(false)
                    .create()
                    .show();
        }

        public void updateAutoBackup() {
            if (useAutoBackup != null) {
                useAutoBackup.setEnabled(BackupHelper.autoBackupType(getActivity()) == Constants.BackupType.ENCRYPTED);
                if (!useAutoBackup.isEnabled())
                    useAutoBackup.setValue(Constants.AutoBackup.OFF.toString().toLowerCase(Locale.ENGLISH));

                if (useAutoBackup.isEnabled()) {
                    useAutoBackup.setSummary(R.string.settings_desc_auto_backup_password_enc);
                } else {
                    useAutoBackup.setSummary(R.string.settings_desc_auto_backup_requirements);
                }
            }
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            settings = new Settings(getActivity());

            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());

            addPreferencesFromResource(R.xml.preferences);

            CredentialsPreference credentialsPreference = (CredentialsPreference) findPreference(getString(R.string.settings_key_auth));
            credentialsPreference.setEncryptionChangeCallback(newKey -> ((SettingsActivity) getActivity()).tryEncryptionChange(settings.getEncryption(), newKey));

            CheckBoxPreference blockAutofill = (CheckBoxPreference) findPreference(getString(R.string.settings_key_block_autofill));
            CheckBoxPreference autoUnlockAfterAutofill = (CheckBoxPreference) findPreference(getString(R.string.settings_key_auto_unlock_after_autofill));
            if (GeneralUtils.INSTANCE.isOreo()) {
                blockAutofill.setEnabled(true);
                blockAutofill.setSummary(R.string.settings_desc_block_autofill);
                autoUnlockAfterAutofill.setEnabled(true);
                autoUnlockAfterAutofill.setSummary(R.string.settings_desc_auto_unlock_after_autofill);
            } else {
                blockAutofill.setEnabled(false);
                blockAutofill.setSummary(R.string.settings_desc_autofill_requires_android_o);
                autoUnlockAfterAutofill.setEnabled(false);
                autoUnlockAfterAutofill.setSummary(R.string.settings_desc_autofill_requires_android_o);
            }

            // Authentication
            catUI = (PreferenceCategory) findPreference(getString(R.string.settings_key_cat_ui));
            encryption = (ListPreference) findPreference(getString(R.string.settings_key_encryption));
            themeMode = (ListPreference) findPreference(getString(R.string.settings_key_theme_mode));
            themeBlack = (CheckBoxPreference) findPreference(getString(R.string.settings_key_theme_black_auto));
            theme = (ListPreference) findPreference(getString(R.string.settings_key_theme));

            encryption.setOnPreferenceChangeListener((preference, o) -> {
                String newEncryption = (String) o;
                EncryptionType encryptionType = EncryptionType.valueOf(newEncryption.toUpperCase());
                EncryptionType oldEncryptionType = settings.getEncryption();
                AuthMethod authMethod = settings.getAuthMethod();

                if (encryptionType != oldEncryptionType) {
                    if (encryptionType == EncryptionType.PASSWORD) {
                        if (authMethod != AuthMethod.PASSWORD && authMethod != AuthMethod.PIN) {
                            UIHelper.showGenericDialog(getActivity(), R.string.settings_dialog_title_error, R.string.settings_dialog_msg_encryption_invalid_with_auth);
                            return false;
                        } else {
                            if (settings.getAuthCredentials().isEmpty()) {
                                UIHelper.showGenericDialog(getActivity(), R.string.settings_dialog_title_error, R.string.settings_dialog_msg_encryption_invalid_without_credentials);
                                return false;
                            }
                        }

                        encryptionChangeWithDialog(EncryptionType.PASSWORD);
                    } else if (encryptionType == EncryptionType.KEYSTORE) {
                        encryptionChangeWithDialog(EncryptionType.KEYSTORE);
                    }
                }

                return false;
            });

            // Backup location
            Preference backupLocation = findPreference(getString(R.string.settings_key_backup_location));

            if (settings.isBackupLocationSet()) {
                backupLocation.setSummary(R.string.settings_desc_backup_location_set);
            } else {
                backupLocation.setSummary(R.string.settings_desc_backup_location);
            }

            backupLocation.setOnPreferenceClickListener(preference -> {
                ((SettingsActivity) getActivity()).requestBackupAccess();
                return true;
            });

            // OpenPGP
            OpenPgpAppPreference pgpProvider = (OpenPgpAppPreference) findPreference(getString(R.string.settings_key_openpgp_provider));
            pgpEncryptionKey = (EditTextPreference) findPreference(getString(R.string.settings_key_openpgp_key_encrypt));
            pgpSigningKey = (OpenPgpKeyPreference) findPreference(getString(R.string.settings_key_openpgp_key_sign));

            pgpSigningKey.setOpenPgpProvider(pgpProvider.getValue());

            pgpEncryptionKey.setEnabled(pgpProvider.getValue() != null && !pgpProvider.getValue().isEmpty());

            pgpProvider.setOnPreferenceChangeListener((preference, newValue) -> {
                pgpEncryptionKey.setEnabled(newValue != null && !((String) newValue).isEmpty());
                pgpSigningKey.setOpenPgpProvider((String) newValue);

                return true;
            });

            useAutoBackup = (ListPreference) findPreference(getString(R.string.settings_key_auto_backup_password_enc));
            updateAutoBackup();

            useAndroidSync = (CheckBoxPreference) findPreference(getString(R.string.settings_key_enable_android_backup_service));
            useAndroidSync.setEnabled(settings.getEncryption() == EncryptionType.PASSWORD);
            if (!useAndroidSync.isEnabled())
                useAndroidSync.setChecked(false);

            if (sharedPref.contains(getString(R.string.settings_key_special_features)) &&
                    sharedPref.getBoolean(getString(R.string.settings_key_special_features), false)) {
                addPreferencesFromResource(R.xml.preferences_special);

                Preference clearKeyStore = findPreference(getString(R.string.settings_key_clear_keystore));
                clearKeyStore.setOnPreferenceClickListener(preference -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

                    builder.setTitle(R.string.settings_dialog_title_clear_keystore);
                    if (settings.getEncryption() == EncryptionType.PASSWORD)
                        builder.setMessage(R.string.settings_dialog_msg_clear_keystore_password);
                    else if (settings.getEncryption() == EncryptionType.KEYSTORE)
                        builder.setMessage(R.string.settings_dialog_msg_clear_keystore_keystore);

                    builder.setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        KeyStoreHelper.wipeKeys(getActivity());
                        if (settings.getEncryption() == EncryptionType.KEYSTORE) {
                            DatabaseHelper.wipeDatabase(getActivity());
                            ((SettingsActivity) getActivity()).generateNewEncryptionKey();
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
                    });

                    builder.setCancelable(false).create().show();
                    return false;
                });
            }

            // Remove Theme Mode selection option for devices below Android 10. Disable theme selection if Theme Mode is set auto
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                catUI.removePreference(themeMode);
                catUI.removePreference(themeBlack);
            } else {
                if (sharedPref.getString(getString(R.string.settings_key_theme_mode), getString(R.string.settings_default_theme_mode)).equals("auto"))
                    catUI.removePreference(theme);
                else
                    catUI.removePreference(themeBlack);
            }

            Preference clearCache = findPreference(getString(R.string.settings_key_clear_cache));
            clearCache.setOnPreferenceClickListener(preference -> {
                Intent clearCacheIntent = new Intent(getActivity(), CacheActivity.class);
                startActivity(clearCacheIntent);
                return false;
            });
        }
    }
}
