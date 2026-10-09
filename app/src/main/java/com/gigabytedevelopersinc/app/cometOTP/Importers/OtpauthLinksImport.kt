@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

/**
 * Reads a text file of `otpauth://` links, one per line: the "plain text", "URI list" or
 * "otpauth" export that many apps offer, and the shape third-party tools write when they get
 * accounts out of an app that has no export of its own.
 *
 * Lines that are not links are ignored, so a file with comments or an explanatory header reads
 * the same as a bare list.
 */
object OtpauthLinksImport : ImportFormat {

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val text = decodeText(data) ?: throw ImportException.notThisFormat("Not a text file")
        if (text.isBlank())
            throw ImportException.empty()

        val tokens = OtpauthUri.parseAll(text)
        if (tokens.isEmpty())
            throw ImportException.notThisFormat("No otpauth links in the text")

        return tokens
    }

    /**
     * The file as UTF-8 text, without a byte order mark, or null when it is not text at all:
     * a binary file chosen by mistake must be refused rather than searched for links.
     */
    internal fun decodeText(data: ByteArray): String? {
        val start = if (data.size >= 3 && data[0] == 0xEF.toByte() && data[1] == 0xBB.toByte() && data[2] == 0xBF.toByte()) 3 else 0
        val text = String(data, start, data.size - start, Charsets.UTF_8)
        if (text.any { it == '�' || (it.code < 0x20 && it != '\n' && it != '\r' && it != '\t') })
            return null
        return text
    }
}
