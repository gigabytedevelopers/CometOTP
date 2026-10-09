@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Reads the archive Raivo OTP (iOS) exports under Settings > Export OTPs.
 *
 * Raivo writes a ZIP archive, AES-encrypted with a password it asks for, holding
 * `raivo-otp-export.json` (and an HTML copy of the same). The JSON is a list of
 * `{"kind":"TOTP","algorithm":"SHA1","timer":"30","digits":"6","issuer":..,"account":..,
 * "secret":..,"counter":"0"}`, every value a string. The JSON on its own, taken out of the
 * archive on a computer, reads too.
 */
object RaivoImport : ImportFormat {

    private const val ZIP_SIGNATURE_LENGTH = 4

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val json = if (isZip(data)) jsonFromArchive(data, password) else data
        val entries = ImportCrypto.jsonArray(json) ?: throw ImportException.notThisFormat("Not a JSON list")

        val tokens = (0 until entries.length()).map { i ->
            readEntry(entries.optJSONObject(i) ?: throw ImportException.damaged("Entry $i is not an object"))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    private fun isZip(data: ByteArray): Boolean =
        data.size >= ZIP_SIGNATURE_LENGTH && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte() &&
                data[2].toInt() == 3 && data[3].toInt() == 4

    /** The JSON file inside the archive. */
    private fun jsonFromArchive(data: ByteArray, password: String?): ByteArray {
        try {
            ZipInputStream(ByteArrayInputStream(data), password?.toCharArray()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !entry.fileName.endsWith(".json", ignoreCase = true))
                        continue
                    if (entry.isEncrypted && password == null)
                        throw ImportException.passwordRequired()

                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0)
                            break
                        out.write(buffer, 0, n)
                    }
                    return out.toByteArray()
                }
            }
        } catch (e: ZipException) {
            // zip4j reports a missing password the same way as a wrong one, before the entry's
            // own encryption flag can be looked at.
            throw when {
                e.type != ZipException.Type.WRONG_PASSWORD -> ImportException.damaged("Cannot read the archive", e)
                password == null -> ImportException.passwordRequired()
                else -> ImportException.wrongPassword(e)
            }
        }
        throw ImportException.notThisFormat("No JSON file in the archive")
    }

    private fun readEntry(entry: JSONObject): ImportedToken {
        val type = TokenImport.type(entry.optString("kind"))
        return ImportedToken(type, TokenImport.decodeBase32(entry.optString("secret")),
                entry.optString("issuer").trim(), entry.optString("account").trim(),
                TokenImport.algorithm(entry.optString("algorithm")),
                entry.optString("digits").trim().toIntOrNull() ?: TokenCalculator.TOTP_DEFAULT_DIGITS,
                entry.optString("timer").trim().toIntOrNull() ?: TokenCalculator.TOTP_DEFAULT_PERIOD,
                entry.optString("counter").trim().toLongOrNull() ?: 0L)
    }
}
