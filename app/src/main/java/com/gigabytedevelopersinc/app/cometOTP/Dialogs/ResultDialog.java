package com.gigabytedevelopersinc.app.cometOTP.Dialogs;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.gigabytedevelopersinc.app.cometOTP.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Outcome dialog used across the app ("Backup Created", "Restore Successful", "Message has
 * failed to send", ...): a large icon with a success or failure badge, a title, a message and a
 * primary action, optionally followed by a secondary text action.
 */
public class ResultDialog {

    public static AlertDialog showSuccess(@NonNull Context context, @DrawableRes int icon,
                                          @StringRes int title, @StringRes int message,
                                          @StringRes int primaryLabel, @Nullable Runnable onPrimary) {
        return show(context, icon, true, title, message, primaryLabel, onPrimary, 0, null);
    }

    public static AlertDialog showFailure(@NonNull Context context, @DrawableRes int icon,
                                          @StringRes int title, @StringRes int message,
                                          @StringRes int primaryLabel, @Nullable Runnable onPrimary,
                                          @StringRes int secondaryLabel, @Nullable Runnable onSecondary) {
        return show(context, icon, false, title, message, primaryLabel, onPrimary, secondaryLabel, onSecondary);
    }

    private static AlertDialog show(@NonNull Context context, @DrawableRes int icon, boolean success,
                                    @StringRes int title, @StringRes int message,
                                    @StringRes int primaryLabel, @Nullable Runnable onPrimary,
                                    @StringRes int secondaryLabel, @Nullable Runnable onSecondary) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_result, null, false);

        ImageView iconView = view.findViewById(R.id.resultIcon);
        ImageView badge = view.findViewById(R.id.resultBadge);
        TextView titleView = view.findViewById(R.id.resultTitle);
        TextView messageView = view.findViewById(R.id.resultMessage);
        MaterialButton primary = view.findViewById(R.id.resultPrimary);
        MaterialButton secondary = view.findViewById(R.id.resultSecondary);

        iconView.setImageResource(icon);
        badge.setImageResource(success ? R.drawable.ic_check_mark : R.drawable.ic_close);
        badge.setBackgroundResource(success ? R.drawable.bg_badge_success : R.drawable.bg_badge_error);
        titleView.setText(title);
        messageView.setText(message);
        primary.setText(primaryLabel);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(false)
                .create();

        primary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPrimary != null)
                onPrimary.run();
        });

        if (secondaryLabel != 0) {
            secondary.setVisibility(View.VISIBLE);
            secondary.setText(secondaryLabel);
            secondary.setOnClickListener(v -> {
                dialog.dismiss();
                if (onSecondary != null)
                    onSecondary.run();
            });
        }

        dialog.show();
        return dialog;
    }
}
