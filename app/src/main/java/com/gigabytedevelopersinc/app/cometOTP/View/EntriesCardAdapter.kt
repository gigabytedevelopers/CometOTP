@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.View

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Database.EntryList
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.BulkTagsDialog
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ManualEntryDialog
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ThumbnailPickerSheet
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupNotifications
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupRunner
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupScheduler
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SortMode
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.InProcessBackupQueue
import com.gigabytedevelopersinc.app.cometOTP.Utilities.RecycleBin
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ScreenshotGuard
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.gigabytedevelopersinc.app.cometOTP.View.ItemTouchHelper.ItemTouchHelperAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.concurrent.Executors
import javax.crypto.SecretKey

class EntriesCardAdapter(private val context: Context, private val tagsFilterAdapter: TagsAdapter) :
    RecyclerView.Adapter<EntryViewHolder>(), ItemTouchHelperAdapter {

    // Named "entries" in the Java version; renamed because getEntries() below is now the
    // "entries" property.
    private val entryList: EntryList
    // Only null before the first load, as in the Java version, where reading it then threw.
    private lateinit var displayedEntries: ArrayList<Entry>
    private var callback: Callback? = null
    private var tagsFilter: List<String> = ArrayList()

    /**
     * What the search bar holds; empty when not searching. Kept here rather than applied once, so
     * every refresh (an edit, a new entry, a reload, a sort change) shows the search results again
     * instead of the whole tag-filtered list.
     */
    var searchQuery: String = ""
        set(query) {
            field = query
            showFilteredEntries()
        }

    private val settings: Settings = Settings(context)
    private val taskHandler: Handler = Handler(Looper.getMainLooper())

    var sortMode: SortMode = SortMode.UNSORTED
        set(mode) {
            field = mode
            entriesChanged(RecyclerView.NO_POSITION)
        }

    /**
     * Manage mode: tapping a card selects it, and the selected entries can be removed or tagged
     * together. Entries are held by identity: the displayed list holds the same objects as the
     * stored one, and two entries may be equal while being different accounts' copies.
     */
    var selectionMode = false
        private set
    private val selected: MutableSet<Entry> = Collections.newSetFromMap(IdentityHashMap())

    init {
        this.entryList = EntryList()

        setHasStableIds(true)
    }

    /** Static in the Java version: every adapter instance shares one key. */
    var encryptionKey: SecretKey?
        get() = sharedEncryptionKey
        set(key) {
            sharedEncryptionKey = key
        }

    override fun getItemCount(): Int {
        return displayedEntries.size
    }

    override fun getItemId(position: Int): Long {
        return displayedEntries[position].listId
    }

    val entries: ArrayList<Entry>
        get() = entryList.entries

    fun saveAndRefresh(auto_backup: Boolean) {
        saveAndRefresh(auto_backup, RecyclerView.NO_POSITION)
    }

    fun saveAndRefresh(auto_backup: Boolean, itemPos: Int) {
        updateTagsFilter()
        entriesChanged(itemPos)
        saveEntries(auto_backup)
    }

    fun addEntry(e: Entry) {
        if (entryList.addEntry(e)) {
            saveAndRefresh(settings.autoBackupEncryptedPasswordsEnabled)
        } else {
            (context as MainActivity).makeSnackbar(R.string.toast_entry_exists, Snackbar.LENGTH_LONG).show()
        }
    }

    /**
     * Adds several entries with one save and one auto-backup, rather than one per entry. Entries
     * already in the list are left out without a message each; the caller reports them.
     *
     * @return how many were added.
     */
    fun addEntries(newEntries: List<Entry>): Int {
        var added = 0
        for (e in newEntries) {
            if (entryList.addEntry(e))
                added++
        }

        if (added > 0)
            saveAndRefresh(settings.autoBackupEncryptedPasswordsEnabled)

        return added
    }

    private fun getRealIndex(displayPosition: Int): Int {
        return entryList.indexOf(displayedEntries[displayPosition])
    }

    private fun entriesChanged(itemPos: Int) {
        showFilteredEntries()

        if (itemPos == RecyclerView.NO_POSITION)
            notifyDataSetChanged()
        else
            notifyItemChanged(itemPos)
    }

    fun updateTagsFilter() {
        val inUseTags = tags

        val tagsHashMap = HashMap<String, Boolean>()
        for (tag in tagsFilterAdapter.tags) {
            if (inUseTags.contains(tag))
                tagsHashMap[tag] = false
        }
        for (tag in tagsFilterAdapter.activeTags) {
            if (inUseTags.contains(tag))
                tagsHashMap[tag] = true
        }
        for (tag in tags) {
            if (inUseTags.contains(tag))
                if (!tagsHashMap.containsKey(tag))
                    tagsHashMap[tag] = true
        }

        tagsFilterAdapter.setTags(tagsHashMap)
        tagsFilter = tagsFilterAdapter.activeTags
    }

    /**
     * True once the entries shown here came from the database, and false again when a load fails.
     * Saving what is on screen after a failed load would replace every stored account with it, so
     * saving (and the auto-backup) is refused until a load succeeds.
     */
    private var databaseLoaded = false

    private fun showDatabaseLoadFailed() {
        (context as MainActivity).makeSnackbar(R.string.toast_database_load_failed,
                Snackbar.LENGTH_LONG)
                .show()
    }

    fun saveEntries(auto_backup: Boolean) {
        if (!databaseLoaded) {
            showDatabaseLoadFailed()
            return
        }

        DatabaseHelper.saveDatabase(context, entryList.entries, sharedEncryptionKey)

        if (auto_backup)
            autoSync()
    }

    /**
     * Auto Sync: backs up shortly after an edit, never on the main thread. Waiting a moment makes
     * one backup of a burst of edits instead of one per edit.
     */
    private fun autoSync() {
        if (BackupHelper.autoBackupType(context) != Constants.BackupType.ENCRYPTED)
            return

        if (settings.encryption == Constants.EncryptionType.KEYSTORE) {
            // The background job loads the key itself, and still runs if the app is closed
            // straight after the edit.
            BackupScheduler.autoSync(context)
        } else {
            // With password encryption only this process has the key.
            backUpInBackground(scheduled = false, delayMs = AUTO_SYNC_DELAY_MS)
        }
    }

    /**
     * Makes the scheduled backup once the database is open, if one is due. With password
     * encryption this is the only way a scheduled backup gets made; with KeyStore encryption it
     * catches up on a backup the background job could not make (the phone never idle, the app
     * restricted by the system). A failed attempt is not repeated on every resume.
     */
    private fun backUpIfDue() {
        if (!settings.scheduledBackupEnabled || backupQueue.isBusy)
            return

        val now = System.currentTimeMillis()
        if (!BackupScheduler.isOverdue(settings.lastBackupSuccess, settings.scheduledBackupInterval, now))
            return
        val sinceAttempt = now - settings.lastBackupAttempt
        if (sinceAttempt in 0 until RETRY_AFTER_FAILURE_MS)
            return
        if (BackupHelper.autoBackupType(context) != Constants.BackupType.ENCRYPTED)
            return

        backUpInBackground(scheduled = true)
    }

    private fun backUpInBackground(scheduled: Boolean, delayMs: Long = 0) {
        val key = sharedEncryptionKey ?: return
        val appContext = context.applicationContext

        // Capture only the application context and key. A destroyed adapter must neither cancel
        // the backup nor be kept alive while charging/network constraints delay it.
        val owner = WeakReference(this)
        backupQueue.submit(delayMs) {
            val current = Settings(appContext)
            if (scheduled && (!current.scheduledBackupEnabled ||
                    !BackupScheduler.isOverdue(current.lastBackupSuccess, current.scheduledBackupInterval, System.currentTimeMillis())))
                return@submit true
            if (!BackupScheduler.foregroundConstraintsMet(appContext, current, scheduled))
                return@submit false
            val outcome = BackupRunner.run(appContext, key)
            backupResultHandler.post {
                owner.get()?.showBackupOutcome(appContext, outcome)
            }
            true
        }
    }

    private fun showBackupOutcome(appContext: Context, outcome: BackupRunner.Outcome) {
        val message = when (outcome) {
            is BackupRunner.Outcome.Success -> {
                BackupNotifications.clear(appContext)
                R.string.backup_snackbar_auto_success
            }
            is BackupRunner.Outcome.Retry -> outcome.messageId
            is BackupRunner.Outcome.Failed -> outcome.messageId
            is BackupRunner.Outcome.Skipped -> return
        }

        val activity = context as MainActivity
        if (activity.isFinishing || activity.isDestroyed)
            return

        activity.makeSnackbar(message, Snackbar.LENGTH_LONG).show()
    }

    fun loadEntries() {
        val key = sharedEncryptionKey
        if (key != null) {
            val newEntries = DatabaseHelper.loadDatabase(context, key)
            if (newEntries != null) {
                databaseLoaded = true
                entryList.updateEntries(newEntries, true)
                backUpIfDue()
            } else {
                // Whatever is on screen stays, but nothing is saved until a load succeeds.
                databaseLoaded = false
                showDatabaseLoadFailed()
            }
            entriesChanged(RecyclerView.NO_POSITION)
        }
    }

    fun filterByTags(tags: List<String>) {
        tagsFilter = tags
        showFilteredEntries()
    }

    /** Shows the search results while searching (across every tag, as before), else the entries in the selected tags. */
    private fun showFilteredEntries() {
        displayedEntries = if (searchQuery.isEmpty())
            entryList.getEntriesFilteredByTags(tagsFilter, settings.noTagsToggle, settings.tagFunctionality, sortMode)
        else
            entryList.getFilteredEntries(searchQuery, settings.searchValues, sortMode)

        // Only what is on screen stays selected: an action on cards out of sight (removed, or
        // filtered away) would come as a surprise.
        if (selected.isNotEmpty()) {
            val shown: MutableSet<Entry> = Collections.newSetFromMap(IdentityHashMap())
            shown.addAll(displayedEntries)
            if (selected.retainAll(shown))
                callback?.onSelectionChanged(selected.size)
        }

        notifyDataSetChanged()
    }

    fun updateTimeBasedTokens() {
        var change = false

        for (e in entryList.entries) {
            if (e.isTimeBased) {
                val cardVisible = !settings.tapToReveal || e.isVisible

                val item_changed = e.updateOTP(false)
                var color_changed = false

                // The colour turns red when the token is about to expire. It is checked only for a
                // card that shows its token, and only when the red is shown or the next token is.
                if (settings.isHighlightTokenOptionEnabled || settings.showNextToken) {
                    color_changed = cardVisible && e.hasColorChanged()
                }


                change = change || item_changed || color_changed ||
                        (cardVisible && (e.hasNonDefaultPeriod() || settings.isShowIndividualTimeoutsEnabled))
            }
        }

        if (change)
            notifyDataSetChanged()
    }

    override fun onBindViewHolder(entryViewHolder: EntryViewHolder, i: Int) {
        val entry = displayedEntries[i]
        bindEntry(entryViewHolder, entry)
        entryViewHolder.bindSelection(selectionMode, selected.contains(entry), selectionMode && canReorder())
    }

    /** Shows [entry] on [holder]: a card in the list, or the copy heading its options sheet. */
    private fun bindEntry(holder: EntryViewHolder, entry: Entry) {
        // Every entry gets its token worked out as the card is bound, rather than only the
        // counter-based ones. A time-based entry whose token is still current returns straight
        // away, so this costs nothing in the common case, and a freshly loaded entry no longer
        // waits for the next tick of the updater to show anything.
        entry.updateOTP(false)

        if (settings.isHighlightTokenOptionEnabled)
            holder.updateColor(entry.color)
        holder.updateValues(entry)

        holder.setLabelSize(settings.labelSize)
        holder.setLabelScroll(settings.labelDisplay)

        if (settings.thumbnailVisible)
            holder.setThumbnailSize(settings.thumbnailSize)
    }

    /* Manage mode */

    /** Starts manage mode, with the card at [position] selected (none for NO_POSITION). */
    fun startSelection(position: Int) {
        selectionMode = true
        if (position in displayedEntries.indices)
            selected.add(displayedEntries[position])
        notifyItemRangeChanged(0, itemCount)
        callback?.onSelectionChanged(selected.size)
    }

    fun endSelection() {
        if (!selectionMode)
            return
        selectionMode = false
        selected.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    private fun toggleSelection(position: Int) {
        val entry = displayedEntries[position]
        if (!selected.remove(entry))
            selected.add(entry)
        notifyItemChanged(position)
        callback?.onSelectionChanged(selected.size)
    }

    val selectedCount: Int
        get() = selected.size

    val allSelected: Boolean
        get() = displayedEntries.isNotEmpty() && selected.size == displayedEntries.size

    /** Selects every card on screen, or none once they all are. */
    fun toggleSelectAll() {
        if (allSelected)
            selected.clear()
        else
            selected.addAll(displayedEntries)
        notifyItemRangeChanged(0, itemCount)
        callback?.onSelectionChanged(selected.size)
    }

    /**
     * Whether the cards can be dragged into a new order: only while the list shows every entry in
     * its own order, since that is the order a move changes (see [onItemMove]).
     */
    fun canReorder(): Boolean {
        return sortMode == SortMode.UNSORTED && searchQuery.isEmpty() && entryList.isEqual(displayedEntries)
    }

    /** The selected entries, in the order they are shown. */
    private fun selectedInOrder(): List<Entry> {
        return displayedEntries.filter { selected.contains(it) }
    }

    /** Asks before removing every selected entry, naming them, then leaves manage mode. */
    fun removeSelected() {
        val toRemove = selectedInOrder()
        if (toRemove.isEmpty())
            return

        val names = toRemove.take(MAX_NAMES_LISTED).joinToString("\n") { "• " + displayName(it) }
        val more = toRemove.size - MAX_NAMES_LISTED
        val list = if (more > 0) names + "\n" + context.resources.getQuantityString(R.plurals.label_and_more, more, more) else names

        MaterialAlertDialogBuilder(context)
            .setTitle(context.resources.getQuantityString(R.plurals.dialog_title_remove_selected, toRemove.size, toRemove.size))
            .setMessage(context.resources.getQuantityString(R.plurals.dialog_msg_remove_selected, toRemove.size, list))
            .setPositiveButton(R.string.menu_popup_remove) { _, _ ->
                // Manage mode closes first: the snackbar then sits above the add button it brings back.
                moveToBin(toRemove) {
                    selected.clear()
                    callback?.onSelectionFinished()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Adds tags to, or takes them off, every selected entry at once. */
    fun tagSelected() {
        val toTag = selectedInOrder()
        if (toTag.isEmpty())
            return

        BulkTagsDialog.show(context, toTag, tags) {
            saveAndRefresh(settings.autoBackupEncryptedFullEnabled)
        }
    }

    private fun displayName(entry: Entry): String {
        val issuer = entry.issuer
        val label = entry.label
        return when {
            issuer.isNotEmpty() && !label.isNullOrEmpty() -> "$issuer ($label)"
            issuer.isNotEmpty() -> issuer
            else -> label ?: ""
        }
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): EntryViewHolder {
        val itemView = LayoutInflater.from(viewGroup.context).inflate(R.layout.component_card, viewGroup, false)

        val viewHolder = EntryViewHolder(context, itemView, settings.tapToReveal)
        viewHolder.setCallback(object : EntryViewHolder.Callback {
            override fun onMoveEventStart() {
                callback?.onMoveEventStart()
            }

            override fun onMoveEventStop() {
                callback?.onMoveEventStop()
            }

            override fun onCardLongClicked(position: Int) {
                if (selectionMode)
                    toggleSelection(position)
                else
                    showOptionsSheet(position)
            }

            override fun onStartDrag(holder: EntryViewHolder) {
                if (selectionMode && canReorder())
                    callback?.onStartDrag(holder)
            }

            override fun onMoveRequested(from: Int, to: Int) {
                if (selectionMode && canReorder() && to in displayedEntries.indices)
                    onItemMove(from, to)
            }

            override fun onCopyButtonClicked(text: String, position: Int) {
                copyHandler(position, text, settings.isMinimizeAppOnCopyEnabled)
            }

            override fun onCardSingleClicked(position: Int, text: String) {
                if (selectionMode) {
                    toggleSelection(position)
                    return
                }
                when (settings.tapSingle) {
                    Constants.TapMode.REVEAL -> {
                        establishPinIfNeeded(position)
                        cardTapToRevealHandler(position)
                    }
                    Constants.TapMode.COPY -> {
                        establishPinIfNeeded(position)
                        copyHandler(position, text, false)
                    }
                    Constants.TapMode.COPY_BACKGROUND -> {
                        establishPinIfNeeded(position)
                        copyHandler(position, text, true)
                    }
                    Constants.TapMode.SEND_KEYSTROKES -> {
                        establishPinIfNeeded(position)
                        sendKeystrokes(position)
                    }
                    else -> {
                        // If tap-to-reveal is disabled a single tab still needs to establish the PIN
                        if (!settings.tapToReveal)
                            establishPinIfNeeded(position)
                    }
                }
            }


            override fun onCardDoubleClicked(position: Int, text: String) {
                if (selectionMode)
                    return
                when (settings.tapDouble) {
                    Constants.TapMode.REVEAL -> {
                        establishPinIfNeeded(position)
                        cardTapToRevealHandler(position)
                    }
                    Constants.TapMode.COPY -> {
                        establishPinIfNeeded(position)
                        copyHandler(position, text, false)
                    }
                    Constants.TapMode.COPY_BACKGROUND -> {
                        establishPinIfNeeded(position)
                        copyHandler(position, text, true)
                    }
                    Constants.TapMode.SEND_KEYSTROKES -> {
                        establishPinIfNeeded(position)
                        sendKeystrokes(position)
                    }
                    else -> {
                    }
                }
            }

            override fun onCounterClicked(position: Int) {
                updateEntry(
                        displayedEntries[position],
                        entryList.getEntry(getRealIndex(position)),
                        position
                )
            }

            override fun onCounterLongPressed(position: Int) {
                setCounter(position)
            }
        })

        return viewHolder
    }

    private fun establishPinIfNeeded(position: Int) {
        val entry = displayedEntries[position]

        if (entry.type == Entry.OTPType.MOTP && entry.pin.isEmpty())
            establishPIN(position)
    }

    private fun copyHandler(position: Int, text: String, dropToBackground: Boolean) {
        Tools.copyToClipboard(context, text)
        updateLastUsedAndFrequency(position, getRealIndex(position))
        // The Java version also checked context != null here; context is never null.
        if (dropToBackground) {
            (context as MainActivity).moveTaskToBack(true)
        }
    }

    private fun cardTapToRevealHandler(position: Int) {
        val entry = displayedEntries[position]
        val realIndex = entryList.indexOf(entry)

        if (entry.isVisible) {
            hideEntry(entry)
        } else {
            entryList.getEntry(realIndex).hideTask = Runnable { hideEntry(entry) }
            // Int multiplication widened afterwards, as in the Java version.
            taskHandler.postDelayed(entryList.getEntry(realIndex).hideTask!!, (settings.tapToRevealTimeout * 1000).toLong())

            if (entry.isCounterBased) {
                updateEntry(entry, entryList.getEntry(realIndex), position)
            }
            entry.isVisible = true
            notifyItemChanged(position)
        }
    }

    private fun updateEntry(entry: Entry, realEntry: Entry, position: Int) {
        val counter = entry.counter + 1

        entry.counter = counter
        entry.updateOTP(false)
        notifyItemChanged(position)

        realEntry.counter = counter
        realEntry.updateOTP(false)
        saveEntries(settings.autoBackupEncryptedFullEnabled)
    }

    private fun hideEntry(entry: Entry) {
        val pos = displayedEntries.indexOf(entry)
        val realIndex = entryList.indexOf(entry)

        // The entry left the list (deleted, or dropped by a reload) before its reveal timed out.
        // There is nothing left to hide or to count as used.
        if (realIndex < 0) {
            entry.isVisible = false
            cancelHideTask(entry)
            return
        }

        entryList.getEntry(realIndex).isVisible = false
        cancelHideTask(entryList.getEntry(realIndex))

        val updateNeeded = updateLastUsedAndFrequency(pos, realIndex)

        if (pos >= 0) {
            displayedEntries[pos].isVisible = false

            if (updateNeeded)
                notifyItemChanged(pos)
        }
    }

    /** Drops the entry's pending re-hide, if it has one. */
    private fun cancelHideTask(entry: Entry) {
        // Handler.removeCallbacks(null) removes nothing, so a missing task is simply skipped.
        val hideTask = entry.hideTask
        if (hideTask != null)
            taskHandler.removeCallbacks(hideTask)
        entry.hideTask = null
    }

    /**
     * Drops every pending re-hide. Called when the screen showing this adapter goes away, so no
     * timer fires into an adapter that is no longer shown (and keeps it alive until it does).
     */
    fun cancelPendingTasks() {
        taskHandler.removeCallbacksAndMessages(null)
        for (e in entryList.entries)
            e.hideTask = null
    }

    private fun setCounter(pos: Int) {
        val builder = MaterialAlertDialogBuilder(context)

        // Inflated in the dialog's theme, so the field has the app's field style.
        val container = LayoutInflater.from(builder.context).inflate(R.layout.dialog_text_field, null)
        container.findViewById<TextInputLayout>(R.id.dialog_field_layout).hint = context.getString(R.string.label_counter)
        val input = container.findViewById<EditText>(R.id.dialog_field)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText(String.format(Locale.ENGLISH, "%d", displayedEntries[pos].counter))

        val dialog = builder.setTitle(R.string.dialog_title_counter)
                .setView(container)
                .setPositiveButton(R.string.button_save) { _, _ ->
                    val realIndex = getRealIndex(pos)
                    val newCounter = java.lang.Long.parseLong(input.editableText.toString())

                    displayedEntries[pos].counter = newCounter
                    notifyItemChanged(pos)

                    val e = entryList.getEntry(realIndex)
                    e.counter = newCounter

                    saveEntries(settings.autoBackupEncryptedFullEnabled)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->

                }
                .setCancelable(false)
                .create()
        addCounterValidationWatcher(input, dialog)
        dialog.show()
    }

    private fun addCounterValidationWatcher(input: EditText, dialog: AlertDialog) {
        val counterWatcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
            }

            override fun onTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
            }

            override fun afterTextChanged(input: Editable) {
                val positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                if (positive != null) {
                    positive.isEnabled = isZeroOrPositiveLongInput(input)
                }
            }

            private fun isZeroOrPositiveLongInput(input: Editable): Boolean {
                return try {
                    !TextUtils.isEmpty(input) && (java.lang.Long.parseLong(input.toString()) >= 0)
                } catch (e: NumberFormatException) {
                    false
                }
            }
        }
        input.addTextChangedListener(counterWatcher)
    }

    private fun updateLastUsedAndFrequency(position: Int, realIndex: Int): Boolean {
        val timeStamp = System.currentTimeMillis()
        val entryUsedFrequency = entryList.getEntry(realIndex).usedFrequency

        if (position >= 0) {
            val displayEntryUsedFrequency = displayedEntries[position].usedFrequency
            displayedEntries[position].lastUsed = timeStamp
            displayedEntries[position].usedFrequency = displayEntryUsedFrequency + 1
        }

        entryList.getEntry(realIndex).lastUsed = timeStamp
        entryList.getEntry(realIndex).usedFrequency = entryUsedFrequency + 1
        saveEntries(false)

        if (sortMode == SortMode.LAST_USED) {
            displayedEntries = EntryList.sortEntries(displayedEntries, sortMode)
            notifyDataSetChanged()
            return false
        } else if (sortMode == SortMode.MOST_USED) {
            displayedEntries = EntryList.sortEntries(displayedEntries, sortMode)
            notifyDataSetChanged()
            return false
        }

        return true
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (sortMode == SortMode.UNSORTED && entryList.isEqual(displayedEntries)) {
            entryList.swapEntries(fromPosition, toPosition)

            displayedEntries = entryList.entries
            notifyItemMoved(fromPosition, toPosition)

            saveEntries(false)
        }

        return true
    }

    fun changeThumbnail(pos: Int) {
        val realIndex = getRealIndex(pos)
        val entry = entryList.getEntry(realIndex)

        ThumbnailPickerSheet.show(context, entry.issuer, entry.label) { thumbnail ->
            val e = entryList.getEntry(getRealIndex(pos))
            e.thumbnail = thumbnail

            saveEntries(settings.autoBackupEncryptedFullEnabled)
            notifyItemChanged(pos)
        }
    }

    fun establishPIN(pos: Int) {
        val builder = MaterialAlertDialogBuilder(context)

        // Inflated in the dialog's theme, so the field has the app's field style.
        val container = LayoutInflater.from(builder.context).inflate(R.layout.dialog_text_field, null)
        val fieldLayout = container.findViewById<TextInputLayout>(R.id.dialog_field_layout)
        fieldLayout.hint = context.getString(R.string.dialog_title_pin)
        val input = container.findViewById<EditText>(R.id.dialog_field)
        input.setRawInputType(InputType.TYPE_NUMBER_VARIATION_PASSWORD or InputType.TYPE_CLASS_NUMBER)
        input.transformationMethod = PasswordTransformationMethod()
        fieldLayout.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        input.setText(displayedEntries[pos].pin)
        input.requestFocus()
        UIHelper.showKeyboard(context, input, true)

        builder.setTitle(R.string.dialog_title_pin)
                .setCancelable(false)
                .setView(container)
                .setPositiveButton(R.string.button_accept) { _, _ ->
                    val realIndex = getRealIndex(pos)
                    val newPin = input.editableText.toString()

                    displayedEntries[pos].pin = newPin
                    val e = entryList.getEntry(realIndex)
                    e.pin = newPin
                    e.updateOTP(true)
                    notifyItemChanged(pos)
                    UIHelper.hideKeyboard(context, input)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> UIHelper.hideKeyboard(context, input) }
                .create()
                .show()
    }

    @SuppressLint("StringFormatInvalid")
    fun removeItem(pos: Int) {
        val entry = displayedEntries[pos]
        val message = context.getString(R.string.dialog_msg_confirm_delete, displayName(entry))

        MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_title_remove)
                .setMessage(message)
                .setPositiveButton(R.string.menu_popup_remove) { _, _ -> moveToBin(listOf(entry)) }
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(false)
                .show()
    }

    /**
     * Moves [entries] from the list to the removed accounts ([RecycleBin]) and offers to undo it.
     * They go into the bin first and leave the list after, so a bin that cannot be written leaves
     * them where they were. False in that case. [afterRemoval] runs before the undo snackbar is
     * shown, so that the screen it is placed on is the one it stays on (see makeSnackbar).
     */
    private fun moveToBin(entries: List<Entry>, afterRemoval: () -> Unit = {}): Boolean {
        val key = sharedEncryptionKey ?: return false
        if (!RecycleBin.add(context, entries, key)) {
            (context as MainActivity).makeSnackbar(R.string.toast_remove_failed, Snackbar.LENGTH_LONG).show()
            return false
        }

        // Where they were, to put them back there on undo.
        val places = entries.map { entryList.indexOf(it) }
        for (entry in entries) {
            val realIndex = entryList.indexOf(entry)
            if (realIndex < 0)
                continue
            cancelHideTask(entryList.getEntry(realIndex))
            entryList.removeEntry(realIndex)
        }
        saveAndRefresh(settings.autoBackupEncryptedFullEnabled)
        afterRemoval()

        val message = if (entries.size == 1)
            context.getString(R.string.toast_removed_one, displayName(entries[0]))
        else
            context.resources.getQuantityString(R.plurals.toast_removed_many, entries.size, entries.size)
        (context as MainActivity).makeSnackbar(message, UNDO_DURATION_MS)
                .setAction(R.string.button_undo) { undoRemoval(entries, places) }
                .show()
        return true
    }

    /** Puts [entries] back at their [places] in the list and takes them out of the bin. */
    private fun undoRemoval(entries: List<Entry>, places: List<Int>) {
        val key = sharedEncryptionKey ?: return
        // The earliest place first, so that the later places still line up.
        for ((entry, place) in entries.zip(places).sortedBy { it.second })
            entryList.insertEntry(if (place < 0) Int.MAX_VALUE else place, entry)
        RecycleBin.take(context, entries, key)
        saveAndRefresh(settings.autoBackupEncryptedFullEnabled)
    }

    // sends the current OTP code via a "Send Action" with the MIME type "text/x-keystrokes"
    // other apps (eg. https://github.com/KDE/kdeconnect-android ) can listen for this and handle
    // the current code on their own (eg. sending it to a connected device/browser/...)
    private fun sendKeystrokes(pos: Int) {
        val otp = displayedEntries[pos].currentOTP
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "text/x-keystrokes"
        sendIntent.putExtra(Intent.EXTRA_TEXT, otp)
        if (sendIntent.resolveActivity(this.context.packageManager) != null) {
            this.context.startActivity(sendIntent)
        }
    }


    private fun showQRCode(pos: Int) {
        // Declared nullable to keep the Java version's null check (Entry.toUri() no longer returns null).
        val uri: Uri? = displayedEntries[pos].toUri()
        if (uri != null) {
            val bitmap: Bitmap
            try {
                bitmap = BarcodeEncoder().encodeBitmap(uri.toString(), BarcodeFormat.QR_CODE, 0, 0)
            } catch (ignored: Exception) {
                (context as MainActivity).makeSnackbar(R.string.toast_qr_failed_to_generate,
                        Snackbar.LENGTH_LONG)
                        .show()
                return
            }
            val drawable = BitmapDrawable(context.resources, bitmap)
            drawable.isFilterBitmap = false

            val image = ImageView(context)
            image.adjustViewBounds = true
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            image.setImageDrawable(drawable)

            MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.dialog_title_qr_code)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .setView(image)
                    .create()
                    .show()
        } else {
            (context as MainActivity).makeSnackbar(R.string.toast_qr_unsupported,
                    Snackbar.LENGTH_LONG)
                    .show()
        }
    }

    /**
     * Options for a single service, opened with a long press on its card. The card itself heads
     * the sheet and keeps ticking, so it is clear which service the options are for. The tapped
     * row fills in before the sheet closes, matching the navigation and add sheets.
     */
    private fun showOptionsSheet(pos: Int) {
        val entry = displayedEntries[pos]

        val sheet = BottomSheetDialog(context)
        sheet.setContentView(R.layout.sheet_card_options)
        // It shows the code; the screen's protection does not reach a sheet.
        ScreenshotGuard.protect(sheet.window, settings)

        val header = sheet.findViewById<FrameLayout>(R.id.card_options_header)
        if (header != null) {
            val cardView = LayoutInflater.from(context).inflate(R.layout.component_card, header, false)
            header.addView(cardView)
            val preview = EntryViewHolder(context, cardView, settings.tapToReveal)
            preview.showAsPreview()
            bindEntry(preview, entry)

            // Rebound just after each second turns, as the list's own updater does.
            val ticker = object : Runnable {
                override fun run() {
                    bindEntry(preview, entry)
                    taskHandler.postDelayed(this, untilNextSecond())
                }
            }
            taskHandler.postDelayed(ticker, untilNextSecond())
            sheet.setOnDismissListener { taskHandler.removeCallbacks(ticker) }
        }

        val pin = sheet.findViewById<View>(R.id.card_options_pin)
        if (pin != null && entry.type == Entry.OTPType.MOTP)
            pin.visibility = View.VISIBLE

        // Copy is only here while a tap copies and the card has no copy button: with the
        // button there, it would be the same thing twice.
        sheet.findViewById<View>(R.id.card_options_copy)?.visibility = if (settings.tapCopies) View.VISIBLE else View.GONE
        bindOption(sheet, R.id.card_options_copy) {
            val token = entry.currentOTP
            if (!token.isNullOrEmpty()) {
                establishPinIfNeeded(pos)
                copyHandler(pos, token, false)
            }
        }
        bindOption(sheet, R.id.card_options_select) {
            callback?.onSelectionRequested(pos)
        }
        bindOption(sheet, R.id.card_options_edit) {
            ManualEntryDialog.show(context as MainActivity, settings, this@EntriesCardAdapter,
                    entryList.getEntry(getRealIndex(pos))
            ) { saveAndRefresh(settings.autoBackupEncryptedFullEnabled, pos) }
        }
        bindOption(sheet, R.id.card_options_image) { changeThumbnail(pos) }
        bindOption(sheet, R.id.card_options_qr) { showQRCode(pos) }
        bindOption(sheet, R.id.card_options_pin) { establishPIN(pos) }
        bindOption(sheet, R.id.card_options_keystrokes) { sendKeystrokes(pos) }
        bindOption(sheet, R.id.card_options_remove) { removeItem(pos) }

        sheet.show()
    }

    /** Milliseconds until just after the next whole second. */
    private fun untilNextSecond(): Long {
        return 1000 - System.currentTimeMillis() % 1000 + 20
    }

    private fun bindOption(sheet: BottomSheetDialog, id: Int, action: Runnable) {
        val row = sheet.findViewById<View>(id)
            ?: return

        row.setOnClickListener { v ->
            if (!v.isEnabled)
                return@setOnClickListener

            v.isSelected = true
            v.isEnabled = false
            v.postDelayed({
                sheet.dismiss()
                action.run()
            }, CARD_OPTION_FEEDBACK_MS)
        }
    }

    fun setCallback(cb: Callback?) {
        this.callback = cb
    }

    val tags: List<String>
        get() = entryList.allTags

    interface Callback {
        fun onMoveEventStart()
        fun onMoveEventStop()

        /** "Select" in a card's options: start manage mode with that card selected. */
        fun onSelectionRequested(position: Int)
        /** Manage mode: [count] cards are selected now. */
        fun onSelectionChanged(count: Int)
        /** Manage mode is done with, e.g. after removing the selected cards. */
        fun onSelectionFinished()
        /** Manage mode: drag [holder] by its handle. */
        fun onStartDrag(holder: EntryViewHolder)
    }

    companion object {
        /** How long a tapped option stays highlighted before its sheet closes. */
        private const val CARD_OPTION_FEEDBACK_MS = 180L

        /** How many of the selected entries a removal names before "and N more". */
        private const val MAX_NAMES_LISTED = 5

        /** How long a removal can be undone from its snackbar; the bin keeps it for 30 days. */
        private const val UNDO_DURATION_MS = 6000

        private var sharedEncryptionKey: SecretKey? = null

        private const val AUTO_SYNC_DELAY_MS = 3_000L
        private const val RETRY_AFTER_FAILURE_MS = 30 * 60 * 1000L

        // Shared like the key: a backup started by one adapter instance must stop the next
        // instance (after the activity is recreated) from starting a second one.
        private val backupQueue = InProcessBackupQueue(Executors.newSingleThreadScheduledExecutor())
        private val backupResultHandler = Handler(Looper.getMainLooper())
    }
}
