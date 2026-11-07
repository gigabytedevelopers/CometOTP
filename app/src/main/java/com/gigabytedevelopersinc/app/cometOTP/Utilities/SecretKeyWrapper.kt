@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import android.annotation.SuppressLint
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyPair
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Wraps [SecretKey] instances using a public/private key pair stored in
 * the platform [KeyStore]. This allows us to protect symmetric keys with
 * hardware-backed crypto, if provided by the device.
 *
 * See [key wrapping](http://en.wikipedia.org/wiki/Key_Wrap) for more
 * details.
 *
 * Not inherently thread safe.
 */
class SecretKeyWrapper {
    private val mPair: KeyPair

    /**
     * Create a wrapper using the public/private key pair with the given alias.
     * If no pair with that alias exists, it will be generated.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    constructor(keyPair: KeyPair) {
        mPair = keyPair
    }

    /**
     * Wrap a [SecretKey] using the public key assigned to this wrapper.
     * Use [unwrap] to later recover the original
     * [SecretKey].
     *
     * @return a wrapped version of the given [SecretKey] that can be
     *         safely stored on untrusted storage.
     */
    @SuppressLint("GetInstance")
    @Throws(GeneralSecurityException::class)
    fun wrap(key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(Constants.ALGORITHM_ASYMMETRIC)
        cipher.init(Cipher.WRAP_MODE, mPair.public)
        return cipher.wrap(key)
    }

    /**
     * Unwrap a [SecretKey] using the private key assigned to this
     * wrapper.
     *
     * @param blob a wrapped [SecretKey] as previously returned by
     *            [wrap].
     */
    @SuppressLint("GetInstance")
    @Throws(GeneralSecurityException::class)
    fun unwrap(blob: ByteArray): SecretKey {
        val cipher = Cipher.getInstance(Constants.ALGORITHM_ASYMMETRIC)
        cipher.init(Cipher.UNWRAP_MODE, mPair.private)

        return cipher.unwrap(blob, "AES", Cipher.SECRET_KEY) as SecretKey
    }
}
