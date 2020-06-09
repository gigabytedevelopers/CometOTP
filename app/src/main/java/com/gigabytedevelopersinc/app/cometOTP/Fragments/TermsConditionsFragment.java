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
public class TermsConditionsFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_terms_conditions, container, false);

        WebView webViewer = v.findViewById(R.id.terms_conditions_webview);
        webViewer.loadUrl("file:///android_asset/www/terms_conditions.html");
        return v;
    }
}
