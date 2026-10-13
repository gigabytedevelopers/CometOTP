@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserStore
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSync
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.ConnectedBrowsers
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.PairedBrowser
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.Pairing
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.TestBrowsers
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.VaultPayload
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import javax.crypto.SecretKey

/**
 * Settings → Security → Connected extensions: the browsers paired with CometOTP, how each one's
 * copy stands, pairing another, and renaming or removing them. Pairing needs a lock and, the first
 * time, an explanation of what a browser gets. Removing one deletes its vault on the relay, then
 * says which services it had, since a lost computer keeps them.
 */
class ConnectedExtensionsActivity : BaseActivity() {
    private var encryptionKey: SecretKey? = null
    private val browsers: MutableList<PairedBrowser> = ArrayList()
    private lateinit var adapter: BrowserListAdapter
    private lateinit var content: View
    private lateinit var emptyState: View
    private lateinit var pausedNote: View
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private val scanPairingCode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val link = ScanActivity.takeResult()
        if (result.resultCode == RESULT_OK && link != null)
            pair(link)
    }

    private val pairBrowser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == PairBrowserActivity.RESULT_SCAN_AGAIN)
            scan()
    }

    // It names browsers and, after a removal, services: kept out of screenshots like the list.
    override val protectsScreen: Boolean
        get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.extensions_title)
        setContentView(R.layout.activity_container)
        setSupportActionBar(findViewById<Toolbar>(R.id.container_toolbar))

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_connected_extensions
        content = stub.inflate()

        val keyMaterial = intent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
        if (keyMaterial != null && keyMaterial.isNotEmpty())
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

        emptyState = content.findViewById(R.id.extensions_empty)
        // The computer icon stands in for an illustration: smaller, and in the accent colour.
        emptyState.findViewById<ImageView>(R.id.emptyIllustration).apply {
            setImageResource(R.drawable.ic_computer)
            imageTintList = ColorStateList.valueOf(Tools.getThemeColor(context, androidx.appcompat.R.attr.colorPrimary))
            layoutParams = layoutParams.apply { width = resources.getDimensionPixelSize(R.dimen.extensions_empty_icon) }
        }
        emptyState.findViewById<TextView>(R.id.emptyTitle).setText(R.string.extensions_empty_title)
        emptyState.findViewById<TextView>(R.id.emptySubtitle).setText(R.string.extensions_empty_subtitle)
        pausedNote = content.findViewById(R.id.extensions_paused)

        val list = content.findViewById<RecyclerView>(R.id.extensions_list)
        list.layoutManager = LinearLayoutManager(this)
        adapter = BrowserListAdapter()
        list.adapter = adapter

        val pairButton = content.findViewById<View>(R.id.extensions_pair)
        pairButton.visibility = if (encryptionKey != null) View.VISIBLE else View.GONE
        pairButton.setOnClickListener { beforePairing { scan() } }

        if (encryptionKey == null)
            Toast.makeText(this, R.string.toast_database_load_failed, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }

    private fun reload() {
        browsers.clear()
        encryptionKey?.let { browsers.addAll(BrowserStore.load(this, it)) }
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (browsers.isEmpty()) View.VISIBLE else View.GONE
        pausedNote.visibility = if (browsers.isNotEmpty() && BrowserSync.isPaused(settings)) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_connected_extensions, menu)
        menu.findItem(R.id.extensions_add_test)?.isVisible = TestBrowsers.AVAILABLE
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.extensions_add_test) {
            addTestBrowser()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Runs [action] once pairing may go ahead: CometOTP has a lock, since a browser gets a copy of
     * the keys, and the person has read what that means (once).
     */
    private fun beforePairing(action: () -> Unit) {
        if (BrowserSync.isPaused(settings)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.lock_required_title)
                .setMessage(R.string.pair_lock_required_message)
                // Security, where a lock is set up, is the screen below this one.
                .setPositiveButton(R.string.lock_required_set_up) { _, _ -> finish() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        if (settings.browserPairingExplained) {
            action()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_explain_title)
            .setMessage(R.string.pair_explain_message)
            .setPositiveButton(R.string.pair_explain_continue) { _, _ ->
                settings.browserPairingExplained = true
                action()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun scan() {
        scanPairingCode.launch(Intent(this, ScanActivity::class.java).putExtra(ScanActivity.EXTRA_PAIRING, true))
    }

    /** Answers the browser's pairing code [link]. */
    private fun pair(link: String) {
        val key = encryptionKey ?: return
        val code = try {
            // Debug builds pair with the relay on the development computer, over plain http.
            Pairing.parseUri(link, allowInsecureRelay = TestBrowsers.AVAILABLE)
        } catch (e: BrowserSyncException) {
            Snackbar.make(content, R.string.scan_not_pairing, Snackbar.LENGTH_LONG).show()
            return
        }
        pairBrowser.launch(PairBrowserActivity.intent(this, code, key))
    }

    /** Debug builds: a browser paired with the relay on the development computer, to try this screen. */
    private fun addTestBrowser() {
        val key = encryptionKey ?: return
        val number = browsers.size + 1
        val entries = DatabaseHelper.loadDatabase(this, key) ?: ArrayList()
        val services = VaultPayload.services(entries)
        val appContext = applicationContext
        worker.execute {
            TestBrowsers.add(appContext, key, number)
            BrowserSync.sync(appContext, services, key, cleanUpPending = false)
            main.post { if (!isDestroyed) reload() }
        }
    }

    private fun showOptions(browser: PairedBrowser) {
        if (browser.state == PairedBrowser.State.REMOVING) {
            MaterialAlertDialogBuilder(this)
                .setTitle(browser.name)
                .setItems(arrayOf(getString(R.string.extensions_retry_remove))) { _, _ -> remove(browser) }
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(browser.name)
            .setItems(arrayOf(getString(R.string.extensions_rename), getString(R.string.extensions_remove))) { _, which ->
                if (which == 0) rename(browser) else confirmRemove(browser)
            }
            .show()
    }

    private fun rename(browser: PairedBrowser) {
        val key = encryptionKey ?: return
        val builder = MaterialAlertDialogBuilder(this)
        val container = LayoutInflater.from(builder.context).inflate(R.layout.dialog_text_field, null)
        container.findViewById<TextInputLayout>(R.id.dialog_field_layout).hint = getString(R.string.extensions_name_hint)
        val input = container.findViewById<EditText>(R.id.dialog_field)
        input.setText(browser.name)
        input.setSelection(input.text.length)
        builder.setTitle(R.string.extensions_rename)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = ConnectedBrowsers.cleanName(input.text.toString()) ?: return@setPositiveButton
                BrowserStore.update(this, browser.copy(name = name), key)
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemove(browser: PairedBrowser) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.extensions_remove_title, browser.name))
            .setMessage(R.string.extensions_remove_message)
            .setPositiveButton(R.string.extensions_remove) { _, _ -> remove(browser) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Marks [browser] for removal at once, so it gets no more updates, and asks the relay to
     * delete its vault in the background; then says which services it had.
     */
    private fun remove(browser: PairedBrowser) {
        val key = encryptionKey ?: return
        val entries = DatabaseHelper.loadDatabase(this, key) ?: ArrayList()
        val appContext = applicationContext
        worker.execute {
            val done = BrowserSync.remove(appContext, browser, key)
            main.post {
                if (isDestroyed)
                    return@post
                reload()
                Snackbar.make(content,
                    if (done) getString(R.string.extensions_removed, browser.name) else getString(R.string.extensions_remove_pending),
                    Snackbar.LENGTH_LONG).show()
                showWhatItHad(browser, entries)
            }
        }
    }

    private fun showWhatItHad(browser: PairedBrowser, entries: List<Entry>) {
        val (services, deleted) = ConnectedBrowsers.sharedServices(browser.sharedIds, entries)
        if (services.isEmpty() && deleted == 0)
            return
        val lines = services.map { "• " + displayName(it) }.toMutableList()
        if (deleted > 0)
            lines.add("• " + resources.getQuantityString(R.plurals.extensions_had_deleted, deleted, deleted))
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.extensions_had_title, browser.name))
            .setMessage(lines.joinToString("\n") + "\n\n" + getString(R.string.extensions_had_advice))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun displayName(entry: Entry): String {
        val issuer = entry.issuer
        val label = entry.label
        return when {
            issuer.isNotEmpty() && !label.isNullOrEmpty() -> "$issuer ($label)"
            issuer.isNotEmpty() -> issuer
            else -> label ?: ""
        }
    }

    private fun statusText(browser: PairedBrowser): String {
        val status = when (ConnectedBrowsers.status(browser, BrowserSync.isPaused(settings))) {
            ConnectedBrowsers.Status.REMOVING -> getString(R.string.extensions_status_removing)
            ConnectedBrowsers.Status.PENDING -> getString(R.string.extensions_status_pending)
            ConnectedBrowsers.Status.BROKEN -> getString(R.string.extensions_status_broken)
            ConnectedBrowsers.Status.PAUSED -> getString(R.string.extensions_status_paused)
            ConnectedBrowsers.Status.NOT_SYNCED -> getString(R.string.extensions_status_not_synced)
            ConnectedBrowsers.Status.SYNCED -> {
                val now = System.currentTimeMillis()
                if (now - browser.uploadedAt < DateUtils.MINUTE_IN_MILLIS) getString(R.string.extensions_status_synced_now)
                else getString(R.string.extensions_status_synced,
                    DateUtils.getRelativeTimeSpanString(browser.uploadedAt, now, DateUtils.MINUTE_IN_MILLIS))
            }
        }
        val lastRead = browser.lastRead?.let { lastCheckedDay(it) } ?: return status
        return status + " · " + getString(R.string.extensions_last_checked, lastRead)
    }

    /** The relay gives the day (UTC) the browser last checked: today, yesterday or a date. */
    private fun lastCheckedDay(day: String): String? {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val time = try {
            parser.parse(day)?.time
        } catch (e: ParseException) {
            null
        } ?: return null
        return DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private inner class BrowserListAdapter : RecyclerView.Adapter<BrowserViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BrowserViewHolder =
            BrowserViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_browser_row, parent, false))

        override fun onBindViewHolder(holder: BrowserViewHolder, position: Int) = holder.bind(browsers[position])

        override fun getItemCount(): Int = browsers.size
    }

    private inner class BrowserViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val name: TextView = v.findViewById(R.id.browser_name)
        private val paired: TextView = v.findViewById(R.id.browser_paired)
        private val status: TextView = v.findViewById(R.id.browser_status)

        fun bind(browser: PairedBrowser) {
            name.text = browser.name
            paired.text = getString(R.string.extensions_paired_on,
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(browser.pairedAt)))
            status.text = statusText(browser)
            itemView.setOnClickListener { showOptions(browser) }
        }
    }
}
