@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import android.content.Context
import javax.crypto.SecretKey

/**
 * The debug build pairs stand-in browsers, pairs over plain http with a local relay and takes
 * pairing links from adb (src/debug); releases do none of that.
 */
object TestBrowsers {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun add(context: Context, key: SecretKey, number: Int, relay: Relay = RelayClient()): PairedBrowser? = null
}
