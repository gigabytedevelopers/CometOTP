@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.RecycleBin
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import javax.crypto.SecretKey

/**
 * Removed accounts: what is in the [RecycleBin], how long each stays, and the way back. Restoring
 * puts an account back at the end of the list; deleting one for good, or emptying the bin, asks
 * first. The list reloads when this screen closes, like after the Tags screen.
 */
class RemovedAccountsActivity : BaseActivity() {
    private var encryptionKey: SecretKey? = null
    private var entriesChanged = false

    private val items: MutableList<RecycleBin.Removed> = ArrayList()
    private lateinit var adapter: RemovedListAdapter
    private lateinit var emptyState: View
    private lateinit var content: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.removed_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_removed
        content = stub.inflate()

        val keyMaterial = intent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
        if (keyMaterial != null && keyMaterial.isNotEmpty())
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

        emptyState = content.findViewById(R.id.removed_empty)
        emptyState.findViewById<ImageView>(R.id.emptyIllustration).setImageResource(R.drawable.ill_empty_services)
        emptyState.findViewById<TextView>(R.id.emptyTitle).setText(R.string.removed_empty_title)
        emptyState.findViewById<TextView>(R.id.emptySubtitle).setText(R.string.removed_empty_subtitle)

        val list = content.findViewById<RecyclerView>(R.id.removed_list)
        list.layoutManager = LinearLayoutManager(this)
        adapter = RemovedListAdapter()
        list.adapter = adapter

        loadItems()
    }

    private fun loadItems() {
        val key = encryptionKey
        if (key != null)
            items.addAll(RecycleBin.load(this, key))
        else
            Toast.makeText(this, R.string.toast_database_load_failed, Toast.LENGTH_LONG).show()
        adapter.notifyItemRangeInserted(0, items.size)
        updateEmptyState()
    }

    /** Takes [item] off the screen and saves what is left in the bin. */
    private fun dropItem(item: RecycleBin.Removed, key: SecretKey) {
        val index = items.indexOf(item)
        if (index < 0)
            return
        items.removeAt(index)
        adapter.notifyItemRemoved(index)
        RecycleBin.save(this, items, key)
        updateEmptyState()
    }

    private fun updateEmptyState() {
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_removed, menu)
        menu.findItem(R.id.removed_empty_bin)?.isVisible = items.isNotEmpty()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.removed_empty_bin) {
            confirmEmptyBin()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * Puts the account back at the end of the list. One that is in the list again already (added
     * anew since) only leaves the bin.
     */
    private fun restore(item: RecycleBin.Removed) {
        val key = encryptionKey ?: return
        val entries = DatabaseHelper.loadDatabase(this, key)
        if (entries == null) {
            // Saving what was not loaded would replace every account.
            Toast.makeText(this, R.string.toast_database_load_failed, Toast.LENGTH_LONG).show()
            return
        }

        val name = displayName(item.entry)
        val message: String
        if (entries.contains(item.entry)) {
            message = getString(R.string.removed_already_in_list, name)
        } else {
            entries.add(item.entry)
            if (!DatabaseHelper.saveDatabase(this, entries, key)) {
                Snackbar.make(content, R.string.removed_restore_failed, Snackbar.LENGTH_LONG).show()
                return
            }
            entriesChanged = true
            message = getString(R.string.removed_restored, name)
        }

        dropItem(item, key)
        Snackbar.make(content, message, Snackbar.LENGTH_LONG).show()
    }

    private fun confirmDelete(item: RecycleBin.Removed) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_delete_permanently)
            .setMessage(getString(R.string.dialog_msg_delete_permanently, displayName(item.entry)))
            .setPositiveButton(R.string.button_delete_permanently) { _, _ ->
                val key = encryptionKey ?: return@setPositiveButton
                dropItem(item, key)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmEmptyBin() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_title_empty_bin)
            .setMessage(resources.getQuantityString(R.plurals.dialog_msg_empty_bin, items.size, items.size))
            .setPositiveButton(R.string.button_delete_permanently) { _, _ ->
                val count = items.size
                items.clear()
                adapter.notifyItemRangeRemoved(0, count)
                RecycleBin.wipe(this)
                updateEmptyState()
            }
            .setNegativeButton(android.R.string.cancel, null)
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

    override fun finish() {
        if (entriesChanged) {
            val data = Intent()
            data.putExtra("reload", true)
            setResult(RESULT_OK, data)
        }
        super.finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return false
    }

    private inner class RemovedListAdapter : RecyclerView.Adapter<RemovedViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemovedViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_removed_row, parent, false)
            return RemovedViewHolder(view)
        }

        override fun onBindViewHolder(holder: RemovedViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int {
            return items.size
        }
    }

    private inner class RemovedViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.removed_icon)
        private val name: TextView = v.findViewById(R.id.removed_name)
        private val label: TextView = v.findViewById(R.id.removed_label)
        private val days: TextView = v.findViewById(R.id.removed_days)
        private val restore: View = v.findViewById(R.id.removed_restore)
        private val delete: View = v.findViewById(R.id.removed_delete)

        fun bind(item: RecycleBin.Removed) {
            val entry = item.entry
            val issuer = entry.issuer
            val accountLabel = entry.label
            name.text = if (issuer.isNotEmpty()) issuer else accountLabel
            label.text = if (issuer.isNotEmpty()) accountLabel else null
            label.visibility = if (issuer.isNotEmpty() && !accountLabel.isNullOrEmpty()) View.VISIBLE else View.GONE

            val left = RecycleBin.daysLeft(item.removedAt, System.currentTimeMillis())
            days.text = resources.getQuantityString(R.plurals.removed_days_left, left, left)

            val size = resources.getDimensionPixelSize(R.dimen.issuer_icon_size)
            icon.setImageBitmap(EntryThumbnail.getThumbnailGraphic(this@RemovedAccountsActivity, issuer, accountLabel, size, entry.thumbnail))

            val shownName = displayName(entry)
            restore.contentDescription = getString(R.string.removed_restore_format, shownName)
            delete.contentDescription = getString(R.string.removed_delete_format, shownName)
            restore.setOnClickListener { restore(item) }
            delete.setOnClickListener { confirmDelete(item) }
        }
    }
}
