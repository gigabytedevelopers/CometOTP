@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

/**
 * Reads the export Ente Auth writes under Settings > Data > Export codes as "Plain text": one
 * otpauth link per line, the same shape the "Other apps" source reads. Ente's "Encrypted"
 * export is JSON with `kdfParams` and `encryptedData`, protected with libsodium's password
 * hashing and secret stream, which CometOTP cannot open; it is told apart so the user is sent
 * back for the plain one rather than told the file is not an export.
 */
object EnteImport : ImportFormat {

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val json = ImportCrypto.jsonObject(data)
        if (json != null && (json.has("kdfParams") || json.has("encryptedData")))
            throw ImportException.encryptionUnsupported("Encrypted Ente export")

        return OtpauthLinksImport.read(data, password)
    }
}
