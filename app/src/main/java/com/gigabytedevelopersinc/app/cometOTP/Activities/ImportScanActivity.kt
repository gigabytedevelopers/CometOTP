@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GoogleAuthImportSession
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GoogleAuthMigration
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * Scans the codes of a Google Authenticator export one after another.
 *
 * The regular scanner hands back one code and closes, which for an export shown as several codes
 * would mean reopening the camera for every one. This keeps the camera running, counts the codes
 * as they arrive, ignores the repeats a continuous scan produces from one code on screen, and
 * finishes by itself once the export is complete. Closing it early keeps what was scanned.
 *
 * Nothing is handed back in the result: the codes go into [GoogleAuthImportSession.pending],
 * which the main screen reads, so the secrets never travel in an Intent.
 */
class ImportScanActivity : ThemedActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView

    /** The last text decoded; a code on screen is decoded again on every frame. */
    private var lastText: String? = null

    private var askedForCamera = false

    private val session: GoogleAuthImportSession
        get() = GoogleAuthImportSession.pending

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.import_camera_permission_denied, Toast.LENGTH_LONG).show()
            finishScanning()
        }
        // When granted, onResume starts the camera: the permission dialog paused this screen.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The codes carry every secret of the export in the clear.
        if (!settings.screenshotsEnabled)
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContentView(R.layout.activity_import_scan)

        barcodeView = findViewById(R.id.importBarcodeView)
        barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        barcodeView.decodeContinuous(BarcodeCallback { result -> onDecoded(result.text) })

        findViewById<View>(R.id.importScanClose).setOnClickListener { finishScanning() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishScanning()
        })

        applyInsets()
        showProgress()
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            barcodeView.resume()
        } else if (!askedForCamera) {
            askedForCamera = true
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    private fun onDecoded(text: String?) {
        if (text == null || text == lastText)
            return
        lastText = text

        if (!GoogleAuthMigration.isMigrationUri(text)) {
            barcodeView.setStatusText(getString(R.string.import_scan_not_export))
            return
        }

        val payload = try {
            GoogleAuthMigration.parse(text)
        } catch (e: IllegalArgumentException) {
            barcodeView.setStatusText(getString(R.string.import_scan_damaged))
            return
        }

        val added = try {
            session.add(payload)
        } catch (e: IllegalArgumentException) {
            barcodeView.setStatusText(getString(R.string.import_scan_damaged))
            return
        }

        if (added != GoogleAuthImportSession.Added.REPEAT)
            confirm()

        if (session.isComplete) {
            finishScanning()
            return
        }

        if (added == GoogleAuthImportSession.Added.RESTARTED)
            barcodeView.setStatusText(resources.getQuantityString(R.plurals.import_scan_restarted,
                    session.expected, session.received, session.expected))
        else
            showProgress()
    }

    private fun showProgress() {
        barcodeView.setStatusText(if (session.isEmpty)
            getString(R.string.import_scan_prompt_first)
        else
            resources.getQuantityString(R.plurals.import_scan_prompt_next,
                    session.expected, session.received, session.expected))
    }

    /** A tick in the hand for every new code, so the user knows to move on without looking. */
    private fun confirm() {
        val feedback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM
        else
            HapticFeedbackConstants.VIRTUAL_KEY
        barcodeView.performHapticFeedback(feedback)
    }

    private fun finishScanning() {
        setResult(RESULT_OK)
        finish()
    }

    /** Keeps the close button and the status line clear of the system bars; the camera fills the screen. */
    private fun applyInsets() {
        val close = findViewById<View>(R.id.importScanClose)
        val status = barcodeView.statusView
        val closeMargin = resources.getDimensionPixelSize(R.dimen.space_sm)
        val statusPadding = status.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.importScanRoot)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            close.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = closeMargin + bars.top
                marginStart = closeMargin + bars.left
            }
            status.updatePadding(bottom = statusPadding + bars.bottom)
            insets
        }
    }
}
