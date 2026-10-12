@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ScreenshotGuard
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
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
    /** Material's emphasis for what cannot be used. */
    private const val DISABLED_ALPHA = 0.38f

    fun show(callingActivity: MainActivity, settings: Settings, adapter: EntriesCardAdapter) {
        show(callingActivity, settings, adapter, null, null)
    }

    @SuppressLint("SetTextI18n")
    fun show(callingActivity: MainActivity, settings: Settings, adapter: EntriesCardAdapter,
             oldEntry: Entry?, updateCallback: UpdateCallback?) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        val isNewEntry = oldEntry == null

        val sheet = BottomSheetDialog(callingActivity)
        sheet.setContentView(R.layout.sheet_create_service)
        // It shows the key; the screen's protection does not reach a sheet.
        ScreenshotGuard.protect(sheet.window, settings)
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
        val secretViewLayout = sheet.findViewById<View>(R.id.manual_secret_view_layout)
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
                || secretInput == null || secretView == null || secretViewLayout == null || typeLayout == null || typeInput == null
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
        // mOTP's period and length are fixed, so its help says so rather than what most use.
        periodLayout.setEndIconOnClickListener {
            showHelp(context, R.string.label_period,
                    if (state.type == Entry.OTPType.MOTP) R.string.help_period_motp else R.string.help_period)
        }
        val digitsLayout = digitsInput.parent.parent as TextInputLayout
        digitsLayout.setEndIconOnClickListener {
            showHelp(context, R.string.label_digits,
                    if (state.type == Entry.OTPType.MOTP) R.string.help_digits_motp else R.string.help_digits)
        }

        /* --- type dependent fields ---------------------------------------------------------- */
        val applyType = Runnable {
            val type = state.type
            // mOTP codes come from MD5, not from any of the algorithms on offer.
            algorithmLayout.visibility = if (type == Entry.OTPType.MOTP) View.GONE else View.VISIBLE

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
                periodInput.setText(String.format(Locale.US, "%d", TokenCalculator.MOTP_PERIOD))
                digitsInput.isEnabled = false
                periodInput.isEnabled = false
                algorithmLayout.isEnabled = false
            }
        }

        typeInput.setOnItemClickListener { _, _, position, _ ->
            state.type = TYPES[position]
            applyType.run()
            validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsLayout, digitsInput, periodInput, counterInput, submitButton)
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
                validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsLayout, digitsInput, periodInput, counterInput, submitButton)
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
                    entry.initials = state.initials
                    entry.badgeColor = state.badgeColor
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
                    entry.initials = state.initials
                    entry.badgeColor = state.badgeColor
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
                    entry.initials = state.initials
                    entry.badgeColor = state.badgeColor
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
            // Hidden until the eye shows it, and copied only then, as on the PIN and password fields.
            // The eye and the QR code show the key only while the app has a lock (requireLock).
            secretView.transformationMethod = PasswordTransformationMethod.getInstance()
            val secretField = sheet.findViewById<TextInputLayout>(R.id.manual_secret_view_field)
            secretField?.setEndIconOnClickListener {
                adapter.requireLock {
                    val show = secretView.transformationMethod is PasswordTransformationMethod
                    secretView.transformationMethod = if (show) null else PasswordTransformationMethod.getInstance()
                    secretField.setEndIconDrawable(if (show) R.drawable.ic_visibility_invisible else R.drawable.ic_visibility_visible)
                    secretField.endIconContentDescription =
                            context.getString(if (show) R.string.action_hide_key else R.string.action_show_key)
                }
            }
            sheet.findViewById<View>(R.id.manual_secret_qr)?.setOnClickListener {
                adapter.requireLock { adapter.showQRCode(oldEntry) }
            }
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
            secretViewLayout.visibility = View.VISIBLE
            // The type cannot change, and Info shows it with the other settings that cannot.
            typeLayout.visibility = View.GONE
            sheet.findViewById<View>(R.id.manual_info)?.apply {
                visibility = View.VISIBLE
                setOnClickListener { showInfo(context, oldEntry) }
            }

            // The brand icon or initials choice takes the icon tile's place.
            iconFrame.visibility = View.GONE
            state.initials = oldEntry.initials
            state.badgeColor = oldEntry.badgeColor
            state.brand = if (oldEntry.thumbnail != EntryThumbnail.EntryThumbnails.Default) oldEntry.thumbnail
                    else thumbnailFor(oldEntry.issuer)
            bindPersonalisation(sheet, context, state, issuerInput, labelInput)

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
        validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsLayout, digitsInput, periodInput, counterInput, submitButton)

        sheet.show()
    }

    private fun finishNewEntry(e: Entry, state: State) {
        if (!state.autoThumbnail)
            e.thumbnail = state.thumbnail
        e.updateOTP(false)
        e.lastUsed = System.currentTimeMillis()
    }

    private fun validate(state: State, isNewEntry: Boolean, issuerInput: EditText, labelInput: EditText,
                         secretInput: EditText, digitsLayout: TextInputLayout, digitsInput: EditText,
                         periodInput: EditText, counterInput: EditText, submit: MaterialButton) {
        val digitsSupported = isSupportedDigitsInput(digitsInput)
        digitsLayout.error = if (digitsSupported || TextUtils.isEmpty(digitsInput.text)) null
            else digitsLayout.resources.getQuantityString(R.plurals.error_unsupported_digits,
                    TokenCalculator.SUPPORTED_DIGITS.last, TokenCalculator.SUPPORTED_DIGITS.first, TokenCalculator.SUPPORTED_DIGITS.last)

        if ((TextUtils.isEmpty(labelInput.text) && TextUtils.isEmpty(issuerInput.text)) ||
                (TextUtils.isEmpty(secretInput.text) && isNewEntry) ||
                !digitsSupported) {
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

    private fun isSupportedDigitsInput(editText: EditText): Boolean {
        val digits = editText.text.toString().toIntOrNull() ?: return false
        return digits in TokenCalculator.SUPPORTED_DIGITS
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

    /**
     * Editing: whether the card shows the service's brand icon or its initials, and the initials'
     * text and badge colour, which can only change while the initials are chosen. Without a brand
     * icon to offer, choosing one opens the icon picker.
     */
    private fun bindPersonalisation(sheet: BottomSheetDialog, context: Context, state: State,
                                    issuerInput: EditText, labelInput: EditText) {
        val section = sheet.findViewById<View>(R.id.manual_personalisation)
        val brandChoice = sheet.findViewById<View>(R.id.manual_choice_brand)
        val brandPicture = sheet.findViewById<ImageView>(R.id.manual_choice_brand_picture)
        val initialsChoice = sheet.findViewById<View>(R.id.manual_choice_initials)
        val initialsPicture = sheet.findViewById<ImageView>(R.id.manual_choice_initials_picture)
        val changeIcon = sheet.findViewById<View>(R.id.manual_change_icon)
        val editInitials = sheet.findViewById<View>(R.id.manual_edit_initials)
        val badgeRow = sheet.findViewById<View>(R.id.manual_badge_row)
        val badgeDot = sheet.findViewById<View>(R.id.manual_badge_dot)
        val badgeName = sheet.findViewById<TextView>(R.id.manual_badge_name)
        val badgeChevron = sheet.findViewById<ImageView>(R.id.manual_badge_chevron)
        val palette = sheet.findViewById<LinearLayout>(R.id.manual_badge_palette)
        if (section == null || brandChoice == null || brandPicture == null || initialsChoice == null
                || initialsPicture == null || changeIcon == null || editInitials == null || badgeRow == null
                || badgeDot == null || badgeName == null || badgeChevron == null || palette == null)
            return

        section.visibility = View.VISIBLE
        // Cut to their rounded tiles; android:clipToOutline in XML needs Android 12.
        brandPicture.clipToOutline = true
        initialsPicture.clipToOutline = true
        val size = context.resources.getDimensionPixelSize(R.dimen.choice_picture_size)
        val brandPadding = context.resources.getDimensionPixelSize(R.dimen.space_sm)

        // The swatches: Automatic (0, the colour picked from the issuer), then the tag colours. One
        // more than the tag sheet's, so they share the row's width rather than each taking its own.
        val swatch = dp(context, 28)
        val gap = dp(context, 4)
        for (color in intArrayOf(0) + TagStore.palette(context)) {
            val holder = FrameLayout(context)
            holder.layoutParams = LinearLayout.LayoutParams(0, swatch + gap * 2, 1f)
            holder.tag = color
            holder.contentDescription = if (color == 0) context.getString(R.string.personalise_automatic)
                    else TagStore.colorName(context, color)

            val dot = View(context)
            dot.layoutParams = FrameLayout.LayoutParams(swatch, swatch, Gravity.CENTER)
            dot.setBackgroundResource(R.drawable.bg_color_dot)
            holder.addView(dot)

            val ring = View(context)
            ring.layoutParams = FrameLayout.LayoutParams(swatch + gap * 2, swatch + gap * 2, Gravity.CENTER)
            ring.setBackgroundResource(R.drawable.bg_color_dot_selected)
            holder.addView(ring)

            palette.addView(holder)
        }

        val refresh = Runnable {
            val issuer = issuerInput.text.toString()
            val label = labelInput.text.toString()
            val initialsChosen = state.thumbnail == EntryThumbnail.EntryThumbnails.Default
            brandChoice.isSelected = !initialsChosen
            initialsChoice.isSelected = initialsChosen

            if (state.brand != EntryThumbnail.EntryThumbnails.Default) {
                brandPicture.imageTintList = null
                brandPicture.setPadding(brandPadding, brandPadding, brandPadding, brandPadding)
                brandPicture.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context, issuer, label, size, state.brand))
            } else {
                // Nothing to offer yet: the brand mark, as on an empty icon tile.
                brandPicture.setPadding(brandPadding, brandPadding, brandPadding, brandPadding)
                brandPicture.setImageResource(R.drawable.ic_logo_mark)
                brandPicture.imageTintList = ColorStateList.valueOf(
                        Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary))
            }
            initialsPicture.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context, issuer, label, size,
                    EntryThumbnail.EntryThumbnails.Default, state.initials, state.badgeColor))

            // The initials' text and colour, as Material dims what cannot be used.
            for (row in arrayOf(editInitials, badgeRow)) {
                row.isEnabled = initialsChosen
                row.alpha = if (initialsChosen) 1f else DISABLED_ALPHA
            }
            if (!initialsChosen) {
                palette.visibility = View.GONE
                badgeChevron.setImageResource(R.drawable.ic_expand_more)
            }

            val automatic = EntryThumbnail.automaticBadgeColor(context, issuer, label)
            tint(badgeDot, if (state.badgeColor != 0) state.badgeColor else automatic)
            badgeName.text = if (state.badgeColor != 0) TagStore.colorName(context, state.badgeColor)
                    else context.getString(R.string.personalise_automatic)
            for (i in 0 until palette.childCount) {
                val holder = palette.getChildAt(i) as FrameLayout
                val color = holder.tag as Int
                tint(holder.getChildAt(0), if (color == 0) automatic else color)
                holder.getChildAt(1).visibility = if (color == state.badgeColor) View.VISIBLE else View.GONE
            }
        }

        val pickIcon = {
            ThumbnailPickerSheet.show(context, issuerInput.text.toString(), labelInput.text.toString()) { thumbnail ->
                if (thumbnail != EntryThumbnail.EntryThumbnails.Default)
                    state.brand = thumbnail
                state.thumbnail = thumbnail
                state.autoThumbnail = false
                refresh.run()
            }
        }

        brandChoice.setOnClickListener {
            if (state.brand == EntryThumbnail.EntryThumbnails.Default) {
                pickIcon()
            } else {
                state.thumbnail = state.brand
                state.autoThumbnail = false
                refresh.run()
            }
        }
        initialsChoice.setOnClickListener {
            state.thumbnail = EntryThumbnail.EntryThumbnails.Default
            state.autoThumbnail = false
            refresh.run()
        }
        changeIcon.setOnClickListener { pickIcon() }

        editInitials.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(context)
            val container = LayoutInflater.from(builder.context).inflate(R.layout.dialog_text_field, null)
            val fieldLayout = container.findViewById<TextInputLayout>(R.id.dialog_field_layout)
            val input = container.findViewById<EditText>(R.id.dialog_field)
            fieldLayout.hint = context.getString(R.string.personalise_initials)
            fieldLayout.helperText = context.getString(R.string.personalise_initials_help)
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            input.filters = arrayOf(InputFilter.LengthFilter(Entry.MAX_INITIALS))
            input.setText(state.initials)
            input.setSelection(input.text.length)
            builder.setTitle(R.string.personalise_edit_initials)
                    .setView(container)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        state.initials = input.text.toString().trim().take(Entry.MAX_INITIALS)
                        refresh.run()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
        }

        badgeRow.setOnClickListener {
            val open = palette.visibility != View.VISIBLE
            palette.visibility = if (open) View.VISIBLE else View.GONE
            badgeChevron.setImageResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        }
        for (i in 0 until palette.childCount) {
            val holder = palette.getChildAt(i)
            holder.setOnClickListener {
                state.badgeColor = holder.tag as Int
                refresh.run()
            }
        }

        // The initials follow the issuer and label as they are typed.
        val follow = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = refresh.run()
        }
        issuerInput.addTextChangedListener(follow)
        labelInput.addTextChangedListener(follow)

        refresh.run()
    }

    private fun tint(view: View, @ColorInt color: Int) {
        val background = ContextCompat.getDrawable(view.context, R.drawable.bg_color_dot)?.mutate() as GradientDrawable?
        if (background != null) {
            background.setColor(color)
            view.background = background
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return Math.round(value * context.resources.displayMetrics.density)
    }

    /** A service's settings that cannot change once it is added, to read. */
    private fun showInfo(context: Context, entry: Entry) {
        val builder = MaterialAlertDialogBuilder(context)
        val inflater = LayoutInflater.from(builder.context)
        val rows = LinearLayout(builder.context)
        rows.orientation = LinearLayout.VERTICAL
        // In line with the dialog's title.
        val padding = context.resources.getDimensionPixelSize(R.dimen.space_lg)
        rows.setPadding(padding, context.resources.getDimensionPixelSize(R.dimen.space_sm), padding, 0)

        fun row(nameRes: Int, value: String) {
            val view = inflater.inflate(R.layout.item_info_row, rows, false)
            view.findViewById<TextView>(R.id.info_name).setText(nameRes)
            view.findViewById<TextView>(R.id.info_value).text = value
            rows.addView(view)
        }

        row(R.string.label_type, typeLabel(context, entry.type))
        // mOTP codes come from MD5, whatever algorithm the entry was stored with.
        row(R.string.label_algorithm, if (entry.type == Entry.OTPType.MOTP) "MD5" else entry.algorithm.name)
        if (entry.isTimeBased)
            row(R.string.label_refresh_time, context.resources.getQuantityString(
                    R.plurals.info_refresh_time, entry.period, entry.period))
        else
            row(R.string.label_counter, String.format(Locale.getDefault(), "%d", entry.counter))
        row(R.string.label_digits, String.format(Locale.getDefault(), "%d", entry.digits))

        builder.setTitle(R.string.label_info)
                .setView(rows)
                .setPositiveButton(android.R.string.ok, null)
                .show()
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
        /** Editing: the brand icon on offer, the entry's own or the one its issuer matches (Default: none). */
        var brand = EntryThumbnail.EntryThumbnails.Default
        var initials = ""
        @ColorInt
        var badgeColor = 0
    }

    fun interface UpdateCallback {
        fun onUpdate()
    }
}
