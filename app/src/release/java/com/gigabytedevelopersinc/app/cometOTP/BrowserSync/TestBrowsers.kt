@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import android.content.Context
import javax.crypto.SecretKey

/** The debug build pairs stand-in browsers to try Connected extensions (src/debug); releases cannot. */
object TestBrowsers {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun add(context: Context, key: SecretKey, number: Int, relay: Relay = RelayClient()): PairedBrowser? = null
}
