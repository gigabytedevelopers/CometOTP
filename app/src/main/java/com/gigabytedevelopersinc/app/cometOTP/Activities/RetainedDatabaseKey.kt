@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import androidx.lifecycle.ViewModel
import javax.crypto.SecretKey

/**
 * The database key of a screen that can change it (settings, security), kept across configuration
 * changes in memory only. It used to go into the saved instance state, which the system may write
 * to disk. Whatever the key was changed to has to reach MainActivity when the screen closes.
 */
class RetainedDatabaseKey : ViewModel() {
    var key: SecretKey? = null

    /** False until the first instance of the screen in this process has set [key]. */
    var initialized = false

    companion object {
        /** Saved-state flag: the key has been changed since the screen was started. */
        const val STATE_LAUNCH_KEY_STALE = "RetainedDatabaseKey.launchKeyStale"
    }
}

/** Where a newly created screen takes its database key from, see [initialDatabaseKeyState]. */
internal enum class DatabaseKeySource {
    /** The key kept in memory by the instance before a configuration change. */
    RETAINED,

    /** The key MainActivity started the screen with. */
    LAUNCH_INTENT,

    /** No key. */
    NONE
}

/**
 * Decides which database key a newly created screen works with and whether it still reports the
 * encryption as changed. [retained] is whether an earlier instance in this process kept its key
 * ([RetainedDatabaseKey.initialized]); [savedEncryptionChanged] is the flag from the saved state,
 * null without saved state; [savedLaunchKeyStale] is whether the key had been changed at any time
 * since the screen was started ([RetainedDatabaseKey.STATE_LAUNCH_KEY_STALE]).
 *
 * Saved state without a retained key means the process was killed in between. The launch intent
 * then still holds the key the screen was started with, which is stale if the key had been changed
 * since, so no key is used then (a change attempted with no key fails without touching the
 * database). Nothing is reported as changed either: MainActivity was killed too, asks for
 * authentication again and loads the current key itself, and a "changed" result without a key
 * would only make it ask a second time.
 */
internal fun initialDatabaseKeyState(
    retained: Boolean,
    savedEncryptionChanged: Boolean?,
    savedLaunchKeyStale: Boolean
): Pair<DatabaseKeySource, Boolean> {
    return when {
        retained -> Pair(DatabaseKeySource.RETAINED, savedEncryptionChanged ?: false)
        savedEncryptionChanged == null -> Pair(DatabaseKeySource.LAUNCH_INTENT, false)
        savedEncryptionChanged || savedLaunchKeyStale -> Pair(DatabaseKeySource.NONE, false)
        else -> Pair(DatabaseKeySource.LAUNCH_INTENT, false)
    }
}
