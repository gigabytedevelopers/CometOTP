@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewStub
import android.webkit.WebView
import androidx.appcompat.widget.Toolbar
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 4/10/2018
 **/
class LicensesActivity : BaseActivity() {

    //private WebView mWebView;

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.licenses_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.licenses_activity
        stub.inflate()

        val webViewer = findViewById<WebView>(R.id.web_view)
        webViewer.loadUrl("file:///android_asset/www/licenses.html")
    }
}
