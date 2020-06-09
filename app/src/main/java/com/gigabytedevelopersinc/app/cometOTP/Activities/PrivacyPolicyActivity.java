package com.gigabytedevelopersinc.app.cometOTP.Activities;

import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.view.ViewStub;

import com.gigabytedevelopersinc.app.cometOTP.Fragments.PrivacyPolicyFragment;
import com.gigabytedevelopersinc.app.cometOTP.Fragments.TermsConditionsFragment;
import com.gigabytedevelopersinc.app.cometOTP.R;

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 6/3/2018
 **/
public class PrivacyPolicyActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.terms_conditions);
        setContentView(R.layout.activity_container);

        Toolbar toolbar = findViewById(R.id.container_toolbar);
        setSupportActionBar(toolbar);

        ViewStub stub = findViewById(R.id.container_stub);
        stub.setLayoutResource(R.layout.component_privacypolicy);

        View v = stub.inflate();

        ViewPager viewPager = v.findViewById(R.id.viewPager);
        TabLayout tabLayout = v.findViewById(R.id.tabLayout);
        PrivacyPolicyPageAdapter privacyPolicyPageAdapter = new PrivacyPolicyPageAdapter(getSupportFragmentManager());

        viewPager.setAdapter(privacyPolicyPageAdapter);
        tabLayout.setupWithViewPager(viewPager);
    }
    // Go back to the main activity
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }

    private class PrivacyPolicyPageAdapter extends FragmentPagerAdapter {
        PrivacyPolicyPageAdapter(FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int pos) {
            switch(pos) {
                case 0:
                    //getSupportActionBar().setTitle(R.string.terms_conditions);
                    return new TermsConditionsFragment();
                case 1:
                    //getSupportActionBar().setTitle(R.string.privacy_policy);
                    return new PrivacyPolicyFragment();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public String getPageTitle(int pos) {
            switch(pos) {
                case 0:
                    return getString(R.string.terms_conditions);
                case 1:
                    return getString(R.string.privacy_policy);
                default:
                    return null;
            }
        }
    }
}
