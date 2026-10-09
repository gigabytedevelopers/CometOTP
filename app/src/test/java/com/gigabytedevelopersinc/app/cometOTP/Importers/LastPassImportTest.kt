@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LastPassImportTest {

    private fun read(json: String) = LastPassImport.read(json.toByteArray(), null)

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
        assertEquals(3, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)

        // The user renamed this one; what the site set is kept as a fallback only.
        assertEquals("Bank", tokens[1].issuer)
        assertEquals("alice", tokens[1].label)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(60, tokens[1].period)

        // Names left blank fall back to the originals.
        assertEquals("Original", tokens[2].issuer)
        assertEquals("orig@example.com", tokens[2].label)

        assertEquals(3, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun refusesWhatIsNotALastPassExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, """{"services":[]}""")
        assertFails(ImportException.Kind.DAMAGED, """{"version":3,"accounts":["x"]}""")
        assertFails(ImportException.Kind.EMPTY, """{"version":3,"accounts":[],"folders":[]}""")
    }

    companion object {
        private const val EXPORT = """{"deviceId":"d","deviceSecret":"s","localDeviceId":"l","deviceName":"Phone","version":3,"accounts":[
            {"accountID":"1","lmiUserId":"","issuerName":"GitHub","originalIssuerName":"GitHub","userName":"bob","originalUserName":"bob","pushNotification":false,"secret":"JBSWY3DPEHPK3PXP","timeStep":30,"digits":6,"creationTimestamp":1700000000000,"isFavorite":false,"algorithm":"SHA1","folderData":{"folderId":0,"position":0}},
            {"accountID":"2","issuerName":"Bank","originalIssuerName":"Big Bank Inc","userName":"alice","originalUserName":"alice@example.com","secret":"JBSWY3DPEE","timeStep":60,"digits":8,"algorithm":"SHA256","folderData":{"folderId":1,"position":0}},
            {"accountID":"3","issuerName":"","originalIssuerName":"Original","userName":"","originalUserName":"orig@example.com","secret":"JBSWY3DPEE","timeStep":30,"digits":6,"algorithm":"SHA1"}
        ],"folders":[{"id":0,"name":"Favorites","isOpened":true},{"id":1,"name":"Other","isOpened":true}]}"""
    }
}
