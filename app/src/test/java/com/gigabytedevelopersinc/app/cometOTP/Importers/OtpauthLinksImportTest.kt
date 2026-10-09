@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class OtpauthLinksImportTest {

    private fun read(text: String) = OtpauthLinksImport.read(text.toByteArray(Charsets.UTF_8), null)

    private fun assertFails(kind: ImportException.Kind, data: ByteArray) {
        try {
            OtpauthLinksImport.read(data, null)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun readsOneLinkPerLine() {
        val tokens = read("otpauth://totp/A:a?secret=JBSWY3DPEE\r\notpauth://hotp/B:b?secret=JBSWY3DPEE&counter=3\n")
        assertEquals(listOf("A", "B"), tokens.map { it.issuer })
        assertEquals(3L, tokens[1].counter)
    }

    @Test
    fun ignoresAByteOrderMarkCommentsAndBlankLines() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val text = "# Exported 2026-10-09\n\notpauth://totp/A:a?secret=JBSWY3DPEE\n\n".toByteArray()
        val tokens = OtpauthLinksImport.read(bom + text, null)
        assertEquals(1, tokens.size)
        assertEquals("A", tokens[0].issuer)
    }

    @Test
    fun refusesTextWithoutLinksAndFilesThatAreNotText() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "{\"services\": []}".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0))
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x41, 0x00))
        assertFails(ImportException.Kind.EMPTY, ByteArray(0))
        assertFails(ImportException.Kind.EMPTY, "  \n\n".toByteArray())
    }

    @Test
    fun tellsTextFromBinary() {
        assertEquals("héllo\tworld\n", OtpauthLinksImport.decodeText("héllo\tworld\n".toByteArray()))
        assertNull(OtpauthLinksImport.decodeText(byteArrayOf(0, 1, 2)))
    }
}
