@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OnePasswordImportTest {

    private fun archive(vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in files) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun assertFails(kind: ImportException.Kind, data: ByteArray) {
        try {
            OnePasswordImport.read(data, null)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    private fun assertExport(tokens: List<ImportedToken>) {
        // Items without a one-time password are not accounts and are not listed.
        assertEquals(3, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)

        // A bare secret: the item's title and username name the account.
        assertEquals("My Bank", tokens[1].issuer)
        assertEquals("alice", tokens[1].label)
        assertArrayEquals("Hello!".toByteArray(), tokens[1].secret)

        // A link with no issuer takes the item's title.
        assertEquals("Fallback", tokens[2].issuer)
        assertEquals("dan", tokens[2].label)
        assertEquals(8, tokens[2].digits)

        assertEquals(3, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun readsA1puxArchive() {
        assertExport(OnePasswordImport.read(archive("export.attributes" to "{}", "export.data" to DATA, "files/x.png" to "png"), null))
    }

    @Test
    fun readsTheExportDataOnItsOwn() {
        assertExport(OnePasswordImport.read(DATA.toByteArray(), null))
    }

    @Test
    fun readsACsvExport() {
        val csv = "Title,Url,Username,Password,OTPAuth,Favorite,Archived,Tags,Notes\n" +
                "GitHub,https://github.com,bob,pw,otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&issuer=GitHub,,,,\n" +
                "My Bank,,alice,pw,JBSWY3DPEE,,,,\"a note\"\n" +
                "No code,,nobody,pw,,,,,\n" +
                "Fallback,,dan,pw,otpauth://totp/dan?secret=JBSWY3DPEE&digits=8,,,,\n"
        assertExport(OnePasswordImport.read(csv.toByteArray(), null))
    }

    @Test
    fun refusesWhatIsNotA1PasswordExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, """{"services":[]}""".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "name,secret\nGitHub,JBSWY3DPEE\n".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, archive("readme.txt" to "hello"))
        assertFails(ImportException.Kind.DAMAGED, archive("export.data" to "not json"))
        assertFails(ImportException.Kind.EMPTY, """{"accounts":[{"vaults":[{"items":[{"overview":{"title":"x"},"details":{"sections":[]}}]}]}]}""".toByteArray())
        assertFails(ImportException.Kind.EMPTY, "Title,Username,OTPAuth\nx,y,\n".toByteArray())
    }

    companion object {
        private const val DATA = """{"accounts":[{"attrs":{"name":"Personal"},"vaults":[{"attrs":{"name":"Private"},"items":[
            {"uuid":"1","state":"active","categoryUuid":"001","overview":{"title":"GitHub","subtitle":"bob","url":"https://github.com"},"details":{"loginFields":[{"value":"bob","designation":"username","fieldType":"T"},{"value":"pw","designation":"password","fieldType":"P"}],"notesPlain":"","sections":[{"title":"","fields":[{"title":"one-time password","id":"TOTP_1","value":{"totp":"otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"},"guarded":true}]}]}},
            {"uuid":"2","state":"active","categoryUuid":"001","overview":{"title":"My Bank"},"details":{"loginFields":[{"value":"alice","designation":"username"}],"sections":[{"fields":[{"title":"note","value":{"string":"hi"}},{"title":"one-time password","value":{"totp":"JBSWY3DPEE"}}]}]}},
            {"uuid":"3","state":"active","categoryUuid":"001","overview":{"title":"No code"},"details":{"loginFields":[{"value":"nobody","designation":"username"}],"sections":[]}},
            {"uuid":"4","state":"active","categoryUuid":"003","overview":{"title":"A note"},"details":{"notesPlain":"hello"}},
            {"uuid":"5","state":"active","categoryUuid":"001","overview":{"title":"Fallback"},"details":{"loginFields":[{"value":"dan","designation":"username"}],"sections":[{"fields":[{"value":{"totp":"otpauth://totp/dan?secret=JBSWY3DPEE&digits=8"}}]}]}}
        ]}]}]}"""
    }
}
