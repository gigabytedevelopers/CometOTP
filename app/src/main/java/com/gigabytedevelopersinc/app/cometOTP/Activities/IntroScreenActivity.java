package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ConfirmedPasswordTransformationHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EditorActionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper;
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.IntroActivity;
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.SlideFragment;
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.FragmentSlide;
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.SimpleSlide;
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.Slide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class IntroScreenActivity extends IntroActivity {
    private static final String STATE_ENCRYPTION_TYPE = "IntroScreenActivity.encryptionType";
    private static final String STATE_AUTH_METHOD = "IntroScreenActivity.authMethod";
    private static final String STATE_SYNC_ENABLED = "IntroScreenActivity.syncEnabled";
    private static final String STATE_SETUP_FINISHED = "IntroScreenActivity.setupFinished";

    // Slide positions, see onCreate()
    static final int SLIDE_WELCOME = 0;
    static final int SLIDE_ENCRYPTION = 1;
    static final int SLIDE_AUTHENTICATION = 2;
    static final int SLIDE_ANDROID_SYNC = 3;
    static final int SLIDE_FINISHED = 4;

    private Settings settings;

    /* The activity is the source of truth for the choices made on the slides. The fragments
     * report their changes here and read their initial state back from here, so the intro
     * survives being recreated (rotation on large screens, where Android 16+ ignores the
     * portrait lock, or process death) without relying on fragment instances that the
     * FragmentManager may have restored on its own. */
    private Constants.EncryptionType encryptionType = Constants.EncryptionType.KEYSTORE;
    private Constants.AuthMethod authMethod = Constants.AuthMethod.NONE;
    private boolean syncEnabled = false;

    private boolean setupFinished = false;

    private void saveSettings() {
        String password = null;

        if (authMethod == Constants.AuthMethod.PASSWORD || authMethod == Constants.AuthMethod.PIN) {
            AuthenticationFragment authenticationFragment = getAuthenticationFragment();
            password = authenticationFragment != null ? authenticationFragment.getPassword() : null;

            if (password == null || password.isEmpty()) {
                SimpleSlide finalSlide = (SimpleSlide) getSlide(getCount() - 1);

                if (finalSlide != null) {
                    Fragment finalFragment = finalSlide.getFragment();

                    if (finalFragment != null) {
                        View finalView = finalFragment.getView();

                        if (finalView != null) {
                            TextView title = finalView.findViewById(R.id.mi_title);
                            TextView desc = finalView.findViewById(R.id.mi_description);

                            title.setText(R.string.intro_slide4_title_failed);
                            desc.setText(R.string.intro_slide4_desc_failed);
                        }
                    }
                }

                return;
            }
        }

        settings.setEncryption(encryptionType);
        settings.setAuthMethod(authMethod);
        settings.setAndroidBackupServiceEnabled(syncEnabled);

        if (authMethod == Constants.AuthMethod.PASSWORD || authMethod == Constants.AuthMethod.PIN)
            settings.setAuthCredentials(password);

        settings.setFirstTimeWarningShown(true);
        setupFinished = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        settings = new Settings(this);

        if (savedInstanceState != null) {
            encryptionType = Constants.EncryptionType.valueOf(savedInstanceState.getString(STATE_ENCRYPTION_TYPE, encryptionType.name()));
            authMethod = Constants.AuthMethod.valueOf(savedInstanceState.getString(STATE_AUTH_METHOD, authMethod.name()));
            syncEnabled = savedInstanceState.getBoolean(STATE_SYNC_ENABLED, syncEnabled);
            setupFinished = savedInstanceState.getBoolean(STATE_SETUP_FINISHED, setupFinished);
        }

        setButtonBackFunction(BUTTON_BACK_FUNCTION_BACK);

        addSlide(new SimpleSlide.Builder()
                .title(R.string.intro_slide1_title)
                .description(R.string.intro_slide1_desc)
                .background(R.color.colorPrimary)
                .backgroundDark(R.color.colorPrimaryDark)
                .canGoBackward(false)
                .scrollable(false)
                .build()
        );

        addSlide(new FragmentSlide.Builder()
                .background(R.color.colorPrimary)
                .backgroundDark(R.color.colorPrimaryDark)
                .fragment(new EncryptionFragment())
                .build()
        );

        addSlide(new FragmentSlide.Builder()
                .background(R.color.colorPrimary)
                .backgroundDark(R.color.colorPrimaryDark)
                .fragment(new AuthenticationFragment())
                .build()
        );

        addSlide(new FragmentSlide.Builder()
                .background(R.color.colorPrimary)
                .backgroundDark(R.color.colorPrimaryDark)
                .fragment(new AndroidSyncFragment())
                .build()
        );

        addSlide(new SimpleSlide.Builder()
                .title(R.string.intro_slide4_title)
                .description(R.string.intro_slide4_desc)
                .background(R.color.colorPrimary)
                .backgroundDark(R.color.colorPrimaryDark)
                .scrollable(false)
                .build()
        );

        addOnNavigationBlockedListener((position, direction) -> {
            if (position == SLIDE_AUTHENTICATION) {
                AuthenticationFragment authenticationFragment = getAuthenticationFragment();
                if (authenticationFragment != null)
                    authenticationFragment.flashWarning();
            }
        });

        addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (position == getCount() - 1)
                    saveSettings();
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_ENCRYPTION_TYPE, encryptionType.name());
        outState.putString(STATE_AUTH_METHOD, authMethod.name());
        outState.putBoolean(STATE_SYNC_ENABLED, syncEnabled);
        outState.putBoolean(STATE_SETUP_FINISHED, setupFinished);
    }

    @Override
    public Intent onSendActivityResult(int result) {
        Intent data = new Intent();
        data.putExtra(Constants.EXTRA_INTRO_FINISHED, setupFinished);
        return data;
    }

    @Override
    protected void onBackNavigation() {
        // We don't want users to quit the intro screen and end up in an uninitialized state,
        // so the back gesture only walks back through the slides.
        if (getCurrentSlidePosition() > 0)
            previousSlide();
    }

    /* Accessors for the slide fragments. The adapter swaps in the instances restored by the
     * FragmentManager, so they are always looked up instead of being cached in fields. */
    @Nullable
    private <T extends Fragment> T getSlideFragment(int position, Class<T> type) {
        if (position < 0 || position >= getCount())
            return null;

        Slide slide = getSlide(position);
        if (!(slide instanceof FragmentSlide))
            return null;

        Fragment fragment = ((FragmentSlide) slide).getFragment();
        return type.isInstance(fragment) ? type.cast(fragment) : null;
    }

    @Nullable
    private AuthenticationFragment getAuthenticationFragment() {
        return getSlideFragment(SLIDE_AUTHENTICATION, AuthenticationFragment.class);
    }

    @Nullable
    static IntroScreenActivity hostOf(Fragment fragment) {
        return fragment.getActivity() instanceof IntroScreenActivity ? (IntroScreenActivity) fragment.getActivity() : null;
    }

    Constants.EncryptionType getEncryptionType() {
        return encryptionType;
    }

    Constants.AuthMethod getAuthMethod() {
        return authMethod;
    }

    void onEncryptionTypeSelected(Constants.EncryptionType newEncryptionType) {
        encryptionType = newEncryptionType;

        AuthenticationFragment authenticationFragment = getAuthenticationFragment();
        if (authenticationFragment != null)
            authenticationFragment.updateEncryptionType(newEncryptionType);
    }

    void onAuthMethodSelected(Constants.AuthMethod newAuthMethod) {
        authMethod = newAuthMethod;
    }

    void onSyncEnabledChanged(boolean enabled) {
        syncEnabled = enabled;
    }

    public static class EncryptionFragment extends SlideFragment {
        private Spinner selection;
        private TextView desc;

        private SparseArray<Constants.EncryptionType> selectionMapping;

        public EncryptionFragment() {
        }

        private void generateSelectionMapping() {
            String[] encValues = getResources().getStringArray(R.array.settings_values_encryption);

            selectionMapping = new SparseArray<>();
            for (int i = 0; i < encValues.length; i++)
                selectionMapping.put(i, Constants.EncryptionType.valueOf(encValues[i].toUpperCase()));
        }

        public Constants.EncryptionType getEncryptionType() {
            if (selection == null || selectionMapping == null)
                return Constants.EncryptionType.KEYSTORE;

            return selectionMapping.get(selection.getSelectedItemPosition());
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.component_intro_encryption, container, false);

            selection = root.findViewById(R.id.introEncryptionSelection);
            desc = root.findViewById(R.id.introEncryptionDesc);

            generateSelectionMapping();

            selection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    Constants.EncryptionType encryptionType = selectionMapping.get(i);

                    if (encryptionType == Constants.EncryptionType.PASSWORD)
                        desc.setText(R.string.intro_slide2_desc_password);
                    else if (encryptionType == Constants.EncryptionType.KEYSTORE)
                        desc.setText(R.string.intro_slide2_desc_keystore);

                    IntroScreenActivity host = hostOf(EncryptionFragment.this);
                    if (host != null)
                        host.onEncryptionTypeSelected(encryptionType);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });

            IntroScreenActivity host = hostOf(this);
            Constants.EncryptionType initialType = host != null ? host.getEncryptionType() : Constants.EncryptionType.KEYSTORE;
            selection.setSelection(selectionMapping.indexOfValue(initialType));

            return root;
        }
    }

    public static class AndroidSyncFragment extends SlideFragment {
        private SwitchCompat introAndroidSync;

        public AndroidSyncFragment() {
        }

        public boolean getSyncEnabled()
        {
            return introAndroidSync != null && introAndroidSync.isChecked();
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View root = inflater.inflate(R.layout.component_intro_android_sync, container, false);

            IntroScreenActivity host = hostOf(this);
            boolean syncPossible = host != null && host.getEncryptionType() != Constants.EncryptionType.KEYSTORE;

            introAndroidSync = root.findViewById(R.id.introAndroidSync);
            introAndroidSync.setOnCheckedChangeListener((compoundButton, b) -> {
                compoundButton.setText(b ?
                        R.string.settings_toast_android_sync_enabled :
                        R.string.settings_toast_android_sync_disabled
                );

                IntroScreenActivity activity = hostOf(AndroidSyncFragment.this);
                if (activity != null)
                    activity.onSyncEnabledChanged(b);
            });

            introAndroidSync.setChecked(syncPossible);
            introAndroidSync.setEnabled(syncPossible);

            return root;
        }
    }

    public static class AuthenticationFragment extends SlideFragment implements TextView.OnEditorActionListener {
        private Constants.EncryptionType encryptionType = Constants.EncryptionType.KEYSTORE;

        private int minLength = Constants.AUTH_MIN_PASSWORD_LENGTH;
        private String lengthWarning = "";
        private String noPasswordWarning = "";
        private String confirmPasswordWarning = "";
        private String passwordMismatchWarning = "";

        private TextView desc = null;
        private Spinner selection = null;
        private TextView authWarnings = null;
        private LinearLayout credentialsLayout = null;
        private TextInputLayout passwordLayout = null;
        private TextInputEditText passwordInput = null;
        private EditText passwordConfirm = null;

        private SparseArray<Constants.AuthMethod> selectionMapping;

        public AuthenticationFragment() {
        }

        public void updateEncryptionType(Constants.EncryptionType encryptionType) {
            this.encryptionType = encryptionType;

            if (desc != null) {
                if (encryptionType == Constants.EncryptionType.KEYSTORE) {
                    desc.setText(R.string.intro_slide3_desc_keystore);

                    selection.setSelection(selectionMapping.indexOfValue(Constants.AuthMethod.NONE));
                } else if (encryptionType == Constants.EncryptionType.PASSWORD) {
                    desc.setText(R.string.intro_slide3_desc_password);

                    Constants.AuthMethod selectedMethod = selectionMapping.get(selection.getSelectedItemPosition());
                    if (selectedMethod != Constants.AuthMethod.PASSWORD && selectedMethod != Constants.AuthMethod.PIN )
                        selection.setSelection(selectionMapping.indexOfValue(Constants.AuthMethod.PASSWORD));
                }
            }
        }

        private void generateSelectionMapping() {
            Constants.AuthMethod[] authValues = Constants.AuthMethod.values();

            selectionMapping = new SparseArray<>();
            for (int i = 0; i < authValues.length; i++)
                selectionMapping.put(i, authValues[i]);
        }

        @SuppressWarnings("SameParameterValue")
        private void updateWarning(int resId) {
            updateWarning(getString(resId));
        }

        private void updateWarning(String warning) {
            authWarnings.setText(warning);
        }

        private void hideWarning() {
            authWarnings.setVisibility(View.GONE);
            authWarnings.setText(null);
        }

        public void flashWarning() {
            if (authWarnings == null)
                return;

            if (authWarnings.getText().toString().isEmpty()) {
                authWarnings.setVisibility(View.GONE);
            } else {
                authWarnings.setVisibility(View.VISIBLE);
                ObjectAnimator animator = ObjectAnimator.ofInt(authWarnings, "backgroundColor",
                        Color.TRANSPARENT, ContextCompat.getColor(requireContext(), R.color.warning_red), Color.TRANSPARENT);
                animator.setDuration(500);
                animator.setRepeatCount(0);
                animator.setInterpolator(new AccelerateDecelerateInterpolator());
                animator.setEvaluator(new ArgbEvaluator());
                animator.start();
            }
        }

        public Constants.AuthMethod getAuthMethod() {
            if (selection == null || selectionMapping == null)
                return Constants.AuthMethod.NONE;

            return selectionMapping.get(selection.getSelectedItemPosition());
        }

        @Nullable
        public String getPassword() {
            if (passwordInput != null && passwordInput.getText() != null)
                return passwordInput.getText().toString();
            else
                return null;
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final View root = inflater.inflate(R.layout.component_intro_authentication, container, false);

            desc = root.findViewById(R.id.introAuthDesc);
            selection = root.findViewById(R.id.introAuthSelection);
            authWarnings = root.findViewById(R.id.introAuthWarnings);
            credentialsLayout = root.findViewById(R.id.introCredentialsLayout);
            passwordLayout = root.findViewById(R.id.introPasswordLayout);
            passwordInput = root.findViewById(R.id.introPasswordEdit);
            passwordConfirm = root.findViewById(R.id.introPasswordConfirm);

            generateSelectionMapping();

            // Pick up the choices made so far (also after the activity has been recreated)
            IntroScreenActivity host = hostOf(this);
            Constants.AuthMethod initialMethod = Constants.AuthMethod.NONE;
            if (host != null) {
                encryptionType = host.getEncryptionType();
                initialMethod = host.getAuthMethod();
            }
            if (encryptionType == Constants.EncryptionType.PASSWORD) {
                desc.setText(R.string.intro_slide3_desc_password);
                if (initialMethod != Constants.AuthMethod.PASSWORD && initialMethod != Constants.AuthMethod.PIN)
                    initialMethod = Constants.AuthMethod.PASSWORD;
            } else {
                desc.setText(R.string.intro_slide3_desc_keystore);
            }

            final String[] authEntries = getResources().getStringArray(R.array.settings_entries_auth);
            ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(getIntroActivity(), android.R.layout.simple_spinner_item, authEntries) {
                @Override
                public boolean isEnabled(int position){
                    return encryptionType != Constants.EncryptionType.PASSWORD ||
                            position == selectionMapping.indexOfValue(Constants.AuthMethod.PASSWORD) ||
                            position == selectionMapping.indexOfValue(Constants.AuthMethod.PIN);
                }

                @Override
                public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;

                    tv.setEnabled(encryptionType != Constants.EncryptionType.PASSWORD ||
                            position == selectionMapping.indexOfValue(Constants.AuthMethod.PASSWORD) ||
                            position == selectionMapping.indexOfValue(Constants.AuthMethod.PIN));

                    return view;
                }
            };

            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            selection.setAdapter(spinnerArrayAdapter);

            selection.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    Constants.AuthMethod authMethod = selectionMapping.get(i);

                    IntroScreenActivity activity = hostOf(AuthenticationFragment.this);
                    if (activity != null)
                        activity.onAuthMethodSelected(authMethod);

                    if (authMethod == Constants.AuthMethod.PASSWORD) {
                        setupForPasswordInput();
                    } else if (authMethod == Constants.AuthMethod.PIN) {
                        setupForPinInput();
                    } else {
                        credentialsLayout.setVisibility(View.INVISIBLE);
                        UIHelper.hideKeyboard(getIntroActivity(), root);
                    }

                    passwordInput.setText(null);
                    passwordConfirm.setText(null);

                    authWarnings.setVisibility(View.GONE);

                    updateNavigation();
                }

                private void setupForPasswordInput() {
                    credentialsLayout.setVisibility(View.VISIBLE);

                    passwordLayout.setHint(getString(R.string.settings_hint_password));
                    passwordConfirm.setHint(R.string.settings_hint_password_confirm);

                    passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    passwordConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

                    ConfirmedPasswordTransformationHelper.setup(passwordLayout, passwordInput, passwordConfirm);

                    minLength = Constants.AUTH_MIN_PASSWORD_LENGTH;
                    lengthWarning = getString(R.string.settings_label_short_password, minLength);
                    noPasswordWarning = getString(R.string.intro_slide3_warn_no_password);
                    confirmPasswordWarning = getString(R.string.intro_slide3_warn_confirm_password);
                    passwordMismatchWarning = getString(R.string.intro_slide3_warn_password_mismatch);

                    focusOnPasswordInput();
                }

                private void focusOnPasswordInput() {
                    if (getIntroActivity().getCurrentSlidePosition() == SLIDE_AUTHENTICATION) {
                        passwordInput.requestFocus();
                        UIHelper.showKeyboard(getContext(), passwordInput);
                    }
                }

                private void setupForPinInput() {
                    credentialsLayout.setVisibility(View.VISIBLE);

                    passwordLayout.setHint(getString(R.string.settings_hint_pin));
                    passwordConfirm.setHint(R.string.settings_hint_pin_confirm);

                    passwordInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
                    passwordConfirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

                    ConfirmedPasswordTransformationHelper.setup(passwordLayout, passwordInput, passwordConfirm);

                    minLength = Constants.AUTH_MIN_PIN_LENGTH;
                    lengthWarning = getString(R.string.settings_label_short_pin, minLength);
                    noPasswordWarning = getString(R.string.intro_slide3_warn_no_pin);
                    confirmPasswordWarning = getString(R.string.intro_slide3_warn_confirm_pin);
                    passwordMismatchWarning = getString(R.string.intro_slide3_warn_pin_mismatch);

                    focusOnPasswordInput();
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    updateNavigation();
                }

                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            };

            passwordInput.addTextChangedListener(textWatcher);
            passwordConfirm.addTextChangedListener(textWatcher);

            passwordConfirm.setOnEditorActionListener(this);

            selection.setSelection(selectionMapping.indexOfValue(initialMethod));

            return root;
        }

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (EditorActionHelper.isActionDoneOrKeyboardEnter(actionId, event)) {
                nextSlide();
                return true;
            } else {
                // Ignore action up after keyboard enter. Otherwise the go-back button would be selected
                // after pressing enter with an invalid password.
                return EditorActionHelper.isActionUpKeyboardEnter(event);
            }
        }

        @Override
        public boolean canGoForward() {
            if (selection == null || passwordInput == null || passwordConfirm == null) {
                // The view has not been created (yet); nothing to validate against.
                return false;
            }

            Constants.AuthMethod authMethod = selectionMapping.get(selection.getSelectedItemPosition());

            if (authMethod == Constants.AuthMethod.PIN || authMethod == Constants.AuthMethod.PASSWORD) {
                String password = null;

                if (passwordInput.getText() != null)
                    password = passwordInput.getText().toString();

                String confirm = passwordConfirm.getText().toString();

                if (password != null && !password.isEmpty()) {
                    if (password.length() < minLength) {
                        updateWarning(lengthWarning);
                        return false;
                    } else {
                        if (!confirm.isEmpty()) {
                            if (confirm.equals(password)) {
                                hideWarning();
                                return true;
                            } else {
                                updateWarning(passwordMismatchWarning);
                                return false;
                            }
                        } else {
                            updateWarning(confirmPasswordWarning);
                            return false;
                        }
                    }
                } else {
                    updateWarning(noPasswordWarning);
                    return false;
                }
            } else if (authMethod == Constants.AuthMethod.DEVICE) {
                Context context = getContext();
                if (context == null)
                    return false;

                KeyguardManager km = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);

                if (! km.isKeyguardSecure()) {
                    updateWarning(R.string.settings_toast_auth_device_not_secure);
                    return false;
                }

                hideWarning();
                return true;
            } else {
                hideWarning();
                return true;
            }
        }
    }
}
