@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewStub
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * Support &amp; FAQs: a list of frequently asked questions and a shortcut to the contact form.
 */
class SupportActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.support_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_support
        val v = stub.inflate()

        val list = v.findViewById<LinearLayout>(R.id.faq_list)
        val titles = resources.getStringArray(R.array.faq_titles)
        val inflater = LayoutInflater.from(this)

        for (i in titles.indices) {
            val index = i
            val row = inflater.inflate(R.layout.item_faq_row, list, false)
            row.findViewById<TextView>(R.id.faq_title).text = titles[i]
            row.setOnClickListener {
                val intent = Intent(this, FaqDetailActivity::class.java)
                intent.putExtra(FaqDetailActivity.EXTRA_INDEX, index)
                startActivity(intent)
            }
            list.addView(row)
        }

        v.findViewById<View>(R.id.contact_fab).setOnClickListener {
            startActivity(Intent(this, ContactActivity::class.java))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return false
    }
}
