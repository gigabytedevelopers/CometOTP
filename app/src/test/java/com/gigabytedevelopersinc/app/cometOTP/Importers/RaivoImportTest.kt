@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Raivo's archive is a ZIP with AES encryption, which only zip4j can write here, so the
 * archives are built with the same library that reads them; the format on the wire is zip4j's
 * responsibility. The JSON inside is Raivo's, every value a string.
 */
class RaivoImportTest {

    private val password = "raivo-pass"

    private fun archive(password: String?, vararg files: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out, password?.toCharArray()).use { zip ->
            for ((name, content) in files) {
                val params = ZipParameters()
                params.fileNameInZip = name
                if (password != null) {
                    params.isEncryptFiles = true
                    params.encryptionMethod = EncryptionMethod.AES
                    params.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
                zip.putNextEntry(params)
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun assertFails(kind: ImportException.Kind, data: ByteArray, password: String? = null) {
        try {
            RaivoImport.read(data, password)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    private fun assertExport(tokens: List<ImportedToken>) {
        assertEquals(3, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)

        assertEquals(Entry.OTPType.HOTP, tokens[1].type)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(5L, tokens[1].counter)

        assertEquals(HashAlgorithm.SHA512, tokens[2].algorithm)
        assertEquals(60, tokens[2].period)

        assertEquals(3, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun opensTheArchiveWithThePassword() {
        val archive = archive(password, "raivo-otp-export.html" to "<html></html>", "raivo-otp-export.json" to JSON)
        assertExport(RaivoImport.read(archive, password))
    }

    @Test
    fun readsTheJsonOnItsOwn() {
        assertExport(RaivoImport.read(JSON.toByteArray(), null))
        assertExport(RaivoImport.read(archive(null, "raivo-otp-export.json" to JSON), null))
    }

    @Test
    fun asksForThePasswordAndRejectsAWrongOne() {
        val archive = archive(password, "raivo-otp-export.json" to JSON)
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, archive)
        assertFails(ImportException.Kind.WRONG_PASSWORD, archive, "raivo-pas")
    }

    @Test
    fun refusesWhatIsNotARaivoExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, """{"services":[]}""".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, archive(null, "readme.txt" to "hello"))
        assertFails(ImportException.Kind.DAMAGED, "PK\u0003\u0004 not really a zip".toByteArray(Charsets.ISO_8859_1))
        assertFails(ImportException.Kind.EMPTY, "[]".toByteArray())
    }

    companion object {
        private const val JSON = """[
            {"kind":"TOTP","algorithm":"SHA1","timer":"30","digits":"6","issuer":"GitHub","account":"bob","secret":"JBSWY3DPEHPK3PXP","counter":"0","iconType":"","iconValue":"","pinned":"false"},
            {"kind":"HOTP","algorithm":"SHA256","timer":"30","digits":"8","issuer":"Counter","account":"dave","secret":"JBSWY3DPEE","counter":"5","iconType":"","iconValue":"","pinned":"false"},
            {"kind":"TOTP","algorithm":"SHA512","timer":"60","digits":"6","issuer":"Slow","account":"carol","secret":"JBSWY3DPEE","counter":"0","iconType":"","iconValue":"","pinned":"true"}
        ]"""
    }
}
