package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TagStore;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

/**
 * "Create Tag" / "Edit Tag" bottom sheet: name, colour picker and the show-item-count switch.
 */
public class TagEditSheet {

    public interface Callback {
        /**
         * @param oldName the tag being edited, or null when a new tag is created
         */
        void onTagSaved(@Nullable String oldName, @NonNull String name, @ColorInt int color, boolean showCount);
    }

    public static void show(@NonNull Context context, @Nullable String existingTag,
                            @NonNull List<String> takenNames, @NonNull Callback callback) {
        BottomSheetDialog sheet = new BottomSheetDialog(context);
        sheet.setContentView(R.layout.sheet_tag_edit);

        TextView title = sheet.findViewById(R.id.sheetTitle);
        View close = sheet.findViewById(R.id.sheetClose);
        TextInputLayout nameLayout = sheet.findViewById(R.id.tag_name_layout);
        TextInputEditText nameInput = sheet.findViewById(R.id.tag_name);
        View colorRow = sheet.findViewById(R.id.tag_color_row);
        View colorDot = sheet.findViewById(R.id.tag_color_dot);
        TextView colorName = sheet.findViewById(R.id.tag_color_name);
        ImageView colorChevron = sheet.findViewById(R.id.tag_color_chevron);
        LinearLayout palette = sheet.findViewById(R.id.tag_palette);
        MaterialSwitch showCount = sheet.findViewById(R.id.tag_show_count);
        MaterialButton submit = sheet.findViewById(R.id.tag_submit);

        if (title == null || close == null || nameLayout == null || nameInput == null || colorRow == null
                || colorDot == null || colorName == null || colorChevron == null || palette == null
                || showCount == null || submit == null)
            return;

        final boolean isNew = existingTag == null;
        final int[] selected = new int[]{TagStore.defaultColor(context)};

        title.setText(isNew ? R.string.tags_sheet_create : R.string.tags_sheet_edit);
        submit.setText(isNew ? R.string.button_create : R.string.button_save);

        if (!isNew) {
            nameInput.setText(existingTag);
            TagStore.Meta meta = TagStore.metaOf(context, existingTag);
            if (meta != null) {
                selected[0] = meta.color;
                showCount.setChecked(meta.showCount);
            }
        }

        final Runnable applyColor = () -> {
            tint(colorDot, selected[0]);
            colorName.setText(TagStore.colorName(context, selected[0]));
        };
        applyColor.run();

        /* Colour picker: the row toggles a swatch strip open and closed. */
        int[] colors = TagStore.palette(context);
        int swatch = dp(context, 32);
        int gap = dp(context, 6);
        for (int color : colors) {
            FrameLayout holder = new FrameLayout(context);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(swatch + gap * 2, swatch + gap * 2);
            holder.setLayoutParams(lp);

            View dot = new View(context);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(swatch, swatch, Gravity.CENTER);
            dot.setLayoutParams(dotParams);
            dot.setBackgroundResource(R.drawable.bg_color_dot);
            tint(dot, color);
            holder.addView(dot);

            View ring = new View(context);
            FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(swatch + gap * 2, swatch + gap * 2, Gravity.CENTER);
            ring.setLayoutParams(ringParams);
            ring.setBackgroundResource(R.drawable.bg_color_dot_selected);
            ring.setVisibility(color == selected[0] ? View.VISIBLE : View.GONE);
            ring.setTag("ring:" + color);
            holder.addView(ring);

            holder.setOnClickListener(v -> {
                selected[0] = color;
                for (int i = 0; i < palette.getChildCount(); i++) {
                    View child = palette.getChildAt(i);
                    if (child instanceof FrameLayout) {
                        View childRing = ((FrameLayout) child).getChildAt(1);
                        Object tag = childRing.getTag();
                        childRing.setVisibility(("ring:" + color).equals(tag) ? View.VISIBLE : View.GONE);
                    }
                }
                applyColor.run();
            });

            palette.addView(holder);
        }

        colorRow.setOnClickListener(v -> {
            boolean open = palette.getVisibility() != View.VISIBLE;
            palette.setVisibility(open ? View.VISIBLE : View.GONE);
            colorChevron.setImageResource(open ? R.drawable.ic_expand_less : R.drawable.ic_expand_more);
        });

        nameLayout.setEndIconOnClickListener(v -> new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.tags_hint_name)
                .setMessage(R.string.tags_help_name)
                .setPositiveButton(android.R.string.ok, null)
                .show());

        final Runnable validate = () -> {
            Editable text = nameInput.getText();
            String value = text != null ? text.toString().trim() : "";
            boolean duplicate = !value.isEmpty()
                    && !value.equalsIgnoreCase(existingTag)
                    && containsIgnoreCase(takenNames, value);

            nameLayout.setError(duplicate ? context.getString(R.string.tags_error_exists) : null);
            submit.setEnabled(!TextUtils.isEmpty(value) && !duplicate);
        };

        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validate.run();
            }
        });

        // Editing an existing tag starts with a valid name, so enable the button right away
        validate.run();

        close.setOnClickListener(v -> sheet.dismiss());
        submit.setOnClickListener(v -> {
            Editable text = nameInput.getText();
            String value = text != null ? text.toString().trim() : "";
            if (value.isEmpty())
                return;

            sheet.dismiss();
            callback.onTagSaved(existingTag, value, selected[0], showCount.isChecked());
        });

        sheet.show();
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String candidate : values) {
            if (candidate.equalsIgnoreCase(value))
                return true;
        }
        return false;
    }

    private static void tint(View view, @ColorInt int color) {
        GradientDrawable background = (GradientDrawable) ContextCompat.getDrawable(view.getContext(), R.drawable.bg_color_dot);
        if (background != null) {
            background = (GradientDrawable) background.mutate();
            background.setColor(color);
            view.setBackground(background);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
