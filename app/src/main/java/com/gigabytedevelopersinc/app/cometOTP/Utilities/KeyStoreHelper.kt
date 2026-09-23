@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.gigabytedevelopersinc.app.cometOTP.R
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.ProviderException
import java.security.spec.AlgorithmParameterSpec
import java.util.Calendar
import java.util.GregorianCalendar
import javax.crypto.SecretKey
import javax.security.auth.x500.X500Principal

object KeyStoreHelper {

    @JvmStatic
    fun wipeKeys(context: Context) {
        val keyFile = File(context.filesDir.toString() + "/" + Constants.FILENAME_ENCRYPTED_KEY)
        keyFile.delete()

        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            if (keyStore.containsAlias(Constants.KEYSTORE_ALIAS_WRAPPING))
                keyStore.deleteEntry(Constants.KEYSTORE_ALIAS_WRAPPING)
        } catch (e: GeneralSecurityException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    @Throws(GeneralSecurityException::class, IOException::class)
    fun loadOrGenerateAsymmetricKeyPair(@Suppress("UNUSED_PARAMETER") context: Context, alias: String): KeyPair? {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        if (!keyStore.containsAlias(alias)) {
            val start: Calendar = GregorianCalendar()
            val end: Calendar = GregorianCalendar()
            end.add(Calendar.YEAR, 100)

            val spec: AlgorithmParameterSpec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setCertificateSubject(X500Principal("CN=$alias"))
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(start.time)
                .setCertificateNotAfter(end.time)
                .build()

            val gen = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")

            gen.initialize(spec)
            gen.generateKeyPair()
        }

        val entry = keyStore.getEntry(alias, null) as KeyStore.PrivateKeyEntry?

        return if (entry != null)
            KeyPair(entry.certificate.publicKey, entry.privateKey)
        else
            null
    }

    @JvmStatic
    fun loadEncryptionKeyFromKeyStore(context: Context, failSilent: Boolean): SecretKey? {
        var encKey: SecretKey? = null

        try {
            val pair = loadOrGenerateAsymmetricKeyPair(context, Constants.KEYSTORE_ALIAS_WRAPPING)
            if (pair != null)
                encKey = EncryptionHelper.loadOrGenerateWrappedKey(File(context.filesDir.toString() + "/" + Constants.FILENAME_ENCRYPTED_KEY), pair)
        } catch (e: GeneralSecurityException) {
            onKeyStoreError(context, failSilent, e)
        } catch (e: IOException) {
            onKeyStoreError(context, failSilent, e)
        } catch (e: ProviderException) {
            onKeyStoreError(context, failSilent, e)
        }

        return encKey
    }

    private fun onKeyStoreError(context: Context, failSilent: Boolean, e: Exception) {
        e.printStackTrace()
        if (!failSilent)
            UIHelper.showGenericDialog(context, R.string.dialog_title_keystore_error, R.string.dialog_msg_keystore_error)
    }
}
