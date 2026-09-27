@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

/**
 * Collects the codes of one Google Authenticator export until every one has been scanned.
 *
 * An export of more than about ten accounts is split over several codes, which can arrive in any
 * order, twice, or mixed up with the codes of an earlier export when the user starts again in
 * Google Authenticator. Codes from a different export than the one being collected replace it:
 * each export gets a new batch id, so a different id means the user has started over.
 *
 * Holds decrypted secrets in memory until [clear]; call it once the import is done or abandoned.
 */
class GoogleAuthImportSession {

    enum class Added {
        /** A code this session had not seen. */
        NEW,
        /** The same code again; nothing changed. */
        REPEAT,
        /** A code from a different export, which replaced the codes collected so far. */
        RESTARTED
    }

    private var batchId = 0
    private var batchSize = 0
    private val parts = sortedMapOf<Int, GoogleAuthMigration.Payload>()

    /** Codes collected from the current export. */
    val received: Int
        get() = parts.size

    /** Codes the current export consists of, or 0 before the first one arrives. */
    val expected: Int
        get() = batchSize

    val isEmpty: Boolean
        get() = parts.isEmpty()

    val isComplete: Boolean
        get() = batchSize > 0 && parts.size == batchSize

    /** Every account collected so far, in the order Google Authenticator exported them. */
    val accounts: List<GoogleAuthMigration.Account>
        get() = parts.values.flatMap { it.accounts }

    /** @throws IllegalArgumentException when the code numbers itself outside its own export. */
    fun add(payload: GoogleAuthMigration.Payload): Added {
        // Exports made before batching existed leave size and index at 0: a single code.
        val size = if (payload.batchSize <= 0) 1 else payload.batchSize
        val index = if (payload.batchSize <= 0) 0 else payload.batchIndex
        require(index in 0 until size) { "Code ${index + 1} of an export of $size" }

        var restarted = false
        if (parts.isNotEmpty() && (payload.batchId != batchId || size != batchSize)) {
            parts.clear()
            restarted = true
        }

        batchId = payload.batchId
        batchSize = size

        if (parts.containsKey(index))
            return Added.REPEAT

        parts[index] = payload
        return if (restarted) Added.RESTARTED else Added.NEW
    }

    fun clear() {
        parts.clear()
        batchId = 0
        batchSize = 0
    }

    companion object {
        /**
         * The import in progress, shared by the main screen and the scanner so that the secrets
         * never travel between them in an Intent. Only touched on the main thread; lost with the
         * process, which just means scanning the codes again.
         */
        val pending = GoogleAuthImportSession()
    }
}
