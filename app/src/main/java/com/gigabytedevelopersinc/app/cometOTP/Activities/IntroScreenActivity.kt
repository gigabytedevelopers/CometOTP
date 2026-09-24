@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.SparseArray
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.ConfirmedPasswordTransformationHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EditorActionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings
import com.gigabytedevelopersinc.app.cometOTP.Utilities.UIHelper
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.IntroActivity
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.NavigationPolicy
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.OnNavigationBlockedListener
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.app.SlideFragment
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.FragmentSlide
import com.gigabytedevelopersinc.app.cometOTP.View.IntroScreen.slide.SimpleSlide
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

class IntroScreenActivity : IntroActivity() {
    private lateinit var settings: Settings

    /* The activity is the source of truth for the choices made on the slides. The fragments
     * report their changes here and read their initial state back from here, so the intro
     * survives being recreated (rotation on large screens, where Android 16+ ignores the
     * portrait lock, or process death) without relying on fragment instances that the
     * FragmentManager may have restored on its own. */
    internal var encryptionType = Constants.EncryptionType.KEYSTORE
        private set
    internal var authMethod = Constants.AuthMethod.NONE
        private set
    private var syncEnabled = false

    private var setupFinished = false

    private fun saveSettings() {
        // Saving again while the last save is running would store the same choices twice.
        if (settingsJob.isBusy)
            return

        var password: String? = null

        if (authMethod == Constants.AuthMethod.PASSWORD || authMethod == Constants.AuthMethod.PIN) {
            val authenticationFragment = getAuthenticationFragment()
            password = authenticationFragment?.password

            if (password == null || password.isEmpty()) {
                showSetupFailed()
                return
            }
        }

        // Deriving the credentials (PBKDF2) is too slow for the main thread. Until the job is
        // done the intro can't be finished or left (see the navigation policy in onCreate()).
        setNavigationButtonsVisible(false)

        val appContext = applicationContext
        val encryptionType = encryptionType
        val authMethod = authMethod
        val syncEnabled = syncEnabled
        val newPassword = password

        settingsJob.start {
            val settings = Settings(appContext)

            // The credentials go first, in one write together with the lock method: storing the
            // method or a password encryption without them would leave a lock with nothing to
            // check against. If they can't be derived or stored, nothing is stored at all.
            var saved = true
            if (newPassword != null) {
                val credentials = settings.generateAuthCredentials(newPassword)
                saved = credentials != null && settings.saveAuthCredentials(credentials, authMethod)
            }

            if (saved) {
                settings.encryption = encryptionType
                settings.authMethod = authMethod
                settings.androidBackupServiceEnabled = syncEnabled
                settings.firstTimeWarningShown = true
            }

            val outcome: (IntroScreenActivity) -> Unit = { it.onSettingsSaved(saved) }
            outcome
        }
        lockSwipeIfNeeded()
    }

    /** Runs on whichever instance is resumed when [saveSettings]'s job has finished. */
    private fun onSettingsSaved(saved: Boolean) {
        setupFinished = saved
        if (!saved)
            showSetupFailed()

        setNavigationButtonsVisible(true)
        lockSwipeIfNeeded()
    }

    private fun showSetupFailed() {
        val finalSlide = getSlide(getCount() - 1) as SimpleSlide?

        if (finalSlide != null) {
            val finalFragment = finalSlide.fragment

            if (finalFragment != null) {
                val finalView = finalFragment.view

                if (finalView != null) {
                    val title = finalView.findViewById<TextView>(R.id.mi_title)
                    val desc = finalView.findViewById<TextView>(R.id.mi_description)

                    title.setText(R.string.intro_slide4_title_failed)
                    desc.setText(R.string.intro_slide4_desc_failed)
                }
            }
        }
    }

