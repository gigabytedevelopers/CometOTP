@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.json.JSONObject

/**
 * Reads the JSON FreeOTP+ exports from its menu as "Export".
 *
 * The file is `{"tokenOrder":[..],"tokens":[..]}`, each token `{"type":"TOTP","algo":"SHA1",
 * "digits":6,"period":30,"counter":0,"issuerExt":..,"issuerInt":..,"label":..,"secret":[..]}`.
 * The secret is the key as a list of signed bytes, the way Java serialises a byte array. The
 * issuer the site set in the link is `issuerExt`; `issuerInt` is the prefix of the label, and
 * `issuerAlt` and `labelAlt` are what the user renamed them to, which is what they expect to see.
 *
 * FreeOTP+ can also export the same accounts as a text file of otpauth links, which the
 * "Other apps" source reads.
 */
object FreeOtpPlusImport : ImportFormat {

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val root = ImportCrypto.jsonObject(data) ?: throw ImportException.notThisFormat("Not JSON")
        val tokens = root.optJSONArray("tokens") ?: throw ImportException.notThisFormat("No tokens")

        val out = (0 until tokens.length()).map { i ->
            readToken(tokens.optJSONObject(i) ?: throw ImportException.damaged("Token $i is not an object"))
        }
        if (out.isEmpty())
            throw ImportException.empty()
        return out
    }

    private fun readToken(token: JSONObject): ImportedToken {
        val issuer = firstPresent(token, "issuerAlt", "issuerExt", "issuerInt")
        val label = firstPresent(token, "labelAlt", "label")

        val secretArray = token.optJSONArray("secret")
        val secret = if (secretArray == null) {
            // Older exports wrote the secret as base32 text.
            if (token.has("secret") && !token.isNull("secret")) TokenImport.decodeBase32(token.optString("secret")) else ByteArray(0)
        } else {
            ByteArray(secretArray.length()) { i -> secretArray.optInt(i).toByte() }
        }

        return ImportedToken(TokenImport.type(token.optString("type")), secret, issuer, label,
                TokenImport.algorithm(token.optString("algo")),
                token.optInt("digits", TokenCalculator.TOTP_DEFAULT_DIGITS),
                token.optInt("period", TokenCalculator.TOTP_DEFAULT_PERIOD),
                token.optLong("counter", 0))
    }

    private fun firstPresent(o: JSONObject, vararg names: String): String {
        for (name in names) {
            if (o.has(name) && !o.isNull(name)) {
                val value = o.optString(name).trim()
                if (value.isNotEmpty())
                    return value
            }
        }
        return ""
    }
}
