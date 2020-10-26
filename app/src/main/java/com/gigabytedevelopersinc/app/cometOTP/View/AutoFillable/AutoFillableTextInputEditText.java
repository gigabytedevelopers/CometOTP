package com.gigabytedevelopersinc.app.cometOTP.View.AutoFillable;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.autofill.AutofillValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;

/**
 * Project - CometOTP
 * Created with Android Studio
 * Company: Gigabyte Developers
 * User: Emmanuel Nwokoma
 * Title: Founder and CEO
 * Day: Monday, 26
 * Month: October
 * Year: 2020
 * Date: 26 Oct, 2020
 * Time: 9:16 AM
 * Desc: AutoFillableTextInputEditText
 **/
public class AutoFillableTextInputEditText extends TextInputEditText {

    @Nullable private AutoFillTextListener listener = null;

    public AutoFillableTextInputEditText(@NonNull Context context) {
        super(context);
    }

    public AutoFillableTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoFillableTextInputEditText(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void autofill(AutofillValue value) {
        super.autofill(value);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || listener == null) {
            return;
        }

        if (value != null && value.isText()) {
            listener.onTextAutoFilled(value.getTextValue());
        }
    }

    public void setAutoFillTextListener(@Nullable AutoFillTextListener listener) {
        this.listener = listener;
    }

    public interface AutoFillTextListener {

        void onTextAutoFilled(@NonNull CharSequence text);
    }
}