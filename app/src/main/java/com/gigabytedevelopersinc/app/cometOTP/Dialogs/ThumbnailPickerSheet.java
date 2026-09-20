package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.GridView;

import androidx.annotation.NonNull;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings;
import com.gigabytedevelopersinc.app.cometOTP.View.ThumbnailSelectionAdapter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

/**
 * Searchable icon picker shown as an expanded bottom sheet. Used when creating a service and from
 * the card overflow menu.
 */
public class ThumbnailPickerSheet {

    public interface Callback {
        void onThumbnailPicked(EntryThumbnail.EntryThumbnails thumbnail);
    }

    public static void show(@NonNull Context context, String issuer, String label, @NonNull Callback callback) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        sheet.setContentView(R.layout.sheet_thumbnail_picker);

        View content = sheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (content != null) {
            content.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(content);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
        }

        final ThumbnailSelectionAdapter adapter = new ThumbnailSelectionAdapter(context, issuer, label);
        GridView grid = sheet.findViewById(R.id.thumbnail_grid);
        EditText search = sheet.findViewById(R.id.thumbnail_search);
        View close = sheet.findViewById(R.id.sheetClose);

        if (grid == null || search == null)
            return;

        int thumbnailSize = new Settings(context).getThumbnailSize();
        grid.setColumnWidth(Math.max(thumbnailSize, context.getResources().getDimensionPixelSize(R.dimen.issuer_icon_size)));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, position, id) -> {
            EntryThumbnail.EntryThumbnails thumbnail = EntryThumbnail.EntryThumbnails.Default;
            try {
                thumbnail = EntryThumbnail.EntryThumbnails.values()[adapter.getRealIndex(position)];
            } catch (Exception e) {
                e.printStackTrace();
            }
            sheet.dismiss();
            callback.onThumbnailPicked(thumbnail);
        });

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                adapter.filter(editable.toString());
            }
        });

        if (close != null)
            close.setOnClickListener(v -> sheet.dismiss());

        sheet.show();
    }
}
