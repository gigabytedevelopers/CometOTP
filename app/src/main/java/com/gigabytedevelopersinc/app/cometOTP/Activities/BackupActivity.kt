@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewStub
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.PasswordEntryDialog
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Tasks.EncryptedBackupTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.EncryptedRestoreTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.GenericBackupTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.GenericRestoreTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PGPBackupTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PGPRestoreTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PlainTextBackupTask
import com.gigabytedevelopersinc.app.cometOTP.Tasks.PlainTextRestoreTask
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.OpenPgpSignatureResult
import org.openintents.openpgp.util.OpenPgpApi
import org.openintents.openpgp.util.OpenPgpServiceConnection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.regex.Pattern
import javax.crypto.SecretKey

class BackupActivity : BaseActivity() {

    private var backupType = Constants.BackupType.ENCRYPTED
    private var encryptionKey: SecretKey? = null

    private var pgpServiceConnection: OpenPgpServiceConnection? = null
    private var pgpEncryptionUserIDs: String? = null

    private var encryptTargetFile: Uri? = null
    private var decryptSourceFile: Uri? = null

    // The backup and restore forms live in bottom sheets; only one is open at a time.
    private var activeSheet: BottomSheetDialog? = null
    private var sheetButton: MaterialButton? = null
    private var sheetProgress: ProgressBar? = null
    private var sheetClose: View? = null
    private var sheetIsRestore = false

    private var replaceExisting = false
    private var restoreOldFormat = false

    private var reload = false
    private var allowExit = true

    private enum class PgpOperation { ENCRYPT, DECRYPT }

    /* Activity result launchers. One launcher per document flow replaces the request-code based
     * onActivityResult(); they are registered as fields so they survive activity recreation. */
    // Registration order matters: the result registry keys launchers by the order they are
    // registered in, so it must match between the old and the recreated activity.
    private val openPlainLauncher = registerDocumentLauncher(::doRestorePlain)
    private val savePlainLauncher = registerDocumentLauncher(::doBackupPlain)
    private val openCryptLauncher = registerDocumentLauncher { uri -> doRestoreCrypt(uri, false) }
    private val openCryptOldLauncher = registerDocumentLauncher { uri -> doRestoreCrypt(uri, true) }
    private val saveCryptLauncher = registerDocumentLauncher(::doBackupCrypt)
    private val openPgpLauncher = registerDocumentLauncher { uri -> restoreEncryptedWithPGP(uri, null) }
    private val savePgpLauncher = registerDocumentLauncher { uri -> backupEncryptedWithPGP(uri, null) }

