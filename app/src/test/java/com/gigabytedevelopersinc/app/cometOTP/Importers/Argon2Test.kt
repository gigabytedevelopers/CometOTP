@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import org.apache.commons.codec.binary.Hex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * BLAKE2b against RFC 7693's "abc" vector, and Argon2id against RFC 9106's test vector and a
 * set of further values from OpenSSL (through Node 26), covering one, two, three and four lanes,
 * more than one pass, an empty password, a tag longer than a BLAKE2b digest, and the parameters
 * Stratum backs up with. Never taken from the code under test.
 */
class Argon2Test {

    private fun hex(b: ByteArray) = String(Hex.encodeHex(b))
    private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

    @Test
    fun blake2bMatchesTheRfcVector() {
        assertEquals("ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d17d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
                hex(Blake2b.hash(ascii("abc"), 64)))
        // A shorter digest is a different hash, not a truncation.
        assertEquals("bddd813c634239723171ef3fee98579b94964e3bb1cb3e427262c8c068d52319", hex(Blake2b.hash(ascii("abc"), 32)))
        // More than one block.
        assertEquals("d9cf5983dc6b34c0fa1f0226926855ad3eccd2bcdcd8f8053b9a80664d33b5afcc32fd21c70ea14f4ef50ca97c3203c4d1803159f0e01bb6cb1d1c83db52b63c", hex(Blake2b.hash(ByteArray(300) { it.toByte() }, 64)))
    }

    @Test
    fun argon2idMatchesTheRfcVector() {
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
                hex(Argon2.id(ByteArray(32) { 1 }, ByteArray(16) { 2 }, 3, 32, 4, 32, ByteArray(8) { 3 }, ByteArray(12) { 4 })))
    }

    @Test
    fun argon2idMatchesOpenSsl() {
        assertEquals("16a1a498734609dd01456da406de9f3d9da93e6c86c300a12fc1465214ce4922",
                hex(Argon2.id(ascii("password"), ascii("somesalt"), 2, 64, 1, 32)))
        assertEquals("e01622890d95008ca707827e34ae3e30fcec9bd5f944737a76c05f2c8cad2170c558540d1262f14e96efcb76872d6cf87bbdee0220d8c12452ef9bb49640b37c63bc8874085c9f19616967499f027ed78d758a00c481ad454118506a302a0f4efba56728",
                hex(Argon2.id(ascii("password"), ascii("somesaltsomesalt"), 1, 1024, 3, 100)))
        assertEquals("1d69042387ea65a2e98628a0392267f0",
                hex(Argon2.id(ByteArray(0), ascii("saltsaltsalt"), 4, 256, 2, 16)))
    }

    @Test
    fun argon2idRunsStratumsParameters() {
        assertEquals("ff2fd3e1b11bfb9a80ce6f446578a9b6cf7561790f14f4f8a4aa33d6460cb905",
                hex(Argon2.id(ascii("password"), Hex.decodeHex("0102030405060708090a0b0c0d0e0f10".toCharArray()), 3, 65536, 4, 32)))
    }

    @Test
    fun argon2idRefusesParametersItCannotRun() {
        assertThrows(IllegalArgumentException::class.java) { Argon2.id(ascii("p"), ascii("s"), 0, 64, 1, 32) }
        assertThrows(IllegalArgumentException::class.java) { Argon2.id(ascii("p"), ascii("s"), 1, 7, 1, 32) }
        assertThrows(IllegalArgumentException::class.java) { Argon2.id(ascii("p"), ascii("s"), 1, 64, 0, 32) }
        assertThrows(IllegalArgumentException::class.java) { Argon2.id(ascii("p"), ascii("s"), 1, 2 * 1024 * 1024, 1, 32) }
    }
}
