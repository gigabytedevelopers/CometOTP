@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Tools
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Outcome dialog used across the app ("Backup Created", "Restore Successful", "Message has
 * failed to send", "Warning" ...): a large icon with an optional success or failure badge, a
 * title, a message and a primary action, optionally followed by a secondary text action.
 */
object ResultDialog {
    private enum class Kind { SUCCESS, FAILURE, WARNING }

    fun showSuccess(context: Context, @DrawableRes icon: Int,
                    @StringRes title: Int, @StringRes message: Int,
                    @StringRes primaryLabel: Int, onPrimary: Runnable?): AlertDialog {
        return show(context, icon, Kind.SUCCESS, context.getString(title), context.getString(message),
                primaryLabel, onPrimary, 0, null)
    }

    fun showSuccessWithSecondary(context: Context, @DrawableRes icon: Int,
                                 @StringRes title: Int, @StringRes message: Int,
                                 @StringRes primaryLabel: Int, onPrimary: Runnable?,
                                 @StringRes secondaryLabel: Int, onSecondary: Runnable?): AlertDialog {
        return show(context, icon, Kind.SUCCESS, context.getString(title), context.getString(message),
                primaryLabel, onPrimary, secondaryLabel, onSecondary)
    }

    fun showFailure(context: Context, @DrawableRes icon: Int,
                    @StringRes title: Int, @StringRes message: Int,
                    @StringRes primaryLabel: Int, onPrimary: Runnable?,
                    @StringRes secondaryLabel: Int, onSecondary: Runnable?): AlertDialog {
        return show(context, icon, Kind.FAILURE, context.getString(title), context.getString(message),
                primaryLabel, onPrimary, secondaryLabel, onSecondary)
    }

    fun showWarning(context: Context, @StringRes title: Int, message: String,
                    @StringRes primaryLabel: Int, onPrimary: Runnable?,
                    @StringRes secondaryLabel: Int, onSecondary: Runnable?): AlertDialog {
        return show(context, R.drawable.ic_warning_triangle, Kind.WARNING, context.getString(title), message,
                primaryLabel, onPrimary, secondaryLabel, onSecondary)
    }

    fun showWarningIcon(context: Context, @DrawableRes icon: Int,
                        @StringRes title: Int, message: String,
                        @StringRes primaryLabel: Int, onPrimary: Runnable?,
                        @StringRes secondaryLabel: Int, onSecondary: Runnable?): AlertDialog {
        return show(context, icon, Kind.WARNING, context.getString(title), message,
                primaryLabel, onPrimary, secondaryLabel, onSecondary)
    }

    private fun show(context: Context, @DrawableRes icon: Int, kind: Kind,
                     title: String, message: String,
                     @StringRes primaryLabel: Int, onPrimary: Runnable?,
                     @StringRes secondaryLabel: Int, onSecondary: Runnable?): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_result, null, false)

        val iconView = view.findViewById<ImageView>(R.id.resultIcon)
        val badge = view.findViewById<ImageView>(R.id.resultBadge)
        val titleView = view.findViewById<TextView>(R.id.resultTitle)
        val messageView = view.findViewById<TextView>(R.id.resultMessage)
        val primary = view.findViewById<MaterialButton>(R.id.resultPrimary)
        val secondary = view.findViewById<MaterialButton>(R.id.resultSecondary)

        iconView.setImageResource(icon)
        when (kind) {
            Kind.SUCCESS -> {
                badge.setImageResource(R.drawable.ic_check_mark)
                badge.setBackgroundResource(R.drawable.bg_badge_success)
            }
            Kind.FAILURE -> {
                badge.setImageResource(R.drawable.ic_close)
                badge.setBackgroundResource(R.drawable.bg_badge_error)
            }
            Kind.WARNING -> {
                badge.visibility = View.GONE
                val tint = if (icon == R.drawable.ic_warning_triangle)
                    Tools.getThemeColor(context, R.attr.colorWarning)
                else
                    Tools.getThemeColor(context, androidx.appcompat.R.attr.colorError)
                iconView.imageTintList = ColorStateList.valueOf(tint)
            }
        }
        titleView.text = title
        messageView.text = message
        primary.setText(primaryLabel)

        val dialog = MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(kind == Kind.WARNING)
                .create()

        primary.setOnClickListener {
            dialog.dismiss()
            onPrimary?.run()
        }

        if (secondaryLabel != 0) {
            secondary.visibility = View.VISIBLE
            secondary.setText(secondaryLabel)
            secondary.setOnClickListener {
                dialog.dismiss()
                onSecondary?.run()
            }
        }

        dialog.show()
        return dialog
    }
}
