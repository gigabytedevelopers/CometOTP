@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import org.apache.commons.codec.binary.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The key derivations other apps protect their exports with, checked against their RFC test
 * vectors (RFC 7914 for scrypt, RFC 6070 and the SHA-256 vectors that circulate for PBKDF2).
 * The values were taken from OpenSSL through Node 26, never from the code under test.
 */
class ImportKdfTest {

    private fun hex(b: ByteArray) = String(Hex.encodeHex(b))
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test
    fun pbkdf2MatchesTheRfcVectors() {
        assertEquals("ea6c014dc72d6f8ccd1ed92ace1d41f0d8de8957", hex(Pbkdf2.sha1(ascii("password"), ascii("salt"), 2, 20)))
        assertEquals("c5e478d59288c841aa530db6845c4c8d962893a001ce4e11a4963873aa98134a",
                hex(Pbkdf2.sha256(ascii("password"), ascii("salt"), 4096, 32)))
        // More than one block of output, and a length that is not a multiple of the hash.
        assertEquals("348c89dbcbd32b2f32d814b8116e84cf2b17347ebc1800181c4e2a1fb8dd53e1c635518c7dac47e9",
                hex(Pbkdf2.sha256(ascii("passwordPASSWORDpassword"), ascii("saltSALTsaltSALTsaltSALTsaltSALTsalt"), 4096, 40)))
        assertEquals("867f70cf1ade02cff3752599a3a53dc4af34c7a669815ae5d513554e1c8cf252c02d470a285a0501bad999bfe943c08f050235d7d68b1da55e63f73b60a57fce",
                hex(Pbkdf2.sha512(ascii("password"), ascii("salt"), 1, 64)))
    }

    @Test
    fun pbkdf2AcceptsAnEmptyPassword() {
        // scrypt's own first vector is PBKDF2 with an empty password and salt underneath.
        assertEquals("77d6576238657b203b19ca42c18a0497f16b4844e3074ae8dfdffa3fede21442fcd0069ded0948f8326a753a0fc81f17e8d3e0fb2e0d3628cf35e20c38d18906",
                hex(Scrypt.derive(ByteArray(0), ByteArray(0), 16, 1, 1, 64)))
    }

    @Test
    fun scryptMatchesTheRfcVectors() {
        assertEquals("fdbabe1c9d3472007856e7190d01e9fe7c6ad7cbc8237830e77376634b3731622eaf30d92e22a3886ff109279d9830dac727afb94a83ee6d8360cbdfa2cc0640",
                hex(Scrypt.derive(ascii("password"), ascii("NaCl"), 1024, 8, 16, 64)))
        assertEquals("7023bdcb3afd7348461c06cd81fd38ebfda8fbba904f8e3ea9b543f6545da1f2d5432955613f0fcf62d49705242a9af9e61e85dc0d651e40dfcf017b45575887",
                hex(Scrypt.derive(ascii("pleaseletmein"), ascii("SodiumChloride"), 16384, 8, 1, 64)))
    }

    @Test
    fun scryptRefusesParametersItCannotRun() {
        assertThrows(IllegalArgumentException::class.java) { Scrypt.derive(ascii("x"), ascii("y"), 1000, 8, 1, 32) }
        assertThrows(IllegalArgumentException::class.java) { Scrypt.derive(ascii("x"), ascii("y"), 1, 8, 1, 32) }
        assertThrows(IllegalArgumentException::class.java) { Scrypt.derive(ascii("x"), ascii("y"), 1024, 0, 1, 32) }
        assertThrows(IllegalArgumentException::class.java) { Scrypt.derive(ascii("x"), ascii("y"), 1 shl 30, 8, 1, 32) }
    }
}
