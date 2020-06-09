package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.View.TagsAdapter;

import java.util.HashMap;
import java.util.concurrent.Callable;

public class TagsDialog {
    public static void show(Context context, final TagsAdapter tagsAdapter, final Callable newTagCallable, final Callable selectedTagsCallable) {
        int margin = context.getResources().getDimensionPixelSize(R.dimen.activity_margin);
        int marginSmall = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_small);
        int marginMedium = context.getResources().getDimensionPixelSize(R.dimen.activity_margin_medium);

        final EditText input = new EditText(context);
        input.setLayoutParams(new  FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout inputLayout = new FrameLayout(context);
        inputLayout.setPaddingRelative(marginMedium, marginSmall, marginMedium, 0);
        inputLayout.addView(input);

        final AlertDialog.Builder newTagBuilder = new AlertDialog.Builder(context);
        newTagBuilder.setTitle(R.string.button_new_tag)
                .setView(inputLayout)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                    String newTag = input.getText().toString();
                    HashMap<String, Boolean> allTags = tagsAdapter.getTagsWithState();
                    allTags.put(newTag, true);
                    tagsAdapter.setTags(allTags);
                    if(newTagCallable != null) {
                        try {
                            newTagCallable.call();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {

                });

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

        final AlertDialog.Builder tagsSelectorBuilder = new AlertDialog.Builder(context);
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
                .setNeutralButton(R.string.button_new_tag, (dialogInterface, i) -> newTagBuilder.create().show())
                .setCancelable(false)
                .create()
                .show();

    }
}
