package com.gigabytedevelopersinc.app.CometOTP.View;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.github.aakira.expandablelayout.ExpandableLayoutListenerAdapter;
import com.github.aakira.expandablelayout.ExpandableLinearLayout;

import com.gigabytedevelopersinc.app.CometOTP.Activities.MainActivity;
import com.gigabytedevelopersinc.app.CometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.CometOTP.R;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.CometOTP.Utilities.TokenCalculator;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

public class ManualEntryDialog {
    public static void show(final MainActivity callingActivity, Settings settings, final EntriesCardAdapter adapter) {
        ViewGroup container = callingActivity.findViewById(R.id.main_content);
        View inputView = callingActivity.getLayoutInflater().inflate(R.layout.dialog_manual_entry, container, false);

        final Spinner typeInput = inputView.findViewById(R.id.manual_type);
        final EditText labelInput = inputView.findViewById(R.id.manual_label);
        final EditText secretInput = inputView.findViewById(R.id.manual_secret);
        final EditText counterInput = inputView.findViewById(R.id.manual_counter);
        final EditText periodInput = inputView.findViewById(R.id.manual_period);
        final EditText digitsInput = inputView.findViewById(R.id.manual_digits);
        final LinearLayout counterLayout = inputView.findViewById(R.id.manual_layout_counter);
        final LinearLayout periodLayout = inputView.findViewById(R.id.manual_layout_period);
        final Spinner algorithmInput = inputView.findViewById(R.id.manual_algorithm);
        final Button tagsInput = inputView.findViewById(R.id.manual_tags);

        final ArrayAdapter<TokenCalculator.HashAlgorithm> algorithmAdapter = new ArrayAdapter<>(callingActivity, android.R.layout.simple_expandable_list_item_1, TokenCalculator.HashAlgorithm.values());
        final ArrayAdapter<Entry.OTPType> typeAdapter = new ArrayAdapter<>(callingActivity, android.R.layout.simple_expandable_list_item_1, Entry.PublicTypes.toArray(new Entry.OTPType[Entry.PublicTypes.size()]));
        final ArrayAdapter<Entry.OTPType> fullTypeAdapter = new ArrayAdapter<>(callingActivity, android.R.layout.simple_expandable_list_item_1, Entry.OTPType.values());

        if (settings.getSpecialFeatures())
            typeInput.setAdapter(fullTypeAdapter);
        else
            typeInput.setAdapter(typeAdapter);

        algorithmInput.setAdapter(algorithmAdapter);

        periodInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_PERIOD));
        digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS));
        counterInput.setText(String.format(Locale.US, "%d", TokenCalculator.HOTP_INITIAL_COUNTER));

        typeInput.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                Entry.OTPType type = (Entry.OTPType) adapterView.getSelectedItem();

                if (type == Entry.OTPType.STEAM) {
                    counterLayout.setVisibility(View.GONE);
                    periodLayout.setVisibility(View.VISIBLE);

                    digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.STEAM_DEFAULT_DIGITS));
                    periodInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_PERIOD));
                    algorithmInput.setSelection(algorithmAdapter.getPosition(TokenCalculator.HashAlgorithm.SHA1));

                    digitsInput.setEnabled(false);
                    periodInput.setEnabled(false);
                    algorithmInput.setEnabled(false);
                } else if (type == Entry.OTPType.TOTP) {
                    counterLayout.setVisibility(View.GONE);
                    periodLayout.setVisibility(View.VISIBLE);

                    digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS));
                    digitsInput.setEnabled(true);
                    periodInput.setEnabled(true);
                    algorithmInput.setEnabled(true);
                } else if (type == Entry.OTPType.HOTP) {
                    counterLayout.setVisibility(View.VISIBLE);
                    periodLayout.setVisibility(View.GONE);

                    digitsInput.setText(String.format(Locale.US, "%d", TokenCalculator.TOTP_DEFAULT_DIGITS));
                    digitsInput.setEnabled(true);
                    periodInput.setEnabled(true);
                    algorithmInput.setEnabled(true);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        List<String> allTags = adapter.getTags();
        HashMap<String, Boolean> tagsHashMap = new HashMap<>();
        for(String tag: allTags) {
            tagsHashMap.put(tag, false);
        }
        final TagsAdapter tagsAdapter = new TagsAdapter(callingActivity, tagsHashMap);

        final Callable tagsCallable = new Callable() {
            @Override
            public Object call() throws Exception {
                List<String> selectedTags = tagsAdapter.getActiveTags();
                StringBuilder stringBuilder = new StringBuilder();
                for(int j = 0; j < selectedTags.size(); j++) {
                    stringBuilder.append(selectedTags.get(j));
                    if(j < selectedTags.size() - 1) {
                        stringBuilder.append(", ");
                    }
                }
                tagsInput.setText(stringBuilder.toString());
                return null;
            }
        };

        tagsInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TagsDialog.show(callingActivity, tagsAdapter, tagsCallable, tagsCallable);
            }
        });

        final Button expandButton = inputView.findViewById(R.id.dialog_expand_button);
        final ExpandableLinearLayout expandLayout = inputView.findViewById(R.id.dialog_expand_layout);

        expandButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                expandLayout.toggle();
            }
        });

        expandLayout.setListener(new ExpandableLayoutListenerAdapter() {
            @Override
            public void onOpened() {
                super.onOpened();
                expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up_accent, 0);
            }

            @Override
            public void onClosed() {
                super.onClosed();
                expandButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down_accent, 0);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(callingActivity);
        builder.setTitle(R.string.dialog_title_manual_entry)
                .setView(inputView)
                .setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Entry.OTPType type = (Entry.OTPType) typeInput.getSelectedItem();
                        TokenCalculator.HashAlgorithm algorithm = (TokenCalculator.HashAlgorithm) algorithmInput.getSelectedItem();

                        String label = labelInput.getText().toString();
                        String secret = secretInput.getText().toString();
                        int digits = Integer.parseInt(digitsInput.getText().toString());

                        if (type == Entry.OTPType.TOTP || type == Entry.OTPType.STEAM) {
                            int period = Integer.parseInt(periodInput.getText().toString());

                            Entry e = new Entry(type, secret, period, digits, label, algorithm, tagsAdapter.getActiveTags());
                            e.updateOTP();
                            adapter.addEntry(e);
                            adapter.saveEntries();

                            callingActivity.refreshTags();
                        } else if (type == Entry.OTPType.HOTP) {
                            long counter = Long.parseLong(counterInput.getText().toString());

                            Entry e = new Entry(type, secret, counter, digits, label, algorithm, tagsAdapter.getActiveTags());
                            e.updateOTP();
                            adapter.addEntry(e);
                            adapter.saveEntries();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {}
                });

        AlertDialog dialog = builder.create();
        dialog.show();

        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(false);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (TextUtils.isEmpty(labelInput.getText()) || TextUtils.isEmpty(secretInput.getText())
                        || TextUtils.isEmpty(digitsInput.getText()) || Integer.parseInt(digitsInput.getText().toString()) == 0) {
                    positiveButton.setEnabled(false);
                } else {
                    Entry.OTPType type = (Entry.OTPType) typeInput.getSelectedItem();
                    if (type == Entry.OTPType.HOTP) {
                        if (TextUtils.isEmpty(counterInput.getText())) {
                            positiveButton.setEnabled(false);
                        } else {
                            positiveButton.setEnabled(true);
                        }
                    } else if (type == Entry.OTPType.TOTP || type == Entry.OTPType.STEAM) {
                        if (TextUtils.isEmpty(periodInput.getText()) || Integer.parseInt(periodInput.getText().toString()) == 0) {
                            positiveButton.setEnabled(false);
                        } else {
                            positiveButton.setEnabled(true);
                        }
                    } else {
                        positiveButton.setEnabled(true);
                    }
                }
            }
        };

        labelInput.addTextChangedListener(watcher);
        secretInput.addTextChangedListener(watcher);
        periodInput.addTextChangedListener(watcher);
        digitsInput.addTextChangedListener(watcher);
        counterInput.addTextChangedListener(watcher);
    }
}
