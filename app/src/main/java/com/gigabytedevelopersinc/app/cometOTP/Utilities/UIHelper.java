package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class UIHelper {
    public static void showGenericDialog(Context context, int titleId, int messageId) {
        showGenericDialog(context, titleId, messageId, null);
    }

    public static void showGenericDialog(Context context, int titleId, int messageId, final Runnable onOk) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(titleId)
                .setMessage(messageId)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if(onOk != null)
                            onOk.run();
                    }
                })
                .create()
                .show();
    }

    public static void showKeyboard(Context context, View view){
        showKeyboard(context,view,false);
    }

    public static void showKeyboard(Context context, View view, Boolean showForced) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(showForced)
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
            else
                imm.showSoftInput(view, 0);
        }
    }

    public static void hideKeyboard(Context context, View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}