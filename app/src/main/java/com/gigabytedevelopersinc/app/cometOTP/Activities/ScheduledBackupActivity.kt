@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.View
import android.view.ViewStub
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.PasswordEntryDialog
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupNotifications
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupRunner
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupScheduler
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DriveAuth
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.LocalFolderDestination
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar
import javax.crypto.SecretKey

/**
 * Scheduled backups: turn them on, choose how often and where they go, and see how the last one
 * went. The schedule itself is kept by [BackupScheduler]; every change here is handed to it.
 *
 * Started from the Backup & Restore screen with the database key, which "Back up now" needs with
 * password encryption (with KeyStore encryption the key can also be loaded here).
 */
class ScheduledBackupActivity : BaseActivity() {
    private var encryptionKey: SecretKey? = null

    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var frequency: MaterialAutoCompleteTextView
    private lateinit var keep: MaterialAutoCompleteTextView
    private lateinit var destination: MaterialAutoCompleteTextView
    private lateinit var rowTime: View
    private lateinit var groupLocal: View
    private lateinit var groupDrive: View
    private lateinit var rowFolder: View
    private lateinit var rowDriveAccount: View
    private lateinit var rowDriveFolder: View
    private lateinit var rowPassword: View
    private lateinit var chargingSwitch: MaterialSwitch
    private lateinit var unmeteredSwitch: MaterialSwitch
    private lateinit var notifySwitch: MaterialSwitch
    private lateinit var status: TextView
    private lateinit var backupNowButton: MaterialButton
    private lateinit var progress: ProgressBar

    private var nextRun: Long = 0

    private val folderLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val treeUri = result.data?.data
        if (result.resultCode == RESULT_OK && treeUri != null) {
            // Both flags were requested in chooseFolder(); persist exactly those.
            contentResolver.takePersistableUriPermission(treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            settings.backupLocation = treeUri
            settings.backupDestination = Constants.BackupDestinationType.LOCAL
            scheduleChanged()
        }
    }

