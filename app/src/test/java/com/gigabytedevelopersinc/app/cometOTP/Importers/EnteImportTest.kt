@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class EnteImportTest {

    private fun assertFails(kind: ImportException.Kind, text: String) {
        try {
            EnteImport.read(text.toByteArray(), null)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun readsThePlainTextExport() {
        val tokens = EnteImport.read(("otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&algorithm=SHA1&digits=6&period=30&codeDisplay=%7B%22pinned%22%3Afalse%7D\n" +
                "otpauth://hotp/Counter:dave?secret=JBSWY3DPEE&issuer=Counter&counter=4\n").toByteArray(), null)
        assertEquals(listOf("GitHub", "Counter"), tokens.map { it.issuer })
        assertEquals(4L, tokens[1].counter)
    }

    @Test
    fun cannotOpenTheEncryptedExport() {
        assertFails(ImportException.Kind.ENCRYPTION_UNSUPPORTED,
                """{"version":1,"kdfParams":{"memLimit":1073741824,"opsLimit":4,"salt":"c2FsdA=="},"encryptedData":"ZGF0YQ==","encryptionNonce":"bm9uY2U="}""")
    }

    @Test
    fun refusesWhatIsNotAnEnteExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, """{"services":[]}""")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "just some text")
        assertFails(ImportException.Kind.EMPTY, "")
    }
}
