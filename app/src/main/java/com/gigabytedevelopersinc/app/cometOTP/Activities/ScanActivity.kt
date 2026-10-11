@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GoogleAuthMigration
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.gigabytedevelopersinc.app.cometOTP.View.ViewfinderView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * The scanner: the camera full screen with a viewfinder, reading one QR code for a service.
 *
 * A code for a service finishes it with RESULT_OK and the code in [takeResult], kept in memory
 * so its secret never travels in an Intent; the caller confirms it before anything is added.
 * Any other code is named as such under the viewfinder, in red and with two blinks, while the
 * camera goes on looking; the hint comes back once no code has been in view for a moment.
 * The flash can be switched on and off where there is one. Without the camera permission, the
 * screen asks for it in place of the camera, or, once refused for good, opens the app's settings.
 */
class ScanActivity : ThemedActivity() {
    // The camera shows the code, secret and all.
    override val protectsScreen: Boolean
        get() = true

    private lateinit var camera: BarcodeView
    private lateinit var viewfinder: ViewfinderView
    private lateinit var status: TextView
    private lateinit var flash: ImageButton
    private lateinit var permissionPanel: View

    private var hasFlash = false
    private var torchOn = false
    private var askedForCamera = false
    private var cameraBlocked = false

    /** A code in view is decoded again on every frame: each is looked at once. */
    private var lastText: String? = null
    private var hintColor = 0
    private val resetStatus = Runnable {
        lastText = null
        status.setTextColor(hintColor)
        status.setText(R.string.scan_hint)
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Refused for good ("don't ask again"), the system asks no more.
        if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
            cameraBlocked = true
        updateCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        camera = findViewById(R.id.scanCamera)
        viewfinder = findViewById(R.id.scanViewfinder)
        status = findViewById(R.id.scanStatus)
        flash = findViewById(R.id.scanFlash)
        permissionPanel = findViewById(R.id.scanPermission)
        hintColor = status.currentTextColor
        ViewCompat.setAccessibilityHeading(findViewById(R.id.scanTitle), true)

        camera.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        camera.decodeContinuous(BarcodeCallback { result -> onDecoded(result.text) })

        findViewById<View>(R.id.scanClose).setOnClickListener { finish() }
        hasFlash = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        flash.setOnClickListener { setTorch(!torchOn) }
        findViewById<View>(R.id.scanPrivacyMore).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.scan_privacy_title)
                .setMessage(R.string.scan_privacy_msg)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        findViewById<View>(R.id.scanPermissionAllow).setOnClickListener {
            if (cameraBlocked)
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            else
                cameraPermission.launch(Manifest.permission.CAMERA)
        }

        applyInsets()
        // The hint sits under the viewfinder, wherever the square falls.
        viewfinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            status.translationY = viewfinder.squareBottom + resources.getDimension(R.dimen.space_lg)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Light icons whatever the theme: they sit on the camera image, not on the theme's background.
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    override fun onResume() {
        super.onResume()
        if (!cameraGranted() && !askedForCamera) {
            askedForCamera = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
        updateCamera()
    }

    override fun onPause() {
        super.onPause()
        // Pausing the camera also puts its light out.
        camera.pause()
        showTorch(false)
        status.removeCallbacks(resetStatus)
    }

    private fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Runs the camera, or shows why it cannot. Called from onResume and the permission's result. */
    private fun updateCamera() {
        val granted = cameraGranted()
        camera.visibility = if (granted) View.VISIBLE else View.INVISIBLE
        viewfinder.visibility = if (granted) View.VISIBLE else View.GONE
        status.visibility = if (granted) View.VISIBLE else View.GONE
        flash.visibility = if (granted && hasFlash) View.VISIBLE else View.INVISIBLE
        permissionPanel.visibility = if (granted) View.GONE else View.VISIBLE
        if (!granted) {
            findViewById<TextView>(R.id.scanPermissionText).setText(
                if (cameraBlocked) R.string.scan_camera_blocked else R.string.scan_camera_needed)
            findViewById<TextView>(R.id.scanPermissionAllow).setText(
                if (cameraBlocked) R.string.scan_camera_open_settings else R.string.scan_camera_allow)
            return
        }
        camera.resume()
    }

    private fun setTorch(on: Boolean) {
        camera.setTorch(on)
        showTorch(on)
    }

    private fun showTorch(on: Boolean) {
        torchOn = on
        flash.setImageResource(if (on) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        flash.contentDescription = getString(if (on) R.string.scan_flash_off else R.string.scan_flash_on)
    }

    private fun onDecoded(text: String?) {
        if (text == null)
            return
        status.removeCallbacks(resetStatus)
        status.postDelayed(resetStatus, STATUS_RESET_MS)
        if (text == lastText)
            return
        lastText = text

        if (!isServiceCode(text)) {
            showNotService()
            return
        }

        status.removeCallbacks(resetStatus)
        camera.performHapticFeedback(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        result = text
        setResult(RESULT_OK)
        finish()
    }

    /** Says, in red and with two blinks, that the code in view is not one to add. */
    private fun showNotService() {
        status.setText(R.string.scan_not_service)
        status.setTextColor(Tools.getThemeColor(this, androidx.appcompat.R.attr.colorError))
        ObjectAnimator.ofFloat(status, View.ALPHA, 1f, 0.2f, 1f, 0.2f, 1f)
            .setDuration(ERROR_BLINK_MS)
            .start()
    }

    private fun applyInsets() {
        val topBar = findViewById<View>(R.id.scanTopBar)
        val privacy = findViewById<View>(R.id.scanPrivacy)
        val topMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
        val bottomPadding = privacy.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scanRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> { this.topMargin = topMargin + bars.top }
            privacy.setPadding(privacy.paddingLeft, privacy.paddingTop, privacy.paddingRight, bottomPadding + bars.bottom)
            insets
        }
    }

    companion object {
        /** How long after the last code decoded the hint comes back. */
        private const val STATUS_RESET_MS = 1500L
        private const val ERROR_BLINK_MS = 700L

        /** The code read, until the caller takes it. */
        private var result: String? = null

        /** The code the scanner read, once: see [ScanActivity]. */
        fun takeResult(): String? {
            val text = result
            result = null
            return text
        }

        /** Whether [text] is a code to add: an account's link or a Google Authenticator export. */
        fun isServiceCode(text: String): Boolean {
            if (GoogleAuthMigration.isMigrationUri(text))
                return true
            return try {
                Entry(text)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
