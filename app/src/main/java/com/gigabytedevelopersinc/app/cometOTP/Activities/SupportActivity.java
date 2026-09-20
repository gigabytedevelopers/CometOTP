package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.gigabytedevelopersinc.app.cometOTP.R;

/**
 * Support &amp; FAQs: a list of frequently asked questions and a shortcut to the contact form.
 */
public class SupportActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.support_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.content_support);
        View v = stub.inflate();

        LinearLayout list = v.findViewById(R.id.faq_list);
        String[] titles = getResources().getStringArray(R.array.faq_titles);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < titles.length; i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.item_faq_row, list, false);
            ((TextView) row.findViewById(R.id.faq_title)).setText(titles[i]);
            row.setOnClickListener(view -> {
                Intent intent = new Intent(this, FaqDetailActivity.class);
                intent.putExtra(FaqDetailActivity.EXTRA_INDEX, index);
                startActivity(intent);
            });
            list.addView(row);
        }

        v.findViewById(R.id.contact_fab).setOnClickListener(view ->
                startActivity(new Intent(this, ContactActivity.class)));
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
