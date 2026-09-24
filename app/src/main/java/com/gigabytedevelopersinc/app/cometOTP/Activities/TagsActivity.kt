@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.TagEditSheet
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore
import java.util.TreeSet
import javax.crypto.SecretKey

/**
 * Tags screen: lists every tag in use (plus tags created here that no entry uses yet), allows
 * creating, editing and deleting them. Renaming or deleting a tag rewrites the tag on every entry
 * that uses it, so the encrypted database stays the single source of truth.
 */
class TagsActivity : BaseActivity() {
    private var encryptionKey: SecretKey? = null
    private var entriesChanged = false
    // False when the database could not be read (or there is no key): the entries list is then
    // not the stored one, and saving it would replace every stored account.
    private var entriesLoaded = false

    private val entries = ArrayList<Entry>()
    private val tags: MutableList<String> = ArrayList()

    private lateinit var adapter: TagsListAdapter
    private lateinit var emptyState: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.tags_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_tags
        val v = stub.inflate()

        val keyMaterial = intent.getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY)
        if (keyMaterial != null && keyMaterial.isNotEmpty())
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial)

        emptyState = v.findViewById(R.id.tags_empty)
        emptyState.findViewById<ImageView>(R.id.emptyIllustration).setImageResource(R.drawable.ill_no_tags)
        emptyState.findViewById<TextView>(R.id.emptyTitle).setText(R.string.tags_empty_title)
        emptyState.findViewById<TextView>(R.id.emptySubtitle).setText(R.string.tags_empty_subtitle)

        val list = v.findViewById<RecyclerView>(R.id.tags_list)
        list.layoutManager = LinearLayoutManager(this)
        adapter = TagsListAdapter()
        list.adapter = adapter

        v.findViewById<View>(R.id.tags_create).setOnClickListener {
            TagEditSheet.show(this, null, tags, ::saveTag)
        }

        loadTags()
    }

    private fun loadTags() {
        entries.clear()
        entriesLoaded = false
        val encryptionKey = encryptionKey
        if (encryptionKey != null) {
            val loaded = DatabaseHelper.loadDatabase(this, encryptionKey)
            if (loaded != null) {
                entries.addAll(loaded)
                entriesLoaded = true
            } else {
                showDatabaseLoadFailed()
            }
        }

        val all = TreeSet(String.CASE_INSENSITIVE_ORDER)
        for (entry in entries)
            all.addAll(entry.tags)
        all.addAll(TagStore.knownTags(this))

        tags.clear()
        tags.addAll(all)

        adapter.notifyDataSetChanged()
        emptyState.visibility = if (tags.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun usageCount(tag: String): Int {
        var count = 0
        for (entry in entries) {
            if (entry.tags.contains(tag))
                count++
        }
        return count
    }

    private fun showDatabaseLoadFailed() {
        Toast.makeText(this, R.string.toast_database_load_failed, Toast.LENGTH_LONG).show()
    }

    private fun saveTag(oldName: String?, name: String, color: Int, showCount: Boolean) {
        if (oldName != null && oldName != name) {
            // A rename rewrites the entries, which cannot be done without them.
            if (!entriesLoaded) {
                showDatabaseLoadFailed()
                return
            }

            // Rename: rewrite the tag on every entry that uses it
            for (entry in entries) {
                val entryTags = entry.tags
                val index = entryTags.indexOf(oldName)
                if (index >= 0) {
                    entryTags[index] = name
                    entry.tags = entryTags
                }
            }
            saveEntries()
            TagStore.rename(this, oldName, name)
        }

        TagStore.put(this, name, color, showCount)
        loadTags()
    }

    private fun confirmDelete(tag: String) {
        val inUse = usageCount(tag)
        val message = if (inUse > 0)
            resources.getQuantityString(R.plurals.tags_delete_msg_in_use, inUse, inUse)
        else
            getString(R.string.tags_delete_msg)

        ResultDialog.showWarningIcon(this, R.drawable.ic_delete_outline, R.string.tags_delete_title, message,
            R.string.continue_on, { deleteTag(tag) }, R.string.button_cancel, null)
    }

    private fun deleteTag(tag: String) {
        // Deleting removes the tag from the entries, which cannot be done without them.
        if (!entriesLoaded) {
            showDatabaseLoadFailed()
            return
        }

        var touched = false
        for (entry in entries) {
            val entryTags = entry.tags
            if (entryTags.remove(tag)) {
                entry.tags = entryTags
                touched = true
            }
        }

        if (touched)
            saveEntries()

        TagStore.remove(this, tag)
        loadTags()
    }

    private fun saveEntries() {
        if (!entriesLoaded)
            return
        val encryptionKey = encryptionKey ?: return
        DatabaseHelper.saveDatabase(this, entries, encryptionKey)
        entriesChanged = true
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

    private inner class TagsListAdapter : RecyclerView.Adapter<TagViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tag_row, parent, false)
            return TagViewHolder(view)
        }

        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            holder.bind(tags[position])
        }

        override fun getItemCount(): Int {
            return tags.size
        }
    }

    private inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorBar: View = itemView.findViewById(R.id.tag_color_bar)
        private val caption: TextView = itemView.findViewById(R.id.tag_caption)
        private val name: TextView = itemView.findViewById(R.id.tag_name)
        private val menu: View = itemView.findViewById(R.id.tag_menu)

        fun bind(tag: String) {
            colorBar.setBackgroundColor(TagStore.colorOf(this@TagsActivity, tag))
            name.text = tag

            if (TagStore.showsCount(this@TagsActivity, tag)) {
                val count = usageCount(tag)
                caption.text = resources.getQuantityString(R.plurals.tags_caption_count, count, count)
            } else {
                caption.setText(R.string.tags_caption)
            }

            itemView.setOnClickListener {
                TagEditSheet.show(this@TagsActivity, tag, tags, this@TagsActivity::saveTag)
            }

            menu.setOnClickListener {
                val popup = PopupMenu(this@TagsActivity, menu)
                popup.menuInflater.inflate(R.menu.menu_tag, popup.menu)
                popup.setOnMenuItemClickListener { item ->
                    val id = item.itemId
                    if (id == R.id.menu_tag_edit) {
                        TagEditSheet.show(this@TagsActivity, tag, tags, this@TagsActivity::saveTag)
                        return@setOnMenuItemClickListener true
                    } else if (id == R.id.menu_tag_delete) {
                        confirmDelete(tag)
                        return@setOnMenuItemClickListener true
                    }
                    false
                }
                popup.show()
            }
        }
    }
}