    // The account picker and consent screen of Google Play services.
    private val driveAuthLauncher: ActivityResultLauncher<IntentSenderRequest> = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK)
            return@registerForActivityResult
        try {
            onDriveAuthorized(DriveAuth.resultFromIntent(this, result.data))
        } catch (e: ApiException) {
            Toast.makeText(this, DriveAuth.messageFor(e), Toast.LENGTH_LONG).show()
        }
    }

    // Android 13+: failures are reported through notifications, which need this permission.
    private val notificationPermissionLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted)
            Toast.makeText(this, R.string.settings_toast_notifications_denied, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val keyMaterial = intent.getByteArrayExtra(Constants.EXTRA_BACKUP_ENCRYPTION_KEY)
        if (keyMaterial != null && keyMaterial.isNotEmpty())
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

        setTitle(R.string.scheduled_backup_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_scheduled_backup
        val v = stub.inflate()

        enabledSwitch = v.findViewById(R.id.sched_enabled)
        frequency = v.findViewById(R.id.sched_frequency)
        keep = v.findViewById(R.id.sched_keep)
        destination = v.findViewById(R.id.sched_destination)
        rowTime = v.findViewById(R.id.row_time)
        groupLocal = v.findViewById(R.id.group_local)
        groupDrive = v.findViewById(R.id.group_drive)
        rowFolder = v.findViewById(R.id.row_folder)
        rowDriveAccount = v.findViewById(R.id.row_drive_account)
        rowDriveFolder = v.findViewById(R.id.row_drive_folder)
        rowPassword = v.findViewById(R.id.row_password)
        chargingSwitch = v.findViewById(R.id.sched_charging)
        unmeteredSwitch = v.findViewById(R.id.sched_unmetered)
        notifySwitch = v.findViewById(R.id.sched_notify_success)
        status = v.findViewById(R.id.sched_status)
        backupNowButton = v.findViewById(R.id.sched_backup_now)
        progress = v.findViewById(R.id.sched_progress)

        bindRow(rowTime, R.drawable.ic_schedule, R.string.scheduled_backup_label_time) { chooseTime() }
        bindRow(rowFolder, R.drawable.ic_backup, R.string.scheduled_backup_label_folder) { chooseFolder() }
        bindRow(rowDriveAccount, R.drawable.ic_account_circle_gray, R.string.scheduled_backup_label_drive_account) {
            if (settings.driveAccount.isEmpty()) connectDrive() else confirmDisconnectDrive()
        }
        bindRow(rowDriveFolder, R.drawable.ic_backup_cloud, R.string.scheduled_backup_label_drive_folder) { chooseDriveFolder() }
        bindRow(rowPassword, R.drawable.ic_key, R.string.scheduled_backup_label_password) { choosePassword() }

        bindFrequency()
        bindDestination()
        bindKeep()

        enabledSwitch.isChecked = settings.scheduledBackupEnabled
        // A click rather than a checked-change listener: refresh() sets the state too, and a
        // check of isPressed would miss keyboard and TalkBack toggles.
        enabledSwitch.setOnClickListener {
            val checked = enabledSwitch.isChecked
            if (checked && !requirementsMet()) {
                enabledSwitch.isChecked = false
                Toast.makeText(this, R.string.scheduled_backup_toast_requirements, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            settings.scheduledBackupEnabled = checked
            if (checked)
                ensureNotificationPermission()
            scheduleChanged()
        }

        chargingSwitch.isChecked = settings.scheduledBackupOnlyCharging
        chargingSwitch.setOnCheckedChangeListener { _, checked ->
            settings.scheduledBackupOnlyCharging = checked
            scheduleChanged()
        }

        unmeteredSwitch.isChecked = settings.scheduledBackupOnlyUnmetered
        unmeteredSwitch.setOnCheckedChangeListener { _, checked ->
            settings.scheduledBackupOnlyUnmetered = checked
            scheduleChanged()
        }

        notifySwitch.isChecked = settings.scheduledBackupNotifySuccess
        notifySwitch.setOnCheckedChangeListener { _, checked ->
            settings.scheduledBackupNotifySuccess = checked
        }

        backupNowButton.setOnClickListener { backUpNow() }

        // A time picker left open over a recreation (rotation) lost its listener with the old screen.
        (supportFragmentManager.findFragmentByTag(TAG_TIME_PICKER) as MaterialTimePicker?)?.let { listenToTimePicker(it) }

        BackupScheduler.scheduledWorkInfo(this).observe(this) { infos ->
            val info = infos?.firstOrNull { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
            nextRun = info?.nextScheduleTimeMillis?.takeIf { it != Long.MAX_VALUE } ?: 0
            refresh()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        backupJob.onResume(this)
        refresh()
    }

    override fun onPause() {
        backupJob.onPause(this)
        super.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun bindRow(row: View, icon: Int, title: Int, action: () -> Unit) {
        row.findViewById<ImageView>(R.id.row_icon).setImageResource(icon)
        row.findViewById<TextView>(R.id.row_title).setText(title)
        row.setOnClickListener { action() }
    }

    private fun setRowSubtitle(row: View, text: CharSequence) {
        val subtitle = row.findViewById<TextView>(R.id.row_subtitle)
        subtitle.text = text
        subtitle.visibility = View.VISIBLE
    }

    private fun isDebuggable(): Boolean {
        return (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun intervals(): IntArray {
        return if (isDebuggable()) INTERVALS + BackupScheduler.MIN_INTERVAL_MINUTES else INTERVALS
    }

    private fun bindFrequency() {
        var names = resources.getStringArray(R.array.scheduled_backup_frequency_names)
        if (isDebuggable())
            names += getString(R.string.scheduled_backup_frequency_debug)

        val intervals = intervals()
        frequency.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, names))
        val selected = intervals.indexOf(settings.scheduledBackupInterval).takeIf { it >= 0 }
            ?: intervals.indexOf(BackupScheduler.DAY_MINUTES)
        frequency.setText(names[selected], false)
        frequency.setOnItemClickListener { _, _, position, _ ->
            settings.scheduledBackupInterval = intervals[position]
            scheduleChanged()
        }
    }

    private fun bindDestination() {
        val names = resources.getStringArray(R.array.scheduled_backup_destination_names)
        val types = Constants.BackupDestinationType.values()
        destination.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, names))
        destination.setText(names[settings.backupDestination.ordinal], false)
        destination.setOnItemClickListener { _, _, position, _ ->
            settings.backupDestination = types[position]
            scheduleChanged()
        }
    }

    private fun bindKeep() {
        val names = resources.getStringArray(R.array.scheduled_backup_keep_names)
        keep.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, names))
        val selected = KEEP_COUNTS.indexOf(settings.scheduledBackupKeep).takeIf { it >= 0 }
            ?: KEEP_COUNTS.indexOf(5)
        keep.setText(names[selected], false)
        keep.setOnItemClickListener { _, _, position, _ ->
            settings.scheduledBackupKeep = KEEP_COUNTS[position]
        }
    }

    private fun requirementsMet(): Boolean {
        return BackupHelper.autoBackupType(this) == Constants.BackupType.ENCRYPTED
    }

    /** Hands the changed settings to the scheduler and shows them. */
    private fun scheduleChanged() {
        BackupScheduler.reconcile(this)
        refresh()
    }

    private fun refresh() {
        val interval = settings.scheduledBackupInterval
        rowTime.visibility = if (interval >= BackupScheduler.DAY_MINUTES) View.VISIBLE else View.GONE
        setRowSubtitle(rowTime, formatTimeOfDay(settings.scheduledBackupTime))

        val folderName = if (settings.isBackupLocationSet)
            LocalFolderDestination.folderName(this, settings.backupLocation) ?: getString(R.string.scheduled_backup_folder_unnamed)
        else
            getString(R.string.scheduled_backup_folder_not_set)
        setRowSubtitle(rowFolder, folderName)

        val drive = settings.backupDestination == Constants.BackupDestinationType.DRIVE
        groupLocal.visibility = if (drive) View.GONE else View.VISIBLE
        groupDrive.visibility = if (drive) View.VISIBLE else View.GONE
        unmeteredSwitch.visibility = if (drive) View.VISIBLE else View.GONE
        setRowSubtitle(rowDriveAccount, settings.driveAccount.ifEmpty { getString(R.string.scheduled_backup_drive_not_connected) })
        setRowSubtitle(rowDriveFolder, getString(R.string.scheduled_backup_desc_drive_folder, settings.driveFolderName))

        setRowSubtitle(rowPassword, getString(if (settings.isBackupPasswordSet)
            R.string.scheduled_backup_password_set else R.string.scheduled_backup_password_not_set))

        // Turned off elsewhere (the requirements went away) or on from a restored backup.
        enabledSwitch.isChecked = settings.scheduledBackupEnabled

        status.text = statusText()

        val busy = backupJob.isBusy
        backupNowButton.isEnabled = !busy
        backupNowButton.text = if (busy) "" else getString(R.string.scheduled_backup_button_now)
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun statusText(): String {
        val lines = ArrayList<String>()

        val lastSuccess = settings.lastBackupSuccess
        if (lastSuccess > 0) {
            lines.add(getString(R.string.scheduled_backup_status_last, formatDateTime(lastSuccess)))
            if (settings.lastBackupFile.isNotEmpty())
                lines.add(getString(R.string.scheduled_backup_status_file, settings.lastBackupFile))
        } else {
            lines.add(getString(R.string.scheduled_backup_status_never))
        }

        val lastAttempt = settings.lastBackupAttempt
        if (settings.lastBackupError.isNotEmpty() && lastAttempt > lastSuccess)
            lines.add(getString(R.string.scheduled_backup_status_error, formatDateTime(lastAttempt), settings.lastBackupError))

        if (settings.scheduledBackupEnabled) {
            if (!BackupScheduler.isSetUp(this, settings))
                lines.add(getString(R.string.scheduled_backup_toast_requirements))
            else if (settings.encryption == Constants.EncryptionType.PASSWORD)
                lines.add(getString(R.string.scheduled_backup_status_password_mode))
            else if (nextRun > 0)
                lines.add(getString(R.string.scheduled_backup_status_next, formatDateTime(nextRun)))
            else
                lines.add(getString(R.string.scheduled_backup_status_waiting))
        }

        return lines.joinToString("\n")
    }

    private fun formatDateTime(time: Long): String {
        return DateUtils.formatDateTime(this, time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)
    }

    private fun formatTimeOfDay(minuteOfDay: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        calendar.set(Calendar.MINUTE, minuteOfDay % 60)
        return DateFormat.getTimeFormat(this).format(calendar.time)
    }

    private fun chooseTime() {
        val time = settings.scheduledBackupTime
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(time / 60)
            .setMinute(time % 60)
            .setTitleText(R.string.scheduled_backup_label_time)
            .build()
        listenToTimePicker(picker)
        picker.show(supportFragmentManager, TAG_TIME_PICKER)
    }

    private fun listenToTimePicker(picker: MaterialTimePicker) {
        picker.addOnPositiveButtonClickListener {
            settings.scheduledBackupTime = picker.hour * 60 + picker.minute
            scheduleChanged()
        }
    }

    private fun chooseFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && settings.isBackupLocationSet)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, settings.backupLocation)

        try {
            folderLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.backup_toast_file_selection_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun connectDrive() {
        DriveAuth.authorize(this)
            .addOnSuccessListener(this) { result ->
                val pendingIntent = result.pendingIntent
                if (result.hasResolution() && pendingIntent != null)
                    driveAuthLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                else
                    onDriveAuthorized(result)
            }
            .addOnFailureListener(this) { e ->
                Toast.makeText(this, DriveAuth.messageFor(e), Toast.LENGTH_LONG).show()
            }
    }

    private fun onDriveAuthorized(result: AuthorizationResult) {
        // Naming the account can take a Drive call; a closed screen just drops the answer.
        Thread {
            val email = DriveAuth.accountEmail(result)
            runOnUiThread {
                if (!isFinishing && !isDestroyed)
                    saveDriveAccount(email)
            }
        }.start()
    }

    private fun saveDriveAccount(email: String?) {
        if (email.isNullOrEmpty()) {
            Toast.makeText(this, R.string.backup_error_drive_reconnect, Toast.LENGTH_LONG).show()
            return
        }

        // Another account has other folders: look the folder up again on the next backup.
        if (email != settings.driveAccount)
            settings.driveFolderId = ""
        settings.driveAccount = email
        settings.backupDestination = Constants.BackupDestinationType.DRIVE
        scheduleChanged()
    }

    private fun confirmDisconnectDrive() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scheduled_backup_dialog_disconnect_title)
            .setMessage(getString(R.string.scheduled_backup_dialog_disconnect_msg, settings.driveAccount))
            .setPositiveButton(R.string.scheduled_backup_button_disconnect) { _, _ -> disconnectDrive() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun disconnectDrive() {
        // Withdrawing the grant can fail offline; the app forgets the account either way.
        DriveAuth.revoke(applicationContext, settings.driveAccount)

        settings.driveAccount = ""
        settings.driveFolderId = ""
        // Nowhere left to back up to: stop the schedule rather than fail on every run.
        if (settings.backupDestination == Constants.BackupDestinationType.DRIVE)
            settings.scheduledBackupEnabled = false

        Toast.makeText(this, R.string.scheduled_backup_toast_drive_disconnected, Toast.LENGTH_LONG).show()
        scheduleChanged()
    }

    private fun chooseDriveFolder() {
        val view = layoutInflater.inflate(R.layout.dialog_drive_folder, null)
        val input = view.findViewById<TextInputEditText>(R.id.drive_folder_name)
        input.setText(settings.driveFolderName)
        input.setSelection(input.text?.length ?: 0)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.scheduled_backup_dialog_folder_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty() && name != settings.driveFolderName) {
                    settings.driveFolderName = name
                    // Found or created under the new name on the next backup.
                    settings.driveFolderId = ""
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun choosePassword() {
        PasswordEntryDialog(this, PasswordEntryDialog.Mode.UPDATE, settings.blockAccessibility, settings.blockAutofill,
            PasswordEntryDialog.PasswordEnteredCallback { password ->
                if (!settings.setBackupPassword(password))
                    Toast.makeText(this, R.string.scheduled_backup_toast_password_failed, Toast.LENGTH_LONG).show()
                // May be what was missing for the schedule to start.
                scheduleChanged()
            }).show()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun backUpNow() {
        if (!requirementsMet()) {
            Toast.makeText(this, R.string.scheduled_backup_toast_requirements, Toast.LENGTH_LONG).show()
            return
        }
        if (backupJob.isBusy)
            return

        val appContext = applicationContext
        val key = encryptionKey
        val keyStoreMode = settings.encryption == Constants.EncryptionType.KEYSTORE

        backupJob.start {
            val backupKey = key ?: if (keyStoreMode) KeyStoreHelper.loadEncryptionKeyFromKeyStore(appContext, true) else null
            val outcome = BackupRunner.run(appContext, backupKey)
            val deliver: (ScheduledBackupActivity) -> Unit = { it.onBackupFinished(outcome) }
            deliver
        }
        refresh()
    }

    private fun onBackupFinished(outcome: BackupRunner.Outcome) {
        refresh()

        when (outcome) {
            is BackupRunner.Outcome.Success -> {
                BackupNotifications.clear(this)
                ResultDialog.showSuccess(this, R.drawable.ic_backup_cloud,
                    R.string.backup_result_created_title, R.string.backup_result_created_msg,
                    R.string.continue_on, null)
            }
            is BackupRunner.Outcome.Retry ->
                UIHelper.showGenericDialog(this, R.string.backup_notification_title_failed, outcome.messageId)
            is BackupRunner.Outcome.Failed ->
                UIHelper.showGenericDialog(this, R.string.backup_notification_title_failed, outcome.messageId)
            is BackupRunner.Outcome.Skipped ->
                UIHelper.showGenericDialog(this, R.string.backup_notification_title_failed, outcome.messageId)
        }
    }

    companion object {
        private const val TAG_TIME_PICKER = "ScheduledBackupActivity.timePicker"

        /** Every 6 and 12 hours, daily, weekly (minutes); matches scheduled_backup_frequency_names. */
        private val INTERVALS = intArrayOf(6 * 60, 12 * 60, BackupScheduler.DAY_MINUTES, 7 * BackupScheduler.DAY_MINUTES)

        /** Matches scheduled_backup_keep_names. */
        private val KEEP_COUNTS = intArrayOf(1, 3, 5, 10, 30)

        // Outlives the activity, so a backup finishing after a rotation reaches the new screen.
        private val backupJob = RetainedJob<ScheduledBackupActivity>()
    }
}
