package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewStub;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.gigabytedevelopersinc.app.cometOTP.R;

import java.text.DateFormat;
import java.util.Date;

/**
 * One FAQ entry: title, last-updated line, brand hero card and the answer.
 */
public class FaqDetailActivity extends BaseActivity {
    public static final String EXTRA_INDEX = "faq_index";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String[] titles = getResources().getStringArray(R.array.faq_titles);
        String[] bodies = getResources().getStringArray(R.array.faq_bodies);
        int index = Math.max(0, Math.min(getIntent().getIntExtra(EXTRA_INDEX, 0), titles.length - 1));

        setTitle("");
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_faq_detail);
        View v = stub.inflate();

        ((TextView) v.findViewById(R.id.faq_detail_title)).setText(titles[index]);
        ((TextView) v.findViewById(R.id.faq_detail_body)).setText(bodies[index]);

        TextView updated = v.findViewById(R.id.faq_detail_updated);
        updated.setText(getString(R.string.support_last_updated, lastUpdateDate()));
    }

    /** The FAQ ships with the app, so "last updated" is the build date of the installed version. */
    private String lastUpdateDate() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return DateFormat.getDateInstance(DateFormat.LONG).format(new Date(info.lastUpdateTime));
        } catch (PackageManager.NameNotFoundException e) {
            return DateFormat.getDateInstance(DateFormat.LONG).format(new Date());
        }
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
