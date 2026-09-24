@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.preference.PreferenceManager
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.IdRes
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.HideableDialog
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ManualEntryDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AppStart
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.AuthMethod
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.EncryptionType
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants.SortMode
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.LauncherIcon
import com.gigabytedevelopersinc.app.cometOTP.Utilities.NotificationHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ScanQRCodeFromFile
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.gigabytedevelopersinc.app.cometOTP.View.CoachMarkOverlay
import com.gigabytedevelopersinc.app.cometOTP.View.CountdownRingView
import com.gigabytedevelopersinc.app.cometOTP.View.EntriesCardAdapter
import com.gigabytedevelopersinc.app.cometOTP.View.ItemTouchHelper.SimpleItemTouchHelperCallback
import com.gigabytedevelopersinc.app.cometOTP.View.NotchedBottomBar
import com.gigabytedevelopersinc.app.cometOTP.View.TagsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import javax.crypto.SecretKey

class MainActivity : BaseActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var adapter: EntriesCardAdapter
    private lateinit var touchHelperCallback: SimpleItemTouchHelperCallback
    private var activeSheet: BottomSheetDialog? = null

    private var encryptionType = EncryptionType.KEYSTORE
    private var requireAuthentication = false

    private var recreateActivity = false
    private var cacheEncKey = false
    private var focusSearchOnCreate = false
    private var coachMarksRequested = false

    private lateinit var handler: Handler
    private lateinit var handlerTask: Runnable

    // Home shell views
    private lateinit var appBarBrand: View
    private lateinit var appBarSearch: View
    private lateinit var searchField: EditText
    private lateinit var appBarCountdown: CountdownRingView

    private lateinit var bottomBar: NotchedBottomBar
    private lateinit var sortButton: MaterialButton
    private lateinit var fab: FloatingActionButton
    private lateinit var emptyState: View
    private lateinit var emptyIllustration: ImageView
    private lateinit var emptyTitle: TextView
    private lateinit var emptySubtitle: TextView

    private var tagsDrawerListView: ListView? = null
    private lateinit var tagsDrawerAdapter: TagsAdapter
    private var filterString: String? = null
    private var searchMode = false

    private var countDownTimer: CountDownTimer? = null

    // Registered with the process lifecycle in onCreate() and removed again in onDestroy(), so
    // only the live activity is observed.
    private val processLifecycleObserver = ProcessLifecycleObserver()

    fun checkAppStart(context: Context, sharedPreferences: SharedPreferences): AppStart? {
        val pInfo: PackageInfo

        try {
            pInfo = context.packageManager.getPackageInfo(
                    context.packageName, 0)
            val lastVersionCode = sharedPreferences.getInt(
                    LAST_APP_VERSION, -1)
            val currentVersionCode = PackageInfoCompat.getLongVersionCode(pInfo).toInt()
            appStart = checkAppStart(currentVersionCode, lastVersionCode)

            // Update version in preferences
            sharedPreferences.edit()
                    .putInt(LAST_APP_VERSION, currentVersionCode).apply()
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
        return appStart
    }

    fun checkAppStart(currentVersionCode: Int, lastVersionCode: Int): AppStart {
        return if (lastVersionCode == -1) {
            AppStart.FIRST_TIME
        } else if (lastVersionCode < currentVersionCode) {
            AppStart.FIRST_TIME_VERSION
        } else {
            AppStart.NORMAL
        }
    }

    // QR code scanning
    private fun scanQRCode() {
        val options = ScanOptions()
                .setOrientationLocked(false)
                .setBarcodeImageEnabled(true)
                .setBeepEnabled(false)
                .setCaptureActivity(SecureCaptureActivity::class.java)
        scanQrLauncher.launch(options)
    }

    /* Activity result launchers. Each launcher owns the handling of exactly one kind of result. */
    // Registration order matters: the result registry keys launchers by the order they are
    // registered in, so it must match between the old and the recreated activity.

    private val scanQrLauncher: ActivityResultLauncher<ScanOptions> = registerForActivityResult(
            ScanContract()) { result ->
        if (result != null && result.contents != null)
            addQRCode(result.contents)
    }

    private val introLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        var setupFinished = false

        if (result.resultCode == RESULT_OK && result.data != null)
            setupFinished = result.data!!.getBooleanExtra(Constants.EXTRA_INTRO_FINISHED, false)

        if (!setupFinished)
            finishAndRemoveTask()
    }

    private val backupLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null
                && result.data!!.getBooleanExtra("reload", false)) {
            adapter.loadEntries()
            refreshTags()
        }
    }

    private val settingsLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK || result.data == null)
            return@registerForActivityResult

        val encryptionChanged = result.data!!.getBooleanExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_CHANGED, false)
        val newKey = result.data!!.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)

        if (encryptionChanged)
            updateEncryption(newKey)

        if (recreateActivity) {
            cacheEncKey = true
            recreate()
        }
    }

    private val qrImageLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        // A picker result without a document would have been handed to scanQRImage() as null
        // in the Java original, which throws there as well.
        if (result.resultCode == RESULT_OK && result.data != null)
            addQRCode(ScanQRCodeFromFile.scanQRImage(this, result.data!!.data!!))
    }

    // Shared by the password/PIN screen and the device-credential prompt: any result other than
    // RESULT_OK means the user could not be authenticated and the app must not stay open.
    private val authenticateActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(baseContext, R.string.toast_auth_failed_fatal, Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
        } else {
            requireAuthentication = false
            var authKey: ByteArray? = null
            if (result.data != null)
                authKey = result.data!!.getByteArrayExtra(Constants.EXTRA_AUTH_PASSWORD_KEY)
            updateEncryption(authKey)
        }
    }

    private fun showFirstTimeWarning() {
        val introIntent = Intent(this, IntroScreenActivity::class.java)
        introLauncher.launch(introIntent)
    }

    fun authenticate(messageId: Int) {
        val authMethod = settings.authMethod

        if (authMethod == AuthMethod.DEVICE) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
                val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        requireAuthentication = false
                        updateEncryption(null)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        Toast.makeText(baseContext, R.string.toast_auth_failed_fatal, Toast.LENGTH_LONG).show()
                        finishAndRemoveTask()
                    }
                })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                        .setTitle(getString(R.string.security_biometric_title))
                        .setSubtitle(getString(R.string.security_biometric_subtitle))
                        .setAllowedAuthenticators(authenticators)
                        .build())
                return
            }

            val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager?
            if (km!!.isKeyguardSecure) {
                @Suppress("DEPRECATION")
                val authIntent = km.createConfirmDeviceCredentialIntent(getString(R.string.dialog_title_auth), getString(R.string.dialog_msg_auth))
                authenticateActivityResultLauncher.launch(authIntent)
            }
        } else if (authMethod == AuthMethod.PASSWORD || authMethod == AuthMethod.PIN) {
            val authIntent = Intent(this, AuthenticateActivity::class.java)
            authIntent.putExtra(Constants.EXTRA_AUTH_MESSAGE, messageId)
            authenticateActivityResultLauncher.launch(authIntent)
        }
    }

    private fun restoreSortMode() {
        // settings is always set by ThemedActivity.onCreate() by the time this runs.
        if (::adapter.isInitialized && ::touchHelperCallback.isInitialized) {
            val mode = settings.sortMode
            adapter.sortMode = mode

            touchHelperCallback.setDragEnabled(mode == SortMode.UNSORTED)
        }
    }

    private fun populateAdapter() {
        adapter.loadEntries()
        tagsDrawerAdapter.setTags(TagsAdapter.createTagsMap(this, adapter.entries, settings))
        adapter.filterByTags(tagsDrawerAdapter.activeTags)
    }

    private fun checkAutomaticTime() {
        val autoTime = Settings.Global.getInt(contentResolver, Settings.Global.AUTO_TIME, 0)

        if (autoTime == 0)
            HideableDialog.ShowHideableDialog(
                    this,
                    R.string.dialog_title_auto_time,
                    R.string.dialog_msg_auto_time,
                    R.string.settings_key_dialog_hide_auto_time
            )
    }

    // Initialize the main application
    @SuppressLint("WrongConstant")
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setTitle(R.string.app_name)
        val sharedPreferences = getSharedPreferences(
                this.packageName + "_preferences",
                Context.MODE_PRIVATE
        )

        // The Java switch threw on a null AppStart; `!!` keeps that.
        when (checkAppStart(this, sharedPreferences)!!) {
            AppStart.NORMAL, AppStart.FIRST_TIME_VERSION, AppStart.FIRST_TIME -> {}
        }

        if (!settings.screenshotsEnabled)
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_main)

        @Suppress("DEPRECATION")
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        settings.registerPreferenceChangeListener(this)

        // Keeps the launcher in step with the stored choice, e.g. after restoring a backup.
        LauncherIcon.apply(applicationContext, settings.launcherIcon)

        encryptionType = settings.encryption

        if (settings.authMethod != AuthMethod.NONE && savedInstanceState == null)
            requireAuthentication = true

        setBroadcastCallback {
            if (settings.relockOnScreenOff && settings.authMethod != AuthMethod.NONE)
                requireAuthentication = true
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        onBackPressedDispatcher.addCallback(this, closeOverlaysOnBack)

        if (!settings.firstTimeWarningShown) {
            showFirstTimeWarning()
        }

        checkAutomaticTime()

        setupHomeShell()

        val recList = findViewById<RecyclerView>(R.id.cardList)
        recList.setHasFixedSize(true)
        val llm = LinearLayoutManager(this)
        llm.orientation = LinearLayoutManager.VERTICAL
        recList.layoutManager = llm
        FastScrollerBuilder(recList).useMd2Style().build()

        tagsDrawerAdapter = TagsAdapter(this, HashMap())
        adapter = EntriesCardAdapter(this, tagsDrawerAdapter)

        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                updateEmptyState()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                super.onItemRangeChanged(positionStart, itemCount)
                updateEmptyState()
            }

            override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                super.onItemRangeChanged(positionStart, itemCount, payload)
                updateEmptyState()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                updateEmptyState()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                updateEmptyState()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                updateEmptyState()
            }
        })

        if (savedInstanceState != null) {
            val encKey = savedInstanceState.getByteArray("encKey")
            if (encKey != null) {
                adapter.encryptionKey = EncryptionHelper.generateSymmetricKey(encKey)
                requireAuthentication = false
            }
        }

        recList.adapter = adapter

        touchHelperCallback = SimpleItemTouchHelperCallback(adapter)
        val touchHelper = ItemTouchHelper(touchHelperCallback)
        touchHelper.attachToRecyclerView(recList)

        NotificationHelper.initializeNotificationChannels(this)
        restoreSortMode()

        var durationScale = Settings.Global.getFloat(this.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        if (durationScale == 0f)
            durationScale = 1f

        animatorDuration = (1000 / durationScale).toLong()

        adapter.setCallback(object : EntriesCardAdapter.Callback {
            override fun onMoveEventStart() {
                stopUpdater()
            }

            override fun onMoveEventStop() {
                startUpdater()
            }
        })

        handler = Handler(Looper.getMainLooper())
        handlerTask = object : Runnable {
            override fun run() {
                if (!settings.isHideGlobalTimeoutEnabled)
                    appBarCountdown.update(TokenCalculator.TOTP_DEFAULT_PERIOD)

                adapter.updateTimeBasedTokens()

                handler.postDelayed(this, 1000)
            }
        }

        setupDrawer()

        if (savedInstanceState != null) {
            val savedFilter = savedInstanceState.getString("filterString", "")
            if (!TextUtils.isEmpty(savedFilter)) {
                enterSearchMode(false)
                searchField.setText(savedFilter)
            }
            setFilterString(savedFilter)
        }

        if (settings.isFocusSearchOnStartEnabled)
            focusSearchMenu()
    }

    /* ------------------------------------------------------------------------------------------
     * Home shell: app bar, bottom bar, FAB, sheets, search mode
     * ------------------------------------------------------------------------------------------ */

    private fun setupHomeShell() {
        appBarBrand = findViewById(R.id.appBarBrand)
        appBarSearch = findViewById(R.id.appBarSearch)
        searchField = findViewById(R.id.searchField)
        appBarCountdown = findViewById(R.id.appBarCountdown)
        bottomBar = findViewById(R.id.bottomBar)
        fab = findViewById(R.id.fab)
        applyWindowInsets()
        emptyState = findViewById(R.id.emptyState)
        emptyIllustration = emptyState.findViewById(R.id.emptyIllustration)
        emptyTitle = emptyState.findViewById(R.id.emptyTitle)
        emptySubtitle = emptyState.findViewById(R.id.emptySubtitle)

        appBarCountdown.setHighlightExpiring(settings.isHighlightTokenOptionEnabled)
        appBarCountdown.visibility = if (settings.isHideGlobalTimeoutEnabled) View.GONE else View.VISIBLE

        fab.setOnClickListener { showAddSheet() }
        findViewById<View>(R.id.menuButton).setOnClickListener { showNavigationSheet() }
        findViewById<View>(R.id.searchButton).setOnClickListener { enterSearchMode(true) }
        sortButton = findViewById(R.id.sortButton)
        sortButton.setOnClickListener { showSortSheet() }
        updateSortIcon(settings.sortMode)

        findViewById<View>(R.id.searchBack).setOnClickListener { exitSearchMode() }
        findViewById<View>(R.id.searchClear).setOnClickListener { searchField.setText("") }

        searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if (searchMode)
                    setFilterString(s.toString())
            }
        })
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                UIHelper.hideKeyboard(this, searchField)
                return@setOnEditorActionListener true
            }
            false
        }
    }

    /**
     * Shows the branded launch screen: the mark rises in, the wordmark joins it, then the whole
     * lockup fades into the app. The splash is held for the length of that animation, since the
     * first frame is otherwise ready long before it finishes.
     */
    private fun installSplashScreen() {
        // Called on an Activity-typed receiver so that it resolves to SplashScreen's
        // installSplashScreen() extension rather than to this member function.
        val activity: Activity = this
        val splash = activity.installSplashScreen()
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition { SystemClock.uptimeMillis() - shownAt < SPLASH_DURATION_MS }
        splash.setOnExitAnimationListener { provider ->
            provider.view
                    .animate()
                    .alpha(0f)
                    .setDuration(SPLASH_FADE_MS)
                    .withEndAction(provider::remove)
                    .start()
        }
    }

    /**
     * The bar is docked to the very bottom of the window and the list scrolls underneath it, so
     * the navigation-bar inset becomes bar padding rather than a gap below the bar. The add button
     * is then centred on the bar's top edge and the list is padded clear of both.
     */
    @Suppress("DEPRECATION")   // status and navigation bar colours
    private fun applyWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // The docked bar paints behind the navigation bar, so the system must not paint over it.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
            window.isNavigationBarContrastEnforced = false

        val root = findViewById<View>(R.id.main_content)
        val appBar = findViewById<View>(R.id.appBar)
        val list = findViewById<RecyclerView>(R.id.cardList)
        val empty = findViewById<View>(R.id.emptyState)
        val barHeight = resources.getDimensionPixelSize(R.dimen.bottom_bar_height)
        val listGap = resources.getDimensionPixelSize(R.dimen.list_bottom_gap)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())

            appBar.setPadding(bars.left, bars.top, bars.right, 0)
            bottomBar.setPadding(bars.left, 0, bars.right, bars.bottom)

            val docked = barHeight + bars.bottom
            padListBelowBar(list, docked + listGap)
            empty.setPadding(0, 0, 0, docked)

            val lp = fab.layoutParams as ViewGroup.MarginLayoutParams
            val margin = docked - resources.getDimensionPixelSize(R.dimen.fab_size) / 2
            if (lp.bottomMargin != margin) {
                lp.bottomMargin = margin
                fab.layoutParams = lp
            }
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)

        // The bar's real height is the authority: pad from it once it has been laid out, so the
        // last entry in a long list always comes to rest clear of the bar rather than under it.
        bottomBar.addOnLayoutChangeListener { _, _, t, _, b, _, _, _, _ ->
            val height = b - t
            if (height > 0)
                padListBelowBar(list, height + listGap)
        }
    }

    private fun padListBelowBar(list: RecyclerView, bottom: Int) {
        if (list.paddingBottom == bottom)
            return

        list.setPadding(list.paddingLeft, list.paddingTop, list.paddingRight, bottom)
        // Without this the list keeps its old scroll extent and the last item stays hidden.
        list.invalidateItemDecorations()
    }

    private fun enterSearchMode(focus: Boolean) {
        if (searchMode)
            return

        searchMode = true
        appBarBrand.visibility = View.GONE
        appBarCountdown.visibility = View.GONE
        appBarSearch.visibility = View.VISIBLE
        bottomBar.visibility = View.GONE
        fab.hide()
        touchHelperCallback.setDragEnabled(false)
        updateEmptyState()
        updateBackCallbackState()

        if (focus) {
            searchField.requestFocus()
            UIHelper.showKeyboard(this, searchField)
        }
    }

    private fun exitSearchMode() {
        if (!searchMode)
            return

        UIHelper.hideKeyboard(this, searchField)
        searchMode = false
        searchField.setText("")
        setFilterString("")

        appBarSearch.visibility = View.GONE
        appBarBrand.visibility = View.VISIBLE
        appBarCountdown.visibility = if (settings.isHideGlobalTimeoutEnabled) View.GONE else View.VISIBLE
        bottomBar.visibility = View.VISIBLE
        fab.show()

        if (!::adapter.isInitialized || adapter.sortMode == SortMode.UNSORTED)
            touchHelperCallback.setDragEnabled(true)

        updateEmptyState()
        updateBackCallbackState()
    }

    private fun openSheet(layoutRes: Int): BottomSheetDialog {
        dismissSheet()
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(layoutRes)
        sheet.setOnDismissListener { d ->
            if (activeSheet === d)
                activeSheet = null
        }
        activeSheet = sheet
        sheet.show()
        return sheet
    }

    private fun dismissSheet() {
        val sheet = activeSheet
        if (sheet != null) {
            sheet.dismiss()
            activeSheet = null
        }
    }

    /**
     * Binds a sheet row. The tapped row takes the design's filled state and holds it briefly, so
     * the selection is visible before the sheet closes and the destination opens.
     */
    private fun bindSheetAction(sheet: BottomSheetDialog, @IdRes id: Int, action: () -> Unit) {
        val v = sheet.findViewById<View>(id) ?: return

        v.setOnClickListener { view ->
            if (!view.isEnabled)
                return@setOnClickListener

            val parent = view.parent
            if (parent is ViewGroup) {
                for (i in 0 until parent.childCount)
                    parent.getChildAt(i).isSelected = false
            }
            view.isSelected = true
            view.isEnabled = false

            view.postDelayed({
                sheet.dismiss()
                action()
            }, SHEET_ACTION_FEEDBACK_MS)
        }
    }

    private fun showAddSheet() {
        val sheet = openSheet(R.layout.sheet_add_service)
        bindSheetAction(sheet, R.id.add_scan_qr, ::scanQRCode)
        bindSheetAction(sheet, R.id.add_qr_from_image, ::showOpenFileSelector)
        bindSheetAction(sheet, R.id.add_setup_key) { ManualEntryDialog.show(this@MainActivity, settings, adapter) }
    }

    private fun showNavigationSheet() {
        val sheet = openSheet(R.layout.sheet_navigation)
        bindSheetAction(sheet, R.id.nav_home) {}
        bindSheetAction(sheet, R.id.nav_tags, ::openTags)
        bindSheetAction(sheet, R.id.nav_security, ::openSecurity)
        bindSheetAction(sheet, R.id.nav_support) { startActivity(Intent(this, SupportActivity::class.java)) }
        bindSheetAction(sheet, R.id.nav_backup, ::openBackup)
        bindSheetAction(sheet, R.id.nav_settings, ::openSettings)
        bindSheetAction(sheet, R.id.nav_about, ::openAbout)
    }

    private fun showSortSheet() {
        val sheet = openSheet(R.layout.sheet_sort)
        val group = sheet.findViewById<RadioGroup>(R.id.sortGroup) ?: return

        group.check(sortModeToId(if (::adapter.isInitialized) adapter.sortMode else settings.sortMode))
        group.setOnCheckedChangeListener { _, checkedId ->
            applySortMode(idToSortMode(checkedId))
            sheet.dismiss()
        }

        bindSheetAction(sheet, R.id.sort_filter_tags, ::showTagFilterSheet)
    }

    /** The bar's sort icon carries the current sort, as it did before the redesign. */
    private fun updateSortIcon(mode: SortMode) {
        if (!::sortButton.isInitialized)
            return

        val icon = when (mode) {
            SortMode.ISSUER, SortMode.LABEL ->
                R.drawable.ic_bar_sort_name
            SortMode.LAST_USED, SortMode.MOST_USED ->
                R.drawable.ic_bar_sort_time
            else ->
                R.drawable.ic_bar_sort
        }
        sortButton.setIconResource(icon)
    }

    private fun applySortMode(mode: SortMode) {
        settings.sortMode = mode
        updateSortIcon(mode)

        if (::adapter.isInitialized) {
            adapter.sortMode = mode
            touchHelperCallback.setDragEnabled(mode == SortMode.UNSORTED && !searchMode)
        }

        if ((mode == SortMode.LAST_USED || mode == SortMode.MOST_USED) && !settings.usedTokensDialogShown)
            showUsedTokensDialog()
    }

    private fun openBackup() {
        val backupIntent = Intent(this, BackupActivity::class.java)
        val key = adapter.encryptionKey
        if (key != null)
            backupIntent.putExtra(Constants.EXTRA_BACKUP_ENCRYPTION_KEY, key.encoded)
        backupLauncher.launch(backupIntent)
    }

    private fun openTags() {
        val tagsIntent = Intent(this, TagsActivity::class.java)
        val key = adapter.encryptionKey
        if (key != null)
            tagsIntent.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, key.encoded)
        backupLauncher.launch(tagsIntent)
    }

    private fun openSecurity() {
        val securityIntent = Intent(this, SecurityActivity::class.java)
        val key = adapter.encryptionKey
        if (key != null)
            securityIntent.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, key.encoded)
        settingsLauncher.launch(securityIntent)
    }

    private fun openSettings() {
        val settingsIntent = Intent(this, SettingsActivity::class.java)
        val key = adapter.encryptionKey
        if (key != null)
            settingsIntent.putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, key.encoded)
        settingsLauncher.launch(settingsIntent)
    }

    private fun openAbout() {
        startActivity(Intent(this, AboutActivity::class.java))
    }

    private fun checkIntent() {
        val callingIntent: Intent? = intent
        if (callingIntent != null && callingIntent.action != null) {
            // Cache and reset the action to prevent the same intent from being evaluated multiple times
            val intentAction = callingIntent.action
            callingIntent.action = null

            when (intentAction) {
                INTENT_SCAN_QR ->
                    scanQRCode()
                INTENT_IMPORT_QR ->
                    showOpenFileSelector()
                INTENT_ENTER_DETAILS ->
                    ManualEntryDialog.show(this@MainActivity, settings, adapter)
                Intent.ACTION_VIEW ->
                    try {
                        // A missing data string throws here, as it did in Java, and is reported below.
                        val entry = Entry(callingIntent.dataString!!)
                        entry.updateOTP(false)
                        entry.lastUsed = System.currentTimeMillis()
                        adapter.addEntry(entry)
                        Toast.makeText(this, R.string.toast_intent_creation_succeeded, Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, R.string.toast_intent_creation_failed, Toast.LENGTH_LONG).show()
                    }
            }
        }
    }

    // Controls for the updater background task
    fun stopUpdater() {
        handler.removeCallbacks(handlerTask)
    }

    fun startUpdater() {
        handler.post(handlerTask)
    }

    override fun onResume() {
        super.onResume()
        if (requireAuthentication) {
            if (settings.authMethod != AuthMethod.NONE) {
                requireAuthentication = false
                authenticate(R.string.auth_msg_authenticate)
            }
        } else {
            if (settings.firstTimeWarningShown) {
                if (adapter.encryptionKey == null) {
                    updateEncryption(null)
                } else {
                    populateAdapter()
                }
                checkIntent()
            }

            if (setCountDownTimerNow())
                countDownTimer!!.start()
        }

        val currentFilter = filterString
        if (currentFilter != null) {
            // ensure the current filter string is applied after a resume
            setFilterString(currentFilter)
        }

        val cardList = findViewById<View>(R.id.cardList)
        if (cardList.visibility == View.INVISIBLE)
            cardList.visibility = View.VISIBLE
        startUpdater()

        maybeShowCoachMarks()
    }

    /**
     * Shows the onboarding tour once, the first time the home screen is reached after the setup
     * wizard. Each step spotlights one control of the new shell.
     */
    private fun maybeShowCoachMarks() {
        if (coachMarksRequested || requireAuthentication
                || settings.coachMarksShown || !settings.firstTimeWarningShown)
            return

        coachMarksRequested = true

        val root = findViewById<ViewGroup>(android.R.id.content)
        root.post {
            val steps: MutableList<CoachMarkOverlay.Step> = ArrayList()
            steps.add(CoachMarkOverlay.Step(fab,
                    getString(R.string.coach_add_title), getString(R.string.coach_add_body)))
            steps.add(CoachMarkOverlay.Step(findViewById(R.id.sortButton),
                    getString(R.string.coach_sort_title), getString(R.string.coach_sort_body)))
            steps.add(CoachMarkOverlay.Step(findViewById(R.id.searchButton),
                    getString(R.string.coach_search_title), getString(R.string.coach_search_body)))
            steps.add(CoachMarkOverlay.Step(findViewById(R.id.menuButton),
                    getString(R.string.coach_menu_title), getString(R.string.coach_menu_body)))

            if (appBarCountdown.visibility == View.VISIBLE)
                steps.add(CoachMarkOverlay.Step(appBarCountdown,
                        getString(R.string.coach_timer_title), getString(R.string.coach_timer_body)))

            CoachMarkOverlay.show(root, steps) { settings.coachMarksShown = true }
        }
    }

    public override fun onPause() {
        if (settings.authMethod == AuthMethod.DEVICE)
            runOnUiThread { findViewById<View>(R.id.cardList).visibility = View.INVISIBLE }
        super.onPause()
        stopUpdater()
        appBarCountdown.stop()
        countDownTimer?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("filterString", filterString)

        if (cacheEncKey) {
            val key = adapter.encryptionKey
            if (key != null) {
                outState.putByteArray("encKey", key.encoded)
                cacheEncKey = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        // The Java original asserted key != null (assertions are off on Android) and then
        // dereferenced it, so a null key (SharedPreferences.clear() on API 30+) throws here too.
        val changedKey = key!!
        if (changedKey == getString(R.string.settings_key_label_size) ||
                changedKey == getString(R.string.settings_key_label_display) ||
                changedKey == getString(R.string.settings_key_split_group_size) ||
                changedKey == getString(R.string.settings_key_thumbnail_size)) {
            adapter.notifyDataSetChanged()
        } else if (changedKey == getString(R.string.settings_key_search_includes)) {
            adapter.clearFilter()
        } else if (changedKey == getString(R.string.settings_key_tap_single) ||
                changedKey == getString(R.string.settings_key_tap_double) ||
                changedKey == getString(R.string.settings_key_theme) ||
                changedKey == getString(R.string.settings_key_enable_screenshot) ||
                changedKey == getString(R.string.settings_key_tag_functionality) ||
                changedKey == getString(R.string.settings_key_label_highlight_token) ||
                changedKey == getString(R.string.settings_key_theme_mode) ||
                changedKey == getString(R.string.settings_key_theme_black_auto) ||
                changedKey == getString(R.string.settings_key_hide_global_timeout) ||
                changedKey == getString(R.string.settings_key_hide_issuer) ||
                changedKey == getString(R.string.settings_key_show_prev_token)) {
            recreateActivity = true
        }
    }

    private fun updateEncryption(newKey: ByteArray?) {
        var encryptionKey: SecretKey? = null

        encryptionType = settings.encryption

        if (encryptionType == EncryptionType.KEYSTORE) {
            encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(this, false)
        } else if (encryptionType == EncryptionType.PASSWORD) {
            if (newKey != null && newKey.isNotEmpty()) {
                encryptionKey = EncryptionHelper.generateSymmetricKey(newKey)
            } else {
                authenticate(R.string.auth_msg_confirm_encryption)
            }
        }

        if (encryptionKey != null)
            adapter.encryptionKey = encryptionKey

        populateAdapter()
    }

    private fun focusSearchMenu() {
        if (::searchField.isInitialized && ::touchHelperCallback.isInitialized)
            enterSearchMode(true)
        else
            focusSearchOnCreate = true
    }

    private fun setFilterString(newText: String) {
        if (newText.isEmpty())
            adapter.filterByTags(tagsDrawerAdapter.activeTags)
        else
            adapter.filter.filter(newText)

        this.filterString = newText
    }

    private fun showUsedTokensDialog() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.dialog_title_used_tokens)
                .setMessage(R.string.dialog_msg_used_tokens)
                .setPositiveButton(android.R.string.ok) { _, _ -> settings.usedTokensDialogShown = true }
                .create()
                .show()
    }

    /* ------------------------------------------------------------------------------------------
     * Tag filter sheet
     * ------------------------------------------------------------------------------------------ */

    private fun setupDrawer() {
        // The filter is a sheet now, so there is nothing to wire up until it is opened; the
        // stored selection still has to reach the list on start.
        adapter.filterByTags(tagsDrawerAdapter.activeTags)
    }

    private fun showTagFilterSheet() {
        val sheet = openSheet(R.layout.sheet_tag_filter)

        val listView = sheet.findViewById<ListView>(R.id.tags_list_in_drawer)
        tagsDrawerListView = listView
        val noTagsButton = sheet.findViewById<CheckedTextView>(R.id.no_tags_entries)
        val allTagsButton = sheet.findViewById<CheckedTextView>(R.id.all_tags_in_drawer)
        if (listView == null || noTagsButton == null || allTagsButton == null)
            return

        allTagsButton.setOnClickListener { view ->
            val checkedTextView = view as CheckedTextView
            checkedTextView.isChecked = !checkedTextView.isChecked

            settings.allTagsToggle = checkedTextView.isChecked

            // Reads the field at click time, like the Java original.
            val drawerList = tagsDrawerListView!!
            for (i in 0 until drawerList.childCount) {
                val childCheckBox = drawerList.getChildAt(i) as CheckedTextView
                childCheckBox.isChecked = checkedTextView.isChecked
            }

            for (tag in tagsDrawerAdapter.tags) {
                tagsDrawerAdapter.setTagState(tag, checkedTextView.isChecked)
                settings.setTagToggle(tag, checkedTextView.isChecked)
            }

            if (checkedTextView.isChecked) {
                adapter.filterByTags(tagsDrawerAdapter.activeTags)
            } else {
                adapter.filterByTags(ArrayList())
            }
        }
        allTagsButton.isChecked = settings.allTagsToggle

        noTagsButton.setOnClickListener { view ->
            val checkedTextView = view as CheckedTextView
            checkedTextView.isChecked = !checkedTextView.isChecked

            if (settings.tagFunctionality == Constants.TagFunctionality.SINGLE) {
                checkedTextView.isChecked = true
                allTagsButton.isChecked = false
                settings.allTagsToggle = false

                for (tag in tagsDrawerAdapter.tags) {
                    settings.setTagToggle(tag, false)
                    tagsDrawerAdapter.setTagState(tag, false)
                }
            }

            settings.noTagsToggle = checkedTextView.isChecked
            adapter.filterByTags(tagsDrawerAdapter.activeTags)
        }
        noTagsButton.isChecked = settings.noTagsToggle

        listView.adapter = tagsDrawerAdapter
        listView.setOnItemClickListener { _, view, _, _ ->
            val checkedTextView = view as CheckedTextView

            if (settings.tagFunctionality == Constants.TagFunctionality.SINGLE) {
                allTagsButton.isChecked = false
                settings.allTagsToggle = false
                noTagsButton.isChecked = false
                settings.noTagsToggle = false

                for (tag in tagsDrawerAdapter.tags) {
                    settings.setTagToggle(tag, false)
                    tagsDrawerAdapter.setTagState(tag, false)
                }
                checkedTextView.isChecked = true
            } else {
                checkedTextView.isChecked = !checkedTextView.isChecked
            }

            settings.setTagToggle(checkedTextView.text.toString(), checkedTextView.isChecked)
            tagsDrawerAdapter.setTagState(checkedTextView.text.toString(), checkedTextView.isChecked)

            if (!checkedTextView.isChecked) {
                allTagsButton.isChecked = false
                settings.allTagsToggle = false
            }

            if (tagsDrawerAdapter.allTagsActive()) {
                allTagsButton.isChecked = true
                settings.allTagsToggle = true
            }

            adapter.filterByTags(tagsDrawerAdapter.activeTags)
        }

        capTagListHeight(listView)
    }

    /**
     * A ListView measured with wrap_content only measures its first row, and a sheet must not grow
     * past the screen either, so the list is given an explicit height between those bounds.
     */
    private fun capTagListHeight(list: ListView) {
        val rows = tagsDrawerAdapter.count
        if (rows <= 0)
            return

        val row = resources.getDimensionPixelSize(R.dimen.nav_row_height)
        val max = resources.getDimensionPixelSize(R.dimen.tag_filter_max_height)

        val lp = list.layoutParams
        lp.height = Math.min(rows * row, max)
        list.layoutParams = lp
    }

    fun refreshTags() {
        val tagsHashMap = HashMap<String, Boolean>()
        for (tag in tagsDrawerAdapter.tags) {
            tagsHashMap[tag] = false
        }
        for (tag in tagsDrawerAdapter.activeTags) {
            tagsHashMap[tag] = true
        }
        for (tag in adapter.tags) {
            if (!tagsHashMap.containsKey(tag))
                tagsHashMap[tag] = true
        }
        // Picks up a tag created on the Tags screen while this screen was still alive.
        for (tag in TagStore.knownTags(this)) {
            if (!tagsHashMap.containsKey(tag))
                tagsHashMap[tag] = settings.getTagToggle(tag)
        }
        tagsDrawerAdapter.setTags(tagsHashMap)
        adapter.filterByTags(tagsDrawerAdapter.activeTags)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()

        // Refresh Blackout Timer
        countDownTimer?.cancel()

        if (setCountDownTimerNow())
            countDownTimer!!.start()
    }

    private fun setCountDownTimerNow(): Boolean {
        val secondsToBlackout = 1000 * settings.authInactivityDelay

        if (settings.authMethod == AuthMethod.NONE || !settings.authInactivity || secondsToBlackout == 0)
            return false

        countDownTimer = object : CountDownTimer(secondsToBlackout.toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
            }

            override fun onFinish() {
                authenticate(R.string.auth_msg_authenticate)
                this.cancel()
            }
        }

        return true
    }

    private fun showOpenFileSelector() {
        val fileSelectorIntent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        fileSelectorIntent.addCategory(Intent.CATEGORY_OPENABLE)
        fileSelectorIntent.type = "image/*"
        qrImageLauncher.launch(fileSelectorIntent)
    }

    private fun addQRCode(result: String?) {
        if (!TextUtils.isEmpty(result)) {
            try {
                val e = Entry(result!!)
                e.updateOTP(false)
                e.lastUsed = System.currentTimeMillis()
                adapter.addEntry(e)
                refreshTags()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.toast_invalid_qr_code, Toast.LENGTH_LONG).show()
            }
        }
    }

    private inner class ProcessLifecycleObserver : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            if (this@MainActivity.settings.relockOnBackground)
                this@MainActivity.requireAuthentication = true
        }
    }

    /* Predictive back: with the OnBackInvokedCallback enabled (the default when targeting
     * Android 16+), KEYCODE_BACK is no longer dispatched. The callback is only enabled while the
     * search mode is open, so the system back animation still runs otherwise. */
    private val closeOverlaysOnBack: OnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (searchMode)
                exitSearchMode()
            updateBackCallbackState()
        }
    }

    private fun updateBackCallbackState() {
        closeOverlaysOnBack.isEnabled = searchMode
    }

    override fun onDestroy() {
        dismissSheet()
        settings.unregisterPreferenceChangeListener(this)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        super.onDestroy()
    }

    /**
     * Shows the empty state when the list has no items and hides the global countdown while there
     * is nothing to count down for. In search mode the "no results" copy is used instead.
     */
    private fun updateEmptyState() {
        val itemCount = adapter.itemCount
        val empty = itemCount <= 0

        if (!searchMode)
            appBarCountdown.visibility = if (settings.isHideGlobalTimeoutEnabled || empty) View.GONE else View.VISIBLE

        if (searchMode) {
            emptyIllustration.setImageResource(R.drawable.ill_no_results)
            emptyTitle.setText(R.string.empty_search_title)
            emptySubtitle.setText(R.string.empty_search_subtitle)
        } else {
            emptyIllustration.setImageResource(R.drawable.ill_empty_services)
            emptyTitle.setText(R.string.empty_services_title)
            emptySubtitle.setText(R.string.empty_services_subtitle)
        }
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return false
    }

    companion object {
        @JvmField
        var animatorDuration: Long = 1000

        private const val INTENT_SCAN_QR = "com.gigabytedevelopersinc.app.cometOTP.intent.SCAN_QR"
        private const val INTENT_IMPORT_QR = "com.gigabytedevelopersinc.app.cometOTP.intent.IMPORT_QR"
        private const val INTENT_ENTER_DETAILS = "com.gigabytedevelopersinc.app.cometOTP.intent.ENTER_DETAILS"

        /** How long the branded launch screen stays up, covering its animation. */
        private const val SPLASH_DURATION_MS = 820L
        /** How long the launch screen takes to fade into the app. */
        private const val SPLASH_FADE_MS = 260L

        /** How long a tapped sheet row stays highlighted before the sheet closes. */
        private const val SHEET_ACTION_FEEDBACK_MS = 180L

        private const val LAST_APP_VERSION = "1"
        private var appStart: AppStart? = null

        @IdRes
        private fun sortModeToId(mode: SortMode): Int {
            return when (mode) {
                SortMode.ISSUER -> R.id.sort_issuer
                SortMode.LABEL -> R.id.sort_label
                SortMode.LAST_USED -> R.id.sort_last_used
                SortMode.MOST_USED -> R.id.sort_most_used
                else -> R.id.sort_none
            }
        }

        private fun idToSortMode(@IdRes id: Int): SortMode {
            if (id == R.id.sort_issuer) return SortMode.ISSUER
            if (id == R.id.sort_label) return SortMode.LABEL
            if (id == R.id.sort_last_used) return SortMode.LAST_USED
            if (id == R.id.sort_most_used) return SortMode.MOST_USED
            return SortMode.UNSORTED
        }
    }
}
