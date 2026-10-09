@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Reads a 1Password export and takes the items that have a one-time password.
 *
 * The `.1pux` export is a ZIP archive whose `export.data` is JSON: accounts, each with vaults,
 * each with items. An item's one-time password is a section field whose value is `{"totp":..}`,
 * an otpauth link or a bare secret; the item's title and its username login field name the
 * account when the link does not. The `.csv` export has the same in its `OTPAuth` column.
 */
object OnePasswordImport : ImportFormat {

    private const val DATA_FILE = "export.data"

    override fun read(data: ByteArray, password: String?): List<ImportedToken> {
        if (isZip(data))
            return readJson(exportData(data))

        val json = ImportCrypto.jsonObject(data)
        if (json != null)
            return readJson(json)

        val text = OtpauthLinksImport.decodeText(data) ?: throw ImportException.notThisFormat("Not text")
        return readCsv(text)
    }

    private fun isZip(data: ByteArray): Boolean =
        data.size >= 4 && data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte() &&
                data[2].toInt() == 3 && data[3].toInt() == 4

    private fun exportData(archive: ByteArray): JSONObject {
        try {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name != DATA_FILE)
                        continue
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    return ImportCrypto.jsonObject(out.toByteArray()) ?: throw ImportException.damaged("export.data is not JSON")
                }
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException.damaged("Cannot read the archive", e)
        }
        throw ImportException.notThisFormat("No export.data in the archive")
    }

    private fun readJson(root: JSONObject): List<ImportedToken> {
        val accounts = root.optJSONArray("accounts") ?: throw ImportException.notThisFormat("No accounts")
        val tokens = ArrayList<ImportedToken>()

        for (a in 0 until accounts.length()) {
            val vaults = accounts.optJSONObject(a)?.optJSONArray("vaults") ?: continue
            for (v in 0 until vaults.length()) {
                val items = vaults.optJSONObject(v)?.optJSONArray("items") ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    readItem(item)?.let { tokens.add(it) }
                }
            }
        }

        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }

    /** @return the item's one-time password, or null when it has none */
    private fun readItem(item: JSONObject): ImportedToken? {
        val details = item.optJSONObject("details") ?: return null
        val title = item.optJSONObject("overview")?.optString("title")?.trim() ?: ""

        var username = ""
        val loginFields = details.optJSONArray("loginFields")
        if (loginFields != null) {
            for (i in 0 until loginFields.length()) {
                val field = loginFields.optJSONObject(i) ?: continue
                if (field.optString("designation") == "username") {
                    username = field.optString("value").trim()
                    break
                }
            }
        }

        val sections = details.optJSONArray("sections") ?: return null
        for (s in 0 until sections.length()) {
            val fields = sections.optJSONObject(s)?.optJSONArray("fields") ?: continue
            for (f in 0 until fields.length()) {
                val value = fields.optJSONObject(f)?.optJSONObject("value") ?: continue
                if (!value.has("totp") || value.isNull("totp"))
                    continue
                val totp = value.optString("totp").trim()
                if (totp.isNotEmpty())
                    return TokenImport.fromKeyText(totp, title, username)
            }
        }
        return null
    }

    private fun readCsv(text: String): List<ImportedToken> {
        val rows = Csv.parse(text)
        if (rows.isEmpty())
            throw ImportException.notThisFormat("Empty text")
        val header = rows[0].map { it.trim().lowercase() }
        val otpColumn = header.indexOf("otpauth")
        if (otpColumn < 0)
            throw ImportException.notThisFormat("No OTPAuth column")
        val titleColumn = header.indexOf("title")
        val userColumn = header.indexOf("username")

        val tokens = ArrayList<ImportedToken>()
        for (row in rows.drop(1)) {
            val totp = row.getOrNull(otpColumn)?.trim() ?: ""
            if (totp.isEmpty())
                continue
            tokens.add(TokenImport.fromKeyText(totp, row.getOrNull(titleColumn)?.trim() ?: "", row.getOrNull(userColumn)?.trim() ?: ""))
        }
        if (tokens.isEmpty())
            throw ImportException.empty()
        return tokens
    }
}
