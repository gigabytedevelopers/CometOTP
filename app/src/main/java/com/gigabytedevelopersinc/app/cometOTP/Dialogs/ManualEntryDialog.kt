@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import com.gigabytedevelopersinc.app.cometOTP.View.EntriesCardAdapter
import com.gigabytedevelopersinc.app.cometOTP.View.TagsAdapter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import java.util.TreeSet
import java.util.concurrent.Callable

/**
 * "Create Service" / "Edit Service" bottom sheet: key, type, issuer, label, tags, an icon picker
 * and the advanced options (period, digits, algorithm, HOTP counter).
 */
object ManualEntryDialog {
    private val TYPES = Entry.OTPType.values()
    private val ALGORITHMS = TokenCalculator.HashAlgorithm.values()

    @JvmStatic
    fun show(callingActivity: MainActivity, settings: Settings, adapter: EntriesCardAdapter) {
        show(callingActivity, settings, adapter, null, null)
    }

    @SuppressLint("SetTextI18n")
    @JvmStatic
    fun show(callingActivity: MainActivity, settings: Settings, adapter: EntriesCardAdapter,
             oldEntry: Entry?, updateCallback: UpdateCallback?) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        val isNewEntry = oldEntry == null

        val sheet = BottomSheetDialog(callingActivity)
        sheet.setContentView(R.layout.sheet_create_service)
        expand(sheet)

        val root = sheet.findViewById<View>(R.id.manual_submit)
            ?: return
        val inputView = root.parent.parent as View

        if (settings.blockAccessibility)
            inputView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        val title = sheet.findViewById<TextView>(R.id.sheetTitle)
        val close = sheet.findViewById<View>(R.id.sheetClose)
        val iconFrame = sheet.findViewById<View>(R.id.manual_icon_frame)
        val icon = sheet.findViewById<ImageView>(R.id.manual_icon)
        val iconAdd = sheet.findViewById<View>(R.id.manual_icon_add)
        val secretLayout = sheet.findViewById<TextInputLayout>(R.id.manual_secret_layout)
        val secretInput = sheet.findViewById<EditText>(R.id.manual_secret)
        val secretView = sheet.findViewById<TextView>(R.id.manual_secret_view)
        val typeLayout = sheet.findViewById<TextInputLayout>(R.id.manual_type_layout)
        val typeInput = sheet.findViewById<MaterialAutoCompleteTextView>(R.id.manual_type)
        val issuerInput = sheet.findViewById<EditText>(R.id.manual_issuer)
        val labelInput = sheet.findViewById<EditText>(R.id.manual_label)
        val tagsInput = sheet.findViewById<EditText>(R.id.manual_tags)
        val counterLayout = sheet.findViewById<TextInputLayout>(R.id.manual_layout_counter)
        val counterInput = sheet.findViewById<EditText>(R.id.manual_counter)
        val expandButton = sheet.findViewById<TextView>(R.id.dialog_expand_button)
        val expandLayout = sheet.findViewById<View>(R.id.dialog_expand_layout)
        val periodLayout = sheet.findViewById<TextInputLayout>(R.id.manual_layout_period)
        val periodInput = sheet.findViewById<EditText>(R.id.manual_period)
        val digitsInput = sheet.findViewById<EditText>(R.id.manual_digits)
        val algorithmLayout = sheet.findViewById<TextInputLayout>(R.id.manual_algorithm_layout)
        val algorithmInput = sheet.findViewById<MaterialAutoCompleteTextView>(R.id.manual_algorithm)
        val submitButton = sheet.findViewById<MaterialButton>(R.id.manual_submit)

        if (title == null || close == null || iconFrame == null || icon == null || secretLayout == null
                || secretInput == null || secretView == null || typeLayout == null || typeInput == null
                || issuerInput == null || labelInput == null || tagsInput == null || counterLayout == null
                || counterInput == null || expandButton == null || expandLayout == null || periodLayout == null
                || periodInput == null || digitsInput == null || algorithmLayout == null || algorithmInput == null
                || submitButton == null)
            return

