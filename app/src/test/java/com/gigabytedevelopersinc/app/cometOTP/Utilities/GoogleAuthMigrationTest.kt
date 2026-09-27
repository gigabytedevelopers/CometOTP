@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail.EntryThumbnails
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.apache.commons.codec.binary.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Golden values for Google Authenticator's export codes.
 *
 * Every URI here was produced by Google's own protobuf library (Python, 7.34) from the published
 * MigrationPayload schema, never by the decoder under test, so the two can only agree by both
 * being right. The first one is the example that circulates publicly, Google's classic Key URI
 * sample exported from the app. Never edit a golden value to make a test pass.
 */
class GoogleAuthMigrationTest {

    @Test
    fun recognisesOnlyMigrationUris() {
        assertTrue(GoogleAuthMigration.isMigrationUri(PUBLIC_EXAMPLE))
        assertTrue(GoogleAuthMigration.isMigrationUri("OTPAUTH-MIGRATION://offline?data=x"))
        assertTrue(GoogleAuthMigration.isMigrationUri("  otpauth-migration://offline?data=x"))
        assertFalse(GoogleAuthMigration.isMigrationUri("otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP"))
        assertFalse(GoogleAuthMigration.isMigrationUri("https://example.com/otpauth-migration://"))
        assertFalse(GoogleAuthMigration.isMigrationUri(""))
        assertFalse(GoogleAuthMigration.isMigrationUri(null))
    }

    @Test
    fun decodesThePublicExample() {
        val p = GoogleAuthMigration.parse(PUBLIC_EXAMPLE)

        assertEquals(1, p.accounts.size)
        val a = p.accounts[0]
        assertArrayEquals(unhex("48656c6c6f21deadbeef"), a.secret)
        assertEquals("Example:alice@google.com", a.name)
        assertEquals("Example", a.issuer)
        assertEquals(0, a.algorithm)
        assertEquals(0, a.digits)
        assertEquals(2, a.type)
        assertEquals(0L, a.counter)

        // Written before batching existed: all four left at their defaults.
        assertEquals(0, p.version)
        assertEquals(0, p.batchSize)
        assertEquals(0, p.batchIndex)
        assertEquals(0, p.batchId)
    }

    @Test
    fun decodesEveryFieldOfEveryAccount() {
        val p = GoogleAuthMigration.parse(MAPPING)

        assertEquals(1, p.version)
        assertEquals(1, p.batchSize)
        assertEquals(0, p.batchIndex)
        // Negative int32: a ten-byte varint whose low 32 bits are the value.
        assertEquals(-1320428483, p.batchId)

        val a = p.accounts
        assertEquals(7, a.size)
        assertAccount(a[0], unhex("48656c6c6f21deadbeef"), "Example:alice@google.com", "Example", 1, 1, 2, 0)
        assertAccount(a[1], ascii(RFC_KEY_20), "bob@example.com", "GitHub", 2, 2, 2, 0)
        assertAccount(a[2], ascii(RFC_KEY_64), "carol", "", 3, 1, 2, 0)
        // A counter past 32 bits survives.
        assertAccount(a[3], ascii(RFC_KEY_20), "Counter:dave", "Counter", 1, 1, 1, 5_000_000_000L)
        assertAccount(a[4], ascii("secretmd5secret!"), "legacy", "OldCorp", 4, 1, 2, 0)
        assertAccount(a[5], ascii("abcdefghij"), "Slack:eve@example.com", "", 0, 0, 0, 0)
        assertAccount(a[6], UNICODE_SECRET.toByteArray(Charsets.UTF_8), UNICODE_NAME, UNICODE_ISSUER, 1, 1, 2, 0)
    }

    @Test
    fun decodesEachCodeOfASplitExport() {
        for ((i, uri) in BATCH.withIndex()) {
            val p = GoogleAuthMigration.parse(uri)
            assertEquals(3, p.batchSize)
            assertEquals(i, p.batchIndex)
            assertEquals(987654321, p.batchId)
            assertEquals(1, p.accounts.size)
            assertAccount(p.accounts[0], ascii("batch-secret-$i"), "user$i@example.com", "Batch$i", 1, 1, 2, 0)
        }
    }

    @Test
    fun acceptsTheDataWithOrWithoutPercentEncoding() {
        val expected = GoogleAuthMigration.parse(MAPPING)

        // The base64 alphabet's '/' and '=' left as they are.
        val raw = MAPPING.replace("%2F", "/").replace("%3D", "=")
        assertSameAccounts(expected, GoogleAuthMigration.parse(raw))

        // Batch code 2 is the one whose base64 contains a '+'.
        val plus = BATCH[1]
        val reference = GoogleAuthMigration.parse(plus)
        assertSameAccounts(reference, GoogleAuthMigration.parse(plus.replace("%2B", "+")))
        // A '+' that something upstream form-decoded into a space.
        assertSameAccounts(reference, GoogleAuthMigration.parse(plus.replace("%2B", " ")))
    }

