package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.app.backup.BackupManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.widget.Toolbar;
import android.view.ViewStub;
import android.widget.TextView;

import org.openintents.openpgp.util.OpenPgpAppPreference;
import org.openintents.openpgp.util.OpenPgpKeyPreference;
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Preferences.CredentialsPreference;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper;

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

        fragment = new SettingsFragment();

        getFragmentManager().beginTransaction()
                .replace(R.id.container_content, fragment)
                .commit();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
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

    @Override
    public void onBackPressed() {
        finishWithResult();
        super.onBackPressed();
    }

    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        BackupManager backupManager = new BackupManager(this);
        backupManager.dataChanged();

        if (key.equals(getString(R.string.settings_key_theme)) ||
                key.equals(getString(R.string.settings_key_special_features)) ||
                key.equals(getString(R.string.settings_key_theme_mode))) {
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
        }

        if (fragment.useAutoBackup != null) {
            fragment.useAutoBackup.setEnabled(BackupHelper.autoBackupType(this) == Constants.BackupType.ENCRYPTED);
            if (!fragment.useAutoBackup.isEnabled())
                fragment.useAutoBackup.setValue(Constants.AutoBackup.OFF.toString().toLowerCase(Locale.ENGLISH));
        }
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
        startActivityForResult(authIntent, Constants.INTENT_SETTINGS_AUTHENTICATE);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.INTENT_SETTINGS_AUTHENTICATE) {
            if (resultCode == RESULT_OK) {
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
        } else {
            fragment.pgpSigningKey.handleOnActivityResult(requestCode, resultCode, data);
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        PreferenceCategory catSecurity;
        PreferenceCategory catUI;

        Settings settings;
        ListPreference encryption;
        ListPreference useAutoBackup;
        CheckBoxPreference useAndroidSync;

        OpenPgpAppPreference pgpProvider;
        EditTextPreference pgpEncryptionKey;
        OpenPgpKeyPreference pgpSigningKey;
        ListPreference themeMode;
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

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            settings = new Settings(getActivity());

            final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity().getBaseContext());

            addPreferencesFromResource(R.xml.preferences);

            CredentialsPreference credentialsPreference = (CredentialsPreference) findPreference(getString(R.string.settings_key_auth));
            credentialsPreference.setEncryptionChangeCallback(newKey -> ((SettingsActivity) getActivity()).tryEncryptionChange(settings.getEncryption(), newKey));

            // Authentication
            catSecurity = (PreferenceCategory) findPreference(getString(R.string.settings_key_cat_security));
            catUI = (PreferenceCategory) findPreference(getString(R.string.settings_key_cat_ui));
            encryption = (ListPreference) findPreference(getString(R.string.settings_key_encryption));
            themeMode = (ListPreference) findPreference(getString(R.string.settings_key_theme_mode));
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

            // OpenPGP
            pgpProvider = (OpenPgpAppPreference) findPreference(getString(R.string.settings_key_openpgp_provider));
            pgpEncryptionKey = (EditTextPreference) findPreference(getString(R.string.settings_key_openpgp_key_encrypt));
            pgpSigningKey = (OpenPgpKeyPreference) findPreference(getString(R.string.settings_key_openpgp_key_sign));

            pgpSigningKey.setOpenPgpProvider(pgpProvider.getValue());

            if (pgpProvider.getValue() != null && ! pgpProvider.getValue().isEmpty()) {
                pgpEncryptionKey.setEnabled(true);
            } else {
                pgpEncryptionKey.setEnabled(false);
            }

            pgpProvider.setOnPreferenceChangeListener((preference, newValue) -> {
                if (newValue != null && ! ((String) newValue).isEmpty()) {
                    pgpEncryptionKey.setEnabled(true);
                } else {
                    pgpEncryptionKey.setEnabled(false);
                }

                pgpSigningKey.setOpenPgpProvider((String) newValue);

                return true;
            });

            useAutoBackup = (ListPreference) findPreference(getString(R.string.settings_key_auto_backup_password_enc));
            useAutoBackup.setEnabled(BackupHelper.autoBackupType(getActivity()) == Constants.BackupType.ENCRYPTED);
            if(!useAutoBackup.isEnabled())
                useAutoBackup.setValue(Constants.AutoBackup.OFF.toString().toLowerCase(Locale.ENGLISH));

            useAndroidSync = (CheckBoxPreference) findPreference(getString(R.string.settings_key_enable_android_backup_service));
            useAndroidSync.setEnabled(settings.getEncryption() == EncryptionType.PASSWORD);
            if(!useAndroidSync.isEnabled())
                useAndroidSync.setChecked(false);

            if (sharedPref.contains(getString(R.string.settings_key_special_features)) &&
                    sharedPref.getBoolean(getString(R.string.settings_key_special_features), false)) {
                addPreferencesFromResource(R.xml.preferences_special);

                Preference clearKeyStore = findPreference(getString(R.string.settings_key_clear_keystore));
                clearKeyStore.setOnPreferenceClickListener(preference -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    TextView title = new TextView(getActivity());
                    title.setText(R.string.settings_dialog_title_clear_keystore);
                    title.setTextColor(getResources().getColor(R.color.colorPrimary));
                    title.setPadding(50, 50, 0, 0);
                    title.setTextSize(20);
                    builder.setCustomTitle(title);
                    //builder.setTitle(R.string.settings_dialog_title_clear_keystore);
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

            //Remove Theme Mode selection option for devices below Android 10. Disable theme selection if Theme Mode is set auto
            //TODO: 29 needs to be replaced with VERSION_CODE.Q when compileSdk and targetSdk is updated to 29
            if(Build.VERSION.SDK_INT < 29) {
                catUI.removePreference(themeMode);
            } else {
                if(sharedPref.getString(getString(R.string.settings_key_theme_mode),getString(R.string.settings_default_theme_mode)).equals("auto")) {
                    theme.setEnabled(false);
                } else {
                    theme.setEnabled(true);
                }
            }

            Preference clearCache = findPreference(getString(R.string.settings_key_clear_cache));
            clearCache.setOnPreferenceClickListener(preference -> {
                Intent whatsnewIntent = new Intent(getActivity(), CacheActivity.class);
                startActivityForResult(whatsnewIntent, Constants.INTENT_MAIN_CLEARCACHE);
                return false;
            });
        }
    }
}
