@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class FreeOtpPlusImportTest {

    private fun read(json: String) = FreeOtpPlusImport.read(json.toByteArray(), null)

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
        // The secret is a list of signed bytes: negative values are the high half.
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("Example", t.issuer)
        assertEquals("alice@google.com", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)

        assertEquals(Entry.OTPType.HOTP, tokens[1].type)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(3L, tokens[1].counter)
        // No issuer from the link: the one read from the label's prefix stands in.
        assertEquals("Counter", tokens[1].issuer)
        assertEquals("dave", tokens[1].label)

        // What the user renamed the account to is what they expect to see.
        assertEquals("My Bank", tokens[2].issuer)
        assertEquals("savings", tokens[2].label)

        // An older export with the secret as text.
        assertArrayEquals("Hello!".toByteArray(), tokens[3].secret)

        assertEquals(4, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun refusesWhatIsNotAFreeOtpPlusExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "{\"services\":[]}")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "[]")
        assertFails(ImportException.Kind.DAMAGED, "{\"tokenOrder\":[],\"tokens\":[1]}")
        assertFails(ImportException.Kind.EMPTY, "{\"tokenOrder\":[],\"tokens\":[]}")
    }

    companion object {
        private const val EXPORT = """{"tokenOrder":["Example:alice@google.com","Counter:dave","Bank:savings","Old:x"],"tokens":[
            {"algo":"SHA1","counter":0,"digits":6,"issuerExt":"Example","issuerInt":"Example","label":"alice@google.com","period":30,"secret":[72,101,108,108,111,33,-34,-83,-66,-17],"type":"TOTP","imageAlt":null,"issuerAlt":null,"labelAlt":null},
            {"algo":"SHA256","counter":3,"digits":8,"issuerExt":null,"issuerInt":"Counter","label":"dave","period":30,"secret":[72,101,108,108,111,33],"type":"HOTP"},
            {"algo":"SHA1","counter":0,"digits":6,"issuerExt":"Bank","issuerInt":"Bank","label":"acct","period":30,"secret":[72,101,108,108,111,33],"type":"TOTP","issuerAlt":"My Bank","labelAlt":"savings"},
            {"algo":"SHA1","counter":0,"digits":6,"issuerExt":"Old","label":"x","period":30,"secret":"JBSWY3DPEE","type":"TOTP"}
        ]}"""
    }
}
