@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ProtonImportTest {

    private fun read(json: String) = ProtonImport.read(json.toByteArray(), null)

    private fun assertFails(kind: ImportException.Kind, json: String) {
        try {
            read(json)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun readsAnExport() {
        val tokens = read(EXPORT)
        assertEquals(4, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)

        // A link with no issuer takes the name the user gave the entry.
        assertEquals("My Bank", tokens[1].issuer)
        assertEquals("alice", tokens[1].label)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(60, tokens[1].period)

        assertEquals(Entry.OTPType.STEAM, tokens[2].type)
        assertEquals(5, tokens[2].digits)
        assertEquals("Steam", tokens[2].issuer)
        assertEquals("gamer", tokens[2].label)

        assertEquals(Entry.OTPType.STEAM, tokens[3].type)
        assertArrayEquals("Hello!".toByteArray(), tokens[3].secret)
        assertEquals("Steam account", tokens[3].issuer)

        assertEquals(4, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun cannotOpenAPasswordProtectedExport() {
        assertFails(ImportException.Kind.ENCRYPTION_UNSUPPORTED, """{"version":1,"salt":"c2FsdA==","content":"ZW5jcnlwdGVk"}""")
    }

    @Test
    fun refusesWhatIsNotAProtonExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, """{"services":[]}""")
        assertFails(ImportException.Kind.DAMAGED, """{"version":1,"entries":[{"id":"x","content":{"name":"no link"}}]}""")
        assertFails(ImportException.Kind.EMPTY, """{"version":1,"entries":[]}""")
    }

    companion object {
        private const val EXPORT = """{"version":1,"entries":[
            {"id":"1","content":{"uri":"otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30","entry_type":"Totp","name":"GitHub","period":30},"note":null,"order":0},
            {"id":"2","content":{"uri":"otpauth://totp/alice?secret=JBSWY3DPEE&algorithm=SHA256&digits=8&period=60","entry_type":"Totp","name":"My Bank"},"note":"savings","order":1},
            {"id":"3","content":{"uri":"otpauth://steam/Steam:gamer?secret=JBSWY3DPEE&issuer=Steam","entry_type":"Steam","name":"Steam"},"note":null,"order":2},
            {"id":"4","content":{"uri":"steam://JBSWY3DPEE","entry_type":"Steam","name":"Steam account"},"note":null,"order":3}
        ]}"""
    }
}