    // OpenKeychain user interaction (key selection / passphrase) is driven through a PendingIntent
    private val pgpEncryptLauncher: ActivityResultLauncher<IntentSenderRequest> = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK)
            backupEncryptedWithPGP(encryptTargetFile, result.data)
    }
    private val pgpDecryptLauncher: ActivityResultLauncher<IntentSenderRequest> = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == RESULT_OK)
            restoreEncryptedWithPGP(decryptSourceFile, result.data)
    }

    private fun registerDocumentLauncher(onDocumentSelected: (Uri) -> Unit): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null && data.data != null)
                onDocumentSelected(data.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without the database key there is nothing to back up (the database cannot be read) and
        // nowhere to restore to (it cannot be written), e.g. when this is opened before the
        // database was unlocked. Leave with the usual message instead of crashing.
        val keyMaterial = intent.getByteArrayExtra(Constants.EXTRA_BACKUP_ENCRYPTION_KEY)
        if (keyMaterial == null) {
            Toast.makeText(this, R.string.toast_encryption_key_empty, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

        setTitle(R.string.backup_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_backup
        val v = stub.inflate()

        if (savedInstanceState != null) {
            encryptTargetFile = BundleCompat.getParcelable(savedInstanceState, STATE_ENCRYPT_TARGET, Uri::class.java)
            decryptSourceFile = BundleCompat.getParcelable(savedInstanceState, STATE_DECRYPT_SOURCE, Uri::class.java)
        }

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        // Leaving is blocked while a backup or restore task is running.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (allowExit)
                    finishWithResult()
            }
        })

        bindRow(v.findViewById(R.id.row_backup), R.drawable.ic_backup_cloud, R.string.backup_row_backup, ::showBackupSheet)
        bindRow(v.findViewById(R.id.row_restore), R.drawable.ic_restore_cloud, R.string.backup_row_restore, ::showRestoreSheet)

        backupType = settings.defaultBackupType
    }

    private fun bindRow(row: View, icon: Int, title: Int, action: () -> Unit) {
        row.findViewById<ImageView>(R.id.row_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.row_title).setText(title)
        row.setOnClickListener { action() }
    }

    /* ------------------------------------------------------------------------------------------
     * Sheets
     * ------------------------------------------------------------------------------------------ */

    private fun openSheet(layoutRes: Int, restore: Boolean): BottomSheetDialog {
        activeSheet?.dismiss()

        val sheet = BottomSheetDialog(this)
        sheet.setContentView(layoutRes)
        sheet.setOnDismissListener { d ->
            if (activeSheet === d) {
                activeSheet = null
                sheetButton = null
                sheetProgress = null
                sheetClose = null
            }
        }
        activeSheet = sheet
        sheetIsRestore = restore
        val close = sheet.findViewById<View>(R.id.sheetClose)
        sheetClose = close
        close?.setOnClickListener {
            if (allowExit)
                sheet.dismiss()
        }
        sheet.setCancelable(allowExit)
        return sheet
    }

    private fun bindTypeDropdown(dropdown: MaterialAutoCompleteTextView) {
        val names = resources.getStringArray(R.array.backup_encryption_names)
        dropdown.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, names))
        dropdown.setText(names[Math.min(backupType.ordinal, names.size - 1)], false)
        dropdown.setOnItemClickListener { _, _, position, _ -> setupBackupType(Constants.BackupType.values()[position]) }
    }

    private fun showBackupSheet() {
        val sheet = openSheet(R.layout.sheet_backup, false)

        val type = sheet.findViewById<MaterialAutoCompleteTextView>(R.id.backupType)
        val autoSync = sheet.findViewById<MaterialSwitch>(R.id.backup_auto_sync)
        val button = sheet.findViewById<MaterialButton>(R.id.buttonBackup)
        sheetButton = button
        sheetProgress = sheet.findViewById(R.id.progressBarBackup)

        if (type == null || autoSync == null || button == null)
            return

        bindTypeDropdown(type)

        autoSync.isChecked = settings.autoBackupEncryptedFullEnabled
        autoSync.setOnCheckedChangeListener { compoundButton, checked ->
            if (!compoundButton.isPressed)
                return@setOnCheckedChangeListener
            if (checked) {
                if (BackupHelper.autoBackupType(this) == Constants.BackupType.ENCRYPTED) {
                    settings.setAutoBackupEncrypted(Constants.AutoBackup.ALL_EDITS)
                } else {
                    compoundButton.isChecked = false
                    Toast.makeText(this, R.string.backup_toast_auto_sync_requirements, Toast.LENGTH_LONG).show()
                }
            } else {
                settings.setAutoBackupEncrypted(Constants.AutoBackup.OFF)
            }
        }

        button.setOnClickListener {
            when (backupType) {
                Constants.BackupType.PLAIN_TEXT ->
                    backupPlainWithWarning()
                Constants.BackupType.ENCRYPTED ->
                    showSaveFileSelector(Constants.BACKUP_MIMETYPE_CRYPT, Constants.BackupType.ENCRYPTED, saveCryptLauncher) { doBackupCrypt(null) }
                Constants.BackupType.OPEN_PGP ->
                    showSaveFileSelector(Constants.BACKUP_MIMETYPE_PGP, Constants.BackupType.OPEN_PGP, savePgpLauncher) { backupEncryptedWithPGP(null, null) }
                else -> {}
            }
        }

        setupBackupType(backupType)
        sheet.show()
    }

    private fun showRestoreSheet() {
        val sheet = openSheet(R.layout.sheet_restore, true)

        val type = sheet.findViewById<MaterialAutoCompleteTextView>(R.id.restoreType)
        val replace = sheet.findViewById<MaterialSwitch>(R.id.backup_replace)
        val oldFormat = sheet.findViewById<MaterialCheckBox>(R.id.restoreOldCrypt)
        val description = sheet.findViewById<TextView>(R.id.restoreDescription)
        val button = sheet.findViewById<MaterialButton>(R.id.buttonRestore)
        sheetButton = button
        sheetProgress = sheet.findViewById(R.id.progressBarRestore)

        if (type == null || replace == null || oldFormat == null || button == null)
            return

        bindTypeDropdown(type)
        description?.setText(R.string.backup_desc_restore_format)

        replace.isChecked = replaceExisting
        replace.setOnCheckedChangeListener { _, checked -> replaceExisting = checked }

        oldFormat.isChecked = restoreOldFormat
        oldFormat.setOnCheckedChangeListener { _, checked -> restoreOldFormat = checked }

        button.setOnClickListener {
            when (backupType) {
                Constants.BackupType.PLAIN_TEXT ->
                    showOpenFileSelector(openPlainLauncher, MIME_PLAIN)
                Constants.BackupType.ENCRYPTED ->
                    showOpenFileSelector(if (restoreOldFormat) openCryptOldLauncher else openCryptLauncher)
                Constants.BackupType.OPEN_PGP ->
                    showOpenFileSelector(openPgpLauncher)
                else -> {}
            }
        }

        setupBackupType(backupType)
        sheet.show()
    }

    private fun setSheetLoading(loading: Boolean) {
        sheetButton?.let { button ->
            button.isEnabled = !loading
            button.text = if (loading) "" else getString(if (sheetIsRestore) R.string.backup_button_restore_short else R.string.backup_button_backup)
        }
        sheetProgress?.visibility = if (loading) View.VISIBLE else View.GONE
        sheetClose?.isEnabled = !loading
        activeSheet?.setCancelable(!loading)
    }

    private fun setupBackupType(type: Constants.BackupType) {
        val description = activeSheet?.findViewById<TextView>(R.id.backupDescription)
        val warning = activeSheet?.findViewById<TextView>(if (sheetIsRestore) R.id.restoreErrorLabel else R.id.backupErrorLabel)
        val oldFormat = activeSheet?.findViewById<View>(R.id.restoreOldCrypt)

        var enabled = true
        var warningRes = 0

        when (type) {
            Constants.BackupType.PLAIN_TEXT ->
                description?.setText(R.string.backup_desc_plain_short)
            Constants.BackupType.ENCRYPTED ->
                description?.setText(R.string.backup_desc_crypt_short)
            Constants.BackupType.OPEN_PGP -> {
                description?.setText(R.string.backup_desc_pgp_short)

                val pgpProvider = settings.openPGPProvider
                pgpEncryptionUserIDs = settings.openPGPEncryptionUserIDs

                if (TextUtils.isEmpty(pgpProvider)) {
                    warningRes = R.string.backup_desc_openpgp_provider
                    enabled = false
                } else if (TextUtils.isEmpty(pgpEncryptionUserIDs)) {
                    warningRes = R.string.backup_desc_openpgp_keyid
                    enabled = false
                } else {
                    val connection = OpenPgpServiceConnection(this@BackupActivity.applicationContext, pgpProvider)
                    pgpServiceConnection = connection
                    connection.bindToService()
                }
            }
            else -> {}
        }

        if (warning != null) {
            if (warningRes != 0) {
                warning.setText(warningRes)
                warning.visibility = View.VISIBLE
            } else {
                warning.visibility = View.GONE
            }
        }

        oldFormat?.visibility = if (type == Constants.BackupType.ENCRYPTED) View.VISIBLE else View.GONE

        if (allowExit)
            sheetButton?.isEnabled = enabled

        backupType = type
        settings.defaultBackupType = type
    }

    // End with a result
    fun finishWithResult() {
        val data = Intent()
        data.putExtra("reload", reload)
        setResult(RESULT_OK, data)
        finish()
    }

    // Go back to the main activity
    override fun onSupportNavigateUp(): Boolean {
        if (allowExit)
            finishWithResult()

        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(STATE_ENCRYPT_TARGET, encryptTargetFile)
        outState.putParcelable(STATE_DECRYPT_SOURCE, decryptSourceFile)
    }

    public override fun onDestroy() {
        super.onDestroy()

        watchdogHandler.removeCallbacks(watchdog)

        pgpServiceConnection?.unbindFromService()
    }

    // TODO: Show more information about the finished backup (e.g. a notification with the file name)
    private fun notifyBackupState(msgId: Int) {
        Toast.makeText(this, msgId, Toast.LENGTH_LONG).show()
    }

    private fun handleBackupTaskResult(result: GenericBackupTask.BackupTaskResult) {
        showBackupProgress(false)

        if (result.messageId != 0)
            notifyBackupState(result.messageId)
        else
            if (!result.success)
                notifyBackupState(R.string.backup_toast_export_failed)

        // Clean up the task fragment
        val backupTaskFragment = findBackupTaskFragment()
        if (backupTaskFragment != null) {
            supportFragmentManager.beginTransaction()
                    .remove(backupTaskFragment)
                    .commit()
        }

        if (result.success) {
            dismissSheet()
            ResultDialog.showSuccess(this, R.drawable.ic_backup_cloud,
                    R.string.backup_result_created_title, R.string.backup_result_created_msg,
                    R.string.continue_on) { finishWithResult() }
        }
    }

    private fun handleRestoreTaskResult(result: GenericRestoreTask.RestoreTaskResult) {
        if (result.success) {
            if (result.isPGP) {
                val inputStream: InputStream = ByteArrayInputStream(result.payload!!.toByteArray(StandardCharsets.UTF_8))
                val os = ByteArrayOutputStream()

                val api = OpenPgpApi(this, pgpServiceConnection!!.service)
                val resultIntent = api.executeApi(result.decryptIntent, inputStream, os)

                handleOpenPGPResult(resultIntent, os, result.uri, PgpOperation.DECRYPT)
            } else {
                restoreEntries(result.payload, false)
            }
        } else {
            if (result.messageId != 0)
                notifyBackupState(result.messageId)
            else
                notifyBackupState(R.string.backup_toast_import_failed)
        }

        showRestoreProgress(false)

        // Clean up the task fragment
        val restoreTaskFragment = findRestoreTaskFragment()
        if (restoreTaskFragment != null) {
            supportFragmentManager.beginTransaction()
                    .remove(restoreTaskFragment)
                    .commit()
        }

        if (result.success && !result.isPGP)
            showRestoreSuccess()
    }

    private fun showRestoreSuccess() {
        dismissSheet()
        ResultDialog.showSuccess(this, R.drawable.ic_restore_cloud,
                R.string.backup_result_restored_title, R.string.backup_result_restored_msg,
                R.string.continue_on) { finishWithResult() }
    }

    private fun toggleInProgressMode(running: Boolean) {
        allowExit = !running
        setSheetLoading(running)
    }

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdog = Runnable {
        toggleInProgressMode(false)
        UIHelper.showGenericDialog(this, R.string.backup_error_stalled_title,
                R.string.backup_error_stalled_msg)
    }

    private fun showBackupProgress(running: Boolean) {
        armWatchdog(running)
        toggleInProgressMode(running)
    }

    private fun showRestoreProgress(running: Boolean) {
        armWatchdog(running)
        toggleInProgressMode(running)
    }

    private fun armWatchdog(running: Boolean) {
        watchdogHandler.removeCallbacks(watchdog)
        if (running)
            watchdogHandler.postDelayed(watchdog, TASK_WATCHDOG_MS)
    }

    private fun dismissSheet() {
        val sheet = activeSheet
        if (sheet != null) {
            sheet.dismiss()
            activeSheet = null
        }
    }

    /**
     * Refuses a file whose name does not match the chosen backup format. Handing, say, a plain
     * JSON file to the encrypted restore left the task grinding with nothing to report, so the
     * sheet sat on its spinner until the app was killed.
     *
     * @return true when the file can be handed to the restore task
     */
    private fun matchesFormat(uri: Uri, extensions: Array<String>): Boolean {
        val name = documentName(uri) ?: return true

        val lower = name.lowercase(Locale.ENGLISH)
        for (extension in extensions) {
            if (lower.endsWith(extension))
                return true
        }

        showRestoreProgress(false)
        UIHelper.showGenericDialog(this, R.string.backup_error_format_title,
                R.string.backup_error_format_msg)
        return false
    }

    private fun documentName(uri: Uri): String? {
        val file = DocumentFile.fromSingleUri(this, uri)
        return file?.name
    }

    /* Generic functions for all backup/restore options */

    private fun showOpenFileSelector(launcher: ActivityResultLauncher<Intent>) {
        showOpenFileSelector(launcher, null)
    }

    /**
     * @param mimeTypes narrows the picker to the formats the chosen backup type can actually read,
     *                  so a file the restore cannot parse is harder to pick in the first place
     */
    private fun showOpenFileSelector(launcher: ActivityResultLauncher<Intent>, mimeTypes: Array<String>?) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        if (mimeTypes != null)
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

        try {
            launcher.launch(intent)
            return
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Failed to use ACTION_OPEN_DOCUMENT, no matching activity found!")
        }

        intent.action = Intent.ACTION_GET_CONTENT

        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Failed to use ACTION_GET_CONTENT, no matching activity found!")
            Toast.makeText(this, R.string.backup_toast_file_selection_failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * @param launcher         receives the document created by the system file picker
     * @param backupToLocation runs the backup into the configured backup location instead,
     *                         when the user chose not to be asked for a file every time
     */
    private fun showSaveFileSelector(mimeType: String, backupType: Constants.BackupType,
                                     launcher: ActivityResultLauncher<Intent>, backupToLocation: () -> Unit) {
        if (settings.backupAsk) {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = mimeType
            intent.putExtra(Intent.EXTRA_TITLE, BackupHelper.backupFilename(this, backupType))
            launcher.launch(intent)
        } else {
            if (settings.isBackupLocationSet) {
                backupToLocation()
            } else {
                Toast.makeText(this, R.string.backup_toast_no_location, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun restoreEntries(text: String?, finish: Boolean) {
        val entries = DatabaseHelper.stringToEntries(text)

        if (entries.size > 0) {
            if (!replaceExisting) {
                val currentEntries = DatabaseHelper.loadDatabase(this, encryptionKey)

                entries.removeAll(currentEntries)
                entries.addAll(currentEntries)
            }

            if (DatabaseHelper.saveDatabase(this, entries, encryptionKey)) {
                reload = true

                if (finish)
                    showRestoreSuccess()
                else
                    Toast.makeText(this, R.string.backup_toast_import_success, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.backup_toast_import_save_failed, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, R.string.backup_toast_import_no_entries, Toast.LENGTH_LONG).show()
        }
    }

    /* Plain-text backup functions */

    private fun doRestorePlain(uri: Uri) {
        if (!matchesFormat(uri, EXT_PLAIN))
            return

        if (Tools.isExternalStorageReadable()) {
            val task = PlainTextRestoreTask(this, uri)
            task.setCallback(::handleRestoreTaskResult)

            startRestoreTask(task)
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show()
        }
    }

    private fun doBackupPlain(uri: Uri?) {
        if (Tools.isExternalStorageWritable()) {
            val entries = DatabaseHelper.loadDatabase(this, encryptionKey)

            val task = PlainTextBackupTask(this, entries, uri)
            task.setCallback(::handleBackupTaskResult)

            startBackupTask(task)
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")   // android.R.string.yes / no
    private fun backupPlainWithWarning() {
        val builder = MaterialAlertDialogBuilder(this)

        builder.setTitle(R.string.backup_dialog_title_security_warning)
                .setMessage(R.string.backup_dialog_msg_export_warning)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    showSaveFileSelector(Constants.BACKUP_MIMETYPE_PLAIN, Constants.BackupType.PLAIN_TEXT, savePlainLauncher) { doBackupPlain(null) }
                }
                .setNegativeButton(android.R.string.no) { _, _ -> }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .create()
                .show()
    }

    /* Encrypted backup functions */

    private fun doRestoreCrypt(uri: Uri, oldFormat: Boolean) {
        if (!matchesFormat(uri, EXT_CRYPT))
            return

        val password = settings.backupPasswordEnc

        if (password.isEmpty()) {
            val pwDialog = PasswordEntryDialog(this, PasswordEntryDialog.Mode.ENTER, settings.blockAccessibility, settings.blockAutofill,
                    PasswordEntryDialog.PasswordEnteredCallback { newPassword -> doRestoreCryptWithPassword(uri, newPassword, oldFormat) })
            pwDialog.show()
        } else {
            doRestoreCryptWithPassword(uri, password, oldFormat)
        }
    }

    private fun doRestoreCryptWithPassword(uri: Uri, password: String, oldFormat: Boolean) {
        if (Tools.isExternalStorageReadable()) {
            val task = EncryptedRestoreTask(this, uri, password, oldFormat)
            task.setCallback(::handleRestoreTaskResult)

            startRestoreTask(task)
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show()
        }
    }

    private fun doBackupCrypt(uri: Uri?) {
        val password = settings.backupPasswordEnc

        if (password.isEmpty()) {
            val pwDialog = PasswordEntryDialog(this, PasswordEntryDialog.Mode.UPDATE, settings.blockAccessibility, settings.blockAutofill,
                    PasswordEntryDialog.PasswordEnteredCallback { newPassword -> doBackupCryptWithPassword(uri, newPassword) })
            pwDialog.show()
        } else {
            doBackupCryptWithPassword(uri, password)
        }
    }

    private fun doBackupCryptWithPassword(uri: Uri?, password: String) {
        if (Tools.isExternalStorageWritable()) {
            val entries = DatabaseHelper.loadDatabase(this, encryptionKey)

            val task = EncryptedBackupTask(this, entries, password, uri)
            task.setCallback(::handleBackupTaskResult)

            startBackupTask(task)
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show()
        }
    }

    /* OpenPGP backup functions */

    private fun restoreEncryptedWithPGP(uri: Uri?, decryptIntent: Intent?) {
        // A null uri is let through: in the Java original DocumentFile could not name it, so the
        // format check passed it on to the restore task.
        if (decryptIntent == null && uri != null && !matchesFormat(uri, EXT_PGP))
            return

        val intent = decryptIntent ?: Intent(OpenPgpApi.ACTION_DECRYPT_VERIFY)

        val task = PGPRestoreTask(this, uri, intent)
        task.setCallback(::handleRestoreTaskResult)

        startRestoreTask(task)
    }

    private fun doBackupEncrypted(uri: Uri?, data: String) {
        if (Tools.isExternalStorageWritable()) {
            val task = PGPBackupTask(this, data, uri)
            task.setCallback(::handleBackupTaskResult)

            startBackupTask(task)
        } else {
            Toast.makeText(this, R.string.backup_toast_storage_not_accessible, Toast.LENGTH_LONG).show()
        }
    }

    private fun backupEncryptedWithPGP(uri: Uri?, encryptIntent: Intent?) {
        val entries = DatabaseHelper.loadDatabase(this, encryptionKey)
        val plainJSON = DatabaseHelper.entriesToString(entries)

        var intent = encryptIntent
        if (intent == null) {
            intent = Intent()

            if (settings.openPGPSigningKey != 0L) {
                intent.action = OpenPgpApi.ACTION_SIGN_AND_ENCRYPT
                intent.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, settings.openPGPSigningKey)
            } else {
                intent.action = OpenPgpApi.ACTION_ENCRYPT
            }

            // Pattern.split() keeps Java's String.split() semantics (trailing empty strings dropped).
            intent.putExtra(OpenPgpApi.EXTRA_USER_IDS, Pattern.compile(",").split(pgpEncryptionUserIDs!!))
            intent.putExtra(OpenPgpApi.EXTRA_REQUEST_ASCII_ARMOR, true)
        }

        val inputStream: InputStream = ByteArrayInputStream(plainJSON.toByteArray(StandardCharsets.UTF_8))
        val os = ByteArrayOutputStream()
        val api = OpenPgpApi(this, pgpServiceConnection!!.service)
        val result = api.executeApi(intent, inputStream, os)
        handleOpenPGPResult(result, os, uri, PgpOperation.ENCRYPT)
    }

    fun outputStreamToString(os: ByteArrayOutputStream): String {
        return String(os.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun handleOpenPGPResult(result: Intent, os: ByteArrayOutputStream?, file: Uri?, operation: PgpOperation) {
        if (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR) == OpenPgpApi.RESULT_CODE_SUCCESS) {
            if (operation == PgpOperation.ENCRYPT) {
                if (os != null)
                    doBackupEncrypted(file, outputStreamToString(os))
            } else if (operation == PgpOperation.DECRYPT) {
                if (os != null) {
                    if (settings.openPGPVerify) {
                        val sigResult = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_SIGNATURE, OpenPgpSignatureResult::class.java)

                        if (sigResult!!.result == OpenPgpSignatureResult.RESULT_VALID_KEY_CONFIRMED) {
                            restoreEntries(outputStreamToString(os), true)
                        } else {
                            Toast.makeText(this, R.string.backup_toast_openpgp_not_verified, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        restoreEntries(outputStreamToString(os), true)
                    }
                }
            }
        } else if (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR) == OpenPgpApi.RESULT_CODE_USER_INTERACTION_REQUIRED) {
            val pi = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_INTENT, PendingIntent::class.java)
                    ?: return

            // Remember the target file across the user interaction (also survives recreation, see
            // onSaveInstanceState) and continue the same operation once the user is done.
            val request = IntentSenderRequest.Builder(pi.intentSender).build()
            if (operation == PgpOperation.ENCRYPT) {
                encryptTargetFile = file
                pgpEncryptLauncher.launch(request)
            } else if (operation == PgpOperation.DECRYPT) {
                decryptSourceFile = file
                pgpDecryptLauncher.launch(request)
            }
        } else if (result.getIntExtra(OpenPgpApi.RESULT_CODE, OpenPgpApi.RESULT_CODE_ERROR) == OpenPgpApi.RESULT_CODE_ERROR) {
            val error = IntentCompat.getParcelableExtra(result, OpenPgpApi.RESULT_ERROR, OpenPgpError::class.java)
            val message = if (error != null) error.message else ""
            Toast.makeText(this, String.format(getString(R.string.backup_toast_openpgp_error), message), Toast.LENGTH_LONG).show()
        }
    }

    private fun findBackupTaskFragment(): BackupTaskFragment? {
        return supportFragmentManager.findFragmentByTag(TAG_BACKUP_TASK_FRAGMENT) as BackupTaskFragment?
    }

    private fun findRestoreTaskFragment(): RestoreTaskFragment? {
        return supportFragmentManager.findFragmentByTag(TAG_RESTORE_TASK_FRAGMENT) as RestoreTaskFragment?
    }

    private fun startBackupTask(task: GenericBackupTask) {
        var backupTaskFragment = findBackupTaskFragment()
        val restoreTaskFragment = findRestoreTaskFragment()

        // Don't start a task if we already have an active task running (backup or restore).
        if ((backupTaskFragment == null || backupTaskFragment.task.isCanceled) && (restoreTaskFragment == null || restoreTaskFragment.task.isCanceled)) {
            if (backupTaskFragment == null) {
                backupTaskFragment = BackupTaskFragment()
                supportFragmentManager
                        .beginTransaction()
                        .add(backupTaskFragment, TAG_BACKUP_TASK_FRAGMENT)
                        .commit()
            }

            backupTaskFragment.startTask(task)

            showBackupProgress(true)
        }
    }

    private fun startRestoreTask(task: GenericRestoreTask) {
        val backupTaskFragment = findBackupTaskFragment()
        var restoreTaskFragment = findRestoreTaskFragment()

        // Don't start a task if we already have an active task running (backup or restore).
        if ((backupTaskFragment == null || backupTaskFragment.task.isCanceled) && (restoreTaskFragment == null || restoreTaskFragment.task.isCanceled)) {
            if (restoreTaskFragment == null) {
                restoreTaskFragment = RestoreTaskFragment()
                supportFragmentManager
                        .beginTransaction()
                        .add(restoreTaskFragment, TAG_RESTORE_TASK_FRAGMENT)
                        .commit()
            }

            restoreTaskFragment.startTask(task)

            showRestoreProgress(true)
        }
    }

    private fun checkBackgroundBackupTask() {
        val backupTaskFragment = findBackupTaskFragment()

        if (backupTaskFragment != null) {
            if (backupTaskFragment.task.isCanceled) {
                // The task was canceled or has finished, so remove the task fragment.
                supportFragmentManager.beginTransaction()
                        .remove(backupTaskFragment)
                        .commit()
            } else {
                backupTaskFragment.task.setCallback(::handleBackupTaskResult)
                showBackupProgress(true)
            }
        }

    }

    private fun checkBackgroundRestoreTask() {
        val restoreTaskFragment = findRestoreTaskFragment()

        if (restoreTaskFragment != null) {
            if (restoreTaskFragment.task.isCanceled) {
                // The task was canceled or has finished, so remove the task fragment.
                supportFragmentManager.beginTransaction()
                        .remove(restoreTaskFragment)
                        .commit()
            } else {
                restoreTaskFragment.task.setCallback(::handleRestoreTaskResult)
                showRestoreProgress(true)
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // We don't want the task to callback to a dead activity and cause a memory leak, so null it here.
        val backupTaskFragment = findBackupTaskFragment()
        val restoreTaskFragment = findRestoreTaskFragment()

        if (backupTaskFragment != null)
            backupTaskFragment.task.setCallback(null)

        if (restoreTaskFragment != null)
            restoreTaskFragment.task.setCallback(null)
    }

    override fun onResume() {
        super.onResume()

        checkBackgroundBackupTask()
        checkBackgroundRestoreTask()
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return allowExit   // Don't destroy the backup activity as long as a backup task is running
    }

    /** Retained instance fragment to hold a running [GenericBackupTask] between configuration changes.*/
    @Suppress("DEPRECATION")   // retainInstance
    class BackupTaskFragment : Fragment() {
        lateinit var task: GenericBackupTask

        init {
            retainInstance = true
        }

        fun startTask(task: GenericBackupTask) {
            this.task = task
            this.task.execute()
        }
    }

    /** Retained instance fragment to hold a running [GenericRestoreTask] between configuration changes.*/
    @Suppress("DEPRECATION")   // retainInstance
    class RestoreTaskFragment : Fragment() {
        lateinit var task: GenericRestoreTask

        init {
            retainInstance = true
        }

        fun startTask(task: GenericRestoreTask) {
            this.task = task
            this.task.execute()
        }
    }

    companion object {
        private val TAG = BackupActivity::class.java.simpleName

        private const val TAG_BACKUP_TASK_FRAGMENT = "BackupActivity.BackupTaskFragmentTag"
        private const val TAG_RESTORE_TASK_FRAGMENT = "BackupActivity.RestoreTaskFragmentTag"

        private const val STATE_ENCRYPT_TARGET = "BackupActivity.encryptTargetFile"
        private const val STATE_DECRYPT_SOURCE = "BackupActivity.decryptSourceFile"

        /**
         * A backup or restore that never reports back used to leave the sheet on its spinner for good,
         * with no way out but killing the app. The watchdog gives up after a bounded wait and hands
         * the sheet back to the user with an error.
         */
        private const val TASK_WATCHDOG_MS = 45_000L

        /*
         * The system picker filters by media type, which the storage provider assigns, not by file
         * extension. JSON has a registered type so the plain-text restore can be narrowed safely.
         * The .aes and .gpg backups do not, and providers type them inconsistently, so narrowing
         * them risks hiding the very file the user came for. Those stay unfiltered and the extension
         * check below is what keeps a mismatched file from being restored.
         */
        private val MIME_PLAIN = arrayOf("application/json", "text/json", "text/plain")

        private val EXT_PLAIN = arrayOf(".json")
        private val EXT_CRYPT = arrayOf(".aes")
        private val EXT_PGP = arrayOf(".gpg", ".pgp", ".asc")
    }
}
