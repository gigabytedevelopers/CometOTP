@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Settings

class PanicResponderActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callingIntent: Intent? = intent
        if (callingIntent != null && PANIC_TRIGGER_ACTION == callingIntent.action) {
            val settings = Settings(this)

            // Settings.panicResponse is nullable; no stored response means the preference's
            // default, the empty set (no response selected), so nothing is wiped.
            val response = settings.panicResponse ?: emptySet()

            if (response.contains("accounts")) {
                DatabaseHelper.wipeDatabase(this)
                KeyStoreHelper.wipeKeys(this)
            }

            if (response.contains("settings"))
                settings.clear(true)
        }

        finishAndRemoveTask()
    }

    companion object {
        const val PANIC_TRIGGER_ACTION = "info.guardianproject.panic.action.TRIGGER"
    }
}
