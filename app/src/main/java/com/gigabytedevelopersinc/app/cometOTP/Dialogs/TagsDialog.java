package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore;
import com.gigabytedevelopersinc.app.cometOTP.View.TagsAdapter;

import java.util.HashMap;
import java.util.concurrent.Callable;

public class TagsDialog {
    public static void show(Context context, final TagsAdapter tagsAdapter, final Callable<?> newTagCallable, final Callable<?> selectedTagsCallable) {
        int margin = context.getResources().getDimensionPixelSize(R.dimen.activity_margin);
        int marginSmall = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_small);

        final ListView tagsSelectionView = new ListView(context);
        tagsSelectionView.setDivider(null);
        tagsSelectionView.setLayoutParams(new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tagsSelectionView.setAdapter(tagsAdapter);
        tagsSelectionView.setOnItemClickListener((parent, view, position, id) -> {
            CheckedTextView checkedTextView = ((CheckedTextView)view);
            checkedTextView.setChecked(!checkedTextView.isChecked());

            tagsAdapter.setTagState(checkedTextView.getText().toString(), checkedTextView.isChecked());
        });

        final FrameLayout tagsSelectionLayout = new FrameLayout(context);
        tagsSelectionLayout.setPaddingRelative(margin, marginSmall, margin, 0);
        tagsSelectionLayout.addView(tagsSelectionView);

        final AlertDialog.Builder tagsSelectorBuilder = new MaterialAlertDialogBuilder(context);
        tagsSelectorBuilder.setTitle(R.string.label_tags)
                .setView(tagsSelectionLayout)
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> dialogInterface.dismiss())
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    if(selectedTagsCallable != null) {
                        try {
                            selectedTagsCallable.call();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNeutralButton(R.string.button_new_tag, (dialogInterface, i) ->
                        // The same Create Tag sheet the Tags screen uses, so a tag made here gets
                        // a colour and a count setting and is stored the same way.
                        TagEditSheet.show(context, null, tagsAdapter.getTags(),
                                (oldName, name, color, showCount) -> {
                                    TagStore.put(context, name, color, showCount);

                                    HashMap<String, Boolean> allTags = tagsAdapter.getTagsWithState();
                                    allTags.put(name, true);
                                    tagsAdapter.setTags(allTags);
                                    tagsAdapter.setTagState(name, true);

                                    if (newTagCallable != null) {
                                        try {
                                            newTagCallable.call();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }))
                .setCancelable(false)
                .create()
                .show();

    }
}
