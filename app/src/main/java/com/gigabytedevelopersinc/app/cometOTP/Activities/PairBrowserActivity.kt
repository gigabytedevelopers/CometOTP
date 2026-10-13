@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewStub
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserStore
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairingCode
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PhonePairing
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PhonePairing.Failure
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PhonePairing.Step
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.RelayClient
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.VaultPayload
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.google.android.material.button.MaterialButton
import javax.crypto.SecretKey

/**
 * Pairing a browser, once its code is scanned: the phone's side of protocol §3.4 ([PhonePairing])
 * one step at a time. The person compares the six digits with the browser's and taps Pair; the
 * services are only sent once the browser has confirmed too. Leaving the screen cancels the
 * pairing, and the vault with it: Cancel and Back wait for that, so that Connected extensions no
 * longer lists the pairing when it comes back.
 *
 * The pairing runs on a thread of its own and outlives the screen being recreated (a theme or
 * window change), so it is kept in [current] rather than in the activity.
 */
class PairBrowserActivity : BaseActivity() {

    /** A pairing under way, the step it last reported and the digits it showed. */
    private class Running(val code: PairingCode, val pairing: PhonePairing) {
        @Volatile
        var step: Step = Step.Connecting
        @Volatile
        var sas: String = ""
        var cancelling = false
        var screen: PairBrowserActivity? = null
    }

    private lateinit var badge: ImageView
    private lateinit var title: TextView
    private lateinit var message: TextView
    private lateinit var sas: TextView
    private lateinit var progress: View
    private lateinit var primary: MaterialButton
    private lateinit var secondary: MaterialButton
    private var running: Running? = null

