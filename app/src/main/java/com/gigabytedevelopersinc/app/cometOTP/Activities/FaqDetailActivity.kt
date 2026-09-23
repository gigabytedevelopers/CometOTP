@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewStub
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import com.gigabytedevelopersinc.app.cometOTP.R
import java.text.DateFormat
import java.util.Date

/**
 * One FAQ entry: title, last-updated line, brand hero card and the answer.
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
