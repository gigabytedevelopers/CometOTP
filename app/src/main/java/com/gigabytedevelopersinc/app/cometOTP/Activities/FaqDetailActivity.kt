@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewStub
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import com.gigabytedevelopersinc.app.cometOTP.R
import java.text.DateFormat
import java.util.Date

/**
 * One FAQ entry: title, last-updated line, brand hero card and the answer, and, for an answer
 * that has them, the pages it links to.
 */
class FaqDetailActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val titles = resources.getStringArray(R.array.faq_titles)
        val bodies = resources.getStringArray(R.array.faq_bodies)
        val index = Math.max(0, Math.min(intent.getIntExtra(EXTRA_INDEX, 0), titles.size - 1))

        title = ""
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_faq_detail
        val v = stub.inflate()

        v.findViewById<TextView>(R.id.faq_detail_title).text = titles[index]
        v.findViewById<TextView>(R.id.faq_detail_body).text = bodies[index]

        val updated = v.findViewById<TextView>(R.id.faq_detail_updated)
        updated.text = getString(R.string.support_last_updated, lastUpdateDate())

        showLinks(v, index)
    }

    /** Under an answer that has them (faq_link_names), the pages it links to, each opened in the browser. */
    private fun showLinks(v: View, index: Int) {
        val names = arrayAt(R.array.faq_link_names, index) ?: return
        val urls = arrayAt(R.array.faq_link_urls, index) ?: return

        val list = v.findViewById<LinearLayout>(R.id.faq_detail_link_list)
        val inflater = LayoutInflater.from(this)
        for (i in 0 until Math.min(names.size, urls.size)) {
            val row = inflater.inflate(R.layout.item_faq_link, list, false) as TextView
            row.text = names[i]
            val url = urls[i]
            row.setOnClickListener { open(url) }
            list.addView(row)
        }
        v.findViewById<View>(R.id.faq_detail_links).visibility = View.VISIBLE
    }

    /** The string array that item [index] of the typed array [arrayRes] names, or null for @null. */
    private fun arrayAt(arrayRes: Int, index: Int): Array<String>? {
        val arrays = resources.obtainTypedArray(arrayRes)
        val id = try {
            if (index < arrays.length()) arrays.getResourceId(index, 0) else 0
        } finally {
            arrays.recycle()
        }
        return if (id == 0) null else resources.getStringArray(id)
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.faq_link_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    /** The FAQ ships with the app, so "last updated" is the build date of the installed version. */
    private fun lastUpdateDate(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            DateFormat.getDateInstance(DateFormat.LONG).format(Date(info.lastUpdateTime))
        } catch (e: PackageManager.NameNotFoundException) {
            DateFormat.getDateInstance(DateFormat.LONG).format(Date())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun shouldDestroyOnScreenOff(): Boolean {
        return false
    }

    companion object {
        const val EXTRA_INDEX = "faq_index"
    }
}
