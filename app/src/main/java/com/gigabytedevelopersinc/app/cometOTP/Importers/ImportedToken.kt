@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator

/**
 * One account as another app exported it, read into a shape every importer shares and nothing
 * else: whether CometOTP can generate codes for it is decided once, in [TokenImport.convert].
 *
 * A value an importer could not map onto something CometOTP knows is left as null (a [type] it
 * does not generate, an [algorithm] it cannot hash with, a [secret] that is not valid base32),
 * so that the account is reported to the user by name instead of being dropped.
 *
 * @param secret the key bytes, as the token calculator uses them: the decoded base32 secret, or
 *        for MOTP the characters of the secret itself. Null when the export's secret could not be
 *        decoded, empty when it had none.
 */
class ImportedToken(
    val type: Entry.OTPType?,
    val secret: ByteArray?,
    val issuer: String,
    val label: String,
    val algorithm: TokenCalculator.HashAlgorithm? = TokenCalculator.HashAlgorithm.SHA1,
    val digits: Int = TokenCalculator.TOTP_DEFAULT_DIGITS,
    val period: Int = TokenCalculator.TOTP_DEFAULT_PERIOD,
    val counter: Long = 0
) {
    /** How the account is named in messages: issuer and label, whichever are present. */
    val displayName: String
        get() = TokenImport.displayName(issuer, label)
}