        val context: Context = callingActivity
        val state = State()
        state.thumbnail = if (isNewEntry) EntryThumbnail.EntryThumbnails.Default else oldEntry!!.thumbnail
        state.autoThumbnail = isNewEntry

        /* --- header ------------------------------------------------------------------------- */
        title.setText(if (isNewEntry) R.string.sheet_title_create_service else R.string.sheet_title_edit_service)
        submitButton.setText(if (isNewEntry) R.string.button_create else R.string.button_save)
        close.setOnClickListener { sheet.dismiss() }

        /* --- dropdowns ---------------------------------------------------------------------- */
        val typeLabels = arrayOfNulls<String>(TYPES.size)
        for (i in TYPES.indices)
            typeLabels[i] = typeLabel(context, TYPES[i])
        typeInput.setAdapter(ArrayAdapter(context, R.layout.item_dropdown, typeLabels))

        val algorithmLabels = arrayOfNulls<String>(ALGORITHMS.size)
        for (i in ALGORITHMS.indices)
            algorithmLabels[i] = ALGORITHMS[i].name
        algorithmInput.setAdapter(ArrayAdapter(context, R.layout.item_dropdown, algorithmLabels))

        state.type = if (isNewEntry) Entry.OTPType.TOTP else oldEntry!!.type
        state.algorithm = if (isNewEntry) TokenCalculator.DEFAULT_ALGORITHM else oldEntry!!.algorithm
        typeInput.setText(typeLabel(context, state.type), false)
        algorithmInput.setText(state.algorithm.name, false)

        periodInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_PERIOD))
        digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS))
        counterInput.setText(String.format(Locale.US, "%d", TokenCalculator.HOTP_INITIAL_COUNTER))

        /* --- help buttons ------------------------------------------------------------------- */
        secretLayout.setEndIconOnClickListener { showHelp(context, R.string.label_key, R.string.help_key) }
        periodLayout.setEndIconOnClickListener { showHelp(context, R.string.label_period, R.string.help_period) }
        val digitsLayout = digitsInput.parent.parent as TextInputLayout
        digitsLayout.setEndIconOnClickListener { showHelp(context, R.string.label_digits, R.string.help_digits) }

        /* --- type dependent fields ---------------------------------------------------------- */
        val applyType = Runnable {
            val type = state.type

            if (type == Entry.OTPType.STEAM) {
                counterLayout.visibility = View.GONE
                periodLayout.visibility = View.VISIBLE

                digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.STEAM_DEFAULT_DIGITS))
                periodInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_PERIOD))
                state.algorithm = TokenCalculator.HashAlgorithm.SHA1
                algorithmInput.setText(state.algorithm.name, false)

                digitsInput.isEnabled = false
                periodInput.isEnabled = false
                algorithmLayout.isEnabled = false
            } else if (type == Entry.OTPType.TOTP) {
                counterLayout.visibility = View.GONE
                periodLayout.visibility = View.VISIBLE

                if (isNewEntry)
                    digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS))

                digitsInput.isEnabled = true
                periodInput.isEnabled = true
                algorithmLayout.isEnabled = isNewEntry
            } else if (type == Entry.OTPType.HOTP) {
                counterLayout.visibility = View.VISIBLE
                periodLayout.visibility = View.GONE

                if (isNewEntry)
                    digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS))

                digitsInput.isEnabled = true
                algorithmLayout.isEnabled = isNewEntry
            } else if (type == Entry.OTPType.MOTP) {
                counterLayout.visibility = View.GONE
                periodLayout.visibility = View.VISIBLE

                digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS))
                digitsInput.isEnabled = false
                periodInput.isEnabled = false
                algorithmLayout.isEnabled = false
            }
        }

        typeInput.setOnItemClickListener { _, _, position, _ ->
            state.type = TYPES[position]
            applyType.run()
            validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsInput, periodInput, counterInput, submitButton)
        }
        algorithmInput.setOnItemClickListener { _, _, position, _ -> state.algorithm = ALGORITHMS[position] }

        /* --- tags --------------------------------------------------------------------------- */
        // Tags in use by an entry, plus tags created on the Tags screen that nothing carries yet.
        val allTags: MutableSet<String> = TreeSet(String.CASE_INSENSITIVE_ORDER)
        allTags.addAll(adapter.tags)
        allTags.addAll(TagStore.knownTags(callingActivity))

        val tagsHashMap = HashMap<String, Boolean>()
        for (tag in allTags) {
            tagsHashMap[tag] = false
        }
        val tagsAdapter = TagsAdapter(callingActivity, tagsHashMap)

        val tagsCallable = Callable<Any?> {
            tagsInput.setText(TextUtils.join(", ", tagsAdapter.activeTags))
            null
        }

        val openTags = View.OnClickListener { TagsDialog.show(callingActivity, tagsAdapter, tagsCallable, tagsCallable) }

        tagsInput.setOnClickListener(openTags)
        (tagsInput.parent.parent as TextInputLayout).setEndIconOnClickListener(openTags)

        /* --- icon --------------------------------------------------------------------------- */
        val refreshIcon = Runnable {
            // Until there is something to show, the design puts the brand mark on a muted tile
            // with an add badge over it, rather than a placeholder thumbnail.
            // trim { it <= ' ' } is java.lang.String.trim(); Kotlin's trim() strips Unicode whitespace.
            val empty = state.thumbnail == EntryThumbnail.EntryThumbnails.Default
                    && issuerInput.text.toString().trim { it <= ' ' }.isEmpty()
                    && labelInput.text.toString().trim { it <= ' ' }.isEmpty()

            iconAdd?.visibility = if (empty) View.VISIBLE else View.GONE
            iconFrame.setBackgroundResource(
                    if (empty) R.drawable.bg_thumbnail_placeholder else R.drawable.bg_issuer_icon)

            if (empty) {
                icon.setImageResource(R.drawable.ic_logo_mark)
                icon.imageTintList = ColorStateList.valueOf(
                        Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary))
                return@Runnable
            }

            icon.imageTintList = null
            val size = context.resources.getDimensionPixelSize(R.dimen.issuer_icon_size)
            icon.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context,
                    issuerInput.text.toString(), labelInput.text.toString(), size, state.thumbnail))
        }
        refreshIcon.run()

        iconFrame.setOnClickListener {
            ThumbnailPickerSheet.show(context,
                    issuerInput.text.toString(), labelInput.text.toString()) { thumbnail ->
                state.thumbnail = thumbnail
                state.autoThumbnail = false
                refreshIcon.run()
            }
        }

        /* --- advanced options --------------------------------------------------------------- */
        expandButton.setOnClickListener {
            val open = expandLayout.visibility != View.VISIBLE
            expandLayout.visibility = if (open) View.VISIBLE else View.GONE
            expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                    if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more, 0)
        }

        /* --- validation --------------------------------------------------------------------- */
        val watcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
            }

            override fun onTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
            }

            override fun afterTextChanged(editable: Editable?) {
                validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsInput, periodInput, counterInput, submitButton)
            }
        }

        labelInput.addTextChangedListener(watcher)
        issuerInput.addTextChangedListener(watcher)
        secretInput.addTextChangedListener(watcher)
        periodInput.addTextChangedListener(watcher)
        digitsInput.addTextChangedListener(watcher)
        counterInput.addTextChangedListener(watcher)

        issuerInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if (state.autoThumbnail) {
                    state.thumbnail = thumbnailFor(s.toString())
                    refreshIcon.run()
                }
            }
        })

        /* --- submit ------------------------------------------------------------------------- */
        submitButton.setOnClickListener {
            //Replace spaces with empty characters
            val secret = secretInput.text.toString().replace(Regex("\\s+"), "")
            val type = state.type

            if (isNewEntry && !Entry.validateSecret(secret, type)) {
                secretLayout.error = callingActivity.getString(R.string.error_invalid_secret)
                return@setOnClickListener
            }
            secretLayout.error = null

            val algorithm = state.algorithm
            val digits = Integer.parseInt(digitsInput.text.toString())

            val issuer = issuerInput.text.toString()
            val label = labelInput.text.toString()

            if (type == Entry.OTPType.TOTP || type == Entry.OTPType.STEAM) {
                val period = Integer.parseInt(periodInput.text.toString())

                if (isNewEntry) {
                    val e = Entry(type, secret, period, digits, issuer, label, algorithm, tagsAdapter.activeTags)
                    finishNewEntry(e, state)
                    adapter.addEntry(e)
                } else {
                    val entry = oldEntry!!
                    entry.setIssuer(issuer, state.autoThumbnail)
                    entry.label = label
                    entry.digits = digits
                    entry.period = period
                    entry.tags = tagsAdapter.activeTags
                    if (!state.autoThumbnail)
                        entry.thumbnail = state.thumbnail

                    entry.updateOTP(true)

                    updateCallback?.onUpdate()
                }

                callingActivity.refreshTags()
            } else if (type == Entry.OTPType.HOTP) {
                val counter = java.lang.Long.parseLong(counterInput.text.toString())

                if (isNewEntry) {
                    val e = Entry(type, secret, counter, digits, issuer, label, algorithm, tagsAdapter.activeTags)
                    finishNewEntry(e, state)
                    adapter.addEntry(e)
                } else {
                    val entry = oldEntry!!
                    entry.setIssuer(issuer, state.autoThumbnail)
                    entry.label = label
                    entry.digits = digits
                    entry.counter = counter
                    entry.tags = tagsAdapter.activeTags
                    if (!state.autoThumbnail)
                        entry.thumbnail = state.thumbnail

                    entry.updateOTP(true)

                    updateCallback?.onUpdate()
                }

                callingActivity.refreshTags()
            } else if (type == Entry.OTPType.MOTP) {
                if (isNewEntry) {
                    val newEntry = Entry(type, secret, issuer, label, tagsAdapter.activeTags)
                    finishNewEntry(newEntry, state)
                    adapter.addEntry(newEntry)
                } else {
                    val entry = oldEntry!!
                    entry.setIssuer(issuer, state.autoThumbnail)
                    entry.label = label
                    entry.tags = tagsAdapter.activeTags
                    if (!state.autoThumbnail)
                        entry.thumbnail = state.thumbnail

                    entry.updateOTP(false)

                    updateCallback?.onUpdate()
                }

                callingActivity.refreshTags()
            }

            sheet.dismiss()
        }

        /* --- edit mode ---------------------------------------------------------------------- */
        if (oldEntry != null) {
            val oldType = oldEntry.type

            issuerInput.setText(oldEntry.issuer)
            labelInput.setText(oldEntry.label)
            secretView.text = oldEntry.secretEncoded
            digitsInput.setText(String.format(Locale.ENGLISH, "%d", oldEntry.digits))

            if (oldType == Entry.OTPType.TOTP || oldType == Entry.OTPType.STEAM) {
                periodInput.setText(String.format(Locale.ENGLISH, "%d", oldEntry.period))
            } else if (oldType == Entry.OTPType.HOTP) {
                counterInput.setText(String.format(Locale.ENGLISH, "%d", oldEntry.counter))
            }

            for (tag in oldEntry.tags) {
                tagsAdapter.setTagState(tag, true)
            }
            try {
                tagsCallable.call()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            secretLayout.visibility = View.GONE
            secretView.visibility = View.VISIBLE

            typeLayout.isEnabled = false
            algorithmLayout.isEnabled = false
            digitsInput.isEnabled = oldType != Entry.OTPType.STEAM
            periodInput.isEnabled = oldType != Entry.OTPType.STEAM
            counterInput.isEnabled = oldType == Entry.OTPType.HOTP

            // Show the advanced section so the values being edited are visible
            expandLayout.visibility = View.VISIBLE
            expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_less, 0)
        }

        applyType.run()
        validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsInput, periodInput, counterInput, submitButton)

        sheet.show()
    }

    private fun finishNewEntry(e: Entry, state: State) {
        if (!state.autoThumbnail)
            e.thumbnail = state.thumbnail
        e.updateOTP(false)
        e.lastUsed = System.currentTimeMillis()
    }

    private fun validate(state: State, isNewEntry: Boolean, issuerInput: EditText, labelInput: EditText,
                         secretInput: EditText, digitsInput: EditText, periodInput: EditText,
                         counterInput: EditText, submit: MaterialButton) {
        if ((TextUtils.isEmpty(labelInput.text) && TextUtils.isEmpty(issuerInput.text)) ||
                (TextUtils.isEmpty(secretInput.text) && isNewEntry) ||
                !isNonZeroIntegerInput(digitsInput)) {
            submit.isEnabled = false
            return
        }

        val type = state.type
        if (type == Entry.OTPType.HOTP) {
            submit.isEnabled = isZeroOrPositiveLongInput(counterInput)
        } else if (type == Entry.OTPType.TOTP || type == Entry.OTPType.STEAM) {
            submit.isEnabled = isNonZeroIntegerInput(periodInput)
        } else {
            submit.isEnabled = true
        }
    }

    private fun isNonZeroIntegerInput(editText: EditText): Boolean {
        return try {
            val text = editText.text
            !TextUtils.isEmpty(text) && (Integer.parseInt(text.toString()) != 0)
        } catch (e: NumberFormatException) {
            false
        }
    }

    private fun isZeroOrPositiveLongInput(editText: EditText): Boolean {
        return try {
            val text = editText.text
            !TextUtils.isEmpty(text) && (java.lang.Long.parseLong(text.toString()) >= 0)
        } catch (e: NumberFormatException) {
            false
        }
    }

    private fun thumbnailFor(issuer: String): EntryThumbnail.EntryThumbnails {
        if (TextUtils.isEmpty(issuer))
            return EntryThumbnail.EntryThumbnails.Default
        return try {
            EntryThumbnail.EntryThumbnails.valueOfIgnoreCase(issuer)
        } catch (e: Exception) {
            try {
                EntryThumbnail.EntryThumbnails.valueOfFuzzy(issuer)
            } catch (e2: Exception) {
                EntryThumbnail.EntryThumbnails.Default
            }
        }
    }

    private fun typeLabel(context: Context, type: Entry.OTPType): String {
        return when (type) {
            Entry.OTPType.HOTP -> context.getString(R.string.type_hotp)
            Entry.OTPType.MOTP -> context.getString(R.string.type_motp)
            Entry.OTPType.STEAM -> context.getString(R.string.type_steam)
            Entry.OTPType.TOTP -> context.getString(R.string.type_totp)
        }
    }

    private fun showHelp(context: Context, titleRes: Int, messageRes: Int) {
        MaterialAlertDialogBuilder(context)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }

    private fun expand(sheet: BottomSheetDialog) {
        val content = sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (content != null) {
            val behavior = BottomSheetBehavior.from(content)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }
    }

    private class State {
        var type = Entry.OTPType.TOTP
        var algorithm: TokenCalculator.HashAlgorithm = TokenCalculator.DEFAULT_ALGORITHM
        var thumbnail = EntryThumbnail.EntryThumbnails.Default
        var autoThumbnail = true
    }

    fun interface UpdateCallback {
        fun onUpdate()
    }
}
