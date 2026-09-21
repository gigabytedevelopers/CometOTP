package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.gigabytedevelopersinc.app.cometOTP.Activities.MainActivity;
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator;
import com.gigabytedevelopersinc.app.cometOTP.View.EntriesCardAdapter;
import com.gigabytedevelopersinc.app.cometOTP.View.TagsAdapter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * "Create Service" / "Edit Service" bottom sheet: key, type, issuer, label, tags, an icon picker
 * and the advanced options (period, digits, algorithm, HOTP counter).
 */
public class ManualEntryDialog {
    private static final Entry.OTPType[] TYPES = Entry.OTPType.values();
    private static final TokenCalculator.HashAlgorithm[] ALGORITHMS = TokenCalculator.HashAlgorithm.values();

    public static void show(final MainActivity callingActivity, Settings settings, final EntriesCardAdapter adapter) {
        show(callingActivity, settings, adapter, null, null);
    }

    @SuppressLint("SetTextI18n")
    public static void show(final MainActivity callingActivity, Settings settings, final EntriesCardAdapter adapter,
                            @Nullable Entry oldEntry, @Nullable UpdateCallback updateCallback) {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        final boolean isNewEntry = oldEntry == null;

        final BottomSheetDialog sheet = new BottomSheetDialog(callingActivity);
        sheet.setContentView(R.layout.sheet_create_service);
        expand(sheet);

        View root = sheet.findViewById(R.id.manual_submit);
        if (root == null)
            return;
        final View inputView = (View) root.getParent().getParent();

        if (settings.getBlockAccessibility())
            inputView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        final TextView title = sheet.findViewById(R.id.sheetTitle);
        final View close = sheet.findViewById(R.id.sheetClose);
        final View iconFrame = sheet.findViewById(R.id.manual_icon_frame);
        final ImageView icon = sheet.findViewById(R.id.manual_icon);
        final View iconAdd = sheet.findViewById(R.id.manual_icon_add);
        final TextInputLayout secretLayout = sheet.findViewById(R.id.manual_secret_layout);
        final EditText secretInput = sheet.findViewById(R.id.manual_secret);
        final TextView secretView = sheet.findViewById(R.id.manual_secret_view);
        final TextInputLayout typeLayout = sheet.findViewById(R.id.manual_type_layout);
        final MaterialAutoCompleteTextView typeInput = sheet.findViewById(R.id.manual_type);
        final EditText issuerInput = sheet.findViewById(R.id.manual_issuer);
        final EditText labelInput = sheet.findViewById(R.id.manual_label);
        final EditText tagsInput = sheet.findViewById(R.id.manual_tags);
        final TextInputLayout counterLayout = sheet.findViewById(R.id.manual_layout_counter);
        final EditText counterInput = sheet.findViewById(R.id.manual_counter);
        final TextView expandButton = sheet.findViewById(R.id.dialog_expand_button);
        final View expandLayout = sheet.findViewById(R.id.dialog_expand_layout);
        final TextInputLayout periodLayout = sheet.findViewById(R.id.manual_layout_period);
        final EditText periodInput = sheet.findViewById(R.id.manual_period);
        final EditText digitsInput = sheet.findViewById(R.id.manual_digits);
        final TextInputLayout algorithmLayout = sheet.findViewById(R.id.manual_algorithm_layout);
        final MaterialAutoCompleteTextView algorithmInput = sheet.findViewById(R.id.manual_algorithm);
        final MaterialButton submitButton = sheet.findViewById(R.id.manual_submit);

        if (title == null || close == null || iconFrame == null || icon == null || secretLayout == null
                || secretInput == null || secretView == null || typeLayout == null || typeInput == null
                || issuerInput == null || labelInput == null || tagsInput == null || counterLayout == null
                || counterInput == null || expandButton == null || expandLayout == null || periodLayout == null
                || periodInput == null || digitsInput == null || algorithmLayout == null || algorithmInput == null
                || submitButton == null)
            return;

        final Context context = callingActivity;
        final State state = new State();
        state.thumbnail = isNewEntry ? EntryThumbnail.EntryThumbnails.Default : oldEntry.getThumbnail();
        state.autoThumbnail = isNewEntry;

        /* --- header ------------------------------------------------------------------------- */
        title.setText(isNewEntry ? R.string.sheet_title_create_service : R.string.sheet_title_edit_service);
        submitButton.setText(isNewEntry ? R.string.button_create : R.string.button_save);
        close.setOnClickListener(v -> sheet.dismiss());

        /* --- dropdowns ---------------------------------------------------------------------- */
        final String[] typeLabels = new String[TYPES.length];
        for (int i = 0; i < TYPES.length; i++)
            typeLabels[i] = typeLabel(context, TYPES[i]);
        typeInput.setAdapter(new ArrayAdapter<>(context, R.layout.item_dropdown, typeLabels));

        final String[] algorithmLabels = new String[ALGORITHMS.length];
        for (int i = 0; i < ALGORITHMS.length; i++)
            algorithmLabels[i] = ALGORITHMS[i].name();
        algorithmInput.setAdapter(new ArrayAdapter<>(context, R.layout.item_dropdown, algorithmLabels));

        state.type = isNewEntry ? Entry.OTPType.TOTP : oldEntry.getType();
        state.algorithm = isNewEntry ? TokenCalculator.DEFAULT_ALGORITHM : oldEntry.getAlgorithm();
        typeInput.setText(typeLabel(context, state.type), false);
        algorithmInput.setText(state.algorithm.name(), false);

        periodInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_PERIOD));
        digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS));
        counterInput.setText(String.format(Locale.US, "%d", TokenCalculator.HOTP_INITIAL_COUNTER));

        /* --- help buttons ------------------------------------------------------------------- */
        secretLayout.setEndIconOnClickListener(v -> showHelp(context, R.string.label_key, R.string.help_key));
        periodLayout.setEndIconOnClickListener(v -> showHelp(context, R.string.label_period, R.string.help_period));
        TextInputLayout digitsLayout = (TextInputLayout) digitsInput.getParent().getParent();
        digitsLayout.setEndIconOnClickListener(v -> showHelp(context, R.string.label_digits, R.string.help_digits));

        /* --- type dependent fields ---------------------------------------------------------- */
        final Runnable applyType = () -> {
            Entry.OTPType type = state.type;

            if (type == Entry.OTPType.STEAM) {
                counterLayout.setVisibility(View.GONE);
                periodLayout.setVisibility(View.VISIBLE);

                digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.STEAM_DEFAULT_DIGITS));
                periodInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_PERIOD));
                state.algorithm = TokenCalculator.HashAlgorithm.SHA1;
                algorithmInput.setText(state.algorithm.name(), false);

                digitsInput.setEnabled(false);
                periodInput.setEnabled(false);
                algorithmLayout.setEnabled(false);
            } else if (type == Entry.OTPType.TOTP) {
                counterLayout.setVisibility(View.GONE);
                periodLayout.setVisibility(View.VISIBLE);

                if (isNewEntry)
                    digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS));

                digitsInput.setEnabled(true);
                periodInput.setEnabled(true);
                algorithmLayout.setEnabled(isNewEntry);
            } else if (type == Entry.OTPType.HOTP) {
                counterLayout.setVisibility(View.VISIBLE);
                periodLayout.setVisibility(View.GONE);

                if (isNewEntry)
                    digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS));

                digitsInput.setEnabled(true);
                algorithmLayout.setEnabled(isNewEntry);
            } else if (type == Entry.OTPType.MOTP) {
                counterLayout.setVisibility(View.GONE);
                periodLayout.setVisibility(View.VISIBLE);

                digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS));
                digitsInput.setEnabled(false);
                periodInput.setEnabled(false);
                algorithmLayout.setEnabled(false);
            }
        };

        typeInput.setOnItemClickListener((parent, view, position, id) -> {
            state.type = TYPES[position];
            applyType.run();
            validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsInput, periodInput, counterInput, submitButton);
        });
        algorithmInput.setOnItemClickListener((parent, view, position, id) -> state.algorithm = ALGORITHMS[position]);

        /* --- tags --------------------------------------------------------------------------- */
        List<String> allTags = adapter.getTags();
        HashMap<String, Boolean> tagsHashMap = new HashMap<>();
        for(String tag: allTags) {
            tagsHashMap.put(tag, false);
        }
        final TagsAdapter tagsAdapter = new TagsAdapter(callingActivity, tagsHashMap);

        final Callable<?> tagsCallable = () -> {
            tagsInput.setText(TextUtils.join(", ", tagsAdapter.getActiveTags()));
            return null;
        };

        View.OnClickListener openTags = view -> TagsDialog.show(callingActivity, tagsAdapter, tagsCallable, tagsCallable);
        tagsInput.setOnClickListener(openTags);
        ((TextInputLayout) tagsInput.getParent().getParent()).setEndIconOnClickListener(openTags);

        /* --- icon --------------------------------------------------------------------------- */
        final Runnable refreshIcon = () -> {
            // Until there is something to show, the design puts the brand mark on a muted tile
            // with an add badge over it, rather than a placeholder thumbnail.
            boolean empty = state.thumbnail == EntryThumbnail.EntryThumbnails.Default
                    && issuerInput.getText().toString().trim().isEmpty()
                    && labelInput.getText().toString().trim().isEmpty();

            if (iconAdd != null)
                iconAdd.setVisibility(empty ? View.VISIBLE : View.GONE);
            iconFrame.setBackgroundResource(
                    empty ? R.drawable.bg_thumbnail_placeholder : R.drawable.bg_issuer_icon);

            if (empty) {
                icon.setImageResource(R.drawable.ic_logo_mark);
                icon.setImageTintList(ColorStateList.valueOf(
                        Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary)));
                return;
            }

            icon.setImageTintList(null);
            int size = context.getResources().getDimensionPixelSize(R.dimen.issuer_icon_size);
            icon.setImageBitmap(EntryThumbnail.getThumbnailGraphic(context,
                    issuerInput.getText().toString(), labelInput.getText().toString(), size, state.thumbnail));
        };
        refreshIcon.run();

        iconFrame.setOnClickListener(v -> ThumbnailPickerSheet.show(context,
                issuerInput.getText().toString(), labelInput.getText().toString(), thumbnail -> {
                    state.thumbnail = thumbnail;
                    state.autoThumbnail = false;
                    refreshIcon.run();
                }));

        /* --- advanced options --------------------------------------------------------------- */
        expandButton.setOnClickListener(view -> {
            boolean open = expandLayout.getVisibility() != View.VISIBLE;
            expandLayout.setVisibility(open ? View.VISIBLE : View.GONE);
            expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                    open ? R.drawable.ic_expand_less : R.drawable.ic_expand_more, 0);
        });

        /* --- validation --------------------------------------------------------------------- */
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsInput, periodInput, counterInput, submitButton);
            }
        };

        labelInput.addTextChangedListener(watcher);
        issuerInput.addTextChangedListener(watcher);
        secretInput.addTextChangedListener(watcher);
        periodInput.addTextChangedListener(watcher);
        digitsInput.addTextChangedListener(watcher);
        counterInput.addTextChangedListener(watcher);

        issuerInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (state.autoThumbnail) {
                    state.thumbnail = thumbnailFor(s.toString());
                    refreshIcon.run();
                }
            }
        });

        /* --- submit ------------------------------------------------------------------------- */
        submitButton.setOnClickListener(view -> {
            //Replace spaces with empty characters
            String secret = secretInput.getText().toString().replaceAll("\\s+","");
            Entry.OTPType type = state.type;

            if (isNewEntry && !Entry.validateSecret(secret, type)) {
                secretLayout.setError(callingActivity.getString(R.string.error_invalid_secret));
                return;
            }
            secretLayout.setError(null);

            TokenCalculator.HashAlgorithm algorithm = state.algorithm;
            int digits = Integer.parseInt(digitsInput.getText().toString());

            String issuer = issuerInput.getText().toString();
            String label = labelInput.getText().toString();

            if (type == Entry.OTPType.TOTP || type == Entry.OTPType.STEAM) {
                int period = Integer.parseInt(periodInput.getText().toString());

                if (isNewEntry) {
                    Entry e = new Entry(type, secret, period, digits, issuer, label, algorithm, tagsAdapter.getActiveTags());
                    finishNewEntry(e, state);
                    adapter.addEntry(e);
                } else {
                    oldEntry.setIssuer(issuer, state.autoThumbnail);
                    oldEntry.setLabel(label);
                    oldEntry.setDigits(digits);
                    oldEntry.setPeriod(period);
                    oldEntry.setTags(tagsAdapter.getActiveTags());
                    if (!state.autoThumbnail)
                        oldEntry.setThumbnail(state.thumbnail);

                    oldEntry.updateOTP(true);

                    if (updateCallback != null)
                        updateCallback.onUpdate();
                }

                callingActivity.refreshTags();
            } else if (type == Entry.OTPType.HOTP) {
                long counter = Long.parseLong(counterInput.getText().toString());

                if (isNewEntry) {
                    Entry e = new Entry(type, secret, counter, digits, issuer, label, algorithm, tagsAdapter.getActiveTags());
                    finishNewEntry(e, state);
                    adapter.addEntry(e);
                } else {
                    oldEntry.setIssuer(issuer, state.autoThumbnail);
                    oldEntry.setLabel(label);
                    oldEntry.setDigits(digits);
                    oldEntry.setCounter(counter);
                    oldEntry.setTags(tagsAdapter.getActiveTags());
                    if (!state.autoThumbnail)
                        oldEntry.setThumbnail(state.thumbnail);

                    oldEntry.updateOTP(true);

                    if (updateCallback != null)
                        updateCallback.onUpdate();
                }

                callingActivity.refreshTags();
            } else if (type == Entry.OTPType.MOTP) {
                if (isNewEntry) {
                    Entry newEntry = new Entry(type, secret, issuer, label, tagsAdapter.getActiveTags());
                    finishNewEntry(newEntry, state);
                    adapter.addEntry(newEntry);
                } else {
                    oldEntry.setIssuer(issuer, state.autoThumbnail);
                    oldEntry.setLabel(label);
                    oldEntry.setTags(tagsAdapter.getActiveTags());
                    if (!state.autoThumbnail)
                        oldEntry.setThumbnail(state.thumbnail);

                    oldEntry.updateOTP(false);

                    if (updateCallback != null)
                        updateCallback.onUpdate();
                }

                callingActivity.refreshTags();
            }

            sheet.dismiss();
        });

        /* --- edit mode ---------------------------------------------------------------------- */
        if (!isNewEntry) {
            Entry.OTPType oldType = oldEntry.getType();

            issuerInput.setText(oldEntry.getIssuer());
            labelInput.setText(oldEntry.getLabel());
            secretView.setText(oldEntry.getSecretEncoded());
            digitsInput.setText(String.format(Locale.ENGLISH ,"%d", oldEntry.getDigits()));

            if (oldType == Entry.OTPType.TOTP || oldType == Entry.OTPType.STEAM) {
                periodInput.setText(String.format(Locale.ENGLISH, "%d", oldEntry.getPeriod()));
            } else if (oldType == Entry.OTPType.HOTP) {
                counterInput.setText(String.format(Locale.ENGLISH, "%d", oldEntry.getCounter()));
            }

            for(String tag: oldEntry.getTags()) {
                tagsAdapter.setTagState(tag, true);
            }
            try {
                tagsCallable.call();
            } catch (Exception e) {
                e.printStackTrace();
            }

            secretLayout.setVisibility(View.GONE);
            secretView.setVisibility(View.VISIBLE);

            typeLayout.setEnabled(false);
            algorithmLayout.setEnabled(false);
            digitsInput.setEnabled(oldType != Entry.OTPType.STEAM);
            periodInput.setEnabled(oldType != Entry.OTPType.STEAM);
            counterInput.setEnabled(oldType == Entry.OTPType.HOTP);

            // Show the advanced section so the values being edited are visible
            expandLayout.setVisibility(View.VISIBLE);
            expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand_less, 0);
        }

        applyType.run();
        validate(state, isNewEntry, issuerInput, labelInput, secretInput, digitsInput, periodInput, counterInput, submitButton);

        sheet.show();
    }

    private static void finishNewEntry(Entry e, State state) {
        if (!state.autoThumbnail)
            e.setThumbnail(state.thumbnail);
        e.updateOTP(false);
        e.setLastUsed(System.currentTimeMillis());
    }

    private static void validate(State state, boolean isNewEntry, EditText issuerInput, EditText labelInput,
                                 EditText secretInput, EditText digitsInput, EditText periodInput,
                                 EditText counterInput, MaterialButton submit) {
        if ((TextUtils.isEmpty(labelInput.getText()) && TextUtils.isEmpty(issuerInput.getText())) ||
                (TextUtils.isEmpty(secretInput.getText()) && isNewEntry) ||
                !isNonZeroIntegerInput(digitsInput)) {
            submit.setEnabled(false);
            return;
        }

        Entry.OTPType type = state.type;
        if (type == Entry.OTPType.HOTP) {
            submit.setEnabled(isZeroOrPositiveLongInput(counterInput));
        } else if (type == Entry.OTPType.TOTP || type == Entry.OTPType.STEAM) {
            submit.setEnabled(isNonZeroIntegerInput(periodInput));
        } else {
            submit.setEnabled(true);
        }
    }

    private static boolean isNonZeroIntegerInput(EditText editText) {
        try {
            Editable text = editText.getText();
            return !TextUtils.isEmpty(text) && (Integer.parseInt(text.toString()) != 0);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isZeroOrPositiveLongInput(EditText editText) {
        try {
            Editable text = editText.getText();
            return !TextUtils.isEmpty(text) && (Long.parseLong(text.toString()) >= 0);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static EntryThumbnail.EntryThumbnails thumbnailFor(String issuer) {
        if (TextUtils.isEmpty(issuer))
            return EntryThumbnail.EntryThumbnails.Default;
        try {
            return EntryThumbnail.EntryThumbnails.valueOfIgnoreCase(issuer);
        } catch (Exception e) {
            try {
                return EntryThumbnail.EntryThumbnails.valueOfFuzzy(issuer);
            } catch (Exception e2) {
                return EntryThumbnail.EntryThumbnails.Default;
            }
        }
    }

    private static String typeLabel(Context context, Entry.OTPType type) {
        switch (type) {
            case HOTP: return context.getString(R.string.type_hotp);
            case MOTP: return context.getString(R.string.type_motp);
            case STEAM: return context.getString(R.string.type_steam);
            case TOTP:
            default: return context.getString(R.string.type_totp);
        }
    }

    private static void showHelp(Context context, int titleRes, int messageRes) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static void expand(@NonNull BottomSheetDialog sheet) {
        View content = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (content != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(content);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }
    }

    private static class State {
        Entry.OTPType type = Entry.OTPType.TOTP;
        TokenCalculator.HashAlgorithm algorithm = TokenCalculator.DEFAULT_ALGORITHM;
        EntryThumbnail.EntryThumbnails thumbnail = EntryThumbnail.EntryThumbnails.Default;
        boolean autoThumbnail = true;
    }

    public interface UpdateCallback {
        void onUpdate();
    }
}
