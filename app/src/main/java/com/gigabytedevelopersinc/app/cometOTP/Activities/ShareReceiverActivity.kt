@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * Where an image shared from another app arrives (the SEND and SEND_MULTIPLE filters in the
 * manifest point here), so that CometOTP is offered on the share sheet.
 *
 * The share sheet starts its target inside the sharing app's task. Pointing the filters at the
 * main screen would put a second copy of it there, on top of the gallery, with its own lock screen
 * and its own copy of the list. This screen shows nothing: it hands the images on to the main
 * screen in CometOTP's own task, reusing the instance that is already there, and closes.
 *
 * The forwarded intent carries the sender's read grant on the images, which this screen holds for
 * as long as it is alive; passing the grant on is what lets the main screen open them after this
 * one has gone.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val received = intent
        if (received != null && received.action in SHARE_ACTIONS) {
            val forward = Intent(received).setClass(this, MainActivity::class.java)
            // Only these flags. The sender's own flags are not for the main screen; CLEAR_TOP
            // with SINGLE_TOP delivers to the main screen already in the task (through
            // onNewIntent) rather than stacking another one on top of it.
            forward.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                startActivity(forward)
            } catch (e: SecurityException) {
                // The sender named an image it never granted access to; the grant cannot be
                // passed on, so there is nothing the main screen could read.
                Toast.makeText(this, R.string.share_no_image, Toast.LENGTH_LONG).show()
            }
        }

        // Theme.NoDisplay: must be finished before onResume completes.
        finish()
    }

    companion object {
        private val SHARE_ACTIONS = setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
    }
}
