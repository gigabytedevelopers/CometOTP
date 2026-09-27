@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.GoogleAuthImportSession.Added
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GoogleAuthMigrationTest.Companion.BATCH
import com.gigabytedevelopersinc.app.cometOTP.Utilities.GoogleAuthMigrationTest.Companion.PUBLIC_EXAMPLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAuthImportSessionTest {

    private fun code(i: Int) = GoogleAuthMigration.parse(BATCH[i])

    private fun names(s: GoogleAuthImportSession) = s.accounts.map { it.name }

    @Test
    fun startsEmpty() {
        val s = GoogleAuthImportSession()
        assertTrue(s.isEmpty)
        assertFalse(s.isComplete)
        assertEquals(0, s.received)
        assertEquals(0, s.expected)
        assertEquals(emptyList<String>(), names(s))
    }

    @Test
    fun completesOnceEveryCodeOfTheExportIsIn() {
        val s = GoogleAuthImportSession()

        assertEquals(Added.NEW, s.add(code(0)))
        assertEquals(1, s.received)
        assertEquals(3, s.expected)
        assertFalse(s.isComplete)

        assertEquals(Added.NEW, s.add(code(1)))
        assertFalse(s.isComplete)

        assertEquals(Added.NEW, s.add(code(2)))
        assertTrue(s.isComplete)
        assertEquals(listOf("user0@example.com", "user1@example.com", "user2@example.com"), names(s))
    }

    @Test
    fun keepsTheExportsOrderWhateverOrderTheCodesAreScannedIn() {
        val s = GoogleAuthImportSession()
        s.add(code(2))
        s.add(code(0))
        s.add(code(1))

        assertTrue(s.isComplete)
        assertEquals(listOf("user0@example.com", "user1@example.com", "user2@example.com"), names(s))
    }

    @Test
    fun ignoresACodeScannedTwice() {
        val s = GoogleAuthImportSession()
        s.add(code(0))

        assertEquals(Added.REPEAT, s.add(code(0)))
        assertEquals(1, s.received)
        assertEquals(listOf("user0@example.com"), names(s))
    }

    @Test
    fun startsOverWhenACodeFromAnotherExportArrives() {
        val s = GoogleAuthImportSession()
        s.add(code(0))
        s.add(code(1))

        // A single-code export, with a batch id of its own.
        assertEquals(Added.RESTARTED, s.add(GoogleAuthMigration.parse(GoogleAuthMigrationTest.MAPPING)))
        assertEquals(1, s.received)
        assertEquals(1, s.expected)
        assertTrue(s.isComplete)
        assertEquals(7, s.accounts.size)
    }

    @Test
    fun treatsAnExportFromBeforeBatchingAsOneCode() {
        val s = GoogleAuthImportSession()

        assertEquals(Added.NEW, s.add(GoogleAuthMigration.parse(PUBLIC_EXAMPLE)))
        assertTrue(s.isComplete)
        assertEquals(1, s.expected)
        assertEquals(listOf("Example:alice@google.com"), names(s))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesACodeNumberedOutsideItsOwnExport() {
        GoogleAuthImportSession().add(GoogleAuthMigration.Payload(emptyList(), 1, 3, 3, 42))
    }

    @Test
    fun clearForgetsEverything() {
        val s = GoogleAuthImportSession()
        s.add(code(0))
        s.clear()

        assertTrue(s.isEmpty)
        assertEquals(0, s.expected)
        // Nothing left over to count a new export's first code as a restart against.
        assertEquals(Added.NEW, s.add(code(1)))
    }
}
