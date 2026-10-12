@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

/**
 * Something received from a browser or the relay, or a scanned pairing code, broke the browser
 * sync protocol (docs/protocol.md in CometOTP-Browser). [reason] says what, for the message shown.
 */
class BrowserSyncException(val reason: Reason, message: String) : Exception(message) {

    enum class Reason {
        BAD_ENCODING,
        BAD_URI,
        /** The pairing code needs a newer CometOTP. */
        UNSUPPORTED_VERSION,
        BAD_RELAY,
        BAD_PUBLIC_KEY,
        /** A frame or blob did not open: the wrong key, or it was changed on the way. */
        AUTH_FAILED,
        BAD_MESSAGE,
        BAD_BLOB,
        /** A vault older than one already accepted. */
        ROLLBACK,
    }
}
