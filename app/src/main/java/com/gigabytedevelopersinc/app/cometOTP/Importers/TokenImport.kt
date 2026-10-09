@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.apache.commons.codec.binary.Base32
import java.util.Locale

/**
 * The part of an import that every app shares: turning the accounts an export holds into entries,
 * and reporting the ones CometOTP cannot generate codes for instead of dropping them silently.
 */
object TokenImport {

    /** Why an exported account could not be brought across. */
    enum class SkipReason {
        UNSUPPORTED_TYPE,
        UNSUPPORTED_ALGORITHM,
        UNSUPPORTED_DIGITS,
        UNSUPPORTED_PERIOD,
        EMPTY_SECRET,
        INVALID_SECRET
    }

    class Skipped(val displayName: String, val reason: SkipReason)

    class Converted(val entries: List<Entry>, val skipped: List<Skipped>)

    /** The token calculator raises ten to this power as an Int; more digits would overflow it. */
    const val MAX_DIGITS = 9
    const val MIN_DIGITS = 4

    /**
     * Maps exported accounts onto entries, keeping their order. Accounts that CometOTP cannot
     * generate codes for are returned in [Converted.skipped].
     */
    fun convert(tokens: List<ImportedToken>): Converted {
        val entries = ArrayList<Entry>()
        val skipped = ArrayList<Skipped>()

        for (token in tokens) {
            val reason = unsupportedReason(token)
            if (reason != null) {
                skipped.add(Skipped(token.displayName, reason))
                continue
            }
            entries.add(toEntry(token))
        }

        return Converted(entries, skipped)
    }

    private fun unsupportedReason(token: ImportedToken): SkipReason? {
        val secret = token.secret
        return when {
            secret == null -> SkipReason.INVALID_SECRET
            secret.isEmpty() -> SkipReason.EMPTY_SECRET
            token.type == null -> SkipReason.UNSUPPORTED_TYPE
            token.type == Entry.OTPType.MOTP -> null
            token.algorithm == null -> SkipReason.UNSUPPORTED_ALGORITHM
            token.digits !in MIN_DIGITS..MAX_DIGITS -> SkipReason.UNSUPPORTED_DIGITS
            token.type != Entry.OTPType.HOTP && token.period <= 0 -> SkipReason.UNSUPPORTED_PERIOD
            else -> null
        }
    }

    private fun toEntry(token: ImportedToken): Entry {
        val secret = token.secret!!
        val tags = ArrayList(token.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
        return when (token.type!!) {
            Entry.OTPType.HOTP ->
                Entry(Entry.OTPType.HOTP, Base32().encodeAsString(secret), token.counter, token.digits,
                        token.issuer, token.label, token.algorithm!!, tags)
            Entry.OTPType.TOTP, Entry.OTPType.STEAM ->
                Entry(token.type, Base32().encodeAsString(secret), token.period, token.digits,
                        token.issuer, token.label, token.algorithm!!, tags)
            Entry.OTPType.MOTP ->
                Entry(Entry.OTPType.MOTP, String(secret, Charsets.UTF_8), token.issuer, token.label, tags)
        }
    }

    /* ------------------------------------------------------------------------------------------
     * Helpers for reading export fields
     * ------------------------------------------------------------------------------------------ */

    /**
     * Reads an authenticator key the way password managers store one: an otpauth link, a bare
     * base32 secret, or `steam://` and a secret. The item's [name] and [username] stand in for
     * an issuer and label the key does not carry.
     */
    fun fromKeyText(text: String, name: String, username: String): ImportedToken {
        val key = text.trim()
        if (OtpauthUri.isOtpauthUri(key)) {
            val token = OtpauthUri.parse(key)!!
            return ImportedToken(token.type, token.secret,
                    token.issuer.ifEmpty { name },
                    token.label.ifEmpty { username },
                    token.algorithm, token.digits, token.period, token.counter)
        }

        if (key.startsWith("steam://", ignoreCase = true))
            return ImportedToken(Entry.OTPType.STEAM, decodeBase32(key.substring("steam://".length)),
                    name, username, digits = TokenCalculator.STEAM_DEFAULT_DIGITS)

        return ImportedToken(Entry.OTPType.TOTP, decodeBase32(key), name, username)
    }

    /** "Issuer (label)", or whichever of the two is present. */
    fun displayName(issuer: String, label: String): String = when {
        issuer.isNotEmpty() && label.isNotEmpty() -> "$issuer ($label)"
        issuer.isNotEmpty() -> issuer
        else -> label
    }

    /**
     * Separates issuer and label the way the Key URI format does. Apps often keep the name they
     * were given, which is "Issuer:account" even when the issuer is also stored on its own, so a
     * matching prefix is removed; with no issuer stored, the prefix becomes it.
     */
    fun splitName(name: String, issuer: String): Pair<String, String> {
        val n = name.trim()
        val i = issuer.trim()

        if (i.isNotEmpty()) {
            val label = if (n.startsWith("$i:")) n.substring(i.length + 1).trim() else n
            return i to label
        }

        val colon = n.indexOf(':')
        if (colon > 0)
            return n.substring(0, colon).trim() to n.substring(colon + 1).trim()

        return "" to n
    }

    private val BASE32_ALPHABET = Regex("[A-Z2-7]*")

    /**
     * Decodes a base32 secret as apps write them: any case, with or without padding, and with
     * the spaces or dashes some show for readability.
     *
     * @return the key bytes, empty for an empty secret, or null when the text is not base32. The
     *         codec itself would silently skip characters outside the alphabet, which turns a
     *         mistyped or differently encoded secret into a wrong key that looks fine.
     */
    fun decodeBase32(text: String?): ByteArray? {
        if (text == null)
            return ByteArray(0)

        val cleaned = text.replace(Regex("[\\s\\-=]"), "").uppercase(Locale.ROOT)
        if (!BASE32_ALPHABET.matches(cleaned))
            return null

        return Base32().decode(cleaned)
    }

    /**
     * Reads an algorithm name as exports spell it: "SHA1", "sha-256", "HmacSHA512". Null and
     * empty mean the default, SHA1, as in the Key URI format; anything else is unsupported.
     */
    fun algorithm(name: String?): TokenCalculator.HashAlgorithm? {
        if (name.isNullOrBlank())
            return TokenCalculator.HashAlgorithm.SHA1

        return when (name.trim().uppercase(Locale.ROOT).removePrefix("HMAC").replace("-", "").replace("_", "")) {
            "SHA1" -> TokenCalculator.HashAlgorithm.SHA1
            "SHA256" -> TokenCalculator.HashAlgorithm.SHA256
            "SHA512" -> TokenCalculator.HashAlgorithm.SHA512
            else -> null
        }
    }

    /** Reads a type name as exports spell it: "totp", "HOTP", "Steam", "mOTP". */
    fun type(name: String?): Entry.OTPType? = when (name?.trim()?.uppercase(Locale.ROOT)) {
        null, "", "TOTP" -> Entry.OTPType.TOTP
        "HOTP" -> Entry.OTPType.HOTP
        "STEAM" -> Entry.OTPType.STEAM
        "MOTP" -> Entry.OTPType.MOTP
        else -> null
    }
}
