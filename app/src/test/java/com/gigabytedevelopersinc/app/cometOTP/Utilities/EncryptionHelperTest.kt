@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.apache.commons.codec.binary.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.spec.IvParameterSpec

/**
 * Golden values for the database and backup encryption, produced by the original Java
 * EncryptionHelper. A failure here means existing databases or backup files would no longer
 * decrypt. The AndroidKeyStore itself (KeyStoreHelper) is not available on the JVM; the RSA
 * wrapping it relies on is exercised with a fixed software key pair instead.
 */
class EncryptionHelperTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun constants() {
        assertEquals("AES/GCM/NoPadding", Constants.ALGORITHM_SYMMETRIC)
        assertEquals("RSA/ECB/PKCS1Padding", Constants.ALGORITHM_ASYMMETRIC)
        assertEquals(16, Constants.ENCRYPTION_KEY_LENGTH)
        assertEquals(12, Constants.ENCRYPTION_IV_LENGTH)
        assertEquals(4, Constants.INT_LENGTH)
        assertEquals(140000, Constants.PBKDF2_MIN_ITERATIONS)
        assertEquals(160000, Constants.PBKDF2_MAX_ITERATIONS)
        assertEquals(150000, Constants.PBKDF2_DEFAULT_ITERATIONS)
        assertEquals(256, Constants.PBKDF2_LENGTH)
        assertEquals(16, Constants.PBKDF2_SALT_LENGTH)
    }

    @Test
    fun pbkdf2CredentialsSplitTheDerivedBytes() {
        val c = EncryptionHelper.generatePBKDF2Credentials("correct horse battery staple", SALT16, 1000)
        // The second half is the stored password hash, the first half the database key.
        assertEquals("ef5f3b583df3ad7a5144f6c7727371ec", hex(c.password!!))
        assertEquals("00e9bf90e6fff98019dd9c12a2062036", hex(c.key!!))
    }

    @Test
    fun backupKeyFromPbkdf2() {
        val key = EncryptionHelper.generateSymmetricKeyPBKDF2("backup password", 1000, SALT12)
        assertEquals("PBKDF2WithHmacSHA1", key.algorithm)
        assertEquals("RAW", key.format)
        assertEquals("12a1e1ae088b1a22c64622b092c70e4ae4edb8f362cfae230a8a854e032d9886", hex(key.encoded))
    }

    @Test
    fun oldBackupKeyIsSha256OfThePassword() {
        val key = EncryptionHelper.generateSymmetricKeyFromPassword("hunter2")
        assertEquals("AES", key.algorithm)
        assertEquals("f52fbd32b2b3b86ff88ef6c490628285f482af15ddcb29541f94bcf526a3f6c7", hex(key.encoded))
    }

    @Test
    fun symmetricKeyWrapsTheBytesAsAes() {
        val key = EncryptionHelper.generateSymmetricKey(AES_KEY)
        assertEquals("AES", key.algorithm)
        assertArrayEquals(AES_KEY, key.encoded)
    }

    @Test
    fun encryptWithAnExplicitIvIsDeterministic() {
        val key = EncryptionHelper.generateSymmetricKey(AES_KEY)
        assertEquals(DB_CIPHERTEXT, hex(EncryptionHelper.encrypt(key, IvParameterSpec(IV), DB_PLAIN)))
    }

    @Test
    fun decryptAFixedDatabaseBlob() {
        val key = EncryptionHelper.generateSymmetricKey(AES_KEY)
        val blob = IV + unhex(DB_CIPHERTEXT)
        assertArrayEquals(DB_PLAIN, EncryptionHelper.decrypt(key, blob))
        assertArrayEquals(DB_PLAIN, EncryptionHelper.decrypt(key, IvParameterSpec(IV), unhex(DB_CIPHERTEXT)))
    }

    @Test
    fun encryptPrependsARandomIvAndRoundTrips() {
        val key = EncryptionHelper.generateSymmetricKey(AES_KEY)
        val first = EncryptionHelper.encrypt(key, DB_PLAIN)
        val second = EncryptionHelper.encrypt(key, DB_PLAIN)
        // 12-byte IV + ciphertext + 16-byte GCM tag
        assertEquals(12 + DB_PLAIN.size + 16, first.size)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(DB_PLAIN, EncryptionHelper.decrypt(key, first))
        assertArrayEquals(DB_PLAIN, EncryptionHelper.decrypt(key, second))
    }

    @Test
    fun decryptRejectsTamperingAndTruncation() {
        val key = EncryptionHelper.generateSymmetricKey(AES_KEY)
        val blob = IV + unhex(DB_CIPHERTEXT)
        blob[blob.size - 1] = (blob[blob.size - 1].toInt() xor 1).toByte()
        assertThrows(AEADBadTagException::class.java) { EncryptionHelper.decrypt(key, blob) }
        // Shorter than an IV: java.util.Arrays.copyOfRange's own error, not an index error.
        val error = assertThrows(IllegalArgumentException::class.java) { EncryptionHelper.decrypt(key, ByteArray(5)) }
        assertEquals("12 > 5", error.message)
    }

    /** The current backup file: iterations (big-endian int), 12-byte salt, then IV + ciphertext. */
    @Test
    fun decryptAFixedBackupFile() {
        val file = unhex("000003e8") + SALT12 + IV + unhex(BACKUP_CIPHERTEXT)

        val iter = ByteBuffer.wrap(file.copyOfRange(0, Constants.INT_LENGTH)).int
        val salt = file.copyOfRange(Constants.INT_LENGTH, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH)
        val encrypted = file.copyOfRange(Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH, file.size)
        assertEquals(1000, iter)

        val key = EncryptionHelper.generateSymmetricKeyPBKDF2("backup password", iter, salt)
        assertArrayEquals(BACKUP_PLAIN, EncryptionHelper.decrypt(key, encrypted))
        assertEquals(BACKUP_CIPHERTEXT, hex(EncryptionHelper.encrypt(key, IvParameterSpec(IV), BACKUP_PLAIN)))
    }

    /** The old backup format: IV + ciphertext under SHA-256 of the password. */
    @Test
    fun decryptAFixedOldFormatBackupFile() {
        val key = EncryptionHelper.generateSymmetricKeyFromPassword("hunter2")
        assertArrayEquals(BACKUP_PLAIN, EncryptionHelper.decrypt(key, IV + unhex(OLD_BACKUP_CIPHERTEXT)))
    }

    @Test
    fun randomHelpers() {
        repeat(200) {
            val iterations = EncryptionHelper.generateRandomIterations()
            assertTrue(iterations in 140000..160000)
        }
        assertEquals(0, EncryptionHelper.generateRandom(0).size)
        assertEquals(12, EncryptionHelper.generateRandom(12).size)
        assertNotEquals(hex(EncryptionHelper.generateRandom(16)), hex(EncryptionHelper.generateRandom(16)))
    }

    @Test
    fun rsaDecryptsAFixedCiphertextAndRoundTrips() {
        val pair = rsaPair()
        assertArrayEquals("backup-password".toByteArray(), EncryptionHelper.decrypt(pair.private, unhex(RSA_CIPHERTEXT)))

        val encrypted = EncryptionHelper.encrypt(pair.public, "another".toByteArray())
        assertEquals(128, encrypted.size)
        assertArrayEquals("another".toByteArray(), EncryptionHelper.decrypt(pair.private, encrypted))
    }

    @Test
    fun secretKeyWrapperUnwrapsAFixedBlobAndRoundTrips() {
        val wrapper = SecretKeyWrapper(rsaPair())
        val unwrapped = wrapper.unwrap(unhex(WRAPPED_AES_KEY))
        assertEquals("AES", unwrapped.algorithm)
        assertArrayEquals(AES_KEY, unwrapped.encoded)

        val fresh = EncryptionHelper.generateSymmetricKey(ByteArray(16) { (255 - it).toByte() })
        assertArrayEquals(fresh.encoded, wrapper.unwrap(wrapper.wrap(fresh)).encoded)
    }

    @Test
    fun wrappedKeyFileIsReadBackOrCreated() {
        val existing = File(tmp.root, "otp.key")
        existing.writeBytes(unhex(WRAPPED_AES_KEY))
        val loaded = EncryptionHelper.loadOrGenerateWrappedKey(existing, rsaPair())
        assertArrayEquals(AES_KEY, loaded.encoded)
        assertArrayEquals(unhex(WRAPPED_AES_KEY), existing.readBytes())

        val missing = File(tmp.root, "new.key")
        val created = EncryptionHelper.loadOrGenerateWrappedKey(missing, rsaPair())
        assertTrue(missing.exists())
        assertEquals("AES", created.algorithm)
        assertEquals(16, created.encoded.size)
        assertArrayEquals(created.encoded, EncryptionHelper.loadOrGenerateWrappedKey(missing, rsaPair()).encoded)
    }

    companion object {
        @BeforeClass
        @JvmStatic
        fun installAndroidGcm() = AndroidGcmProvider.install()

        private val SALT16 = ByteArray(16) { it.toByte() }
        private val SALT12 = ByteArray(12) { it.toByte() }
        private val AES_KEY = ByteArray(16) { (it * 7 + 1).toByte() }
        private val IV = ByteArray(12) { (0xA0 + it).toByte() }

        private val DB_PLAIN = """[{"secret":"JBSWY3DPEHPK3PXP"}]""".toByteArray(Charsets.UTF_8)
        private const val DB_CIPHERTEXT = "9013bd471383512d3fd3dd54e7aea9d6f86fbbca61c0ee91238365572d5877ec311aa2aec612cd9e555f7de39a8bb9"

        private val BACKUP_PLAIN = """[{"secret":"JBSWY3DPEHPK3PXP","label":"backup"}]""".toByteArray(Charsets.UTF_8)
        private const val BACKUP_CIPHERTEXT = "b7d453ed89c3b8a91ec42fe64bca1aa98e9977e8d614c63d636f376e3ba02437a08ad00653c39a8aa3377897f083448f7c2a65053b6fdb00dd592553a18737eb"
        private const val OLD_BACKUP_CIPHERTEXT = "fe17f8f1cc47560714f187cddb04bfb0967739ea766796cfda999c4b0f81d19dd77689b8195beab596c4d53a0b3e6d68cd4b462aa09456c77f3a0798857f2c28"

        // A throwaway 1024-bit test key pair; it protects nothing.
        private const val RSA_PUBLIC = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCxtMVWA3hC3A8XlQ+vTqFh7ct/oQAmG9DnNVpj00IO67imb3jpR7tUMbYjwTUiCGKYI3kOz3YgiZ11MinvFPODwkw/XbvblA4DBvLc8KvJFpStWTYElDGl1REEIRbhUxa7ptssgIJXWe+xU8jp/2XMDu9AEMh7XcZGme3uxNH+SwIDAQAB"
        private const val RSA_PRIVATE = "MIICdQIBADANBgkqhkiG9w0BAQEFAASCAl8wggJbAgEAAoGBALG0xVYDeELcDxeVD69OoWHty3+hACYb0Oc1WmPTQg7ruKZveOlHu1QxtiPBNSIIYpgjeQ7PdiCJnXUyKe8U84PCTD9du9uUDgMG8tzwq8kWlK1ZNgSUMaXVEQQhFuFTFrum2yyAgldZ77FTyOn/ZcwO70AQyHtdxkaZ7e7E0f5LAgMBAAECgYAVAZxuHXzi0maKUBmJjI7xJ43tqVd+Kb2ZByqHMFrXrq+mJPkzAkK3oiS4t0cTndAh0demk4mQMRRG3UYtt9lNIILecGwvId001xKQVpMCjJ92+oOd9rA2fb3evrxx0gBsTCUbfe/fGtmoJAhQXerTRLDSBxvhmWoykzQQrdbTYQJBANp6Eg2oI7s5PhPqPLC42/6UXDJUV9EATg+oEBWtQEEk1oA1mJwRBUu6pEQwCI1FVRUbwqxJgJUqr8Yp2p1C6NECQQDQOhr2kgfyVKnX23kNxmkO0HKpBrxRDJ+2MoMTmf0ebYoxNTQX4b0IEZgPBTjgGCoQIOx1lVPldnrbnAgyBHxbAkBHOSZrh1XmsjmXmnQglJM7gjgwPCjIvNW16u8bcfiRhCXddBuFPVNBpd3pUNNo4qJjGEK1kdy2RE07R+e5/tcRAkAHampXaa/6w2UhGOHgybYoHuhWeLTwNgDiHj5ozk0jbkdDpV9rklCHHwlJT6hM4s9sr598OIs6WtPZSl9IWLyNAkB3iH1ok1aQxJurn3Xvem6rIQPhVzCFWAxmZNPXD+tWDgMwUzk1/uVBBzhqvVh+IaKF85GLSWYNuy8nregbYImS"
        private const val RSA_CIPHERTEXT = "9745efca618a67bd72c9ee3a094d27442d15ff1de73a0073701c121af3b1f9cc01d8c665e52a02fd81dbae522fa616b97cfa5f4a1f5c627a0f6a132edbecd39d6ae12b9d386a6cf0e5856f5573d3d3ba4e83e1130291666382f2fd8d41f50dfe2cfcb1660f40d96aad1ce41f21328d4d9af5d82a78e6b95498250756b64ea065"
        private const val WRAPPED_AES_KEY = "1fa040529deda1d2e72a4d9862e4065eaea75198b1fd35f84c8ac33349bfa1a24b338f0f3c7042720699b31cc6d1f59a30244c11078fde17ed84c8cba64bfd5aba49119c292eb84375217c04f9f4ec336349c37eb73cc67ccf41722903e540a115237f32ddac7807882739a8b34cfe6aa69e6b0d0deffe5a5c21dba799b606b8"

        private fun rsaPair(): KeyPair {
            val factory = KeyFactory.getInstance("RSA")
            return KeyPair(
                factory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(RSA_PUBLIC))),
                factory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(RSA_PRIVATE)))
            )
        }

        private fun hex(bytes: ByteArray) = String(Hex.encodeHex(bytes))
        private fun unhex(hex: String): ByteArray = Hex.decodeHex(hex)
    }
}
