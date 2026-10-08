@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import com.gigabytedevelopersinc.app.cometOTP.Utilities.BackupDestination.StoredBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.TimeZone

class BackupRunnerTest {

    @Test
    fun oneKeptBackupOverwritesOneFile() {
        assertEquals("otp_accounts_auto.json.aes", BackupRunner.backupName(NOW, 1))
    }

    @Test
    fun keptBackupsAreNamedByTheTime() {
        val saved = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            // 2026-09-28 14:05:09 UTC
            assertEquals("otp_accounts_auto_2026-09-28_14-05-09.json.aes", BackupRunner.backupName(NOW, 5))
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun onlyAutomaticBackupsAreRecognised() {
        assertTrue(BackupRunner.isAutoBackupName("otp_accounts_auto.json.aes"))
        assertTrue(BackupRunner.isAutoBackupName("otp_accounts_auto_2026-09-28_14-05-09.json.aes"))
        // What BackupActivity names manual backups.
        assertFalse(BackupRunner.isAutoBackupName("otp_accounts.json.aes"))
        assertFalse(BackupRunner.isAutoBackupName("otp_accounts_2026-09-28_14-05-09.json.aes"))
        assertFalse(BackupRunner.isAutoBackupName("otp_accounts_auto_2026-09-28_14-05-09.json"))
        assertFalse(BackupRunner.isAutoBackupName(null))
    }

    @Test
    fun pruningKeepsTheNewestAndNeverTouchesOtherFiles() {
        val stored = listOf(
            backup("otp_accounts_auto_2026-09-24_02-00-00.json.aes", 4),
            backup("otp_accounts_auto_2026-09-25_02-00-00.json.aes", 5),
            backup("otp_accounts_auto_2026-09-26_02-00-00.json.aes", 6),
            backup("otp_accounts_2026-09-01_10-00-00.json.aes", 1),   // manual
            backup("notes.txt", 0),
            backup("otp_accounts_auto_2026-09-27_02-00-00.json.aes", 7)
        )

        val pruned = BackupRunner.backupsToPrune(stored, "otp_accounts_auto_2026-09-27_02-00-00.json.aes", 3)

        assertEquals(listOf("otp_accounts_auto_2026-09-24_02-00-00.json.aes"), pruned.map { it.name })
    }

    @Test
    fun theBackupJustWrittenIsNeverPruned() {
        // Switching from dated files to one file: the single file is written with the oldest
        // name order but the newest time, and must be the one that stays.
        val stored = listOf(
            backup("otp_accounts_auto.json.aes", 100),
            backup("otp_accounts_auto_2026-09-26_02-00-00.json.aes", 6),
            backup("otp_accounts_auto_2026-09-27_02-00-00.json.aes", 7)
        )

        val pruned = BackupRunner.backupsToPrune(stored, "otp_accounts_auto.json.aes", 1)

        assertEquals(listOf("otp_accounts_auto_2026-09-27_02-00-00.json.aes", "otp_accounts_auto_2026-09-26_02-00-00.json.aes"),
            pruned.map { it.name })
    }

    @Test
    fun nothingIsPrunedBelowTheLimit() {
        val stored = listOf(backup("otp_accounts_auto_2026-09-27_02-00-00.json.aes", 7))
        assertTrue(BackupRunner.backupsToPrune(stored, "otp_accounts_auto_2026-09-28_02-00-00.json.aes", 5).isEmpty())
    }

    /** Read back the way EncryptedRestoreTask does, so automatic backups restore like manual ones. */
    @Test
    fun encryptedBackupRestoresWithTheBackupPassword() {
        val plain = """[{"secret":"JBSWY3DPEHPK3PXP","label":"test"}]"""
        val data = BackupHelper.encryptBackup("backup password", plain)

        val iter = ByteBuffer.wrap(Arrays.copyOfRange(data, 0, Constants.INT_LENGTH)).int
        val salt = Arrays.copyOfRange(data, Constants.INT_LENGTH, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH)
        val encrypted = Arrays.copyOfRange(data, Constants.INT_LENGTH + Constants.ENCRYPTION_IV_LENGTH, data.size)

        assertTrue(iter in Constants.PBKDF2_MIN_ITERATIONS..Constants.PBKDF2_MAX_ITERATIONS)

        val key = EncryptionHelper.generateSymmetricKeyPBKDF2("backup password", iter, salt)
        assertEquals(plain, String(EncryptionHelper.decrypt(key, encrypted), StandardCharsets.UTF_8))
    }

    private fun backup(name: String, modified: Long) = StoredBackup(name, modified, name)

    companion object {
        private const val NOW = 1790604309000L

        @BeforeClass
        @JvmStatic
        fun installAndroidGcm() = AndroidGcmProvider.install()
    }
}
