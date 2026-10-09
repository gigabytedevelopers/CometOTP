@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

/**
 * Reads the export file of one authenticator app.
 *
 * Implementations are pure functions of the file's bytes: no Android classes, so that they can
 * be checked against real export files on the JVM.
 */
interface ImportFormat {

    /**
     * Reads every account in the file, in the file's order.
     *
     * @param password what the user typed when asked for the file's password, or null before
     *        they were asked. A format that finds the file encrypted throws
     *        [ImportException.Kind.PASSWORD_REQUIRED] when the password is null and
     *        [ImportException.Kind.WRONG_PASSWORD] when it does not open the file.
     * @throws ImportException when the file cannot be read
     */
    @Throws(ImportException::class)
    fun read(data: ByteArray, password: String?): List<ImportedToken>
}

/** Why an export file could not be read. The kind picks the message the user sees. */
class ImportException(val kind: Kind, message: String? = null, cause: Throwable? = null) : Exception(message, cause) {

    enum class Kind {
        /** The file is not this app's export, or not one of the kinds the importer reads. */
        NOT_THIS_FORMAT,
        /** The file is this app's export, but part of it is missing or malformed. */
        DAMAGED,
        /** The file is encrypted and no password has been given yet. */
        PASSWORD_REQUIRED,
        /** The file is encrypted and the password given does not open it. */
        WRONG_PASSWORD,
        /** The file is encrypted in a way CometOTP cannot open; the app can export it unencrypted. */
        ENCRYPTION_UNSUPPORTED,
        /** The file was read, and holds no accounts. */
        EMPTY
    }

    companion object {
        fun notThisFormat(message: String? = null, cause: Throwable? = null) =
            ImportException(Kind.NOT_THIS_FORMAT, message, cause)

        fun damaged(message: String? = null, cause: Throwable? = null) =
            ImportException(Kind.DAMAGED, message, cause)

        fun passwordRequired() = ImportException(Kind.PASSWORD_REQUIRED)

        fun wrongPassword(cause: Throwable? = null) = ImportException(Kind.WRONG_PASSWORD, null, cause)

        fun encryptionUnsupported(message: String? = null) = ImportException(Kind.ENCRYPTION_UNSUPPORTED, message)

        fun empty() = ImportException(Kind.EMPTY)
    }
}