    @Test
    fun rejectsDamagedCodes() {
        assertRejected("otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP")
        assertRejected("otpauth-migration://offline")
        assertRejected("otpauth-migration://offline?data=")
        assertRejected("otpauth-migration://offline?data=%ZZ")
        assertRejected("otpauth-migration://offline?data=abc%2")

        // The public example is one length-delimited field, so a cut anywhere lands inside it:
        // every shorter copy has to be refused, never read past its end or half-imported.
        val bytes = Base64.decodeBase64(PUBLIC_EXAMPLE.substringAfter("data="))
        for (length in 1 until bytes.size)
            assertRejected("otpauth-migration://offline?data=" + Base64.encodeBase64String(bytes.copyOf(length)))
    }

    @Test
    fun convertsEverySupportedAccount() {
        val converted = GoogleAuthMigration.convert(GoogleAuthMigration.parse(MAPPING).accounts)
        val e = converted.entries

        assertEquals(6, e.size)

        assertEntry(e[0], Entry.OTPType.TOTP, unhex("48656c6c6f21deadbeef"), HashAlgorithm.SHA1, 6, "Example", "alice@google.com")
        assertEquals(30, e[0].period)

        assertEntry(e[1], Entry.OTPType.TOTP, ascii(RFC_KEY_20), HashAlgorithm.SHA256, 8, "GitHub", "bob@example.com")
        assertEquals(EntryThumbnails.GitHub, e[1].thumbnail)

        assertEntry(e[2], Entry.OTPType.TOTP, ascii(RFC_KEY_64), HashAlgorithm.SHA512, 6, "", "carol")

        assertEntry(e[3], Entry.OTPType.HOTP, ascii(RFC_KEY_20), HashAlgorithm.SHA1, 6, "Counter", "dave")
        assertEquals(5_000_000_000L, e[3].counter)

        // Every field unspecified: TOTP, SHA1, six digits; the issuer comes from the name.
        assertEntry(e[4], Entry.OTPType.TOTP, ascii("abcdefghij"), HashAlgorithm.SHA1, 6, "Slack", "eve@example.com")
        assertEquals(EntryThumbnails.Slack, e[4].thumbnail)

        assertEntry(e[5], Entry.OTPType.TOTP, UNICODE_SECRET.toByteArray(Charsets.UTF_8), HashAlgorithm.SHA1, 6, UNICODE_ISSUER, UNICODE_NAME)

        for (entry in e)
            assertEquals(emptyList<String>(), entry.tags)
    }

    @Test
    fun reportsWhatItCannotImportInsteadOfDroppingIt() {
        val converted = GoogleAuthMigration.convert(GoogleAuthMigration.parse(MAPPING).accounts)

        assertEquals(1, converted.skipped.size)
        val s = converted.skipped[0]
        assertEquals(GoogleAuthMigration.SkipReason.UNSUPPORTED_ALGORITHM, s.reason)
        assertEquals("OldCorp (legacy)", s.displayName)
    }

    @Test
    fun skipsFieldValuesItDoesNotKnow() {
        fun reasonFor(algorithm: Int = 1, digits: Int = 1, type: Int = 2, secret: ByteArray = ascii("x")) =
            GoogleAuthMigration.convert(listOf(GoogleAuthMigration.Account(secret, "n", "i", algorithm, digits, type, 0)))
                .skipped.singleOrNull()?.reason

        assertEquals(null, reasonFor())
        assertEquals(GoogleAuthMigration.SkipReason.UNSUPPORTED_ALGORITHM, reasonFor(algorithm = 4))
        assertEquals(GoogleAuthMigration.SkipReason.UNSUPPORTED_ALGORITHM, reasonFor(algorithm = 9))
        assertEquals(GoogleAuthMigration.SkipReason.UNSUPPORTED_DIGITS, reasonFor(digits = 3))
        assertEquals(GoogleAuthMigration.SkipReason.UNSUPPORTED_TYPE, reasonFor(type = 3))
        assertEquals(GoogleAuthMigration.SkipReason.EMPTY_SECRET, reasonFor(secret = ByteArray(0)))
    }

    @Test
    fun importedEntriesSurviveSavingAndLoading() {
        val entries = GoogleAuthMigration.convert(GoogleAuthMigration.parse(MAPPING).accounts).entries

        for (e in entries) {
            val reloaded = Entry(JSONObject(e.toJSON().toString()))
            assertEquals(e, reloaded)
            assertEquals(e.counter, reloaded.counter)
        }
    }

