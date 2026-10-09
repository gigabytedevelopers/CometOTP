@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.json.JSONObject

/**
 * Reads the JSON LastPass Authenticator writes under Settings > Transfer accounts > Export
 * accounts to file.
 *
 * The file is `{"version":3,"accounts":[{"issuerName":..,"userName":..,"secret":..,
 * "timeStep":30,"digits":6,"algorithm":"SHA1"}],"folders":[..]}` with the device's own ids
 * alongside. The issuer and user name are what the user sees, which they may have edited; the
 * `original` fields keep what the site set and are used when the edited ones are blank.
 * LastPass only generates time-based codes, so there is no type or counter.
 */
object LastPassImport : ImportFormat {

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val root = ImportCrypto.jsonObject(data) ?: throw ImportException.notThisFormat("Not JSON")
        val accounts = root.optJSONArray("accounts") ?: throw ImportException.notThisFormat("No accounts")

        val tokens = (0 until accounts.length()).map { i ->
            readAccount(accounts.optJSONObject(i) ?: throw ImportException.damaged("Account $i is not an object"))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    private fun readAccount(account: JSONObject): ImportedToken {
        val issuer = account.optString("issuerName").trim().ifEmpty { account.optString("originalIssuerName").trim() }
        val label = account.optString("userName").trim().ifEmpty { account.optString("originalUserName").trim() }

        return ImportedToken(Entry.OTPType.TOTP, TokenImport.decodeBase32(account.optString("secret")), issuer, label,
                TokenImport.algorithm(account.optString("algorithm")),
                account.optInt("digits", TokenCalculator.TOTP_DEFAULT_DIGITS),
                account.optInt("timeStep", TokenCalculator.TOTP_DEFAULT_PERIOD))
    }
}
