@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.util.Arrays
import java.util.Random
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    class PBKDF2Credentials {
        var password: ByteArray? = null
        var key: ByteArray? = null
    }

    fun generateRandomIterations(): Int {
        val rand = Random()
        return rand.nextInt((Constants.PBKDF2_MAX_ITERATIONS - Constants.PBKDF2_MIN_ITERATIONS) + 1) + Constants.PBKDF2_MIN_ITERATIONS
    }

    fun generateRandom(length: Int): ByteArray {
        val raw = ByteArray(length)
        SecureRandom().nextBytes(raw)

        return raw
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun generatePBKDF2Credentials(password: String, salt: ByteArray, iter: Int): PBKDF2Credentials {
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val keySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iter, Constants.PBKDF2_LENGTH)

        val array = secretKeyFactory.generateSecret(keySpec).encoded

        val halfPoint = array.size / 2

        val credentials = PBKDF2Credentials()
        credentials.password = Arrays.copyOfRange(array, halfPoint, array.size)
        credentials.key = Arrays.copyOfRange(array, 0, halfPoint)

        return credentials
    }

    fun generateSymmetricKey(data: ByteArray): SecretKey {
        return SecretKeySpec(data, 0, data.size, "AES")
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun generateSymmetricKeyPBKDF2(password: String, iter: Int, salt: ByteArray): SecretKey {
        val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        val keySpec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iter, Constants.PBKDF2_LENGTH)

        return secretKeyFactory.generateSecret(keySpec)
    }

    @Throws(NoSuchAlgorithmException::class)
    fun generateSymmetricKeyFromPassword(password: String): SecretKey {
        val sha = MessageDigest.getInstance("SHA-256")

        return SecretKeySpec(sha.digest(password.toByteArray(StandardCharsets.UTF_8)), "AES")
    }

    @Throws(NoSuchPaddingException::class, NoSuchAlgorithmException::class, InvalidAlgorithmParameterException::class, InvalidKeyException::class, UnsupportedEncodingException::class, BadPaddingException::class, IllegalBlockSizeException::class)
    fun encrypt(secretKey: SecretKey, iv: IvParameterSpec, plainText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Constants.ALGORITHM_SYMMETRIC)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)

        return cipher.doFinal(plainText)
    }

    @Throws(NoSuchPaddingException::class, BadPaddingException::class, InvalidKeyException::class, NoSuchAlgorithmException::class, IllegalBlockSizeException::class, UnsupportedEncodingException::class, InvalidAlgorithmParameterException::class)
    fun encrypt(secretKey: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(Constants.ENCRYPTION_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipherText = encrypt(secretKey, IvParameterSpec(iv), plaintext)

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return combined
    }

    @Throws(NoSuchPaddingException::class, BadPaddingException::class, InvalidKeyException::class, NoSuchAlgorithmException::class, IllegalBlockSizeException::class, UnsupportedEncodingException::class, InvalidAlgorithmParameterException::class)
    fun encrypt(publicKey: PublicKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Constants.ALGORITHM_ASYMMETRIC)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)

        return cipher.doFinal(plaintext)
    }

    @Throws(NoSuchPaddingException::class, InvalidKeyException::class, NoSuchAlgorithmException::class, IllegalBlockSizeException::class, BadPaddingException::class, InvalidAlgorithmParameterException::class)
    fun decrypt(secretKey: SecretKey, iv: IvParameterSpec, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Constants.ALGORITHM_SYMMETRIC)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)

        return cipher.doFinal(cipherText)
    }

    @Throws(NoSuchPaddingException::class, InvalidKeyException::class, NoSuchAlgorithmException::class, IllegalBlockSizeException::class, BadPaddingException::class, InvalidAlgorithmParameterException::class)
    fun decrypt(secretKey: SecretKey, cipherText: ByteArray): ByteArray {
        // java.util.Arrays on purpose: unlike Kotlin's copyOfRange it zero-pads past the end.
        val iv = Arrays.copyOfRange(cipherText, 0, Constants.ENCRYPTION_IV_LENGTH)
        val encrypted = Arrays.copyOfRange(cipherText, Constants.ENCRYPTION_IV_LENGTH, cipherText.size)

        return decrypt(secretKey, IvParameterSpec(iv), encrypted)
    }

    @Throws(NoSuchPaddingException::class, InvalidKeyException::class, NoSuchAlgorithmException::class, IllegalBlockSizeException::class, BadPaddingException::class, InvalidAlgorithmParameterException::class)
    fun decrypt(privateKey: PrivateKey, cipherText: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(Constants.ALGORITHM_ASYMMETRIC)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)

        return cipher.doFinal(cipherText)
    }

    /**
     * Load our symmetric secret key.
     * The symmetric secret key is stored securely on disk by wrapping
     * it with a public/private key pair, possibly backed by hardware.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun loadOrGenerateWrappedKey(keyFile: File, keyPair: KeyPair): SecretKey {
        val wrapper = SecretKeyWrapper(keyPair)

        // Generate secret key if none exists
        if (!keyFile.exists()) {
            val raw = generateRandom(Constants.ENCRYPTION_KEY_LENGTH)

            val key: SecretKey = SecretKeySpec(raw, "AES")
            val wrapped = wrapper.wrap(key)


            FileHelper.writeBytesToFile(keyFile, wrapped)
        }

        // Even if we just generated the key, always read it back to ensure we
        // can read it successfully.
        val wrapped = FileHelper.readFileToBytes(keyFile)

        return wrapper.unwrap(wrapped)
    }
}
