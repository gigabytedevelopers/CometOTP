@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Patterns
import android.view.ViewStub
import android.widget.ArrayAdapter
import androidx.appcompat.widget.Toolbar
import com.gigabytedevelopersinc.app.cometOTP.Dialogs.ResultDialog
import com.gigabytedevelopersinc.app.cometOTP.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText

/**
 * Contact form. The message is handed to the user's email app together with the app version and
 * device details, so support can reproduce the problem.
 */
class ContactActivity : BaseActivity() {
    private lateinit var type: MaterialAutoCompleteTextView
    private lateinit var email: TextInputEditText
    private lateinit var problem: TextInputEditText
    private lateinit var send: MaterialButton
    private lateinit var types: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.contact_activity_title)
        setContentView(R.layout.activity_container)

        val toolbar = findViewById<Toolbar>(R.id.container_toolbar)
        setSupportActionBar(toolbar)

        val stub = findViewById<ViewStub>(R.id.container_stub)
        stub.layoutResource = R.layout.content_contact
        val v = stub.inflate()

        type = v.findViewById(R.id.contact_type)
        email = v.findViewById(R.id.contact_email)
        problem = v.findViewById(R.id.contact_problem)
        send = v.findViewById(R.id.contact_send)

        types = resources.getStringArray(R.array.contact_types)
        type.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, types))
        type.setText(types[0], false)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                validate()
            }
        }
        email.addTextChangedListener(watcher)
        problem.addTextChangedListener(watcher)

        send.setOnClickListener { sendMessage() }
    }

    private fun validate() {
        val mail = email.text
        val text = problem.text
        val mailOk = mail != null && Patterns.EMAIL_ADDRESS.matcher(mail.toString().trim()).matches()
        val textOk = text != null && !TextUtils.isEmpty(text.toString().trim())
        send.isEnabled = mailOk && textOk
    }

    private fun sendMessage() {
        var versionName = ""
        try {
            // versionName is nullable; leave the line blank as when the package cannot be found,
            // rather than reporting a version called "null".
            val name: String? = packageManager.getPackageInfo(packageName, 0).versionName
            versionName = name ?: ""
        } catch (ignored: PackageManager.NameNotFoundException) {
        }

        val category = type.text.toString()
        val replyTo = if (email.text != null) email.text.toString().trim() else ""
        val message = if (problem.text != null) problem.text.toString().trim() else ""

        val subject = getString(R.string.contact_email_subject, category, firstLine(message))
        val body = getString(R.string.contact_email_body, message, replyTo, versionName,
                Build.MANUFACTURER + " " + Build.MODEL, Build.VERSION.RELEASE)

        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:")
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.feedback_email_address)))
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_TEXT, body)

        try {
            startActivity(intent)
            ResultDialog.showSuccess(this, R.drawable.ic_email_outline,
                    R.string.contact_result_sent_title, R.string.contact_result_sent_msg,
                    R.string.continue_on) { finish() }
        } catch (e: ActivityNotFoundException) {
            ResultDialog.showFailure(this, R.drawable.ic_email_outline,
                    R.string.contact_result_failed_title, R.string.contact_result_failed_msg,
                    R.string.contact_try_again, { sendMessage() },
                    R.string.contact_close, null)
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
        private fun firstLine(message: String): String {
            val end = message.indexOf('\n')
            val line = if (end >= 0) message.substring(0, end) else message
            return if (line.length > 60) line.substring(0, 57) + "..." else line
        }
    }
}
