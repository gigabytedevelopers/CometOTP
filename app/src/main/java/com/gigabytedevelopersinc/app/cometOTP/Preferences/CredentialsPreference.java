package com.gigabytedevelopersinc.app.cometOTP.Preferences;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.preference.DialogPreference;

import com.gigabytedevelopersinc.app.cometOTP.Utilities.GeneralUtils;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static android.content.Context.KEYGUARD_SERVICE;
import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod;
import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType;

public class CredentialsPreference extends DialogPreference
    implements AdapterView.OnItemClickListener, View.OnClickListener, TextWatcher {
    public static final AuthMethod DEFAULT_VALUE = AuthMethod.NONE;

    public interface EncryptionChangeCallback {
        boolean testEncryptionChange(byte[] newKey);
    }

    private List<String> entries;
    private static final List<AuthMethod> entryValues = Arrays.asList(
            AuthMethod.NONE,
            AuthMethod.PASSWORD,
            AuthMethod.PIN,
            AuthMethod.DEVICE
    );

    private int minLength = 0;

    private Settings settings;
    private AuthMethod value = AuthMethod.NONE;
    private EncryptionChangeCallback encryptionChangeCallback = null;

    private LinearLayout credentialsLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText passwordInput;
    private EditText passwordConfirm;
    private TextView tooShortWarning;

    private Button btnSave;

    public CredentialsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        settings = new Settings(context);
        entries = Arrays.asList(context.getResources().getStringArray(R.array.settings_entries_auth));

        setDialogLayoutResource(R.layout.component_authentication);
    }

    public void setEncryptionChangeCallback(EncryptionChangeCallback cb) {
        this.encryptionChangeCallback = cb;
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        builder.setPositiveButton(null, null);
        builder.setNegativeButton(null, null);
        builder.setCancelable(false);
    }

    @Override
    protected void onBindDialogView(View view) {
        value = settings.getAuthMethod();

        ListView listView = view.findViewById(R.id.credentialSelection);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_single_choice, entries);
        listView.setAdapter(adapter);

        int index = entryValues.indexOf(value);
        listView.setSelection(index);
        listView.setItemChecked(index,true);
        listView.setOnItemClickListener(this);

        credentialsLayout = view.findViewById(R.id.credentialsLayout);

        passwordLayout = view.findViewById(R.id.passwordLayout);
        passwordInput = view.findViewById(R.id.passwordEdit);
        passwordConfirm = view.findViewById(R.id.passwordConfirm);

        if (settings.getBlockAccessibility()) {
            passwordLayout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            passwordConfirm.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        if (GeneralUtils.INSTANCE.isOreo() && settings.getBlockAutofill()) {
            passwordLayout.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
            passwordConfirm.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }

        tooShortWarning = view.findViewById(R.id.tooShortWarning);

        passwordInput.addTextChangedListener(this);
        passwordConfirm.addTextChangedListener(this);

        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnSave = view.findViewById(R.id.btnSave);

        btnCancel.setOnClickListener(this);
        btnSave.setOnClickListener(this);

        updateLayout();

        super.onBindDialogView(view);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            String stringValue = getPersistedString(DEFAULT_VALUE.name().toLowerCase(Locale.ENGLISH));
            value = AuthMethod.valueOf(stringValue.toUpperCase(Locale.ENGLISH));
        } else {
            value = DEFAULT_VALUE;
            persistString(value.name().toLowerCase(Locale.ENGLISH));
        }

        setSummary(entries.get(entryValues.indexOf(value)));
    }

    private void saveValues() {
        byte[] newKey = null;

        if (settings.getEncryption() == EncryptionType.PASSWORD) {
            if (value == AuthMethod.NONE || value == AuthMethod.DEVICE) {
                UIHelper.showGenericDialog(getContext(), R.string.settings_dialog_title_error, R.string.settings_dialog_msg_auth_invalid_with_encryption);
                return;
            }
        }

        if (value == AuthMethod.DEVICE) {
            KeyguardManager km = (KeyguardManager) getContext().getSystemService(KEYGUARD_SERVICE);

            assert km != null;
            if (! km.isKeyguardSecure()) {
                Toast.makeText(getContext(), R.string.settings_toast_auth_device_not_secure, Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (value == AuthMethod.PASSWORD || value == AuthMethod.PIN) {
            String password = Objects.requireNonNull(passwordInput.getText()).toString();
            if (!password.isEmpty()) {
                newKey = settings.setAuthCredentials(password);
            } else {
                return;
            }
        }

        if (settings.getEncryption() == EncryptionType.PASSWORD) {
            if (newKey == null || encryptionChangeCallback == null)
                return;

            if (! encryptionChangeCallback.testEncryptionChange(newKey))
                return;
        }

        persistString(value.toString().toLowerCase());
        setSummary(entries.get(entryValues.indexOf(value)));
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case (R.id.btnCancel):
                getDialog().dismiss();
                break;
            case (R.id.btnSave):
                saveValues();
                getDialog().dismiss();
                break;
            default:
                break;
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        String password = passwordInput.getEditableText().toString();

        if (password.length() >= minLength) {
            tooShortWarning.setVisibility(View.GONE);

            String confirm = passwordConfirm.getEditableText().toString();

            if (!password.isEmpty() && !confirm.isEmpty() && password.equals(confirm)) {
                btnSave.setEnabled(true);
            } else {
                btnSave.setEnabled(false);
            }
        } else {
            tooShortWarning.setVisibility(View.VISIBLE);
        }
    }

    private void updateLayout() {
        if (value == AuthMethod.NONE) {
            credentialsLayout.setVisibility(View.GONE);

            if (getDialog() != null)
                UIHelper.hideKeyboard(getContext(), getDialog().getCurrentFocus());

            btnSave.setEnabled(true);
        } else if (value == AuthMethod.PASSWORD) {
            credentialsLayout.setVisibility(View.VISIBLE);

            passwordLayout.setHint(getContext().getString(R.string.settings_hint_password));
            passwordConfirm.setHint(R.string.settings_hint_password_confirm);

            passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

            passwordInput.setTransformationMethod(new PasswordTransformationMethod());
            passwordConfirm.setTransformationMethod(new PasswordTransformationMethod());

            minLength = Constants.AUTH_MIN_PASSWORD_LENGTH;
            tooShortWarning.setText(getContext().getString(R.string.settings_label_short_password, minLength));

            passwordInput.requestFocus();
            UIHelper.showKeyboard(getContext(), passwordInput);

            btnSave.setEnabled(false);
        } else if (value == AuthMethod.PIN) {
            credentialsLayout.setVisibility(View.VISIBLE);

            passwordLayout.setHint(getContext().getString(R.string.settings_hint_pin));
            passwordConfirm.setHint(R.string.settings_hint_pin_confirm);

            passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            passwordConfirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

            passwordInput.setTransformationMethod(new PasswordTransformationMethod());
            passwordConfirm.setTransformationMethod(new PasswordTransformationMethod());

            minLength = Constants.AUTH_MIN_PIN_LENGTH;
            tooShortWarning.setText(getContext().getString(R.string.settings_label_short_pin, minLength));

            passwordInput.requestFocus();
            UIHelper.showKeyboard(getContext(), passwordInput);

            btnSave.setEnabled(false);
        } else if (value == AuthMethod.DEVICE) {
            credentialsLayout.setVisibility(View.GONE);

            if (getDialog() != null)
                UIHelper.hideKeyboard(getContext(), getDialog().getCurrentFocus());

            btnSave.setEnabled(true);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        value = entryValues.get(position);
        updateLayout();
    }

    // Needed stub functions
    @Override
    public void afterTextChanged(Editable s) {}

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}


}
