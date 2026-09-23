@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.os.Bundle
import android.view.ViewStub
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gigabytedevelopersinc.app.cometOTP.Fragments.PrivacyPolicyFragment
import com.gigabytedevelopersinc.app.cometOTP.Fragments.TermsConditionsFragment
import com.gigabytedevelopersinc.app.cometOTP.R
import com.google.android.material.tabs.TabLayout

/**
 * @author Created by Emmanuel Nwokoma (Founder and CEO at Gigabyte Developers) on 6/3/2018
 **/
class PrivacyPolicyActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.terms_conditions)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.component_privacypolicy

        val v = stub.inflate()

        val viewPager = v.findViewById<ViewPager>(R.id.viewPager)
        val tabLayout = v.findViewById<TabLayout>(R.id.tabLayout)
        val privacyPolicyPageAdapter = PrivacyPolicyPageAdapter(supportFragmentManager)

        viewPager.adapter = privacyPolicyPageAdapter
        tabLayout.setupWithViewPager(viewPager)

        // Predictive back: onBackPressed() is no longer invoked when targeting Android 16+.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    // Go back to the main activity
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    @Suppress("DEPRECATION")
    private inner class PrivacyPolicyPageAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(pos: Int): Fragment {
            return when (pos) {
                0 ->
                    //getSupportActionBar().setTitle(R.string.terms_conditions);
                    TermsConditionsFragment()
                1 ->
                    //getSupportActionBar().setTitle(R.string.privacy_policy);
                    PrivacyPolicyFragment()
                // The Java original returned null here (unreachable, getCount() is 2); a Kotlin
                // override of this @NonNull method cannot, so it fails at the same point instead.
                else -> throw IllegalStateException("No page at position $pos")
            }
        }

        override fun getCount(): Int {
            return 2
        }

        override fun getPageTitle(pos: Int): String? {
            return when (pos) {
                0 -> getString(R.string.terms_conditions)
                1 -> getString(R.string.privacy_policy)
                else -> null
            }
        }
    }
}
