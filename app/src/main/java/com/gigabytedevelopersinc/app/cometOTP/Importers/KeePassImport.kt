@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the XML KeePassXC exports under Database > Export > XML file, and KeePass 2's
 * "KeePass XML (2.x)" export, and takes the entries that have a one-time password.
 *
 * Entries sit in nested groups under the root; each is a list of `String` elements with a `Key`
 * and a `Value`. The one-time password is the `otp` string, an otpauth link as KeePassXC and
 * KeePass 2.51+ keep it, or, from the older KeeOTP and KeeTrayTOTP plugins, a `TOTP Seed` with
 * `TOTP Settings` of "period;digits", where the digits can be "S" for Steam. The entry's title
 * and user name name the account when the key does not. Earlier versions of an entry, kept
 * under `History`, are passed over.
 *
 * The database file itself (.kdbx) is encrypted and is not read here.
 */
object KeePassImport : ImportFormat {

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        val text = OtpauthLinksImport.decodeText(data) ?: throw ImportException.notThisFormat("Not text")
        if (!text.contains("<KeePassFile"))
            throw ImportException.notThisFormat("Not a KeePass XML file")

        val document = try {
            parser().parse(ByteArrayInputStream(data))
        } catch (e: Exception) {
            throw ImportException.damaged("The XML does not parse", e)
        }

        val root = document.documentElement
        if (root == null || root.tagName != "KeePassFile")
            throw ImportException.notThisFormat("Not a KeePass XML file")

        val tokens = ArrayList<ImportedToken>()
        children(root, "Root").forEach { readGroups(it, tokens) }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    /** A parser that reads nothing but the file: no doctype, no external entities. */
    private fun parser() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isExpandEntityReferences = false
        isXIncludeAware = false
        for (feature in listOf("http://apache.org/xml/features/disallow-doctype-decl",
                "http://xml.org/sax/features/external-general-entities",
                "http://xml.org/sax/features/external-parameter-entities")) {
            try {
                setFeature(feature, feature.startsWith("http://apache.org"))
            } catch (e: Exception) {
                // Not every parser knows every feature; the ones that do are the ones that need it.
            }
        }
        // JAXP's own properties, which android.jar does not name as constants.
        for (property in listOf("http://javax.xml.XMLConstants/property/accessExternalDTD",
                "http://javax.xml.XMLConstants/property/accessExternalSchema")) {
            try {
                setAttribute(property, "")
            } catch (e: Exception) {
                // As above.
            }
        }
    }.newDocumentBuilder()

    private fun readGroups(parent: Element, tokens: MutableList<ImportedToken>) {
        for (group in children(parent, "Group")) {
            for (entry in children(group, "Entry"))
                readEntry(entry)?.let { tokens.add(it) }
            readGroups(group, tokens)
        }
    }

    /** @return the entry's one-time password, or null when it has none */
    private fun readEntry(entry: Element): ImportedToken? {
        val strings = HashMap<String, String>()
        for (string in children(entry, "String")) {
            val key = children(string, "Key").firstOrNull()?.textContent ?: continue
            val value = children(string, "Value").firstOrNull()?.textContent ?: ""
            strings[key] = value
        }

        val title = strings["Title"]?.trim() ?: ""
        val username = strings["UserName"]?.trim() ?: ""

        val otp = strings["otp"]?.trim()
        if (!otp.isNullOrEmpty())
            return TokenImport.fromKeyText(otp, title, username)

        val seed = strings["TOTP Seed"]?.trim()
        if (!seed.isNullOrEmpty()) {
            val settings = strings["TOTP Settings"]?.split(';') ?: emptyList()
            val period = settings.getOrNull(0)?.trim()?.toIntOrNull() ?: TokenCalculator.TOTP_DEFAULT_PERIOD
            val digitsSetting = settings.getOrNull(1)?.trim() ?: ""
            if (digitsSetting.equals("S", ignoreCase = true))
                return ImportedToken(Entry.OTPType.STEAM, TokenImport.decodeBase32(seed), title, username,
                        digits = TokenCalculator.STEAM_DEFAULT_DIGITS, period = period)
            return ImportedToken(Entry.OTPType.TOTP, TokenImport.decodeBase32(seed), title, username,
                    digits = digitsSetting.toIntOrNull() ?: TokenCalculator.TOTP_DEFAULT_DIGITS, period = period)
        }

        return null
    }

    private fun children(parent: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == name)
                out.add(node as Element)
        }
        return out
    }
}