    @Test
    fun splitsNamesTheWayKeyUrisDo() {
        fun split(name: String, issuer: String) = GoogleAuthMigration.splitName(name, issuer)

        assertEquals("Example" to "alice", split("Example:alice", "Example"))
        assertEquals("Example" to "alice", split(" Example: alice ", " Example "))
        // A prefix that is not the issuer is part of the label.
        assertEquals("Example" to "Other:alice", split("Other:alice", "Example"))
        assertEquals("Slack" to "eve", split("Slack:eve", ""))
        assertEquals("" to "carol", split("carol", ""))
        // A leading colon names no issuer.
        assertEquals("" to ":carol", split(":carol", ""))
        assertEquals("Example" to "", split("Example:", "Example"))
    }

    companion object {
        const val PUBLIC_EXAMPLE =
            "otpauth-migration://offline?data=CjEKCkhlbGxvId6tvu8SGEV4YW1wbGU6YWxpY2VAZ29vZ2xlLmNvbRoHRXhhbXBsZTAC"

        /** Seven accounts, one per mapping the importer has to get right. */
        const val MAPPING = "otpauth-migration://offline?data=" +
            "CjUKCkhlbGxvId6tvu8SGEV4YW1wbGU6YWxpY2VAZ29vZ2xlLmNvbRoHRXhhbXBsZSABKAEwAgo1ChQxMjM0NTY3" +
            "ODkwMTIzNDU2Nzg5MBIPYm9iQGV4YW1wbGUuY29tGgZHaXRIdWIgAigCMAIKTwpAMTIzNDU2Nzg5MDEyMzQ1Njc4" +
            "OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNBIFY2Fyb2wgAygBMAIKOQoUMTIz" +
            "NDU2Nzg5MDEyMzQ1Njc4OTASDENvdW50ZXI6ZGF2ZRoHQ291bnRlciABKAEwATiA5JfQEgopChBzZWNyZXRtZDVz" +
            "ZWNyZXQhEgZsZWdhY3kaB09sZENvcnAgBCgBMAIKIwoKYWJjZGVmZ2hpahIVU2xhY2s6ZXZlQGV4YW1wbGUuY29t" +
            "CjIKD8O8bsOvY8O2ZMOpLWtleRIOWm%2FDqyDCtyDml6XmnKwaCUNhZsOpIOKYlSABKAEwAhABGAEovbivivv%2F" +
            "%2F%2F%2F%2FAQ%3D%3D"

        /** One export split over three codes. */
        val BATCH = listOf(
            "otpauth-migration://offline?data=CjEKDmJhdGNoLXNlY3JldC0wEhF1c2VyMEBleGFtcGxlLmNvbRoGQmF0Y2gwIAEoATACEAEYAyix0fnWAw%3D%3D",
            "otpauth-migration://offline?data=CjEKDmJhdGNoLXNlY3JldC0xEhF1c2VyMUBleGFtcGxlLmNvbRoGQmF0Y2gxIAEoATACEAEYAyABKLHR%2BdYD",
            "otpauth-migration://offline?data=CjEKDmJhdGNoLXNlY3JldC0yEhF1c2VyMkBleGFtcGxlLmNvbRoGQmF0Y2gyIAEoATACEAEYAyACKLHR%2BdYD"
        )

        private const val RFC_KEY_20 = "12345678901234567890"
        private const val RFC_KEY_64 = "1234567890123456789012345678901234567890123456789012345678901234"
        private const val UNICODE_SECRET = "ünïcödé-key"
        private const val UNICODE_NAME = "Zoë · 日本"
        private const val UNICODE_ISSUER = "Café ☕"

        fun unhex(hex: String): ByteArray = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

        private fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)

        private fun assertAccount(a: GoogleAuthMigration.Account, secret: ByteArray, name: String, issuer: String,
                                  algorithm: Int, digits: Int, type: Int, counter: Long) {
            assertArrayEquals(secret, a.secret)
            assertEquals(name, a.name)
            assertEquals(issuer, a.issuer)
            assertEquals(algorithm, a.algorithm)
            assertEquals(digits, a.digits)
            assertEquals(type, a.type)
            assertEquals(counter, a.counter)
        }

        private fun assertEntry(e: Entry, type: Entry.OTPType, secret: ByteArray, algorithm: HashAlgorithm,
                                digits: Int, issuer: String, label: String) {
            assertEquals(type, e.type)
            assertArrayEquals(secret, e.secret)
            assertEquals(algorithm, e.algorithm)
            assertEquals(digits, e.digits)
            assertEquals(issuer, e.issuer)
            assertEquals(label, e.label)
        }

        private fun assertSameAccounts(expected: GoogleAuthMigration.Payload, actual: GoogleAuthMigration.Payload) {
            assertEquals(expected.accounts.size, actual.accounts.size)
            for ((x, y) in expected.accounts.zip(actual.accounts))
                assertAccount(y, x.secret, x.name, x.issuer, x.algorithm, x.digits, x.type, x.counter)
            assertEquals(expected.batchId, actual.batchId)
        }

        private fun assertRejected(uri: String) {
            try {
                GoogleAuthMigration.parse(uri)
                fail("accepted $uri")
            } catch (expected: IllegalArgumentException) {
            }
        }
    }
}
