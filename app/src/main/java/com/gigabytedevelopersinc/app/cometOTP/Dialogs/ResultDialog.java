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
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Outcome dialog used across the app ("Backup Created", "Restore Successful", "Message has
 * failed to send", "Warning" ...): a large icon with an optional success or failure badge, a
 * title, a message and a primary action, optionally followed by a secondary text action.
 */
public class ResultDialog {
    private enum Kind { SUCCESS, FAILURE, WARNING }

    public static AlertDialog showSuccess(@NonNull Context context, @DrawableRes int icon,
                                          @StringRes int title, @StringRes int message,
                                          @StringRes int primaryLabel, @Nullable Runnable onPrimary) {
        return show(context, icon, Kind.SUCCESS, context.getString(title), context.getString(message),
                primaryLabel, onPrimary, 0, null);
    }

    public static AlertDialog showSuccessWithSecondary(@NonNull Context context, @DrawableRes int icon,
                                                       @StringRes int title, @StringRes int message,
                                                       @StringRes int primaryLabel, @Nullable Runnable onPrimary,
                                                       @StringRes int secondaryLabel, @Nullable Runnable onSecondary) {
        return show(context, icon, Kind.SUCCESS, context.getString(title), context.getString(message),
                primaryLabel, onPrimary, secondaryLabel, onSecondary);
    }

    public static AlertDialog showFailure(@NonNull Context context, @DrawableRes int icon,
                                          @StringRes int title, @StringRes int message,
                                          @StringRes int primaryLabel, @Nullable Runnable onPrimary,
                                          @StringRes int secondaryLabel, @Nullable Runnable onSecondary) {
        return show(context, icon, Kind.FAILURE, context.getString(title), context.getString(message),
                primaryLabel, onPrimary, secondaryLabel, onSecondary);
    }

    public static AlertDialog showWarning(@NonNull Context context, @StringRes int title, @NonNull String message,
                                          @StringRes int primaryLabel, @Nullable Runnable onPrimary,
                                          @StringRes int secondaryLabel, @Nullable Runnable onSecondary) {
        return show(context, R.drawable.ic_warning_triangle, Kind.WARNING, context.getString(title), message,
                primaryLabel, onPrimary, secondaryLabel, onSecondary);
    }

    public static AlertDialog showWarningIcon(@NonNull Context context, @DrawableRes int icon,
                                              @StringRes int title, @NonNull String message,
                                              @StringRes int primaryLabel, @Nullable Runnable onPrimary,
                                              @StringRes int secondaryLabel, @Nullable Runnable onSecondary) {
        return show(context, icon, Kind.WARNING, context.getString(title), message,
                primaryLabel, onPrimary, secondaryLabel, onSecondary);
    }

    private static AlertDialog show(@NonNull Context context, @DrawableRes int icon, Kind kind,
                                    @NonNull String title, @NonNull String message,
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
        switch (kind) {
            case SUCCESS:
                badge.setImageResource(R.drawable.ic_check_mark);
                badge.setBackgroundResource(R.drawable.bg_badge_success);
                break;
            case FAILURE:
                badge.setImageResource(R.drawable.ic_close);
                badge.setBackgroundResource(R.drawable.bg_badge_error);
                break;
            case WARNING:
                badge.setVisibility(View.GONE);
                int tint = icon == R.drawable.ic_warning_triangle
                        ? Tools.getThemeColor(context, R.attr.colorWarning)
                        : Tools.getThemeColor(context, androidx.appcompat.R.attr.colorError);
                iconView.setImageTintList(android.content.res.ColorStateList.valueOf(tint));
                break;
        }
        titleView.setText(title);
        messageView.setText(message);
        primary.setText(primaryLabel);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(kind == Kind.WARNING)
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
