package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry;
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog;
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.TagEditSheet;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import javax.crypto.SecretKey;

/**
 * Tags screen: lists every tag in use (plus tags created here that no entry uses yet), allows
 * creating, editing and deleting them. Renaming or deleting a tag rewrites the tag on every entry
 * that uses it, so the encrypted database stays the single source of truth.
 */
public class TagsActivity extends BaseActivity {
    private SecretKey encryptionKey = null;
    private boolean entriesChanged = false;

    private final ArrayList<Entry> entries = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();

    private TagsListAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.tags_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_tags);
        View v = stub.inflate();

        byte[] keyMaterial = getIntent().getByteArrayExtra(Constants.EXTRA_SETTINGS_ENCRYPTION_KEY);
        if (keyMaterial != null && keyMaterial.length > 0)
            encryptionKey = EncryptionHelper.generateSymmetricKey(keyMaterial);

        emptyState = v.findViewById(R.id.tags_empty);
        ((ImageView) emptyState.findViewById(R.id.emptyIllustration)).setImageResource(R.drawable.ill_no_tags);
        ((TextView) emptyState.findViewById(R.id.emptyTitle)).setText(R.string.tags_empty_title);
        ((TextView) emptyState.findViewById(R.id.emptySubtitle)).setText(R.string.tags_empty_subtitle);

        RecyclerView list = v.findViewById(R.id.tags_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TagsListAdapter();
        list.setAdapter(adapter);

        v.findViewById(R.id.tags_create).setOnClickListener(view ->
                TagEditSheet.show(this, null, tags, this::saveTag));

        loadTags();
    }

    private void loadTags() {
        entries.clear();
        if (encryptionKey != null)
            entries.addAll(DatabaseHelper.loadDatabase(this, encryptionKey));

        TreeSet<String> all = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry entry : entries)
            all.addAll(entry.getTags());
        all.addAll(TagStore.knownTags(this));

        tags.clear();
        tags.addAll(all);

        adapter.notifyDataSetChanged();
        emptyState.setVisibility(tags.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private int usageCount(String tag) {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.getTags().contains(tag))
                count++;
        }
        return count;
    }

    private void saveTag(String oldName, String name, int color, boolean showCount) {
        if (oldName != null && !oldName.equals(name)) {
            // Rename: rewrite the tag on every entry that uses it
            for (Entry entry : entries) {
                List<String> entryTags = entry.getTags();
                int index = entryTags.indexOf(oldName);
                if (index >= 0) {
                    entryTags.set(index, name);
                    entry.setTags(entryTags);
                }
            }
            saveEntries();
            TagStore.rename(this, oldName, name);
        }

        TagStore.put(this, name, color, showCount);
        loadTags();
    }

    private void confirmDelete(String tag) {
        int inUse = usageCount(tag);
        String message = inUse > 0
                ? getResources().getQuantityString(R.plurals.tags_delete_msg_in_use, inUse, inUse)
                : getString(R.string.tags_delete_msg);

        ResultDialog.showWarningIcon(this, R.drawable.ic_delete_outline, R.string.tags_delete_title, message,
                R.string.continue_on, () -> deleteTag(tag), R.string.button_cancel, null);
    }

    private void deleteTag(String tag) {
        boolean touched = false;
        for (Entry entry : entries) {
            List<String> entryTags = entry.getTags();
            if (entryTags.remove(tag)) {
                entry.setTags(entryTags);
                touched = true;
            }
        }

        if (touched)
            saveEntries();

        TagStore.remove(this, tag);
        loadTags();
    }

    private void saveEntries() {
        if (encryptionKey == null)
            return;
        DatabaseHelper.saveDatabase(this, entries, encryptionKey);
        entriesChanged = true;
    }

    @Override
    public void finish() {
        if (entriesChanged) {
            android.content.Intent data = new android.content.Intent();
            data.putExtra("reload", true);
            setResult(RESULT_OK, data);
        }
        super.finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected boolean shouldDestroyOnScreenOff() {
        return false;
    }

    private class TagsListAdapter extends RecyclerView.Adapter<TagViewHolder> {
        @NonNull
        @Override
        public TagViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tag_row, parent, false);
            return new TagViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TagViewHolder holder, int position) {
            holder.bind(tags.get(position));
        }

        @Override
        public int getItemCount() {
            return tags.size();
        }
    }

    private class TagViewHolder extends RecyclerView.ViewHolder {
        private final View colorBar;
        private final TextView caption;
        private final TextView name;
        private final View menu;

        TagViewHolder(@NonNull View itemView) {
            super(itemView);
            colorBar = itemView.findViewById(R.id.tag_color_bar);
            caption = itemView.findViewById(R.id.tag_caption);
            name = itemView.findViewById(R.id.tag_name);
            menu = itemView.findViewById(R.id.tag_menu);
        }

        void bind(String tag) {
            colorBar.setBackgroundColor(TagStore.colorOf(TagsActivity.this, tag));
            name.setText(tag);

            if (TagStore.showsCount(TagsActivity.this, tag)) {
                int count = usageCount(tag);
                caption.setText(getResources().getQuantityString(R.plurals.tags_caption_count, count, count));
            } else {
                caption.setText(R.string.tags_caption);
            }

            itemView.setOnClickListener(v -> TagEditSheet.show(TagsActivity.this, tag,
                    tags, TagsActivity.this::saveTag));

            menu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(TagsActivity.this, menu);
                popup.getMenuInflater().inflate(R.menu.menu_tag, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.menu_tag_edit) {
                        TagEditSheet.show(TagsActivity.this, tag, tags, TagsActivity.this::saveTag);
                        return true;
                    } else if (id == R.id.menu_tag_delete) {
                        confirmDelete(tag);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
    }
}
