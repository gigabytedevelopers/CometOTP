package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.annotation.SuppressLint;
import androidx.appcompat.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.HideableDialog;
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ManualEntryDialog;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.LauncherIcon;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.NotificationHelper;
import com.gigabytedevelopersinc.app.cometOTP.View.NotchedBottomBar;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ScanQRCodeFromFile;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper;
import com.gigabytedevelopersinc.app.cometOTP.View.CoachMarkOverlay;
import com.gigabytedevelopersinc.app.cometOTP.View.CountdownRingView;
import com.gigabytedevelopersinc.app.cometOTP.View.EntriesCardAdapter;
import com.gigabytedevelopersinc.app.cometOTP.View.ItemTouchHelper.SimpleItemTouchHelperCallback;
import com.gigabytedevelopersinc.app.cometOTP.View.TagsAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.crypto.SecretKey;

import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AppStart;
import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod;
import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType;
import static com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SortMode;

import me.zhanghai.android.fastscroll.FastScrollerBuilder;

public class MainActivity extends BaseActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static long animatorDuration = 1000;

    private static final String INTENT_SCAN_QR = "com.gigabytedevelopersinc.app.cometOTP.intent.SCAN_QR";
    private static final String INTENT_IMPORT_QR = "com.gigabytedevelopersinc.app.cometOTP.intent.IMPORT_QR";
    private static final String INTENT_ENTER_DETAILS = "com.gigabytedevelopersinc.app.cometOTP.intent.ENTER_DETAILS";

    private EntriesCardAdapter adapter;
    private SimpleItemTouchHelperCallback touchHelperCallback;
    private BottomSheetDialog activeSheet;

    private EncryptionType encryptionType = EncryptionType.KEYSTORE;
    private boolean requireAuthentication = false;

    private boolean recreateActivity = false;
    private boolean cacheEncKey = false;
    private boolean focusSearchOnCreate = false;
    private boolean coachMarksRequested = false;

    private Handler handler;
    private Runnable handlerTask;

    // Home shell views
    private View appBarBrand;
    private View appBarSearch;
    private EditText searchField;
    private CountdownRingView appBarCountdown;
    /** How long the branded launch screen stays up, covering its animation. */
    private static final long SPLASH_DURATION_MS = 820L;
    /** How long the launch screen takes to fade into the app. */
    private static final long SPLASH_FADE_MS = 260L;

    /** How long a tapped sheet row stays highlighted before the sheet closes. */
    private static final long SHEET_ACTION_FEEDBACK_MS = 180L;

    private NotchedBottomBar bottomBar;
    private FloatingActionButton fab;
    private View emptyState;
    private ImageView emptyIllustration;
    private TextView emptyTitle;
    private TextView emptySubtitle;

    private DrawerLayout tagsDrawerLayout;
    private ListView tagsDrawerListView;
    private TagsAdapter tagsDrawerAdapter;
    private String filterString;
    private boolean searchMode = false;

    private CountDownTimer countDownTimer;

    private static final String LAST_APP_VERSION = "1";
    private static AppStart appStart = null;

    public AppStart checkAppStart(Context context, SharedPreferences sharedPreferences) {
        PackageInfo pInfo;

        try {
            pInfo = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            int lastVersionCode = sharedPreferences.getInt(
                    LAST_APP_VERSION, -1);
            int currentVersionCode = (int) PackageInfoCompat.getLongVersionCode(pInfo);
            appStart = checkAppStart(currentVersionCode, lastVersionCode);

            // Update version in preferences
            sharedPreferences.edit()
                    .putInt(LAST_APP_VERSION, currentVersionCode).apply();
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return appStart;
    }

    public AppStart checkAppStart(int currentVersionCode, int lastVersionCode) {
        if (lastVersionCode == -1) {
            return AppStart.FIRST_TIME;
        } else if (lastVersionCode < currentVersionCode) {
            return AppStart.FIRST_TIME_VERSION;
        } else {
            return AppStart.NORMAL;
        }
    }

    // QR code scanning
    private void scanQRCode(){
        ScanOptions options = new ScanOptions()
                .setOrientationLocked(false)
                .setBarcodeImageEnabled(true)
                .setBeepEnabled(false)
                .setCaptureActivity(SecureCaptureActivity.class);
        scanQrLauncher.launch(options);
    }

    /* Activity result launchers. Each launcher owns the handling of exactly one kind of result. */

    private final ActivityResultLauncher<ScanOptions> scanQrLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result != null && result.getContents() != null)
                    addQRCode(result.getContents());
            });

    private final ActivityResultLauncher<Intent> introLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                boolean setupFinished = false;

                if (result.getResultCode() == RESULT_OK && result.getData() != null)
                    setupFinished = result.getData().getBooleanExtra(Constants.EXTRA_INTRO_FINISHED, false);

                if (!setupFinished)
                    finishAndRemoveTask();
            });

    private final ActivityResultLauncher<Intent> backupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getBooleanExtra("reload", false)) {
                    adapter.loadEntries();
                    refreshTags();
                }
            });

    private final ActivityResultLauncher<Intent> settingsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null)
                    return;

                boolean encryptionChanged = result.getData().getBooleanExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false);
                byte[] newKey = result.getData().getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY);

                if (encryptionChanged)
                    updateEncryption(newKey);

                if (recreateActivity) {
                    cacheEncKey = true;
                    recreate();
                }
            });

    private final ActivityResultLauncher<Intent> qrImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null)
                    addQRCode(ScanQRCodeFromFile.scanQRImage(this, result.getData().getData()));
            });

    // Shared by the password/PIN screen and the device-credential prompt: any result other than
    // RESULT_OK means the user could not be authenticated and the app must not stay open.
    private final ActivityResultLauncher<Intent> authenticateActivityResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK) {
                    Toast.makeText(getBaseContext(), R.string.toast_auth_failed_fatal, Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                } else {
                    requireAuthentication = false;
                    byte[] authKey = null;
                    if (result.getData() != null)
                        authKey = result.getData().getByteArrayExtra(Constants.EXTRA_AUTH_PASSWORD_KEY);
                    updateEncryption(authKey);
                }
            });

    private void showFirstTimeWarning() {
        Intent introIntent = new Intent(this, IntroScreenActivity.class);
        introLauncher.launch(introIntent);
    }

    public void authenticate(int messageId) {
        AuthMethod authMethod = settings.getAuthMethod();

        if (authMethod == AuthMethod.DEVICE) {
            int authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
            if (BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        requireAuthentication = false;
                        updateEncryption(null);
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        Toast.makeText(getBaseContext(), R.string.toast_auth_failed_fatal, Toast.LENGTH_LONG).show();
                        finishAndRemoveTask();
                    }
                });
                prompt.authenticate(new BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.security_biometric_title))
                        .setSubtitle(getString(R.string.security_biometric_subtitle))
                        .setAllowedAuthenticators(authenticators)
                        .build());
                return;
            }

            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            assert km != null;
            if (km.isKeyguardSecure()) {
                Intent authIntent = km.createConfirmDeviceCredentialIntent(getString(R.string.dialog_title_auth), getString(R.string.dialog_msg_auth));
                authenticateActivityResultLauncher.launch(authIntent);
            }
        } else if (authMethod == AuthMethod.PASSWORD || authMethod == AuthMethod.PIN) {
            Intent authIntent = new Intent(this, AuthenticateActivity.class);
            authIntent.putExtra(Constants.EXTRA_AUTH_MESSAGE, messageId);
            authenticateActivityResultLauncher.launch(authIntent);
        }
    }

    private void restoreSortMode() {
        if (settings != null && adapter != null && touchHelperCallback != null) {
            SortMode mode = settings.getSortMode();
            adapter.setSortMode(mode);

            touchHelperCallback.setDragEnabled(mode == SortMode.UNSORTED);
        }
    }

    private void populateAdapter() {
        adapter.loadEntries();
        tagsDrawerAdapter.setTags(TagsAdapter.createTagsMap(adapter.getEntries(), settings));
        adapter.filterByTags(tagsDrawerAdapter.getActiveTags());
    }

    private void checkAutomaticTime() {
        int autoTime = Settings.Global.getInt(getContentResolver(), Settings.Global.AUTO_TIME, 0);

        if (autoTime == 0)
            HideableDialog.ShowHideableDialog(
                    this,
                    R.string.dialog_title_auto_time,
                    R.string.dialog_msg_auto_time,
                    R.string.settings_key_dialog_hide_auto_time
            );
    }

    // Initialize the main application
    @SuppressLint("WrongConstant")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        installSplashScreen();
        super.onCreate(savedInstanceState);

        setTitle(R.string.app_name);
        SharedPreferences sharedPreferences = getSharedPreferences(
                this.getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );

        switch (checkAppStart(this, sharedPreferences)) {
            case NORMAL:
            case FIRST_TIME_VERSION:
            case FIRST_TIME:
            default:
                break;
        }

        if (!settings.getScreenshotsEnabled())
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        settings.registerPreferenceChangeListener(this);

        // Keeps the launcher in step with the stored choice, e.g. after restoring a backup.
        LauncherIcon.apply(getApplicationContext(), settings.getLauncherIcon());

        encryptionType = settings.getEncryption();

        if (settings.getAuthMethod() != AuthMethod.NONE && savedInstanceState == null)
            requireAuthentication = true;

        setBroadcastCallback(() -> {
            if (settings.getRelockOnScreenOff() && settings.getAuthMethod() != AuthMethod.NONE)
                requireAuthentication = true;
        });

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new ProcessLifecycleObserver());
        getOnBackPressedDispatcher().addCallback(this, closeOverlaysOnBack);

        if (!settings.getFirstTimeWarningShown()) {
            showFirstTimeWarning();
        }

        checkAutomaticTime();

        setupHomeShell();

        RecyclerView recList = findViewById(R.id.cardList);
        recList.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        recList.setLayoutManager(llm);
        new FastScrollerBuilder(recList).useMd2Style().build();

        tagsDrawerAdapter = new TagsAdapter(this, new HashMap<>());
        adapter = new EntriesCardAdapter(this, tagsDrawerAdapter);

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                updateEmptyState();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                super.onItemRangeChanged(positionStart, itemCount);
                updateEmptyState();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                super.onItemRangeChanged(positionStart, itemCount, payload);
                updateEmptyState();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);
                updateEmptyState();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);
                updateEmptyState();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount);
                updateEmptyState();
            }
        });

        if (savedInstanceState != null) {
            byte[] encKey = savedInstanceState.getByteArray("encKey");
            if (encKey != null) {
                adapter.setEncryptionKey(EncryptionHelper.generateSymmetricKey(encKey));
                requireAuthentication = false;
            }
        }

        recList.setAdapter(adapter);

        touchHelperCallback = new SimpleItemTouchHelperCallback(adapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(touchHelperCallback);
        touchHelper.attachToRecyclerView(recList);

        NotificationHelper.initializeNotificationChannels(this);
        restoreSortMode();

        float durationScale = android.provider.Settings.Global.getFloat(this.getContentResolver(), android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0);
        if (durationScale == 0)
            durationScale = 1;

        animatorDuration = (long) (1000 / durationScale);

        adapter.setCallback(new EntriesCardAdapter.Callback() {
            @Override
            public void onMoveEventStart() {
                stopUpdater();
            }

            @Override
            public void onMoveEventStop() {
                startUpdater();
            }
        });

        handler = new Handler(Looper.getMainLooper());
        handlerTask = new Runnable() {
            @Override
            public void run() {
                if (!settings.isHideGlobalTimeoutEnabled())
                    appBarCountdown.update(TokenCalculator.TOTP_DEFAULT_PERIOD);

                adapter.updateTimeBasedTokens();

                handler.postDelayed(this, 1000);
            }
        };

        setupDrawer();

        if (savedInstanceState != null) {
            String savedFilter = savedInstanceState.getString("filterString", "");
            if (!TextUtils.isEmpty(savedFilter)) {
                enterSearchMode(false);
                searchField.setText(savedFilter);
            }
            setFilterString(savedFilter);
        }

        if (settings.isFocusSearchOnStartEnabled())
            focusSearchMenu();
    }

    /* ------------------------------------------------------------------------------------------
     * Home shell: app bar, bottom bar, FAB, sheets, search mode
     * ------------------------------------------------------------------------------------------ */

    private void setupHomeShell() {
        appBarBrand = findViewById(R.id.appBarBrand);
        appBarSearch = findViewById(R.id.appBarSearch);
        searchField = findViewById(R.id.searchField);
        appBarCountdown = findViewById(R.id.appBarCountdown);
        bottomBar = findViewById(R.id.bottomBar);
        fab = findViewById(R.id.fab);
        applyWindowInsets();
        emptyState = findViewById(R.id.emptyState);
        emptyIllustration = emptyState.findViewById(R.id.emptyIllustration);
        emptyTitle = emptyState.findViewById(R.id.emptyTitle);
        emptySubtitle = emptyState.findViewById(R.id.emptySubtitle);

        appBarCountdown.setHighlightExpiring(settings.isHighlightTokenOptionEnabled());
        appBarCountdown.setVisibility(settings.isHideGlobalTimeoutEnabled() ? View.GONE : View.VISIBLE);

        fab.setOnClickListener(v -> showAddSheet());
        findViewById(R.id.menuButton).setOnClickListener(v -> showNavigationSheet());
        findViewById(R.id.searchButton).setOnClickListener(v -> enterSearchMode(true));
        findViewById(R.id.sortButton).setOnClickListener(v -> showSortSheet());

        findViewById(R.id.searchBack).setOnClickListener(v -> exitSearchMode());
        findViewById(R.id.searchClear).setOnClickListener(v -> searchField.setText(""));

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (searchMode)
                    setFilterString(s.toString());
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                UIHelper.hideKeyboard(this, searchField);
                return true;
            }
            return false;
        });
    }

    /**
     * Shows the branded launch screen: the mark rises in, the wordmark joins it, then the whole
     * lockup fades into the app. The splash is held for the length of that animation, since the
     * first frame is otherwise ready long before it finishes.
     */
    private void installSplashScreen() {
        SplashScreen splash = SplashScreen.installSplashScreen(this);
        final long shownAt = SystemClock.uptimeMillis();
        splash.setKeepOnScreenCondition(
                () -> SystemClock.uptimeMillis() - shownAt < SPLASH_DURATION_MS);
        splash.setOnExitAnimationListener(provider -> provider.getView()
                .animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MS)
                .withEndAction(provider::remove)
                .start());
    }

    /**
     * The bar is docked to the very bottom of the window and the list scrolls underneath it, so
     * the navigation-bar inset becomes bar padding rather than a gap below the bar. The add button
     * is then centred on the bar's top edge and the list is padded clear of both.
     */
    private void applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // The docked bar paints behind the navigation bar, so the system must not paint over it.
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            getWindow().setNavigationBarContrastEnforced(false);

        final View root = findViewById(R.id.main_content);
        final View appBar = findViewById(R.id.appBar);
        final RecyclerView list = findViewById(R.id.cardList);
        final View empty = findViewById(R.id.emptyState);
        final int barHeight = getResources().getDimensionPixelSize(R.dimen.bottom_bar_height);
        final int listGap = getResources().getDimensionPixelSize(R.dimen.list_bottom_gap);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            appBar.setPadding(bars.left, bars.top, bars.right, 0);
            bottomBar.setPadding(bars.left, 0, bars.right, bars.bottom);

            int docked = barHeight + bars.bottom;
            padListBelowBar(list, docked + listGap);
            empty.setPadding(0, 0, 0, docked);

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fab.getLayoutParams();
            int margin = docked - getResources().getDimensionPixelSize(R.dimen.fab_size) / 2;
            if (lp.bottomMargin != margin) {
                lp.bottomMargin = margin;
                fab.setLayoutParams(lp);
            }
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(root);

        // The bar's real height is the authority: pad from it once it has been laid out, so the
        // last entry in a long list always comes to rest clear of the bar rather than under it.
        bottomBar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or_, ob) -> {
            int height = b - t;
            if (height > 0)
                padListBelowBar(list, height + listGap);
        });
    }

    private void padListBelowBar(RecyclerView list, int bottom) {
        if (list.getPaddingBottom() == bottom)
            return;

        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), bottom);
        // Without this the list keeps its old scroll extent and the last item stays hidden.
        list.invalidateItemDecorations();
    }

    private void enterSearchMode(boolean focus) {
        if (searchMode)
            return;

        searchMode = true;
        appBarBrand.setVisibility(View.GONE);
        appBarCountdown.setVisibility(View.GONE);
        appBarSearch.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.GONE);
        fab.hide();
        touchHelperCallback.setDragEnabled(false);
        updateEmptyState();
        updateBackCallbackState();

        if (focus) {
            searchField.requestFocus();
            UIHelper.showKeyboard(this, searchField);
        }
    }

    private void exitSearchMode() {
        if (!searchMode)
            return;

        UIHelper.hideKeyboard(this, searchField);
        searchMode = false;
        searchField.setText("");
        setFilterString("");

        appBarSearch.setVisibility(View.GONE);
        appBarBrand.setVisibility(View.VISIBLE);
        appBarCountdown.setVisibility(settings.isHideGlobalTimeoutEnabled() ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        fab.show();

        if (adapter == null || adapter.getSortMode() == SortMode.UNSORTED)
            touchHelperCallback.setDragEnabled(true);

        updateEmptyState();
        updateBackCallbackState();
    }

    private BottomSheetDialog openSheet(int layoutRes) {
        dismissSheet();
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        sheet.setContentView(layoutRes);
        sheet.setOnDismissListener(d -> {
            if (activeSheet == d)
                activeSheet = null;
        });
        activeSheet = sheet;
        sheet.show();
        return sheet;
    }

    private void dismissSheet() {
        if (activeSheet != null) {
            activeSheet.dismiss();
            activeSheet = null;
        }
    }

    /**
     * Binds a sheet row. The tapped row takes the design's filled state and holds it briefly, so
     * the selection is visible before the sheet closes and the destination opens.
     */
    private void bindSheetAction(BottomSheetDialog sheet, @IdRes int id, Runnable action) {
        View v = sheet.findViewById(id);
        if (v == null)
            return;

        v.setOnClickListener(view -> {
            if (!view.isEnabled())
                return;

            if (view.getParent() instanceof ViewGroup) {
                ViewGroup rows = (ViewGroup) view.getParent();
                for (int i = 0; i < rows.getChildCount(); i++)
                    rows.getChildAt(i).setSelected(false);
            }
            view.setSelected(true);
            view.setEnabled(false);

            view.postDelayed(() -> {
                sheet.dismiss();
                action.run();
            }, SHEET_ACTION_FEEDBACK_MS);
        });
    }

    private void showAddSheet() {
        BottomSheetDialog sheet = openSheet(R.layout.sheet_add_service);
        bindSheetAction(sheet, R.id.add_scan_qr, this::scanQRCode);
        bindSheetAction(sheet, R.id.add_qr_from_image, this::showOpenFileSelector);
        bindSheetAction(sheet, R.id.add_setup_key, () -> ManualEntryDialog.show(MainActivity.this, settings, adapter));
    }

    private void showNavigationSheet() {
        BottomSheetDialog sheet = openSheet(R.layout.sheet_navigation);
        bindSheetAction(sheet, R.id.nav_home, () -> {});
        bindSheetAction(sheet, R.id.nav_tags, this::openTags);
        bindSheetAction(sheet, R.id.nav_security, this::openSecurity);
        bindSheetAction(sheet, R.id.nav_support, () -> startActivity(new Intent(this, SupportActivity.class)));
        bindSheetAction(sheet, R.id.nav_backup, this::openBackup);
        bindSheetAction(sheet, R.id.nav_settings, this::openSettings);
        bindSheetAction(sheet, R.id.nav_about, this::openAbout);
    }

    private void showSortSheet() {
        BottomSheetDialog sheet = openSheet(R.layout.sheet_sort);
        RadioGroup group = sheet.findViewById(R.id.sortGroup);
        if (group == null)
            return;

        group.check(sortModeToId(adapter != null ? adapter.getSortMode() : settings.getSortMode()));
        group.setOnCheckedChangeListener((g, checkedId) -> {
            applySortMode(idToSortMode(checkedId));
            sheet.dismiss();
        });

        bindSheetAction(sheet, R.id.sort_filter_tags, () -> tagsDrawerLayout.openDrawer(GravityCompat.START));
    }

    @IdRes
    private static int sortModeToId(SortMode mode) {
        switch (mode) {
            case ISSUER: return R.id.sort_issuer;
            case LABEL: return R.id.sort_label;
            case LAST_USED: return R.id.sort_last_used;
            case MOST_USED: return R.id.sort_most_used;
            case UNSORTED:
            default: return R.id.sort_none;
        }
    }

    private static SortMode idToSortMode(@IdRes int id) {
        if (id == R.id.sort_issuer) return SortMode.ISSUER;
        if (id == R.id.sort_label) return SortMode.LABEL;
        if (id == R.id.sort_last_used) return SortMode.LAST_USED;
        if (id == R.id.sort_most_used) return SortMode.MOST_USED;
        return SortMode.UNSORTED;
    }

    private void applySortMode(SortMode mode) {
        settings.setSortMode(mode);

        if (adapter != null) {
            adapter.setSortMode(mode);
            touchHelperCallback.setDragEnabled(mode == SortMode.UNSORTED && !searchMode);
        }

        if ((mode == SortMode.LAST_USED || mode == SortMode.MOST_USED) && !settings.getUsedTokensDialogShown())
            showUsedTokensDialog();
    }

    private void openBackup() {
        Intent backupIntent = new Intent(this, BackupActivity.class);
        if (adapter.getEncryptionKey() != null)
            backupIntent.putExtra(Constants.EXTRA_BACKUP_ENCRYPTION_KEY, adapter.getEncryptionKey().getEncoded());
        backupLauncher.launch(backupIntent);
    }

    private void openTags() {
        Intent tagsIntent = new Intent(this, TagsActivity.class);
        if (adapter.getEncryptionKey() != null)
            tagsIntent.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, adapter.getEncryptionKey().getEncoded());
        backupLauncher.launch(tagsIntent);
    }

    private void openSecurity() {
        Intent securityIntent = new Intent(this, SecurityActivity.class);
        if (adapter.getEncryptionKey() != null)
            securityIntent.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, adapter.getEncryptionKey().getEncoded());
        settingsLauncher.launch(securityIntent);
    }

    private void openSettings() {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        if (adapter.getEncryptionKey() != null)
            settingsIntent.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, adapter.getEncryptionKey().getEncoded());
        settingsLauncher.launch(settingsIntent);
    }

    private void openAbout() {
        startActivity(new Intent(this, AboutActivity.class));
    }

    private void checkIntent() {
        Intent callingIntent = getIntent();
        if (callingIntent != null && callingIntent.getAction() != null) {
            // Cache and reset the action to prevent the same intent from being evaluated multiple times
            String intentAction = callingIntent.getAction();
            callingIntent.setAction(null);

            switch (intentAction) {
                case INTENT_SCAN_QR:
                    scanQRCode();
                    break;
                case INTENT_IMPORT_QR:
                    showOpenFileSelector();
                    break;
                case INTENT_ENTER_DETAILS:
                    ManualEntryDialog.show(MainActivity.this, settings, adapter);
                    break;
                case Intent.ACTION_VIEW:
                    try {
                        Entry entry = new Entry(callingIntent.getDataString());
                        entry.updateOTP(false);
                        entry.setLastUsed(System.currentTimeMillis());
                        adapter.addEntry(entry);
                        Toast.makeText(this, R.string.toast_intent_creation_succeeded, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.toast_intent_creation_failed, Toast.LENGTH_LONG).show();
                    }
                    break;
            }
        }
    }

    // Controls for the updater background task
    public void stopUpdater() {
        handler.removeCallbacks(handlerTask);
    }

    public void startUpdater() {
        handler.post(handlerTask);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (requireAuthentication) {
            if (settings.getAuthMethod() != AuthMethod.NONE) {
                requireAuthentication = false;
                authenticate(R.string.auth_msg_authenticate);
            }
        } else {
            if (settings.getFirstTimeWarningShown()) {
                if (adapter.getEncryptionKey() == null) {
                    updateEncryption(null);
                } else {
                    populateAdapter();
                }
                checkIntent();
            }

            if (setCountDownTimerNow())
                countDownTimer.start();
        }

        if (filterString != null) {
            // ensure the current filter string is applied after a resume
            setFilterString(this.filterString);
        }

        View cardList = findViewById(R.id.cardList);
        if(cardList.getVisibility() == View.INVISIBLE)
            cardList.setVisibility(View.VISIBLE);
        startUpdater();

        maybeShowCoachMarks();
    }

    /**
     * Shows the onboarding tour once, the first time the home screen is reached after the setup
     * wizard. Each step spotlights one control of the new shell.
     */
    private void maybeShowCoachMarks() {
        if (coachMarksRequested || requireAuthentication
                || settings.getCoachMarksShown() || !settings.getFirstTimeWarningShown())
            return;

        coachMarksRequested = true;

        final ViewGroup root = findViewById(android.R.id.content);
        root.post(() -> {
            List<CoachMarkOverlay.Step> steps = new ArrayList<>();
            steps.add(new CoachMarkOverlay.Step(fab,
                    getString(R.string.coach_add_title), getString(R.string.coach_add_body)));
            steps.add(new CoachMarkOverlay.Step(findViewById(R.id.sortButton),
                    getString(R.string.coach_sort_title), getString(R.string.coach_sort_body)));
            steps.add(new CoachMarkOverlay.Step(findViewById(R.id.searchButton),
                    getString(R.string.coach_search_title), getString(R.string.coach_search_body)));
            steps.add(new CoachMarkOverlay.Step(findViewById(R.id.menuButton),
                    getString(R.string.coach_menu_title), getString(R.string.coach_menu_body)));

            if (appBarCountdown.getVisibility() == View.VISIBLE)
                steps.add(new CoachMarkOverlay.Step(appBarCountdown,
                        getString(R.string.coach_timer_title), getString(R.string.coach_timer_body)));

            CoachMarkOverlay.show(root, steps, () -> settings.setCoachMarksShown(true));
        });
    }

    @Override
    public void onPause() {
        if(settings.getAuthMethod() == AuthMethod.DEVICE)
            runOnUiThread(() -> findViewById(R.id.cardList).setVisibility(View.INVISIBLE));
        super.onPause();
        stopUpdater();
        appBarCountdown.stop();
        if (countDownTimer != null)
            countDownTimer.cancel();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("filterString", filterString);

        if (cacheEncKey && adapter.getEncryptionKey() != null) {
            outState.putByteArray("encKey", adapter.getEncryptionKey().getEncoded());
            cacheEncKey = false;
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        assert key != null;
        if (key.equals(getString(R.string.settings_key_label_size)) ||
                key.equals(getString(R.string.settings_key_label_display)) ||
                key.equals(getString(R.string.settings_key_split_group_size)) ||
                key.equals(getString(R.string.settings_key_thumbnail_size))) {
            adapter.notifyDataSetChanged();
        } else if (key.equals(getString(R.string.settings_key_search_includes))) {
            adapter.clearFilter();
        } else if (key.equals(getString(R.string.settings_key_tap_single)) ||
                key.equals(getString(R.string.settings_key_tap_double)) ||
                key.equals(getString(R.string.settings_key_theme)) ||
                key.equals(getString(R.string.settings_key_enable_screenshot)) ||
                key.equals(getString(R.string.settings_key_tag_functionality)) ||
                key.equals(getString(R.string.settings_key_label_highlight_token)) ||
                key.equals(getString(R.string.settings_key_theme_mode)) ||
                key.equals(getString(R.string.settings_key_theme_black_auto)) ||
                key.equals(getString(R.string.settings_key_hide_global_timeout)) ||
                key.equals(getString(R.string.settings_key_hide_issuer)) ||
                key.equals(getString(R.string.settings_key_show_prev_token))) {
            recreateActivity = true;
        }
    }

    private void updateEncryption(byte[] newKey) {
        SecretKey encryptionKey = null;

        encryptionType = settings.getEncryption();

        if (encryptionType == EncryptionType.KEYSTORE) {
            encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(this, false);
        } else if (encryptionType == EncryptionType.PASSWORD) {
            if (newKey != null && newKey.length > 0) {
                encryptionKey = EncryptionHelper.generateSymmetricKey(newKey);
            } else {
                authenticate(R.string.auth_msg_confirm_encryption);
            }
        }

        if (encryptionKey != null)
            adapter.setEncryptionKey(encryptionKey);

        populateAdapter();
    }

    private void focusSearchMenu() {
        if (searchField != null && touchHelperCallback != null)
            enterSearchMode(true);
        else
            focusSearchOnCreate = true;
    }

    private void setFilterString(String newText) {
        if (newText.isEmpty())
            adapter.filterByTags(tagsDrawerAdapter.getActiveTags());
        else
            adapter.getFilter().filter(newText);

        this.filterString = newText;
    }

    private void showUsedTokensDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.dialog_title_used_tokens)
                .setMessage(R.string.dialog_msg_used_tokens)
                .setPositiveButton(android.R.string.ok, (DialogInterface dialogInterface, int i) -> settings.setUsedTokensDialogShown(true))
                .create()
                .show();
    }

    /* ------------------------------------------------------------------------------------------
     * Tag filter drawer
     * ------------------------------------------------------------------------------------------ */

    private void setupDrawer() {
        tagsDrawerListView = findViewById(R.id.tags_list_in_drawer);
        tagsDrawerLayout = findViewById(R.id.drawer_layout);

        tagsDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                closeOverlaysOnBack.setEnabled(true);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                updateBackCallbackState();
            }
        });

        final CheckedTextView noTagsButton = findViewById(R.id.no_tags_entries);
        final CheckedTextView allTagsButton = findViewById(R.id.all_tags_in_drawer);

        allTagsButton.setOnClickListener(view -> {
            CheckedTextView checkedTextView = ((CheckedTextView)view);
            checkedTextView.setChecked(!checkedTextView.isChecked());

            settings.setAllTagsToggle(checkedTextView.isChecked());

            for(int i = 0; i < tagsDrawerListView.getChildCount(); i++) {
                CheckedTextView childCheckBox = (CheckedTextView) tagsDrawerListView.getChildAt(i);
                childCheckBox.setChecked(checkedTextView.isChecked());
            }

            for (String tag: tagsDrawerAdapter.getTags()) {
                tagsDrawerAdapter.setTagState(tag, checkedTextView.isChecked());
                settings.setTagToggle(tag, checkedTextView.isChecked());
            }

            if(checkedTextView.isChecked()) {
                adapter.filterByTags(tagsDrawerAdapter.getActiveTags());
            } else {
                adapter.filterByTags(new ArrayList<>());
            }
        });
        allTagsButton.setChecked(settings.getAllTagsToggle());

        noTagsButton.setOnClickListener(view -> {
            CheckedTextView checkedTextView = ((CheckedTextView)view);
            checkedTextView.setChecked(!checkedTextView.isChecked());

            if(settings.getTagFunctionality() == Constants.TagFunctionality.SINGLE) {
                checkedTextView.setChecked(true);
                allTagsButton.setChecked(false);
                settings.setAllTagsToggle(false);

                for (String tag: tagsDrawerAdapter.getTags()) {
                    settings.setTagToggle(tag, false);
                    tagsDrawerAdapter.setTagState(tag, false);
                }
            }

            settings.setNoTagsToggle(checkedTextView.isChecked());
            adapter.filterByTags(tagsDrawerAdapter.getActiveTags());
        });
        noTagsButton.setChecked(settings.getNoTagsToggle());

        tagsDrawerListView.setAdapter(tagsDrawerAdapter);
        tagsDrawerListView.setOnItemClickListener((parent, view, position, id) -> {
            CheckedTextView checkedTextView = ((CheckedTextView)view);

            if(settings.getTagFunctionality() == Constants.TagFunctionality.SINGLE) {
                allTagsButton.setChecked(false);
                settings.setAllTagsToggle(false);
                noTagsButton.setChecked(false);
                settings.setNoTagsToggle(false);

                for (String tag: tagsDrawerAdapter.getTags()) {
                    settings.setTagToggle(tag, false);
                    tagsDrawerAdapter.setTagState(tag, false);
                }
                checkedTextView.setChecked(true);
            }else {
                checkedTextView.setChecked(!checkedTextView.isChecked());
            }

            settings.setTagToggle(checkedTextView.getText().toString(), checkedTextView.isChecked());
            tagsDrawerAdapter.setTagState(checkedTextView.getText().toString(), checkedTextView.isChecked());

            if (! checkedTextView.isChecked()) {
                allTagsButton.setChecked(false);
                settings.setAllTagsToggle(false);
            }

            if (tagsDrawerAdapter.allTagsActive()) {
                allTagsButton.setChecked(true);
                settings.setAllTagsToggle(true);
            }

            adapter.filterByTags(tagsDrawerAdapter.getActiveTags());
        });

        adapter.filterByTags(tagsDrawerAdapter.getActiveTags());
    }

    public void refreshTags() {
        HashMap<String, Boolean> tagsHashMap = new HashMap<>();
        for(String tag: tagsDrawerAdapter.getTags()) {
            tagsHashMap.put(tag, false);
        }
        for(String tag: tagsDrawerAdapter.getActiveTags()) {
            tagsHashMap.put(tag, true);
        }
        for(String tag: adapter.getTags()) {
            if(!tagsHashMap.containsKey(tag))
                tagsHashMap.put(tag, true);
        }
        tagsDrawerAdapter.setTags(tagsHashMap);
        adapter.filterByTags(tagsDrawerAdapter.getActiveTags());
    }

    @Override
    public void onUserInteraction(){
        super.onUserInteraction();

        // Refresh Blackout Timer
        if (countDownTimer != null)
            countDownTimer.cancel();

        if (setCountDownTimerNow())
            countDownTimer.start();
    }

    private boolean setCountDownTimerNow() {
        int secondsToBlackout = 1000 * settings.getAuthInactivityDelay();

        if (settings.getAuthMethod() == AuthMethod.NONE || !settings.getAuthInactivity() || secondsToBlackout == 0)
            return false;

        countDownTimer = new CountDownTimer(secondsToBlackout, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                authenticate(R.string.auth_msg_authenticate);
                this.cancel();
            }
        };

        return true;
    }

    private void showOpenFileSelector(){
        Intent fileSelectorIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        fileSelectorIntent.addCategory(Intent.CATEGORY_OPENABLE);
        fileSelectorIntent.setType("image/*");
        qrImageLauncher.launch(fileSelectorIntent);
    }

    private void addQRCode(String result){
        if(!TextUtils.isEmpty(result)) {
            try {
                Entry e = new Entry(result);
                e.updateOTP(false);
                e.setLastUsed(System.currentTimeMillis());
                adapter.addEntry(e);
                refreshTags();
            } catch (Exception e) {
                Toast.makeText(this, R.string.toast_invalid_qr_code, Toast.LENGTH_LONG).show();
            }
        }
    }

    private class ProcessLifecycleObserver implements DefaultLifecycleObserver {
        @Override
        public void onStop(@NonNull LifecycleOwner owner) {
            if (MainActivity.this.settings.getRelockOnBackground())
                MainActivity.this.requireAuthentication = true;
        }
    }

    /* Predictive back: with the OnBackInvokedCallback enabled (the default when targeting
     * Android 16+), KEYCODE_BACK is no longer dispatched. The callback is only enabled while the
     * search mode or the tags drawer is open, so the system back animation still runs otherwise. */
    private final OnBackPressedCallback closeOverlaysOnBack = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            if (isTagsDrawerOpen()) {
                tagsDrawerLayout.closeDrawer(GravityCompat.START);
            } else if (searchMode) {
                exitSearchMode();
            }
            updateBackCallbackState();
        }
    };

    private boolean isTagsDrawerOpen() {
        return tagsDrawerLayout != null && tagsDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    private void updateBackCallbackState() {
        closeOverlaysOnBack.setEnabled(searchMode || isTagsDrawerOpen());
    }

    @Override
    protected void onDestroy() {
        dismissSheet();
        settings.unregisterPreferenceChangeListener(this);
        super.onDestroy();
    }

    /**
     * Shows the empty state when the list has no items and hides the global countdown while there
     * is nothing to count down for. In search mode the "no results" copy is used instead.
     */
    private void updateEmptyState(){
        int itemCount = adapter.getItemCount();
        boolean empty = itemCount <= 0;

        if (!searchMode)
            appBarCountdown.setVisibility((settings.isHideGlobalTimeoutEnabled() || empty) ? View.GONE : View.VISIBLE);

        if (searchMode) {
            emptyIllustration.setImageResource(R.drawable.ill_no_results);
            emptyTitle.setText(R.string.empty_search_title);
            emptySubtitle.setText(R.string.empty_search_subtitle);
        } else {
            emptyIllustration.setImageResource(R.drawable.ill_empty_services);
            emptyTitle.setText(R.string.empty_services_title);
            emptySubtitle.setText(R.string.empty_services_subtitle);
        }
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    @Override
    protected boolean shouldDestroyOnScreenOff() {
        return false;
    }
}
