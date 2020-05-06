package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.os.Bundle;
import androidx.appcompat.widget.Toolbar;
import android.view.ViewStub;
import android.webkit.WebView;

import com.gigabytedevelopersinc.app.cometOTP.R;

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 4/10/2018
 **/
public class LicensesActivity extends BaseActivity {

    //private WebView mWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.licenses_activity_title);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.licenses_activity);
        stub.inflate();

        WebView webViewer = findViewById(R.id.web_view);
        webViewer.loadUrl("file:///android_asset/www/licenses.html");
    }
}
