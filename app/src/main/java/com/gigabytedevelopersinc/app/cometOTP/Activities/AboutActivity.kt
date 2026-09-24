@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewStub
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import saschpe.android.customtabs.CustomTabsHelper
import saschpe.android.customtabs.WebViewFallback


class AboutActivity : BaseActivity() {
    private var mBottomSheetDialog: BottomSheetDialog? = null

    @SuppressLint("IntentReset")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTitle(R.string.about_activity_title)
        setContentView(R.layout.activity_container)
        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)
        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_about
        val v = stub.inflate()
        val builder = CustomTabsIntent.Builder()
        builder.setShowTitle(true)
        val params = CustomTabColorSchemeParams.Builder()
            //.setNavigationBarColor(ContextCompat.getColor(this, R.color.background))
            .setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary))
            //.setSecondaryToolbarColor(ContextCompat.getColor(activity, R.color.background))
            .build()
        builder.setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, params)
        val customTabsIntent = builder.build()
        val customTabsCompanion = CustomTabsHelper.Companion
        customTabsCompanion.addKeepAliveExtra(this, customTabsIntent.intent)
        var versionName = ""
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionName = packageInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val versionLayout = v.findViewById<LinearLayout>(R.id.about_layout_version)
        versionLayout.setOnClickListener {
            val thisTap = System.currentTimeMillis()
            if (thisTap - lastTap < 500) {
                taps = taps + 1
                if (currentToast != null && taps <= 7) currentToast!!.cancel()
                if (taps >= 3 && taps <= 7) currentToast = Toast.makeText(
                    baseContext, taps.toString(), Toast.LENGTH_SHORT
                )
                if (taps == 7) {
                    if (settings.specialFeatures) Snackbar.make(
                        findViewById(R.id.about),
                        R.string.about_toast_special_features_enabled,
                        Snackbar.LENGTH_LONG
                    ).show() else enableSpecialFeatures()
                }
                if (currentToast != null) currentToast!!.show()
            } else {
                taps = 0
            }
            lastTap = thisTap
        }
        val version = v.findViewById<TextView>(R.id.about_text_version)
        version.text = versionName
        val license = v.findViewById<LinearLayout>(R.id.about_layout_license)
        val changelog = v.findViewById<LinearLayout>(R.id.about_layout_changelog)
        val source = v.findViewById<LinearLayout>(R.id.about_layout_source)
        val licenses = v.findViewById<LinearLayout>(R.id.about_layout_licenses)
        license.setOnClickListener { openURI(MIT_URI) }
        changelog.setOnClickListener {
            customTabsCompanion.openCustomTab(
                this, customTabsIntent,
                changeLogUrl.toUri(),
                WebViewFallback()
            )
        }
        source.setOnClickListener { openURI(WHATSAPP_URI) }
        licenses.setOnClickListener { showLicenses() }
        val author1GitHub = v.findViewById<TextView>(R.id.about_author1_github)
        val author1Paypal = v.findViewById<TextView>(R.id.about_author1_paypal)
        author1GitHub.setOnClickListener {
            try {
                openURI(AUTHOR1_GITHUB)
            } catch (ignored: Exception) {
                copyToClipboard(AUTHOR1_GITHUB)
            }
        }
        author1Paypal.setOnClickListener {
            try {
                openURI(AUTHOR1_PAYPAL)
            } catch (ignored: Exception) {
                copyToClipboard(AUTHOR1_PAYPAL)
            }
        }
        val author2GitHub = v.findViewById<TextView>(R.id.about_author2_github)
        val author2Paypal = v.findViewById<TextView>(R.id.about_author2_paypal)
        author2GitHub.setOnClickListener {
            try {
                openURI(AUTHOR2_GITHUB)
            } catch (ignored: Exception) {
                copyToClipboard(AUTHOR2_GITHUB)
            }
        }
        author2Paypal.setOnClickListener {
            try {
                openURI(AUTHOR2_PAYPAL)
            } catch (ignored: Exception) {
                copyToClipboard(AUTHOR2_PAYPAL)
            }
        }
        val bugReport = v.findViewById<LinearLayout>(R.id.about_layout_bugs)
        bugReport.setOnClickListener {
            val feedback = Intent(Intent.ACTION_SENDTO)
            feedback.type = "text/html"
            feedback.data = "mailto:".toUri()
            feedback.putExtra(
                Intent.EXTRA_EMAIL,
                arrayOf(getString(R.string.feedback_email_address))
            )
            feedback.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_email_subject))
            feedback.putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_email_message))
            startActivity(Intent.createChooser(feedback, getString(R.string.feedback_email_title)))
        }
        val expandButton = v.findViewById<ImageView>(R.id.thumb_expand_button)
        val expand = v.findViewById<LinearLayout>(R.id.thumb_expand)
        val disclaimer = v.findViewById<TextView>(R.id.thumb_disclaimer)
        val privacyPolicy = v.findViewById<LinearLayout>(R.id.privacy_policy)

        privacyPolicy.setOnClickListener {
            val privacyPolicyIntent = Intent(this@AboutActivity, PrivacyPolicyActivity::class.java)
            resultLauncher.launch(privacyPolicyIntent)
        }
        expand.setOnClickListener {
            val open = disclaimer.visibility != View.VISIBLE
            disclaimer.visibility = if (open) View.VISIBLE else View.GONE
            expandButton.setImageResource(
                if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more
            )
        }

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    private val resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // There are no request codes
            val data: Intent? = result.data
            //doSomeOperations()
        }
    }

    @SuppressLint("InflateParams")
    private fun enableSpecialFeatures() {
        val bottomSheetLayout = layoutInflater.inflate(R.layout.bottom_sheet_special_features, null)
        bottomSheetLayout.findViewById<View>(R.id.button_no)
            .setOnClickListener { mBottomSheetDialog!!.dismiss() }
        bottomSheetLayout.findViewById<View>(R.id.button_yes).setOnClickListener {
            mBottomSheetDialog!!.dismiss()
            settings.specialFeatures = true
            Snackbar.make(
                findViewById(R.id.about), R.string.about_toast_special_features,
                Snackbar.LENGTH_LONG
            ).show()
        }
        mBottomSheetDialog = BottomSheetDialog(this)
        mBottomSheetDialog!!.setContentView(bottomSheetLayout)
        mBottomSheetDialog!!.setCancelable(false)
        mBottomSheetDialog!!.show()
    }

    // Go back to the main activity
    /*override fun onSupportNavigateUp(): Boolean {
        finish()
        return true

    }*/

    private val onBackPressedCallback = object: OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finish()
        }
    }

    private fun openURI(uri: String?) {
        val openURI = Intent(Intent.ACTION_VIEW)
        if (uri != null) {
            openURI.data = uri.toUri()
        }
        startActivity(openURI)
    }

    private fun copyToClipboard(uri: String?) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("CometOTP", uri)
        clipboard.setPrimaryClip(clip)
        Snackbar.make(
            findViewById(R.id.about),
            getString(R.string.about_toast_copied_to_clipboard),
            Snackbar.LENGTH_LONG
        ).show()
        //Toast.makeText(this, getString(R.string.about_toast_copied_to_clipboard), Toast.LENGTH_SHORT).show();
    }

    private fun showLicenses() {
        val licensesIntent = Intent(this, LicensesActivity::class.java)
        resultLauncher.launch(licensesIntent)
        /*String backgroundColor = Tools.getCSSRGBAString(Tools.getThemeColor(this, R.attr.colorBackgroundFloating));
        String textColor = Tools.getCSSRGBAString(Tools.getThemeColor(this, android.R.attr.textColorPrimary));
        String textColorSecondary = Tools.getCSSRGBAString(Tools.getThemeColor(this, android.R.attr.textColorSecondary));
        String cssFormat = getString(R.string.custom_notices_style, backgroundColor, textColor, textColorSecondary);
        LicensesDialog dialog = new LicensesDialog.Builder(this)
                .setNotices(R.raw.licenses)
                .setTitle(R.string.about_label_licenses)
                .setShowFullLicenseText(false)
                .setIncludeOwnLicense(true)
                .setNoticesCssStyle(cssFormat)
                .build();

        dialog.show();*/
    }

    companion object {
        private const val GITHUB_URI = "https://github.com/gigabytedevelopers/CometOTP"
        private const val WHATSAPP_URI = "https://chat.whatsapp.com/HvUjQXAkMne3hjSiy1sUpc"

        // private static final String CHANGELOG_URI = GITHUB_URI + "/blob/master/CHANGELOG.md";
        private const val MIT_URI = "$GITHUB_URI/blob/master/LICENSE.txt"
        private const val AUTHOR1_GITHUB = "https://github.com/gigabytedevelopers"
        private const val AUTHOR1_PAYPAL = "https://paypal.me/gigabtedevelopers"

        private const val AUTHOR2_GITHUB = "https://github.com/enwokoma"
        private const val AUTHOR2_PAYPAL = "https://www.paypal.com/donate/?hosted_button_id=G35ZM2EKPVZ9Y"
        private const val changeLogUrl = "https://gigabytedevelopers.com/apps/cometotp/changelog"

        // private static final String BUGREPORT_URI = GITHUB_URI + "/issues";
        val imageResources = intArrayOf(
            R.id.aboutImgVersion, R.id.aboutImgLicense, R.id.aboutImgChangelog, R.id.aboutImgSource,
            R.id.aboutImgOpenSource, R.id.aboutImgAuthor1, R.id.aboutImgAuthor2, R.id.aboutImgBugs
        )
        var lastTap: Long = 0
        var taps = 0
        var currentToast: Toast? = null
    }
}