package com.gigabytedevelopersinc.app.CometOTP.Utilities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class UIHelper {
    public static void showGenericDialog(Context context, int titleId, int messageId) {
        showGenericDialog(context, titleId, messageId, null);
    }

    public static void showGenericDialog(Context context, int titleId, int messageId, final Runnable onOk) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
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

    public static void showKeyboard(Context context, View view) {
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
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
