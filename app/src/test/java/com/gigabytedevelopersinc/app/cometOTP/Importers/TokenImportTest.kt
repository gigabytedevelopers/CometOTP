@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenImportTest {

    private val key = "Hello!".toByteArray(Charsets.US_ASCII)

    private fun token(type: Entry.OTPType? = Entry.OTPType.TOTP, secret: ByteArray? = key,
                      algorithm: HashAlgorithm? = HashAlgorithm.SHA1, digits: Int = 6, period: Int = 30,
                      counter: Long = 0) =
        ImportedToken(type, secret, "Issuer", "label", algorithm, digits, period, counter)

    private fun reasonFor(t: ImportedToken) = TokenImport.convert(listOf(t)).skipped.singleOrNull()?.reason

    @Test
    fun convertsEachTypeOntoTheMatchingEntry() {
        val converted = TokenImport.convert(listOf(
                token(),
                token(Entry.OTPType.HOTP, counter = 42),
                token(Entry.OTPType.STEAM, digits = 5),
                ImportedToken(Entry.OTPType.MOTP, "0123456789abcdef".toByteArray(), "Issuer", "label")))

        assertEquals(0, converted.skipped.size)
        val e = converted.entries
        assertEquals(4, e.size)

        assertEquals(Entry.OTPType.TOTP, e[0].type)
        assertArrayEquals(key, e[0].secret)
        assertEquals(30, e[0].period)
        assertEquals(6, e[0].digits)
        assertEquals("Issuer", e[0].issuer)
        assertEquals("label", e[0].label)

        assertEquals(Entry.OTPType.HOTP, e[1].type)
        assertEquals(42L, e[1].counter)

        assertEquals(Entry.OTPType.STEAM, e[2].type)
        assertEquals(5, e[2].digits)

        assertEquals(Entry.OTPType.MOTP, e[3].type)
        assertEquals("0123456789abcdef", String(e[3].secret!!))
    }

    @Test
    fun keepsTagsTidy() {
        val e = TokenImport.convert(listOf(ImportedToken(Entry.OTPType.TOTP, key, "I", "l",
                tags = listOf(" work ", "", "work", "home")))).entries.single()
        assertEquals(listOf("work", "home"), e.tags)
    }

    @Test
    fun keepsAlgorithmDigitsAndPeriod() {
        val e = TokenImport.convert(listOf(token(algorithm = HashAlgorithm.SHA512, digits = 8, period = 60))).entries.single()
        assertEquals(HashAlgorithm.SHA512, e.algorithm)
        assertEquals(8, e.digits)
        assertEquals(60, e.period)
    }

    @Test
    fun reportsWhatItCannotImportByName() {
        assertNull(reasonFor(token()))
        assertEquals(TokenImport.SkipReason.INVALID_SECRET, reasonFor(token(secret = null)))
        assertEquals(TokenImport.SkipReason.EMPTY_SECRET, reasonFor(token(secret = ByteArray(0))))
        assertEquals(TokenImport.SkipReason.UNSUPPORTED_TYPE, reasonFor(token(type = null)))
        assertEquals(TokenImport.SkipReason.UNSUPPORTED_ALGORITHM, reasonFor(token(algorithm = null)))
        assertEquals(TokenImport.SkipReason.UNSUPPORTED_DIGITS, reasonFor(token(digits = 3)))
        assertEquals(TokenImport.SkipReason.UNSUPPORTED_DIGITS, reasonFor(token(digits = 10)))
        assertEquals(TokenImport.SkipReason.UNSUPPORTED_PERIOD, reasonFor(token(period = 0)))
        // A counter-based account has no period to be wrong.
        assertNull(reasonFor(token(Entry.OTPType.HOTP, period = 0)))

        val skipped = TokenImport.convert(listOf(token(algorithm = null))).skipped.single()
        assertEquals("Issuer (label)", skipped.displayName)
    }

    @Test
    fun keepsTheExportsOrderAcrossEntriesAndSkips() {
        val converted = TokenImport.convert(listOf(
                ImportedToken(Entry.OTPType.TOTP, key, "A", ""),
                ImportedToken(null, key, "B", ""),
                ImportedToken(Entry.OTPType.TOTP, key, "C", "")))
        assertEquals(listOf("A", "C"), converted.entries.map { it.issuer })
        assertEquals(listOf("B"), converted.skipped.map { it.displayName })
    }

    @Test
    fun decodesBase32AsAppsWriteIt() {
        val expected = "Hello!".toByteArray()
        assertArrayEquals(expected, TokenImport.decodeBase32("JBSWY3DPEE"))
        assertArrayEquals(expected, TokenImport.decodeBase32("JBSWY3DPEE======"))
        assertArrayEquals(expected, TokenImport.decodeBase32("jbswy3dpee"))
        assertArrayEquals(expected, TokenImport.decodeBase32("jbsw y3dp ee"))
        assertArrayEquals(expected, TokenImport.decodeBase32("JBSW-Y3DP-EE"))
        assertArrayEquals(ByteArray(0), TokenImport.decodeBase32(""))
        assertArrayEquals(ByteArray(0), TokenImport.decodeBase32(null))
        // Not base32: the codec would quietly drop the bad characters and hand back a wrong key.
        assertNull(TokenImport.decodeBase32("JBSWY3DPEE1"))
        assertNull(TokenImport.decodeBase32("0123456789abcdef"))
        assertNull(TokenImport.decodeBase32("otpauth://totp/x?secret=JBSWY3DPEE"))
    }

    @Test
    fun readsAlgorithmAndTypeNamesAsExportsSpellThem() {
        assertEquals(HashAlgorithm.SHA1, TokenImport.algorithm(null))
        assertEquals(HashAlgorithm.SHA1, TokenImport.algorithm(""))
        assertEquals(HashAlgorithm.SHA1, TokenImport.algorithm("sha1"))
        assertEquals(HashAlgorithm.SHA1, TokenImport.algorithm("SHA-1"))
        assertEquals(HashAlgorithm.SHA1, TokenImport.algorithm("HmacSHA1"))
        assertEquals(HashAlgorithm.SHA256, TokenImport.algorithm("SHA256"))
        assertEquals(HashAlgorithm.SHA256, TokenImport.algorithm("sha-256"))
        assertEquals(HashAlgorithm.SHA512, TokenImport.algorithm("SHA_512"))
        assertNull(TokenImport.algorithm("MD5"))
        assertNull(TokenImport.algorithm("SHA224"))

        assertEquals(Entry.OTPType.TOTP, TokenImport.type(null))
        assertEquals(Entry.OTPType.TOTP, TokenImport.type("totp"))
        assertEquals(Entry.OTPType.HOTP, TokenImport.type("HOTP"))
        assertEquals(Entry.OTPType.STEAM, TokenImport.type("Steam"))
        assertEquals(Entry.OTPType.MOTP, TokenImport.type("motp"))
        assertNull(TokenImport.type("yandex"))
    }

    @Test
    fun readsAKeyAsPasswordManagersStoreOne() {
        val link = TokenImport.fromKeyText("otpauth://totp/GitHub:bob?secret=JBSWY3DPEE&issuer=GitHub&digits=8", "Item", "user")
        assertEquals("GitHub", link.issuer)
        assertEquals("bob", link.label)
        assertEquals(8, link.digits)

        val bareLink = TokenImport.fromKeyText("otpauth://totp/?secret=JBSWY3DPEE", "Item", "user")
        assertEquals("Item", bareLink.issuer)
        assertEquals("user", bareLink.label)

        val secret = TokenImport.fromKeyText(" jbsw y3dp ee ", "Item", "user")
        assertEquals(Entry.OTPType.TOTP, secret.type)
        assertArrayEquals("Hello!".toByteArray(), secret.secret)
        assertEquals("Item", secret.issuer)

        val steam = TokenImport.fromKeyText("steam://JBSWY3DPEE", "Steam", "gamer")
        assertEquals(Entry.OTPType.STEAM, steam.type)
        assertEquals(5, steam.digits)
        assertArrayEquals("Hello!".toByteArray(), steam.secret)
    }

    @Test
    fun namesAccountsByWhatIsPresent() {
        assertEquals("Issuer (label)", TokenImport.displayName("Issuer", "label"))
        assertEquals("Issuer", TokenImport.displayName("Issuer", ""))
        assertEquals("label", TokenImport.displayName("", "label"))
        assertEquals("", TokenImport.displayName("", ""))
    }

    @Test
    fun splitsNamesTheWayKeyUrisDo() {
        assertEquals("Example" to "alice", TokenImport.splitName("Example:alice", "Example"))
        assertEquals("Example" to "alice", TokenImport.splitName("Example: alice", "Example"))
        assertEquals("Example" to "Other:alice", TokenImport.splitName("Other:alice", "Example"))
        assertEquals("Example" to "alice", TokenImport.splitName("alice", "Example"))
        assertEquals("Example" to "alice", TokenImport.splitName("Example:alice", ""))
        assertEquals("" to "alice", TokenImport.splitName("alice", ""))
        assertEquals("" to ":alice", TokenImport.splitName(":alice", ""))
    }
}
