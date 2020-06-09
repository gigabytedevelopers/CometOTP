package com.gigabytedevelopersinc.app.cometOTP.Fragments;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.gigabytedevelopersinc.app.cometOTP.R;

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 6/3/2018
 **/
public class PrivacyPolicyFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_privacypolicy, container, false);

        WebView webViewer = v.findViewById(R.id.privacy_policy_webview);
        webViewer.loadUrl("file:///android_asset/www/privacy_policy.html");
        return v;
    }
}
