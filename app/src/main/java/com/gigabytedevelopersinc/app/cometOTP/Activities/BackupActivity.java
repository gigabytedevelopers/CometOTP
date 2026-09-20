package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.IntentCompat;
import androidx.core.os.BundleCompat;
import androidx.core.util.Consumer;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewStub;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import org.openintents.openpgp.OpenPgpError;
import org.openintents.openpgp.OpenPgpSignatureResult;
import org.openintents.openpgp.util.OpenPgpApi;
import org.openintents.openpgp.util.OpenPgpServiceConnection;
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.PasswordEntryDialog;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.EncryptedBackupTask;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.EncryptedRestoreTask;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.GenericBackupTask;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.GenericRestoreTask;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PGPBackupTask;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PGPRestoreTask;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PlainTextBackupTask;
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PlainTextRestoreTask;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.crypto.SecretKey;

public class BackupActivity extends BaseActivity {
    private final static String TAG = BackupActivity.class.getSimpleName();

    private static final String TAG_BACKUP_TASK_FRAGMENT = "BackupActivity.BackupTaskFragmentTag";
    private static final String TAG_RESTORE_TASK_FRAGMENT = "BackupActivity.RestoreTaskFragmentTag";

    private Constants.BackupType backupType = Constants.BackupType.ENCRYPTED;
    private SecretKey encryptionKey = null;

    private OpenPgpServiceConnection pgpServiceConnection;
    private String pgpEncryptionUserIDs;

    private Uri encryptTargetFile;
    private Uri decryptSourceFile;

    // The backup and restore forms live in bottom sheets; only one is open at a time.
    private BottomSheetDialog activeSheet;
    private MaterialButton sheetButton;
    private ProgressBar sheetProgress;
    private View sheetClose;
    private boolean sheetIsRestore = false;

    private boolean replaceExisting = false;
    private boolean restoreOldFormat = false;

    private boolean reload = false;
    private boolean allowExit = true;

    private enum PgpOperation { ENCRYPT, DECRYPT }

    private static final String STATE_ENCRYPT_TARGET = "BackupActivity.encryptTargetFile";
    private static final String STATE_DECRYPT_SOURCE = "BackupActivity.decryptSourceFile";

    /* Activity result launchers. One launcher per document flow replaces the request-code based
     * onActivityResult(); they are registered as fields so they survive activity recreation. */
    private final ActivityResultLauncher<Intent> openPlainLauncher = registerDocumentLauncher(this::doRestorePlain);
    private final ActivityResultLauncher<Intent> savePlainLauncher = registerDocumentLauncher(this::doBackupPlain);
    private final ActivityResultLauncher<Intent> openCryptLauncher = registerDocumentLauncher(uri -> doRestoreCrypt(uri, false));
    private final ActivityResultLauncher<Intent> openCryptOldLauncher = registerDocumentLauncher(uri -> doRestoreCrypt(uri, true));
    private final ActivityResultLauncher<Intent> saveCryptLauncher = registerDocumentLauncher(this::doBackupCrypt);
    private final ActivityResultLauncher<Intent> openPgpLauncher = registerDocumentLauncher(uri -> restoreEncryptedWithPGP(uri, null));
    private final ActivityResultLauncher<Intent> savePgpLauncher = registerDocumentLauncher(uri -> backupEncryptedWithPGP(uri, null));

