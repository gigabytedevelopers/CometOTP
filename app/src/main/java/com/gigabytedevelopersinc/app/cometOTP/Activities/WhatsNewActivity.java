package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.gigabytedevelopersinc.app.cometOTP.BuildConfig;
import com.gigabytedevelopersinc.app.cometOTP.R;

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 5/31/2018
 **/
public class WhatsNewActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.whatsnew_activity);

        final TextView whatsNewText = findViewById(R.id.whatsnew_version);
        final Button whatsNewButton = findViewById(R.id.whatsnew_button);
        whatsNewButton.setVisibility(View.INVISIBLE);
        whatsNewText.append(BuildConfig.VERSION_NAME);

        // TODO: Always update WhatsNew at values/strings_whatsnew after new release

        final Snackbar snackbar = Snackbar.make(findViewById(R.id.whatsnew), "Thank's for Updating CometOTP to " + BuildConfig.VERSION_NAME, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("CONTINUE", v -> {
            snackbar.dismiss();
            whatsNewButton.setVisibility(View.VISIBLE);
            whatsNewButton.setOnClickListener(view -> finish());
        });
        snackbar.show();
        /*whatsNewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });*/
    }
}
