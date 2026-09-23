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
    private val mCipher: Cipher
    private val mPair: KeyPair

    /**
     * Create a wrapper using the public/private key pair with the given alias.
     * If no pair with that alias exists, it will be generated.
     */
    @SuppressLint("GetInstance")
    @Throws(GeneralSecurityException::class, IOException::class)
    constructor(keyPair: KeyPair) {
        mCipher = Cipher.getInstance(Constants.ALGORITHM_ASYMMETRIC)
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
    @Throws(GeneralSecurityException::class)
    fun wrap(key: SecretKey): ByteArray {
        mCipher.init(Cipher.WRAP_MODE, mPair.public)
        return mCipher.wrap(key)
    }

    /**
     * Unwrap a [SecretKey] using the private key assigned to this
     * wrapper.
     *
     * @param blob a wrapped [SecretKey] as previously returned by
     *            [wrap].
     */
    @Throws(GeneralSecurityException::class)
    fun unwrap(blob: ByteArray): SecretKey {
        mCipher.init(Cipher.UNWRAP_MODE, mPair.private)

        return mCipher.unwrap(blob, "AES", Cipher.SECRET_KEY) as SecretKey
    }
}
