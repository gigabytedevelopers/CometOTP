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
import android.widget.Filter
import android.widget.Filterable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Database.EntryList
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ManualEntryDialog
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ThumbnailPickerSheet
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SortMode
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.gigabytedevelopersinc.app.cometOTP.View.ItemTouchHelper.ItemTouchHelperAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.util.Locale
import javax.crypto.SecretKey

class EntriesCardAdapter(private val context: Context, private val tagsFilterAdapter: TagsAdapter) :
    RecyclerView.Adapter<EntryViewHolder>(), ItemTouchHelperAdapter, Filterable {

    private var filter: EntryFilter? = null
    // Named "entries" in the Java version; renamed because getEntries() below is now the
    // "entries" property.
    private val entryList: EntryList
    // Only null before the first load, as in the Java version, where reading it then threw.
    private lateinit var displayedEntries: ArrayList<Entry>
    private var callback: Callback? = null
    private var tagsFilter: List<String> = ArrayList()

    private val settings: Settings = Settings(context)
    private val taskHandler: Handler = Handler(Looper.getMainLooper())

    var sortMode: SortMode = SortMode.UNSORTED
        set(mode) {
            field = mode
            entriesChanged(RecyclerView.NO_POSITION)
        }

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
            Snackbar.make(((context as MainActivity).findViewById<View>(R.id.main_content)), R.string.toast_entry_exists, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun getRealIndex(displayPosition: Int): Int {
        return entryList.indexOf(displayedEntries[displayPosition])
    }

    private fun entriesChanged(itemPos: Int) {
        displayedEntries = entryList.getEntriesSorted(sortMode)
        filterByTags(tagsFilter)

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

    fun saveEntries(auto_backup: Boolean) {
        DatabaseHelper.saveDatabase(context, entryList.entries, sharedEncryptionKey)

        if (auto_backup) {
            val backupType = BackupHelper.autoBackupType(context)
            if (backupType == Constants.BackupType.ENCRYPTED) {
                val cryptBackupFile = BackupHelper.backupFile(context, settings.backupLocation, Constants.BackupType.ENCRYPTED)
                val file = cryptBackupFile.file

                if (file != null) {
                    val keyMaterial = sharedEncryptionKey!!.encoded
                    val encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

                    val success = BackupHelper.backupToFile(context, file.uri, settings.backupPasswordEnc, encryptionKey)
                    if (success) {
                        Snackbar.make(((context as MainActivity).findViewById<View>(R.id.main_content)),
                                R.string.backup_toast_export_success,
                                Snackbar.LENGTH_LONG)
                                .show()
                    } else {
                        Snackbar.make(((context as MainActivity).findViewById<View>(R.id.main_content)),
                                R.string.backup_toast_export_failed,
                                Snackbar.LENGTH_LONG)
                                .show()
                    }
                } else {
                    Snackbar.make(((context as MainActivity).findViewById<View>(R.id.main_content)),
                            cryptBackupFile.errorMessage,
                            Snackbar.LENGTH_LONG)
                            .show()
                }
            }
        }
    }

    fun loadEntries() {
        val key = sharedEncryptionKey
        if (key != null) {
            val newEntries = DatabaseHelper.loadDatabase(context, key)

            entryList.updateEntries(newEntries, true)
            entriesChanged(RecyclerView.NO_POSITION)
        }
    }

    fun filterByTags(tags: List<String>) {
        displayedEntries = entryList.getEntriesFilteredByTags(tags, settings.noTagsToggle, settings.tagFunctionality, sortMode)
        tagsFilter = tags

        notifyDataSetChanged()
    }

    fun updateTimeBasedTokens() {
        var change = false

        for (e in entryList.entries) {
            if (e.isTimeBased) {
                val cardVisible = !settings.tapToReveal || e.isVisible

                val item_changed = e.updateOTP(false)
                var color_changed = false

                // Check color change only if highlighting token feature is enabled and the entry is visible
                if (settings.isHighlightTokenOptionEnabled) {
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

        // Every entry gets its token worked out as the card is bound, rather than only the
        // counter-based ones. A time-based entry whose token is still current returns straight
        // away, so this costs nothing in the common case, and a freshly loaded entry no longer
        // waits for the next tick of the updater to show anything.
        entry.updateOTP(false)

        if (settings.isHighlightTokenOptionEnabled)
            entryViewHolder.updateColor(entry.color)
        entryViewHolder.updateValues(entry)

        entryViewHolder.setLabelSize(settings.labelSize)
        entryViewHolder.setLabelScroll(settings.labelDisplay)

        if (settings.thumbnailVisible)
            entryViewHolder.setThumbnailSize(settings.thumbnailSize)
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

            override fun onMenuButtonClicked(parentView: View, position: Int) {
                showPopupMenu(parentView, position)
            }

            override fun onCopyButtonClicked(text: String, position: Int) {
                copyHandler(position, text, settings.isMinimizeAppOnCopyEnabled)
            }

            override fun onCardSingleClicked(position: Int, text: String) {
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

        if (realIndex >= 0) {
            entryList.getEntry(realIndex).isVisible = false
            // Handler.removeCallbacks(null) removes nothing, so a missing task is simply skipped.
            val hideTask = entryList.getEntry(realIndex).hideTask
            if (hideTask != null)
                taskHandler.removeCallbacks(hideTask)
            entryList.getEntry(realIndex).hideTask = null
        }

        val updateNeeded = updateLastUsedAndFrequency(pos, realIndex)

        if (pos >= 0) {
            displayedEntries[pos].isVisible = false

            if (updateNeeded)
                notifyItemChanged(pos)
        }
    }

    private fun setCounter(pos: Int) {
        val builder = MaterialAlertDialogBuilder(context)

        val marginSmall = context.resources.getDimensionPixelSize(R.dimen.activity_margin_small)
        val marginMedium = context.resources.getDimensionPixelSize(R.dimen.activity_margin_medium)

        val input = EditText(context)
        input.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        input.setText(String.format(Locale.ENGLISH, "%d", displayedEntries[pos].counter))
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setSingleLine()

        val container = FrameLayout(context)
        container.setPaddingRelative(marginMedium, marginSmall, marginMedium, 0)
        container.addView(input)

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

        val marginSmall = context.resources.getDimensionPixelSize(R.dimen.activity_margin_small)
        val marginMedium = context.resources.getDimensionPixelSize(R.dimen.activity_margin_medium)

        val input = EditText(context)
        input.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        input.setRawInputType(InputType.TYPE_NUMBER_VARIATION_PASSWORD or InputType.TYPE_CLASS_NUMBER)
        input.setText(displayedEntries[pos].pin)
        input.setSingleLine()
        input.requestFocus()
        input.transformationMethod = PasswordTransformationMethod()
        UIHelper.showKeyboard(context, input, true)

        val container = FrameLayout(context)
        container.setPaddingRelative(marginMedium, marginSmall, marginMedium, 0)
        container.addView(input)

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
        val builder = MaterialAlertDialogBuilder(context)

        val label = displayedEntries[pos].label
        val message = context.getString(R.string.dialog_msg_confirm_delete, label)

        builder.setTitle(R.string.dialog_title_remove)
                .setMessage(message)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    val realIndex = getRealIndex(pos)

                    displayedEntries.removeAt(pos)
                    notifyItemRemoved(pos)

                    entryList.removeEntry(realIndex)
                    saveEntries(settings.autoBackupEncryptedFullEnabled)
                }
                .setNegativeButton(android.R.string.no) { _, _ ->

                }
                .setCancelable(false)
                .show()
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
                Snackbar.make(((context as MainActivity).findViewById<View>(R.id.main_content)),
                        R.string.toast_qr_failed_to_generate,
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
            Snackbar.make(((context as MainActivity).findViewById<View>(R.id.main_content)),
                    R.string.toast_qr_unsupported,
                    Snackbar.LENGTH_LONG)
                    .show()
        }
    }

    /**
     * Options for a single service, as the bottom sheet the redesign uses for every other menu.
     * The tapped row fills in before the sheet closes, matching the navigation and add sheets.
     */
    private fun showPopupMenu(view: View, pos: Int) {
        val entry = displayedEntries[pos]

        val sheet = BottomSheetDialog(context)
        sheet.setContentView(R.layout.sheet_card_options)

        val title = sheet.findViewById<TextView>(R.id.card_options_title)
        if (title != null) {
            val issuer = entry.issuer
            title.text = if (TextUtils.isEmpty(issuer)) entry.label else issuer
        }

        val pin = sheet.findViewById<View>(R.id.card_options_pin)
        if (pin != null && entry.type == Entry.OTPType.MOTP)
            pin.visibility = View.VISIBLE

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

    override fun getFilter(): EntryFilter {
        if (filter == null)
            filter = EntryFilter()

        return filter!!
    }

    fun clearFilter() {
        if (filter != null)
            filter = null
    }

    val tags: List<String>
        get() = entryList.allTags

    inner class EntryFilter : Filter() {
        private val filterValues: List<Constants.SearchIncludes> = settings.searchValues

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filtered = entryList.getFilteredEntries(constraint, filterValues, sortMode)

            val filterResults = FilterResults()
            filterResults.count = filtered.size
            filterResults.values = filtered

            return filterResults
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            displayedEntries = results.values as ArrayList<Entry>
            notifyDataSetChanged()
        }
    }

    interface Callback {
        fun onMoveEventStart()
        fun onMoveEventStop()
    }

    companion object {
        /** How long a tapped option stays highlighted before its sheet closes. */
        private const val CARD_OPTION_FEEDBACK_MS = 180L

        private var sharedEncryptionKey: SecretKey? = null
    }
}
