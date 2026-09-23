@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 6/3/2018
 **/
class PrivacyPolicyFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_privacypolicy, container, false)

        val webViewer: WebView = v.findViewById(R.id.privacy_policy_webview)
        webViewer.loadUrl("file:///android_asset/www/privacy_policy.html")
        return v
    }
}