    // It shows the six digits and names the browser and, once paired, how many services it has.
    override val protectsScreen: Boolean
        get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.extensions_pair)
        setContentView(R.layout.activity_container)
        setSupportActionBar(findViewById<Toolbar>(R.id.container_toolbar))

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_pair_browser
        val content = stub.inflate()
        badge = content.findViewById(R.id.pair_badge)
        title = content.findViewById(R.id.pair_title)
        message = content.findViewById(R.id.pair_message)
        sas = content.findViewById(R.id.pair_sas)
        progress = content.findViewById(R.id.pair_progress)
        primary = content.findViewById(R.id.pair_primary)
        secondary = content.findViewById(R.id.pair_secondary)
        ViewCompat.setAccessibilityHeading(title, true)
        // Each new step is announced.
        ViewCompat.setAccessibilityLiveRegion(title, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        val existing = current
        val started = if (savedInstanceState != null && existing != null) existing else start()
        if (started == null) {
            finish()
            return
        }
        running = started
        started.screen = this
        show(started.step)
    }

    /** Starts the pairing handed over by [intent], with the database key from the intent. */
    private fun start(): Running? {
        val code = nextCode ?: return null
        nextCode = null
        val keyMaterial = intent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
        if (keyMaterial == null || keyMaterial.isEmpty())
            return null
        val key: SecretKey = EncryptionHelper.generateSymmetricKey(keyMaterial)
        val appContext = applicationContext
        val main = Handler(Looper.getMainLooper())
        val relay = RelayClient()
        lateinit var running: Running
        val pairing = PhonePairing(
            code,
            VaultPayload.phoneName(appContext),
            BrowserStore.records(appContext, key),
            relay,
            relay,
            listener = { step ->
                if (step is Step.Compare)
                    running.sas = step.sas
                running.step = step
                main.post { running.screen?.show(step) }
            },
        )
        running = Running(code, pairing)
        current = running
        Thread({
            pairing.run {
                VaultPayload.services(DatabaseHelper.loadDatabase(appContext, key) ?: ArrayList())
            }
        }, "Browser pairing").start()
        return running
    }

    override fun onDestroy() {
        running?.screen = null
        if (!isChangingConfigurations)
            stop()
        super.onDestroy()
    }

    /** Cancels the pairing unless it is over or being cancelled already. */
    private fun stop() {
        val ending = running ?: return
        if (current === ending)
            current = null
        if (!ending.isOver() && !ending.cancelling)
            Thread({ ending.pairing.cancel() }, "Browser pairing cancel").start()
    }

    private fun Running.isOver() = step is Step.Paired || step is Step.Failed

    /** Closes the screen; a pairing under way is cancelled first, off the main thread since that reaches the relay. */
    private fun leave() {
        val ending = running
        if (ending == null || ending.isOver()) {
            setResult(if (ending?.step is Step.Paired) Activity.RESULT_OK else Activity.RESULT_CANCELED)
            finish()
            return
        }
        if (ending.cancelling)
            return
        ending.cancelling = true
        show(ending.step)
        Thread({
            ending.pairing.cancel()
            // Whichever screen shows it by then, should it have been recreated meanwhile.
            Handler(Looper.getMainLooper()).post { ending.screen?.finish() }
        }, "Browser pairing cancel").start()
    }

    override fun onSupportNavigateUp(): Boolean {
        leave()
        return true
    }

    private fun show(step: Step) {
        if (running?.cancelling == true) {
            showCancelling()
            return
        }
        val browser = running?.code?.browserName ?: ""
        showSas(running?.sas ?: "")
        val over = step is Step.Paired || step is Step.Failed
        badge.visibility = if (over) View.VISIBLE else View.GONE
        sas.visibility = if (step is Step.Compare || step == Step.WaitingForBrowser) View.VISIBLE else View.GONE
        progress.visibility = if (over || step is Step.Compare) View.GONE else View.VISIBLE
        primary.visibility = if (step is Step.Compare || over) View.VISIBLE else View.GONE
        secondary.visibility = if (step is Step.Paired) View.GONE else View.VISIBLE
        secondary.setText(android.R.string.cancel)
        secondary.setOnClickListener { leave() }

        when (step) {
            Step.Connecting -> {
                title.text = getString(R.string.pair_connecting_title, browser)
                message.setText(R.string.pair_connecting_message)
            }
            is Step.Compare -> {
                title.setText(R.string.pair_compare_title)
                message.text = getString(R.string.pair_compare_message, browser)
                primary.setText(R.string.pair_approve)
                primary.setOnClickListener { running?.pairing?.approve() }
            }
            Step.WaitingForBrowser -> {
                title.setText(R.string.pair_waiting_title)
                message.text = getString(R.string.pair_waiting_message, browser)
            }
            Step.Sending -> {
                title.setText(R.string.pair_sending_title)
                message.text = getString(R.string.pair_sending_message, browser)
            }
            is Step.Paired -> {
                badge.setBackgroundResource(R.drawable.bg_badge_success)
                badge.setImageResource(R.drawable.ic_check_mark)
                title.text = getString(R.string.pair_done_title, step.browser.name)
                val count = step.browser.sharedIds?.size ?: 0
                message.text = resources.getQuantityString(R.plurals.pair_done_message, count, count)
                primary.setText(R.string.pair_done)
                primary.setOnClickListener { leave() }
            }
            is Step.Failed -> showFailure(step.failure)
        }
    }

    private fun showCancelling() {
        badge.visibility = View.GONE
        sas.visibility = View.GONE
        progress.visibility = View.VISIBLE
        primary.visibility = View.GONE
        secondary.visibility = View.GONE
        title.setText(R.string.pair_cancelling)
        message.text = ""
    }

    /** "135 557", read out as single digits. */
    private fun showSas(digits: String) {
        sas.text = digits
        sas.contentDescription = digits.filter { it.isDigit() }.toList().joinToString(" ")
    }

    private fun showFailure(failure: Failure) {
        if (failure == Failure.CANCELLED) {
            finish()
            return
        }
        badge.setBackgroundResource(R.drawable.bg_badge_error)
        badge.setImageResource(R.drawable.ic_close)
        val (heading, body) = when (failure) {
            Failure.INVALID_CODE -> R.string.pair_failed_invalid_title to R.string.pair_failed_invalid_message
            Failure.ALREADY_USED -> R.string.pair_failed_used_title to R.string.pair_failed_used_message
            Failure.EXPIRED -> R.string.pair_failed_expired_title to R.string.pair_failed_expired_message
            Failure.CANCELLED_IN_BROWSER -> R.string.pair_failed_cancelled_title to R.string.pair_failed_cancelled_message
            Failure.UNREACHABLE -> R.string.pair_failed_unreachable_title to R.string.pair_failed_unreachable_message
            Failure.BROKEN, Failure.CANCELLED -> R.string.pair_failed_broken_title to R.string.pair_failed_broken_message
        }
        title.setText(heading)
        message.setText(body)
        // A new code is scanned from scratch; the person cancelling in the browser meant it.
        if (failure == Failure.CANCELLED_IN_BROWSER) {
            primary.setText(R.string.pair_done)
            primary.setOnClickListener { leave() }
            secondary.visibility = View.GONE
        } else {
            primary.setText(R.string.pair_try_again)
            primary.setOnClickListener {
                setResult(RESULT_SCAN_AGAIN)
                finish()
            }
            secondary.setText(R.string.pair_done)
        }
    }

    companion object {
        /** The result asking the caller to scan a new code. */
        const val RESULT_SCAN_AGAIN = Activity.RESULT_FIRST_USER

        /** The code to answer, handed over in memory: its secret never travels in an Intent. */
        private var nextCode: PairingCode? = null

        /** The pairing under way, if any, so that a recreated screen picks it up. */
        private var current: Running? = null

        /** An intent pairing with [code]; [key] is the database's, to keep the pairing and read the services. */
        fun intent(activity: Activity, code: PairingCode, key: SecretKey): Intent {
            nextCode = code
            return Intent(activity, PairBrowserActivity::class.java)
                .putExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY, key.encoded)
        }
    }
}
