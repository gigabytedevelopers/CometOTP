package com.gigabytedevelopersinc.app.CometOTP.Activities;

import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.webkit.WebView;

import com.gigabytedevelopersinc.app.CometOTP.R;

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 4/10/2018
 **/
public class LicensesActivity extends BaseActivity {

    //private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.licenses_activity_title);
        setContentView(R.layout.licenses_activity);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        WebView webViewer = findViewById(R.id.web_view);
        webViewer.loadUrl("file:///android_asset/www/licenses.html");
    }
}