    private fun setNavigationButtonsVisible(visible: Boolean) {
        setButtonBackVisible(visible)
        setButtonNextVisible(visible)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settings = Settings(this)

        if (savedInstanceState != null) {
            encryptionType = Constants.EncryptionType.valueOf(savedInstanceState.getString(STATE_ENCRYPTION_TYPE, encryptionType.name))
            authMethod = Constants.AuthMethod.valueOf(savedInstanceState.getString(STATE_AUTH_METHOD, authMethod.name))
            syncEnabled = savedInstanceState.getBoolean(STATE_SYNC_ENABLED, syncEnabled)
            setupFinished = savedInstanceState.getBoolean(STATE_SETUP_FINISHED, setupFinished)
        }

        setButtonBackFunction(BUTTON_BACK_FUNCTION_BACK)

        addSlide(SimpleSlide.Builder()
            .title(R.string.intro_slide1_title)
            .description(R.string.intro_slide1_desc)
            .background(R.color.colorPrimary)
            .backgroundDark(R.color.colorPrimaryDark)
            .canGoBackward(false)
            .scrollable(false)
            .build()
        )

        addSlide(FragmentSlide.Builder()
            .background(R.color.colorPrimary)
            .backgroundDark(R.color.colorPrimaryDark)
            .fragment(EncryptionFragment())
            .build()
        )

        addSlide(FragmentSlide.Builder()
            .background(R.color.colorPrimary)
            .backgroundDark(R.color.colorPrimaryDark)
            .fragment(AuthenticationFragment())
            .build()
        )

        addSlide(FragmentSlide.Builder()
            .background(R.color.colorPrimary)
            .backgroundDark(R.color.colorPrimaryDark)
            .fragment(AndroidSyncFragment())
            .build()
        )

        addSlide(SimpleSlide.Builder()
            .title(R.string.intro_slide4_title)
            .description(R.string.intro_slide4_desc)
            .background(R.color.colorPrimary)
            .backgroundDark(R.color.colorPrimaryDark)
            .scrollable(false)
            .build()
        )

        // While the settings are being saved the intro stays on the final slide: finishing
        // before they are stored would report an unfinished setup, and leaving and coming back
        // would save them a second time.
        setNavigationPolicy(object : NavigationPolicy {
            override fun canGoForward(position: Int): Boolean {
                return !settingsJob.isBusy
            }

            override fun canGoBackward(position: Int): Boolean {
                return !settingsJob.isBusy
            }
        })

        addOnNavigationBlockedListener(object : OnNavigationBlockedListener {
            override fun onNavigationBlocked(position: Int, direction: Int) {
                if (position == SLIDE_AUTHENTICATION) {
                    val authenticationFragment = getAuthenticationFragment()
                    authenticationFragment?.flashWarning()
                }
            }
        })

        addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                if (position == getCount() - 1)
                    saveSettings()
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_ENCRYPTION_TYPE, encryptionType.name)
        outState.putString(STATE_AUTH_METHOD, authMethod.name)
        outState.putBoolean(STATE_SYNC_ENABLED, syncEnabled)
        outState.putBoolean(STATE_SETUP_FINISHED, setupFinished)
    }

    override fun onResume() {
        super.onResume()
        settingsJob.onResume(this)
        setNavigationButtonsVisible(!settingsJob.isBusy)
    }

    override fun onPause() {
        settingsJob.onPause(this)
        super.onPause()
    }

    override fun onSendActivityResult(result: Int): Intent? {
        val data = Intent()
        data.putExtra(Constants.EXTRA_INTRO_FINISHED, setupFinished)
        return data
    }

    override fun onBackNavigation() {
        // We don't want users to quit the intro screen and end up in an uninitialized state,
        // so the back gesture only walks back through the slides.
        if (getCurrentSlidePosition() > 0)
            previousSlide()
    }

    /* Accessors for the slide fragments. The adapter swaps in the instances restored by the
     * FragmentManager, so they are always looked up instead of being cached in fields. */
    private fun <T : Fragment> getSlideFragment(position: Int, type: Class<T>): T? {
        if (position < 0 || position >= getCount())
            return null

        val slide = getSlide(position)
        if (slide !is FragmentSlide)
            return null

        val fragment = slide.fragment
        return if (type.isInstance(fragment)) type.cast(fragment) else null
    }

    private fun getAuthenticationFragment(): AuthenticationFragment? {
        return getSlideFragment(SLIDE_AUTHENTICATION, AuthenticationFragment::class.java)
    }

    internal fun onEncryptionTypeSelected(newEncryptionType: Constants.EncryptionType) {
        encryptionType = newEncryptionType

        val authenticationFragment = getAuthenticationFragment()
        authenticationFragment?.updateEncryptionType(newEncryptionType)
    }

    internal fun onAuthMethodSelected(newAuthMethod: Constants.AuthMethod) {
        authMethod = newAuthMethod
    }

    internal fun onSyncEnabledChanged(enabled: Boolean) {
        syncEnabled = enabled
    }

    class EncryptionFragment : SlideFragment() {
        private var selection: Spinner? = null
        private lateinit var desc: TextView

        private var selectionMapping: SparseArray<Constants.EncryptionType>? = null

        private fun generateSelectionMapping() {
            val encValues = resources.getStringArray(R.array.settings_values_encryption)

            val selectionMapping = SparseArray<Constants.EncryptionType>()
            this.selectionMapping = selectionMapping
            for (i in encValues.indices)
                selectionMapping.put(i, Constants.EncryptionType.valueOf(encValues[i].uppercase(Locale.ROOT)))
        }

        val encryptionType: Constants.EncryptionType?
            get() {
                val selection = selection
                val selectionMapping = selectionMapping
                if (selection == null || selectionMapping == null)
                    return Constants.EncryptionType.KEYSTORE

                return selectionMapping.get(selection.selectedItemPosition)
            }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            val root = inflater.inflate(R.layout.component_intro_encryption, container, false)

            val selection = root.findViewById<Spinner>(R.id.introEncryptionSelection)
            this.selection = selection
            desc = root.findViewById(R.id.introEncryptionDesc)

            generateSelectionMapping()

            selection.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                    val encryptionType = selectionMapping!!.get(i)

                    if (encryptionType == Constants.EncryptionType.PASSWORD)
                        desc.setText(R.string.intro_slide2_desc_password)
                    else if (encryptionType == Constants.EncryptionType.KEYSTORE)
                        desc.setText(R.string.intro_slide2_desc_keystore)

                    val host = hostOf(this@EncryptionFragment)
                    host?.onEncryptionTypeSelected(encryptionType)
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {
                }
            }

            val host = hostOf(this)
            val initialType = host?.encryptionType ?: Constants.EncryptionType.KEYSTORE
            selection.setSelection(selectionMapping!!.indexOfValue(initialType))

            return root
        }
    }

    class AndroidSyncFragment : SlideFragment() {
        private var introAndroidSync: SwitchCompat? = null

        val syncEnabled: Boolean
            get() {
                val introAndroidSync = introAndroidSync
                return introAndroidSync != null && introAndroidSync.isChecked
            }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            val root = inflater.inflate(R.layout.component_intro_android_sync, container, false)

            val host = hostOf(this)
            val syncPossible = host != null && host.encryptionType != Constants.EncryptionType.KEYSTORE

            val introAndroidSync = root.findViewById<SwitchCompat>(R.id.introAndroidSync)
            this.introAndroidSync = introAndroidSync
            introAndroidSync.setOnCheckedChangeListener { compoundButton, b ->
                compoundButton.setText(if (b)
                    R.string.settings_toast_android_sync_enabled
                else
                    R.string.settings_toast_android_sync_disabled
                )

                val activity = hostOf(this@AndroidSyncFragment)
                activity?.onSyncEnabledChanged(b)
            }

            introAndroidSync.isChecked = syncPossible
            introAndroidSync.isEnabled = syncPossible

            return root
        }
    }

    class AuthenticationFragment : SlideFragment(), TextView.OnEditorActionListener {
        private var encryptionType = Constants.EncryptionType.KEYSTORE

        private var minLength = Constants.AUTH_MIN_PASSWORD_LENGTH
        private var lengthWarning = ""
        private var noPasswordWarning = ""
        private var confirmPasswordWarning = ""
        private var passwordMismatchWarning = ""

        private var desc: TextView? = null
        private var selection: Spinner? = null
        private var authWarnings: TextView? = null
        private var credentialsLayout: LinearLayout? = null
        private var passwordLayout: TextInputLayout? = null
        private var passwordInput: TextInputEditText? = null
        private var passwordConfirm: EditText? = null

        private var selectionMapping: SparseArray<Constants.AuthMethod>? = null

        fun updateEncryptionType(encryptionType: Constants.EncryptionType) {
            this.encryptionType = encryptionType

            val desc = desc
            if (desc != null) {
                if (encryptionType == Constants.EncryptionType.KEYSTORE) {
                    desc.setText(R.string.intro_slide3_desc_keystore)

                    selection!!.setSelection(selectionMapping!!.indexOfValue(Constants.AuthMethod.NONE))
                } else if (encryptionType == Constants.EncryptionType.PASSWORD) {
                    desc.setText(R.string.intro_slide3_desc_password)

                    val selectedMethod = selectionMapping!!.get(selection!!.selectedItemPosition)
                    if (selectedMethod != Constants.AuthMethod.PASSWORD && selectedMethod != Constants.AuthMethod.PIN)
                        selection!!.setSelection(selectionMapping!!.indexOfValue(Constants.AuthMethod.PASSWORD))
                }
            }
        }

        private fun generateSelectionMapping() {
            val authValues = Constants.AuthMethod.entries.toTypedArray()

            val selectionMapping = SparseArray<Constants.AuthMethod>()
            this.selectionMapping = selectionMapping
            for (i in authValues.indices)
                selectionMapping.put(i, authValues[i])
        }

        @Suppress("SameParameterValue")
        private fun updateWarning(resId: Int) {
            updateWarning(getString(resId))
        }

        private fun updateWarning(warning: String) {
            authWarnings!!.text = warning
        }

        private fun hideWarning() {
            authWarnings!!.visibility = View.GONE
            authWarnings!!.text = null
        }

        fun flashWarning() {
            val authWarnings = authWarnings ?: return

            if (authWarnings.text.toString().isEmpty()) {
                authWarnings.visibility = View.GONE
            } else {
                authWarnings.visibility = View.VISIBLE
                val animator = ObjectAnimator.ofInt(authWarnings, "backgroundColor",
                    Color.TRANSPARENT, ContextCompat.getColor(requireContext(), R.color.warning_red), Color.TRANSPARENT)
                animator.duration = 500
                animator.repeatCount = 0
                animator.interpolator = AccelerateDecelerateInterpolator()
                animator.setEvaluator(ArgbEvaluator())
                animator.start()
            }
        }

        val authMethod: Constants.AuthMethod?
            get() {
                val selection = selection
                val selectionMapping = selectionMapping
                if (selection == null || selectionMapping == null)
                    return Constants.AuthMethod.NONE

                return selectionMapping.get(selection.selectedItemPosition)
            }

        val password: String?
            get() {
                val text = passwordInput?.text
                return text?.toString()
            }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                                  savedInstanceState: Bundle?): View {
            val root = inflater.inflate(R.layout.component_intro_authentication, container, false)

            desc = root.findViewById(R.id.introAuthDesc)
            selection = root.findViewById(R.id.introAuthSelection)
            authWarnings = root.findViewById(R.id.introAuthWarnings)
            credentialsLayout = root.findViewById(R.id.introCredentialsLayout)
            passwordLayout = root.findViewById(R.id.introPasswordLayout)
            passwordInput = root.findViewById(R.id.introPasswordEdit)
            passwordConfirm = root.findViewById(R.id.introPasswordConfirm)
            // The fields would otherwise keep the password typed so far in the saved instance
            // state, which the system may write to disk.
            passwordInput!!.isSaveEnabled = false
            passwordConfirm!!.isSaveEnabled = false

            generateSelectionMapping()

            // Pick up the choices made so far (also after the activity has been recreated)
            val host = hostOf(this)
            var initialMethod = Constants.AuthMethod.NONE
            if (host != null) {
                encryptionType = host.encryptionType
                initialMethod = host.authMethod
            }
            if (encryptionType == Constants.EncryptionType.PASSWORD) {
                desc!!.setText(R.string.intro_slide3_desc_password)
                if (initialMethod != Constants.AuthMethod.PASSWORD && initialMethod != Constants.AuthMethod.PIN)
                    initialMethod = Constants.AuthMethod.PASSWORD
            } else {
                desc!!.setText(R.string.intro_slide3_desc_keystore)
            }

            val authEntries = resources.getStringArray(R.array.settings_entries_auth)
            val spinnerArrayAdapter = object : ArrayAdapter<String>(introActivity, android.R.layout.simple_spinner_item, authEntries) {
                override fun isEnabled(position: Int): Boolean {
                    return encryptionType != Constants.EncryptionType.PASSWORD ||
                        position == selectionMapping!!.indexOfValue(Constants.AuthMethod.PASSWORD) ||
                        position == selectionMapping!!.indexOfValue(Constants.AuthMethod.PIN)
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getDropDownView(position, convertView, parent)
                    val tv = view as TextView

                    tv.isEnabled = encryptionType != Constants.EncryptionType.PASSWORD ||
                        position == selectionMapping!!.indexOfValue(Constants.AuthMethod.PASSWORD) ||
                        position == selectionMapping!!.indexOfValue(Constants.AuthMethod.PIN)

                    return view
                }
            }

            spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            selection!!.adapter = spinnerArrayAdapter

            selection!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, i: Int, l: Long) {
                    val authMethod = selectionMapping!!.get(i)

                    val activity = hostOf(this@AuthenticationFragment)
                    activity?.onAuthMethodSelected(authMethod)

                    if (authMethod == Constants.AuthMethod.PASSWORD) {
                        setupForPasswordInput()
                    } else if (authMethod == Constants.AuthMethod.PIN) {
                        setupForPinInput()
                    } else {
                        credentialsLayout!!.visibility = View.INVISIBLE
                        UIHelper.hideKeyboard(introActivity, root)
                    }

                    passwordInput!!.setText(null)
                    passwordConfirm!!.setText(null)

                    authWarnings!!.visibility = View.GONE

                    updateNavigation()
                }

                private fun setupForPasswordInput() {
                    credentialsLayout!!.visibility = View.VISIBLE

                    passwordLayout!!.hint = getString(R.string.settings_hint_password)
                    passwordConfirm!!.setHint(R.string.settings_hint_password_confirm)

                    passwordInput!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    passwordConfirm!!.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

                    ConfirmedPasswordTransformationHelper.setup(passwordLayout!!, passwordInput!!, passwordConfirm!!)

                    minLength = Constants.AUTH_MIN_PASSWORD_LENGTH
                    lengthWarning = getString(R.string.settings_label_short_password, minLength)
                    noPasswordWarning = getString(R.string.intro_slide3_warn_no_password)
                    confirmPasswordWarning = getString(R.string.intro_slide3_warn_confirm_password)
                    passwordMismatchWarning = getString(R.string.intro_slide3_warn_password_mismatch)

                    focusOnPasswordInput()
                }

                private fun focusOnPasswordInput() {
                    if (introActivity.getCurrentSlidePosition() == SLIDE_AUTHENTICATION) {
                        passwordInput!!.requestFocus()
                        UIHelper.showKeyboard(context!!, passwordInput)
                    }
                }

                private fun setupForPinInput() {
                    credentialsLayout!!.visibility = View.VISIBLE

                    passwordLayout!!.hint = getString(R.string.settings_hint_pin)
                    passwordConfirm!!.setHint(R.string.settings_hint_pin_confirm)

                    passwordInput!!.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                    passwordConfirm!!.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

                    ConfirmedPasswordTransformationHelper.setup(passwordLayout!!, passwordInput!!, passwordConfirm!!)

                    minLength = Constants.AUTH_MIN_PIN_LENGTH
                    lengthWarning = getString(R.string.settings_label_short_pin, minLength)
                    noPasswordWarning = getString(R.string.intro_slide3_warn_no_pin)
                    confirmPasswordWarning = getString(R.string.intro_slide3_warn_confirm_pin)
                    passwordMismatchWarning = getString(R.string.intro_slide3_warn_pin_mismatch)

                    focusOnPasswordInput()
                }

                override fun onNothingSelected(adapterView: AdapterView<*>?) {
                }
            }

            val textWatcher = object : TextWatcher {
                override fun onTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
                    updateNavigation()
                }

                override fun beforeTextChanged(charSequence: CharSequence?, i: Int, i1: Int, i2: Int) {
                }

                override fun afterTextChanged(editable: Editable?) {
                }
            }

            passwordInput!!.addTextChangedListener(textWatcher)
            passwordConfirm!!.addTextChangedListener(textWatcher)

            passwordConfirm!!.setOnEditorActionListener(this)

            selection!!.setSelection(selectionMapping!!.indexOfValue(initialMethod))

            return root
        }

        override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
            if (EditorActionHelper.isActionDoneOrKeyboardEnter(actionId, event)) {
                nextSlide()
                return true
            } else {
                // Ignore action up after keyboard enter. Otherwise the go-back button would be selected
                // after pressing enter with an invalid password. IME actions other than Done come
                // without a KeyEvent; those are left to the default handling.
                return event != null && EditorActionHelper.isActionUpKeyboardEnter(event)
            }
        }

        override fun canGoForward(): Boolean {
            val selection = selection
            val passwordInput = passwordInput
            val passwordConfirm = passwordConfirm
            if (selection == null || passwordInput == null || passwordConfirm == null) {
                // The view has not been created (yet); nothing to validate against.
                return false
            }

            val authMethod = selectionMapping!!.get(selection.selectedItemPosition)

            if (authMethod == Constants.AuthMethod.PIN || authMethod == Constants.AuthMethod.PASSWORD) {
                var password: String? = null

                if (passwordInput.text != null)
                    password = passwordInput.text.toString()

                val confirm = passwordConfirm.text.toString()

                if (password != null && password.isNotEmpty()) {
                    if (password.length < minLength) {
                        updateWarning(lengthWarning)
                        return false
                    } else {
                        if (confirm.isNotEmpty()) {
                            if (confirm == password) {
                                hideWarning()
                                return true
                            } else {
                                updateWarning(passwordMismatchWarning)
                                return false
                            }
                        } else {
                            updateWarning(confirmPasswordWarning)
                            return false
                        }
                    }
                } else {
                    updateWarning(noPasswordWarning)
                    return false
                }
            } else if (authMethod == Constants.AuthMethod.DEVICE) {
                val context = context ?: return false

                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

                if (!km.isKeyguardSecure) {
                    updateWarning(R.string.settings_toast_auth_device_not_secure)
                    return false
                }

                hideWarning()
                return true
            } else {
                hideWarning()
                return true
            }
        }
    }

    companion object {
        /** Survives recreation of this screen; see [RetainedJob]. */
        private val settingsJob = RetainedJob<IntroScreenActivity>()

        private const val STATE_ENCRYPTION_TYPE = "IntroScreenActivity.encryptionType"
        private const val STATE_AUTH_METHOD = "IntroScreenActivity.authMethod"
        private const val STATE_SYNC_ENABLED = "IntroScreenActivity.syncEnabled"
        private const val STATE_SETUP_FINISHED = "IntroScreenActivity.setupFinished"

        // Slide positions, see onCreate()
        internal const val SLIDE_WELCOME = 0
        internal const val SLIDE_ENCRYPTION = 1
        internal const val SLIDE_AUTHENTICATION = 2
        internal const val SLIDE_ANDROID_SYNC = 3
        internal const val SLIDE_FINISHED = 4

        internal fun hostOf(fragment: Fragment): IntroScreenActivity? {
            return if (fragment.activity is IntroScreenActivity) fragment.activity as IntroScreenActivity? else null
        }
    }
}
