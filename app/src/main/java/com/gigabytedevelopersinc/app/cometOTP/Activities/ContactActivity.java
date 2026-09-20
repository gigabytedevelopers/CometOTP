package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.view.ViewStub;
import android.widget.ArrayAdapter;

import androidx.appcompat.widget.Toolbar;

import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog;
import com.gigabytedevelopersinc.app.cometOTP.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Contact form. The message is handed to the user's email app together with the app version and
 * device details, so support can reproduce the problem.
 */
public class ContactActivity extends BaseActivity {
    private MaterialAutoCompleteTextView type;
    private TextInputEditText email;
    private TextInputEditText problem;
    private MaterialButton send;
    private String[] types;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.contact_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_contact);
        View v = stub.inflate();

        type = v.findViewById(R.id.contact_type);
        email = v.findViewById(R.id.contact_email);
        problem = v.findViewById(R.id.contact_problem);
        send = v.findViewById(R.id.contact_send);

        types = getResources().getStringArray(R.array.contact_types);
        type.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown, types));
        type.setText(types[0], false);

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                validate();
            }
        };
        email.addTextChangedListener(watcher);
        problem.addTextChangedListener(watcher);

        send.setOnClickListener(view -> sendMessage());
    }

    private void validate() {
        Editable mail = email.getText();
        Editable text = problem.getText();
        boolean mailOk = mail != null && Patterns.EMAIL_ADDRESS.matcher(mail.toString().trim()).matches();
        boolean textOk = text != null && !TextUtils.isEmpty(text.toString().trim());
        send.setEnabled(mailOk && textOk);
    }

    private void sendMessage() {
        String versionName = "";
        try {
            versionName = String.valueOf(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String category = type.getText().toString();
        String replyTo = email.getText() != null ? email.getText().toString().trim() : "";
        String message = problem.getText() != null ? problem.getText().toString().trim() : "";

        String subject = getString(R.string.contact_email_subject, category, firstLine(message));
        String body = getString(R.string.contact_email_body, message, replyTo, versionName,
                Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE);

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.feedback_email_address)});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);

        try {
            startActivity(intent);
            ResultDialog.showSuccess(this, R.drawable.ic_email_outline,
                    R.string.contact_result_sent_title, R.string.contact_result_sent_msg,
                    R.string.continue_on, this::finish);
        } catch (ActivityNotFoundException e) {
            ResultDialog.showFailure(this, R.drawable.ic_email_outline,
                    R.string.contact_result_failed_title, R.string.contact_result_failed_msg,
                    R.string.contact_try_again, this::sendMessage,
                    R.string.contact_close, null);
        }
    }

    private static String firstLine(String message) {
        int end = message.indexOf('\n');
        String line = end >= 0 ? message.substring(0, end) : message;
        return line.length() > 60 ? line.substring(0, 57) + "..." : line;
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected boolean shouldDestroyOnScreenOff() {
        return false;
    }
}