    // OpenKeychain user interaction (key selection / passphrase) is driven through a PendingIntent
    private final ActivityResultLauncher<IntentSenderRequest> pgpEncryptLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK)
                    backupEncryptedWithPGP(encryptTargetFile, result.getData());
            });
    private final ActivityResultLauncher<IntentSenderRequest> pgpDecryptLauncher = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK)
                    restoreEncryptedWithPGP(decryptSourceFile, result.getData());
            });

    private ActivityResultLauncher<Intent> registerDocumentLauncher(Consumer<Uri> onDocumentSelected) {
        return registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() == RESULT_OK && data != null && data.getData() != null)
                        onDocumentSelected.accept(data.getData());
                });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.backup_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_backup);
        View v = stub.inflate();

        Intent callingIntent = getIntent();
        byte[] keyMaterial = callingIntent.getByteArrayExtra(Constants.EXTRA_BACKUP_ENCRYPTION_KEY);
        encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial);

        if (savedInstanceState != null) {
            encryptTargetFile = BundleCompat.getParcelable(savedInstanceState, STATE_ENCRYPT_TARGET, Uri.class);
            decryptSourceFile = BundleCompat.getParcelable(savedInstanceState, STATE_DECRYPT_SOURCE, Uri.class);
        }

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        // Leaving is blocked while a backup or restore task is running.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (allowExit)
                    finishWithResult();
            }
        });

        bindRow(v.findViewById(R.id.row_backup), R.drawable.ic_backup_cloud, R.string.backup_row_backup, this::showBackupSheet);
        bindRow(v.findViewById(R.id.row_restore), R.drawable.ic_restore_cloud, R.string.backup_row_restore, this::showRestoreSheet);

        backupType = settings.getDefaultBackupType();
    }

    private void bindRow(View row, int icon, int title, Runnable action) {
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.row_title)).setText(title);
        row.setOnClickListener(view -> action.run());
    }

    /* ------------------------------------------------------------------------------------------
     * Sheets
     * ------------------------------------------------------------------------------------------ */

    private BottomSheetDialog openSheet(int layoutRes, boolean restore) {
        if (activeSheet != null)
            activeSheet.dismiss();

        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(layoutRes);
        sheet.setOnDismissListener(d -> {
            if (activeSheet == d) {
                activeSheet = null;
                sheetButton = null;
                sheetProgress = null;
                sheetClose = null;
            }
        });
        activeSheet = sheet;
        sheetIsRestore = restore;
        sheetClose = sheet.findViewById(R.id.sheetClose);
        if (sheetClose != null)
            sheetClose.setOnClickListener(view -> {
                if (allowExit)
                    sheet.dismiss();
            });
        sheet.setCancelable(allowExit);
        return sheet;
    }

    private void bindTypeDropdown(MaterialAutoCompleteTextView dropdown) {
        String[] names = getResources().getStringArray(R.array.backup_encryption_names);
        dropdown.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, names));
        dropdown.setText(names[Math.min(backupType.ordinal(), names.length - 1)], false);
        dropdown.setOnItemClickListener((parent, view, position, id) -> setupBackupType(Constants.BackupType.values()[position]));
    }

    private void showBackupSheet() {
        BottomSheetDialog sheet = openSheet(R.layout.sheet_backup, false);

        MaterialAutoCompleteTextView type = sheet.findViewById(R.id.backupType);
        MaterialSwitch autoSync = sheet.findViewById(R.id.backup_auto_sync);
        sheetButton = sheet.findViewById(R.id.buttonBackup);
        sheetProgress = sheet.findViewById(R.id.progressBarBackup);

        if (type == null || autoSync == null || sheetButton == null)
            return;

        bindTypeDropdown(type);

        autoSync.setChecked(settings.getAutoBackupEncryptedFullEnabled());
        autoSync.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed())
                return;
            if (checked) {
                if (BackupHelper.autoBackupType(this) == Constants.BackupType.ENCRYPTED) {
                    settings.setAutoBackupEncrypted(Constants.AutoBackup.ALL_EDITS);
                } else {
                    button.setChecked(false);
                    Toast.makeText(this, R.string.backup_toast_auto_sync_requirements, Toast.LENGTH_LONG).show();
                }
            } else {
                settings.setAutoBackupEncrypted(Constants.AutoBackup.OFF);
            }
        });

        sheetButton.setOnClickListener(view -> {
            switch (backupType) {
                case PLAIN_TEXT:
                    backupPlainWithWarning();
                    break;
                case ENCRYPTED:
                    showSaveFileSelector(Constants.BACKUP_MIMETYPE_CRYPT, Constants.BackupType.ENCRYPTED, saveCryptLauncher, () -> doBackupCrypt(null));
                    break;
                case OPEN_PGP:
                    showSaveFileSelector(Constants.BACKUP_MIMETYPE_PGP, Constants.BackupType.OPEN_PGP, savePgpLauncher, () -> backupEncryptedWithPGP(null, null));
                    break;
            }
        });

        setupBackupType(backupType);
        sheet.show();
    }

    private void showRestoreSheet() {
        BottomSheetDialog sheet = openSheet(R.layout.sheet_restore, true);

        MaterialAutoCompleteTextView type = sheet.findViewById(R.id.restoreType);
        MaterialSwitch replace = sheet.findViewById(R.id.backup_replace);
        com.google.android.material.checkbox.MaterialCheckBox oldFormat = sheet.findViewById(R.id.restoreOldCrypt);
        TextView description = sheet.findViewById(R.id.restoreDescription);
        sheetButton = sheet.findViewById(R.id.buttonRestore);
        sheetProgress = sheet.findViewById(R.id.progressBarRestore);

        if (type == null || replace == null || oldFormat == null || sheetButton == null)
            return;

        bindTypeDropdown(type);
        if (description != null)
            description.setText(R.string.backup_desc_restore_format);

        replace.setChecked(replaceExisting);
        replace.setOnCheckedChangeListener((button, checked) -> replaceExisting = checked);

        oldFormat.setChecked(restoreOldFormat);
        oldFormat.setOnCheckedChangeListener((button, checked) -> restoreOldFormat = checked);

        sheetButton.setOnClickListener(view -> {
            switch (backupType) {
                case PLAIN_TEXT:
                    showOpenFileSelector(openPlainLauncher);
                    break;
                case ENCRYPTED:
                    if (restoreOldFormat)
                        showOpenFileSelector(openCryptOldLauncher);
                    else
                        showOpenFileSelector(openCryptLauncher);
                    break;
                case OPEN_PGP:
                    showOpenFileSelector(openPgpLauncher);
                    break;
            }
        });

        setupBackupType(backupType);
        sheet.show();
    }

    private void setSheetLoading(boolean loading) {
        if (sheetButton != null) {
            sheetButton.setEnabled(!loading);
            sheetButton.setText(loading ? "" : getString(sheetIsRestore ? R.string.backup_button_restore_short : R.string.backup_button_backup));
        }
        if (sheetProgress != null)
            sheetProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (sheetClose != null)
            sheetClose.setEnabled(!loading);
        if (activeSheet != null)
            activeSheet.setCancelable(!loading);
    }

    private void setupBackupType(Constants.BackupType type) {
        TextView description = activeSheet != null ? activeSheet.findViewById(R.id.backupDescription) : null;
        TextView warning = activeSheet != null
                ? activeSheet.findViewById(sheetIsRestore ? R.id.restoreErrorLabel : R.id.backupErrorLabel) : null;
        View oldFormat = activeSheet != null ? activeSheet.findViewById(R.id.restoreOldCrypt) : null;

        boolean enabled = true;
        int warningRes = 0;

        switch (type) {
            case PLAIN_TEXT:
                if (description != null)
                    description.setText(R.string.backup_desc_plain_short);
                break;
            case ENCRYPTED:
                if (description != null)
                    description.setText(R.string.backup_desc_crypt_short);
                break;
            case OPEN_PGP:
                if (description != null)
                    description.setText(R.string.backup_desc_pgp_short);

                String PGPProvider = settings.getOpenPGPProvider();
                pgpEncryptionUserIDs = settings.getOpenPGPEncryptionUserIDs();

                if (TextUtils.isEmpty(PGPProvider)) {
                    warningRes = R.string.backup_desc_openpgp_provider;
                    enabled = false;
                } else if (TextUtils.isEmpty(pgpEncryptionUserIDs)){
                    warningRes = R.string.backup_desc_openpgp_keyid;
                    enabled = false;
                } else {
                    pgpServiceConnection = new OpenPgpServiceConnection(BackupActivity.this.getApplicationContext(), PGPProvider);
                    pgpServiceConnection.bindToService();
                }

                break;
        }

        if (warning != null) {
            if (warningRes != 0) {
                warning.setText(warningRes);
                warning.setVisibility(View.VISIBLE);
            } else {
                warning.setVisibility(View.GONE);
            }
        }

        if (oldFormat != null)
            oldFormat.setVisibility(type == Constants.BackupType.ENCRYPTED ? View.VISIBLE : View.GONE);

        if (sheetButton != null && allowExit)
            sheetButton.setEnabled(enabled);

        backupType = type;
        settings.setDefaultBackupType(type);
    }

    // End with a result
    public void finishWithResult() {
        Intent data = new Intent();
        data.putExtra("reload", reload);
        setResult(RESULT_OK, data);
        finish();
    }

    // Go back to the main activity
    @Override
    public boolean onSupportNavigateUp() {
        if (allowExit)
            finishWithResult();

        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_ENCRYPT_TARGET, encryptTargetFile);
        outState.putParcelable(STATE_DECRYPT_SOURCE, decryptSourceFile);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (pgpServiceConnection != null)
            pgpServiceConnection.unbindFromService();
    }

    // TODO: Show more information about the finished backup (e.g. a notification with the file name)
    private void notifyBackupState(int msgId) {
        Toast.makeText(this, msgId, Toast.LENGTH_LONG).show();
    }

    private void handleBackupTaskResult(GenericBackupTask.BackupTaskResult result) {
        showBackupProgress(false);

        if (result.messageId != 0)
            notifyBackupState(result.messageId);
        else
        if (!result.success)
            notifyBackupState(R.string.backup_toast_export_failed);

        // Clean up the task fragment
        BackupTaskFragment backupTaskFragment = findBackupTaskFragment();
        if (backupTaskFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(backupTaskFragment)
                    .commit();
        }

        if (result.success) {
            dismissSheet();
            ResultDialog.showSuccess(this, R.drawable.ic_backup_cloud,
                    R.string.backup_result_created_title, R.string.backup_result_created_msg,
                    R.string.continue_on, this::finishWithResult);
        }
    }

    private void handleRestoreTaskResult(GenericRestoreTask.RestoreTaskResult result) {
        if (result.success) {
            if (result.isPGP) {
                InputStream is = new ByteArrayInputStream(result.payload.getBytes(StandardCharsets.UTF_8));
                ByteArrayOutputStream os = new ByteArrayOutputStream();

                OpenPgpApi api = new OpenPgpApi(this, pgpServiceConnection.getService());
                Intent resultIntent = api.executeApi(result.decryptIntent, is, os);

                handleOpenPGPResult(resultIntent, os, result.uri, PgpOperation.DECRYPT);
            } else {
                restoreEntries(result.payload, false);
            }
        } else {
            if (result.messageId != 0)
                notifyBackupState(result.messageId);
            else
                notifyBackupState(R.string.backup_toast_import_failed);
        }

        showRestoreProgress(false);

        // Clean up the task fragment
        RestoreTaskFragment restoreTaskFragment = findRestoreTaskFragment();
        if (restoreTaskFragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .remove(restoreTaskFragment)
                    .commit();
        }

        if (result.success && !result.isPGP)
            showRestoreSuccess();
    }

    private void showRestoreSuccess() {
        dismissSheet();
        ResultDialog.showSuccess(this, R.drawable.ic_restore_cloud,
                R.string.backup_result_restored_title, R.string.backup_result_restored_msg,
                R.string.continue_on, this::finishWithResult);
    }

    private void toggleInProgressMode(boolean running) {
        allowExit = !running;
        setSheetLoading(running);
    }

    private void showBackupProgress(boolean running) {
        toggleInProgressMode(running);
    }

    private void showRestoreProgress(boolean running) {
        toggleInProgressMode(running);
    }

    private void dismissSheet() {
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
    }

    /* Generic functions for all backup/restore options */

    private void showOpenFileSelector(ActivityResultLauncher<Intent> launcher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        try {
            launcher.launch(intent);
            return;
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "Failed to use ACTION_OPEN_DOCUMENT, no matching activity found!");
        }

        intent.setAction(Intent.ACTION_GET_CONTENT);

        try {
            launcher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Log.d(TAG, "Failed to use ACTION_GET_CONTENT, no matching activity found!");
            Toast.makeText(this, R.string.backup_toast_file_selection_failed, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * @param launcher         receives the document created by the system file picker
     * @param backupToLocation runs the backup into the configured backup location instead,
     *                         when the user chose not to be asked for a file every time
     */
    private void showSaveFileSelector(String mimeType, Constants.BackupType backupType,
                                      ActivityResultLauncher<Intent> launcher, Runnable backupToLocation) {
        if (settings.getBackupAsk()) {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mimeType);
            intent.putExtra(Intent.EXTRA_TITLE, BackupHelper.backupFilename(this, backupType));
            launcher.launch(intent);
        } else {
            if (settings.isBackupLocationSet()) {
                backupToLocation.run();
            } else {
                Toast.makeText(this, R.string.backup_toast_no_location, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void restoreEntries(String text, boolean finish) {
        ArrayList<Entry> entries = DatabaseHelper.stringToEntries(text);

        if (entries.size() > 0) {
            if (! replaceExisting) {
                ArrayList<Entry> currentEntries = DatabaseHelper.loadDatabase(this, encryptionKey);

                entries.removeAll(currentEntries);
                entries.addAll(currentEntries);
            }

            if (DatabaseHelper.saveDatabase(this, entries, encryptionKey)) {
                reload = true;

                if (finish)
                    showRestoreSuccess();
                else
                    Toast.makeText(this, R.string.backup_toast_import_success, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.backup_toast_import_save_failed, Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(this, R.string.backup_toast_import_no_entries, Toast.LENGTH_LONG).show();
        }
    }

    /* Plain-text backup functions */

    private void doRestorePlain(Uri uri) {
        if (Tools.isExternalStorageReadable()) {
            PlainTextRestoreTask task = new PlainTextRestoreTask(this, uri);
            task.setCallback(this::handleRestoreTaskResult);

            startRestoreTask(task);
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show();
        }
    }

    private void doBackupPlain(Uri uri) {
        if (Tools.isExternalStorageWritable()) {
            ArrayList<Entry> entries = DatabaseHelper.loadDatabase(this, encryptionKey);

            PlainTextBackupTask task = new PlainTextBackupTask(this, entries, uri);
            task.setCallback(this::handleBackupTaskResult);

            startBackupTask(task);
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show();
        }
    }

    private void backupPlainWithWarning() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(R.string.backup_dialog_title_security_warning)
                .setMessage(R.string.backup_dialog_msg_export_warning)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showSaveFileSelector(Constants.BACKUP_MIMETYPE_PLAIN, Constants.BackupType.PLAIN_TEXT, savePlainLauncher, () -> doBackupPlain(null));
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {}
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .create()
                .show();
    }

    /* Encrypted backup functions */

    private void doRestoreCrypt(final Uri uri, final boolean old_format) {
        String password = settings.getBackupPasswordEnc();

        if (password.isEmpty()) {
            PasswordEntryDialog pwDialog = new PasswordEntryDialog(this, PasswordEntryDialog.Mode.ENTER, settings.getBlockAccessibility(), settings.getBlockAutofill(), new PasswordEntryDialog.PasswordEnteredCallback() {
                @Override
                public void onPasswordEntered(String newPassword) {
                    doRestoreCryptWithPassword(uri, newPassword, old_format);
                }
            });
            pwDialog.show();
        } else {
            doRestoreCryptWithPassword(uri, password, old_format);
        }
    }

    private void doRestoreCryptWithPassword(Uri uri, String password, boolean old_format) {
        if (Tools.isExternalStorageReadable()) {
            EncryptedRestoreTask task = new EncryptedRestoreTask(this, uri, password, old_format);
            task.setCallback(this::handleRestoreTaskResult);

            startRestoreTask(task);
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show();
        }
    }

    private void doBackupCrypt(final Uri uri) {
        String password = settings.getBackupPasswordEnc();

        if (password.isEmpty()) {
            PasswordEntryDialog pwDialog = new PasswordEntryDialog(this, PasswordEntryDialog.Mode.UPDATE, settings.getBlockAccessibility(), settings.getBlockAutofill(), new PasswordEntryDialog.PasswordEnteredCallback() {
                @Override
                public void onPasswordEntered(String newPassword) {
                    doBackupCryptWithPassword(uri, newPassword);
                }
            });
            pwDialog.show();
        } else {
            doBackupCryptWithPassword(uri, password);
        }
    }

    private void doBackupCryptWithPassword(Uri uri, String password) {
        if (Tools.isExternalStorageWritable()) {
            ArrayList<Entry> entries = DatabaseHelper.loadDatabase(this, encryptionKey);

            EncryptedBackupTask task = new EncryptedBackupTask(this, entries, password, uri);
            task.setCallback(this::handleBackupTaskResult);

            startBackupTask(task);
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show();
        }
    }

    /* OpenPGP backup functions */

    private void restoreEncryptedWithPGP(Uri uri, Intent decryptIntent) {
        if (decryptIntent == null)
            decryptIntent = new Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY);

        PGPRestoreTask task = new PGPRestoreTask(this, uri, decryptIntent);
        task.setCallback(this::handleRestoreTaskResult);

        startRestoreTask(task);
    }

    private void doBackupEncrypted(Uri uri, String data) {
        if (Tools.isExternalStorageWritable()) {
            PGPBackupTask task = new PGPBackupTask(this, data, uri);
            task.setCallback(this::handleBackupTaskResult);

            startBackupTask(task);
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show();
        }
    }

    private void backupEncryptedWithPGP(Uri uri, Intent encryptIntent) {
        ArrayList<Entry> entries = DatabaseHelper.loadDatabase(this, encryptionKey);
        String plainJSON = DatabaseHelper.entriesToString(entries);

        if (encryptIntent == null) {
            encryptIntent = new Intent();

            if (settings.getOpenPGPSigningKey() != 0) {
                encryptIntent.setAction(OpenPgpApi.ACTION_SIGN_AND_ENCRYPT);
                encryptIntent.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, settings.getOpenPGPSigningKey());
            } else {
                encryptIntent.setAction(OpenPgpApi.ACTION_ENCRYPT);
            }

            encryptIntent.putExtra(OpenPgpApi.EXTRA_USER_IDS, pgpEncryptionUserIDs.split(","));
            encryptIntent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true);
        }

        InputStream is = new ByteArrayInputStream(plainJSON.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        OpenPgpApi api = new OpenPgpApi(this, pgpServiceConnection.getService());
        Intent result = api.executeApi(encryptIntent, is, os);
        handleOpenPGPResult(result, os, uri, PgpOperation.ENCRYPT);
    }

    public String outputStreamToString(ByteArrayOutputStream os) {
        return new String(os.toByteArray(), StandardCharsets.UTF_8);
    }

    private void handleOpenPGPResult(Intent result, ByteArrayOutputStream os, Uri file, PgpOperation operation) {
        if (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR) == OpenPgpApi.RESULT_CODE_SUCCESS) {
            if (operation == PgpOperation.ENCRYPT) {
                if (os != null)
                    doBackupEncrypted(file, outputStreamToString(os));
            } else if (operation == PgpOperation.DECRYPT) {
                if (os != null) {
                    if (settings.getOpenPGPVerify()) {
                        OpenPgpSignatureResult sigResult = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_SIGNATURE, OpenPgpSignatureResult.class);

                        assert sigResult != null;
                        if (sigResult.getResult() == OpenPgpSignatureResult.RESULT_VALID_KEY_CONFIRMED) {
                            restoreEntries(outputStreamToString(os), true);
                        } else {
                            Toast.makeText(this, R.string.backup_toast_openpgp_not_verified, Toast.LENGTH_LONG).show();
                        }
                    } else {
                        restoreEntries(outputStreamToString(os), true);
                    }
                }
            }
        } else if (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR) == OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED) {
            PendingIntent pi = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_INTENT, PendingIntent.class);
            if (pi == null)
                return;

            // Remember the target file across the user interaction (also survives recreation, see
            // onSaveInstanceState) and continue the same operation once the user is done.
            IntentSenderRequest request = new IntentSenderRequest.Builder(pi.getIntentSender()).build();
            if (operation == PgpOperation.ENCRYPT) {
                encryptTargetFile = file;
                pgpEncryptLauncher.launch(request);
            } else if (operation == PgpOperation.DECRYPT) {
                decryptSourceFile = file;
                pgpDecryptLauncher.launch(request);
            }
        } else if (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR) == OpenPgpApi.RESULT_CODE_ERROR) {
            OpenPgpError error = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_ERROR, OpenPgpError.class);
            String message = error != null ? error.getMessage() : "";
            Toast.makeText(this, String.format(getString(R.string.backup_toast_openpgp_error), message), Toast.LENGTH_LONG).show();
        }
    }

    @Nullable
    private BackupTaskFragment findBackupTaskFragment() {
        return (BackupTaskFragment) getSupportFragmentManager().findFragmentByTag(TAG_BACKUP_TASK_FRAGMENT);
    }

    @Nullable
    private RestoreTaskFragment findRestoreTaskFragment() {
        return (RestoreTaskFragment) getSupportFragmentManager().findFragmentByTag(TAG_RESTORE_TASK_FRAGMENT);
    }

    private void startBackupTask(GenericBackupTask task) {
        BackupTaskFragment backupTaskFragment = findBackupTaskFragment();
        RestoreTaskFragment restoreTaskFragment = findRestoreTaskFragment();

        // Don't start a task if we already have an active task running (backup or restore).
        if ((backupTaskFragment == null || backupTaskFragment.task.isCanceled()) && (restoreTaskFragment == null || restoreTaskFragment.task.isCanceled())) {
            if (backupTaskFragment == null) {
                backupTaskFragment = new BackupTaskFragment();
                getSupportFragmentManager()
                        .beginTransaction()
                        .add(backupTaskFragment, TAG_BACKUP_TASK_FRAGMENT)
                        .commit();
            }

            backupTaskFragment.startTask(task);

            showBackupProgress(true);
        }
    }

    private void startRestoreTask(GenericRestoreTask task) {
        BackupTaskFragment backupTaskFragment = findBackupTaskFragment();
        RestoreTaskFragment restoreTaskFragment = findRestoreTaskFragment();

        // Don't start a task if we already have an active task running (backup or restore).
        if ((backupTaskFragment == null || backupTaskFragment.task.isCanceled()) && (restoreTaskFragment == null || restoreTaskFragment.task.isCanceled())) {
            if (restoreTaskFragment == null) {
                restoreTaskFragment = new RestoreTaskFragment();
                getSupportFragmentManager()
                        .beginTransaction()
                        .add(restoreTaskFragment, TAG_RESTORE_TASK_FRAGMENT)
                        .commit();
            }

            restoreTaskFragment.startTask(task);

            showRestoreProgress(true);
        }
    }

    private void checkBackgroundBackupTask() {
        BackupTaskFragment backupTaskFragment = findBackupTaskFragment();

        if (backupTaskFragment != null) {
            if (backupTaskFragment.task.isCanceled()) {
                // The task was canceled or has finished, so remove the task fragment.
                getSupportFragmentManager().beginTransaction()
                        .remove(backupTaskFragment)
                        .commit();
            } else {
                backupTaskFragment.task.setCallback(this::handleBackupTaskResult);
                showBackupProgress(true);
            }
        }

    }

    private void checkBackgroundRestoreTask() {
        RestoreTaskFragment restoreTaskFragment = findRestoreTaskFragment();

        if (restoreTaskFragment != null) {
            if (restoreTaskFragment.task.isCanceled()) {
                // The task was canceled or has finished, so remove the task fragment.
                getSupportFragmentManager().beginTransaction()
                        .remove(restoreTaskFragment)
                        .commit();
            } else {
                restoreTaskFragment.task.setCallback(this::handleRestoreTaskResult);
                showRestoreProgress(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // We don't want the task to callback to a dead activity and cause a memory leak, so null it here.
        BackupTaskFragment backupTaskFragment = findBackupTaskFragment();
        RestoreTaskFragment restoreTaskFragment = findRestoreTaskFragment();

        if (backupTaskFragment != null)
            backupTaskFragment.task.setCallback(null);

        if (restoreTaskFragment != null)
            restoreTaskFragment.task.setCallback(null);
    }

    @Override
    public void onResume() {
        super.onResume();

        checkBackgroundBackupTask();
        checkBackgroundRestoreTask();
    }

    @Override
    protected boolean shouldDestroyOnScreenOff() {
        return allowExit;   // Don't destroy the backup activity as long as a backup task is running
    }

    /** Retained instance fragment to hold a running {@link GenericBackupTask} between configuration changes.*/
    public static class BackupTaskFragment extends Fragment {
        GenericBackupTask task;

        public BackupTaskFragment() {
            super();
            setRetainInstance(true);
        }

        public void startTask(@NonNull GenericBackupTask task) {
            this.task = task;
            this.task.execute();
        }
    }

    /** Retained instance fragment to hold a running {@link GenericRestoreTask} between configuration changes.*/
    public static class RestoreTaskFragment extends Fragment {
        GenericRestoreTask task;

        public RestoreTaskFragment() {
            super();
            setRetainInstance(true);
        }

        public void startTask(@NonNull GenericRestoreTask task) {
            this.task = task;
            this.task.execute();
        }
    }
}