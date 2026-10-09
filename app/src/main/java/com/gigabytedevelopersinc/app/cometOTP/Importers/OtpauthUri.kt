@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Reads `otpauth://` links, the Key URI format most apps also write into their exports.
 *
 * Entry has a constructor for the same links, but it parses them with android.net.Uri, which
 * is not there on the JVM, and it throws rather than saying what was wrong. This reads them into
 * an [ImportedToken] so that an account with, say, an unknown algorithm is reported by name.
 */
object OtpauthUri {

    const val SCHEME = "otpauth"

    fun isOtpauthUri(text: String?): Boolean =
        text != null && text.trimStart().startsWith("$SCHEME://", ignoreCase = true)

    /**
     * @return the account the link describes, or null when the text is not an otpauth link at all.
     *         A link whose type, algorithm or secret CometOTP cannot use is still returned, with
     *         that part left null, so that [TokenImport.convert] can report it.
     */
    fun parse(text: String): ImportedToken? {
        val uri = text.trim()
        if (!isOtpauthUri(uri))
            return null

        val afterScheme = uri.substring(SCHEME.length + 3)
        val query = afterScheme.substringAfter('?', "").substringBefore('#')
        val pathPart = afterScheme.substringBefore('?').substringBefore('#')

        val typeName = pathPart.substringBefore('/')
        // The label is the path, with a leading slash some apps double up.
        val rawLabel = pathPart.substringAfter('/', "").trimStart('/')

        val params = parseQuery(query)

        val issuerParam = params["issuer"]?.trim() ?: ""
        val (issuer, label) = TokenImport.splitName(percentDecode(rawLabel), issuerParam)

        val type = TokenImport.type(typeName)
        val secret = TokenImport.decodeBase32(params["secret"])
        val algorithm = TokenImport.algorithm(params["algorithm"])
        val digits = params["digits"]?.trim()?.toIntOrNull()
                ?: if (type == Entry.OTPType.STEAM) TokenCalculator.STEAM_DEFAULT_DIGITS else TokenCalculator.TOTP_DEFAULT_DIGITS
        val period = params["period"]?.trim()?.toIntOrNull() ?: TokenCalculator.TOTP_DEFAULT_PERIOD
        val counter = params["counter"]?.trim()?.toLongOrNull() ?: 0L

        if (type == Entry.OTPType.MOTP) {
            // An mOTP secret is used as the characters themselves, not decoded.
            val raw = params["secret"] ?: ""
            return ImportedToken(type, raw.toByteArray(Charsets.UTF_8), issuer, label)
        }

        return ImportedToken(type, secret, issuer, label, algorithm, digits, period, counter)
    }

    /** Every link in a text, one per line; lines that are not links are ignored. */
    fun parseAll(text: String): List<ImportedToken> =
        text.lineSequence().map { it.trim() }.filter { isOtpauthUri(it) }.mapNotNull { parse(it) }.toList()

    private fun parseQuery(query: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (query.isEmpty())
            return out
        for (pair in query.split('&')) {
            if (pair.isEmpty())
                continue
            val name = percentDecode(pair.substringBefore('=')).lowercase(Locale.ROOT)
            val value = percentDecode(pair.substringAfter('=', ""))
            // The first value wins, as android.net.Uri's getQueryParameter does.
            if (!out.containsKey(name))
                out[name] = value
        }
        return out
    }

    /** Undoes %XX escapes and the '+' for a space that form encoding adds to the label. */
    internal fun percentDecode(s: String): String {
        if (!s.contains('%') && !s.contains('+'))
            return s

        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '%' && i + 2 < s.length && isHex(s[i + 1]) && isHex(s[i + 2]) -> {
                    out.write(s.substring(i + 1, i + 3).toInt(16))
                    i += 3
                }
                c == '+' -> {
                    out.write(' '.code)
                    i++
                }
                c.isHighSurrogate() && i + 1 < s.length -> {
                    out.write(s.substring(i, i + 2).toByteArray(Charsets.UTF_8))
                    i += 2
                }
                else -> {
                    out.write(c.toString().toByteArray(Charsets.UTF_8))
                    i++
                }
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
}
