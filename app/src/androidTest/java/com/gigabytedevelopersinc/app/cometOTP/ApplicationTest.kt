@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP

import androidx.test.platform.app.InstrumentationRegistry
import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.Constants
import com.gigabytedevelopersinc.app.cometOTP.Utilities.DatabaseHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EncryptionHelper
import com.gigabytedevelopersinc.app.cometOTP.Utilities.KeyStoreHelper
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Hex
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tests that need a real device or emulator: Entry parses otpauth:// URLs through android.net.Uri,
 * and the database work goes through the Android keystore.
 *
 * EncryptionHelper is here for a subtler reason: it passes an IvParameterSpec to AES/GCM, which
 * Android's security provider accepts and the JDK's does not, so it can only be checked against a
 * real provider.
 *
 * The parts that need neither — TokenCalculator and Tools — live in app/src/test instead, so they
 * run on every pull request without waiting for an emulator.
 */
class ApplicationTest {

    // String.getBytes() and new String(byte[]) in the Java version used the platform charset,
    // where Kotlin's toByteArray() and String(ByteArray) default to UTF-8.
    private fun bytes(s: String): ByteArray = s.toByteArray(Charset.defaultCharset())
    private fun string(b: ByteArray): String = String(b, Charset.defaultCharset())

    @Test
    fun testEntry() {
        val secret = bytes("Das System ist sicher")
        val label = "5 von 5 Sterne"
        val period = 30

        val s = "{\"secret\":\"" + string(Base32().encode(secret)) + "\"," +
                "\"issuer\":\"\"," +
                "\"label\":\"" + label + "\"," +
                "\"digits\":6," +
                "\"type\":\"TOTP\"," +
                "\"algorithm\":\"SHA1\"," +
                "\"thumbnail\":\"Default\"," +
                "\"last_used\":0," +
                "\"used_frequency\":0," +
                "\"period\":" + period + "," +
                "\"tags\":[\"test1\",\"test2\"]}"

        val e = Entry(JSONObject(s))
        assertArrayEquals(secret, e.secret)
        assertEquals(label, e.label)

        val tags = arrayOf("test1", "test2")
        assertEquals(tags.size.toLong(), e.tags.size.toLong())
        assertArrayEquals(tags, e.tags.toTypedArray())

        assertEquals(s, e.toJSON().toString())
    }


    @Test
    fun testEntryURL() {
        try {
            Entry("DON'T CARE")
            fail()
        } catch (ignored: Exception) {
        }

        try {
            Entry("https://github.com/0xbb/")
            fail()
        } catch (ignored: Exception) {
        }

        try {
            Entry("otpauth://hotp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ")
            fail()
        }
        catch (ignored: Exception) {
        }

        try {
            Entry("otpauth://totp/ACME")
            fail()
        }
        catch (ignored: Exception) {
        }

        var entry = Entry("otpauth://totp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&ALGORITHM=SHA1&digits=6&period=30")
        assertEquals("john.doe@email.com", entry.label)

        val entry2 = Entry("otpauth://totp/ :john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&ALGORITHM=SHA1&digits=6&period=30")
        assertEquals(":john.doe@email.com", entry2.label)

        val entry3 = Entry("otpauth://totp/ :john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=%20&ALGORITHM=SHA1&digits=6&period=30")
        assertEquals("john.doe@email.com", entry3.label)

        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", string(Base32().encode(entry.secret)))


        entry = Entry("otpauth://totp/ACME%20Co:john.doe@email.com?secret=HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ&issuer=ACME%20Co&ALGORITHM=SHA1&digits=6&period=30&tags=test1&tags=test2")
        assertEquals("john.doe@email.com", entry.label)

        assertEquals("HXDMVJECJJWSRB3HWIZR4IFUGFTMXBOZ", string(Base32().encode(entry.secret)))
        val tags = arrayOf("test1", "test2")
        assertEquals(tags.size.toLong(), entry.tags.size.toLong())
        assertArrayEquals(tags, entry.tags.toTypedArray())
    }

    @Test
    fun testSettingsHelper() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        keyStore.deleteEntry("settings")

        File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE).delete()
        File(context.filesDir.toString() + "/" + Constants.FILENAME_ENCRYPTED_KEY).delete()

        val encryptionKey = KeyStoreHelper.loadEncryptionKeyFromKeyStore(context, false)
        var b = DatabaseHelper.loadDatabase(context, encryptionKey)
        assertEquals(0L, b.size.toLong())

        val a = ArrayList<Entry>()
        var e = Entry()
        e.label = "label"
        e.secret = bytes("secret")
        a.add(e)

        e = Entry()
        e.label = "label2"
        e.secret = bytes("secret2")
        a.add(e)

        DatabaseHelper.saveDatabase(context, a, encryptionKey)
        b = DatabaseHelper.loadDatabase(context, encryptionKey)

        assertEquals(a, b)

        File(context.filesDir.toString() + "/" + Constants.FILENAME_DATABASE).delete()
        File(context.filesDir.toString() + "/" + Constants.FILENAME_ENCRYPTED_KEY).delete()
    }

    @Test
    fun testEncryptionHelper() {


        // https://golang.org/src/crypto/cipher/gcm_test.go
        val testCases = arrayOf(
                arrayOf("11754cd72aec309bf52f7687212e8957", "3c819d9a9bed087615030b65", "", "250327c674aaf477aef2675748cf6971"),
                arrayOf("ca47248ac0b6f8372a97ac43508308ed", "ffd2b598feabc9019262d2be", "", "60d20404af527d248d893ae495707d1a"),
                arrayOf("7fddb57453c241d03efbed3ac44e371c", "ee283a3fc75575e33efd4887", "d5de42b461646c255c87bd2962d3b9a2", "2ccda4a5415cb91e135c2a0f78c9b2fdb36d1df9b9d5e596f83e8b7f52971cb3"),
                arrayOf("ab72c77b97cb5fe9a382d9fe81ffdbed", "54cc7dc2c37ec006bcc6d1da", "007c5e5b3e59df24a7c355584fc1518d", "0e1bde206a07a9c2c1b65300f8c649972b4401346697138c7a4891ee59867d0c"),
                arrayOf("feffe9928665731c6d6a8f9467308308", "cafebabefacedbaddecaf888", "d9313225f88406e5a55909c5aff5269a86a7a9531534f7da2e4c303d8a318a721c3c0c95956809532fcf0e2449a6b525b16aedf5aa0de657ba637b391aafd255", "42831ec2217774244b7221b784d0d49ce3aa212f2c02a4e035c17e2329aca12e21d514b25466931c7d8f6a5aac84aa051ba30b396a0aac973d58e091473f59854d5c2af327cd64a62cf35abd2ba6fab4"),

        )

        for (testCase in testCases) {

            val k = SecretKeySpec(Hex().decode(bytes(testCase[0])), "AES")
            val iv = IvParameterSpec(Hex().decode(bytes(testCase[1])))

            val cipherTExt = EncryptionHelper.encrypt(k, iv, Hex().decode(bytes(testCase[2])))
            val cipher = string(Hex().encode(cipherTExt))

            assertEquals(cipher, testCase[3])

            assertEquals(testCase[2], string(Hex().encode(EncryptionHelper.decrypt(k, iv, cipherTExt))))

        }
    }
}
