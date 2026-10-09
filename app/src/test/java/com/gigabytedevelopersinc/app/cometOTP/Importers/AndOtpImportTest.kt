@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.apache.commons.codec.binary.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * The protected backups were built outside the code under test with Node 26's OpenSSL bindings,
 * to andOTP's two layouts: iterations, salt and IV ahead of AES-256-GCM under a PBKDF2-SHA1 key,
 * and the older IV-only layout under the SHA-256 of the password.
 */
class AndOtpImportTest {

    private val password = "andotp-pass"

    private fun assertFails(kind: ImportException.Kind, data: ByteArray, password: String? = null) {
        try {
            AndOtpImport.read(data, password)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    private fun assertBackup(tokens: List<ImportedToken>) {
        assertEquals(4, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!\u00de\u00ad\u00be\u00ef".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("Example", t.issuer)
        assertEquals("alice@google.com", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)
        assertEquals(listOf("work"), t.tags)

        assertEquals(Entry.OTPType.HOTP, tokens[1].type)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(12L, tokens[1].counter)

        assertEquals(Entry.OTPType.STEAM, tokens[2].type)
        assertEquals(5, tokens[2].digits)

        // From before issuers were stored apart: the label carries both.
        assertEquals("Legacy", tokens[3].issuer)
        assertEquals("no-issuer", tokens[3].label)

        val entries = TokenImport.convert(tokens).entries
        assertEquals(4, entries.size)
        assertEquals(listOf("work"), entries[0].tags)
    }

    @Test
    fun readsAPlainTextBackup() {
        assertBackup(AndOtpImport.read(PLAIN.toByteArray(), null))
    }

    @Test
    fun opensAProtectedBackupInEitherLayout() {
        assertBackup(AndOtpImport.read(Base64.decodeBase64(ENCRYPTED), password))
        assertBackup(AndOtpImport.read(Base64.decodeBase64(ENCRYPTED_OLD), password))
    }

    @Test
    fun asksForThePasswordAndRejectsAWrongOne() {
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, Base64.decodeBase64(ENCRYPTED))
        assertFails(ImportException.Kind.WRONG_PASSWORD, Base64.decodeBase64(ENCRYPTED), "andotp-pas")
        assertFails(ImportException.Kind.WRONG_PASSWORD, Base64.decodeBase64(ENCRYPTED_OLD), "andotp-pas")
    }

    @Test
    fun refusesWhatIsNotAnAndOtpBackup() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "{\"services\":[]}".toByteArray())
        assertFails(ImportException.Kind.EMPTY, "[]".toByteArray())
        // Too short to be a protected backup in either layout.
        assertFails(ImportException.Kind.WRONG_PASSWORD, ByteArray(20), password)
    }

    companion object {
        private const val PLAIN = """[{"secret":"JBSWY3DPEHPK3PXP","issuer":"Example","label":"alice@google.com","digits":6,"type":"TOTP","algorithm":"SHA1","thumbnail":"Default","last_used":0,"used_frequency":0,"period":30,"tags":["work"]},{"secret":"JBSWY3DPEE","issuer":"Counter","label":"dave","digits":8,"type":"HOTP","algorithm":"SHA256","thumbnail":"Default","last_used":0,"used_frequency":0,"counter":12,"tags":[]},{"secret":"JBSWY3DPEE","issuer":"Steam","label":"gamer","digits":5,"type":"STEAM","algorithm":"SHA1","thumbnail":"Steam","last_used":0,"used_frequency":0,"period":30,"tags":[]},{"secret":"JBSWY3DPEE","label":"Legacy:no-issuer","digits":6,"type":"TOTP","algorithm":"SHA1","period":30,"tags":[]}]"""
        private const val ENCRYPTED = "AAJJ8AECAwQFBgcICQoLDKGio6SlpqeoqaqrrKMpJ8i5tKDBLsg7wbTxkU07D89fpW9BaXPjPVV6Kla+qbMx/+K0ng4F3oZsl9eHgw86wqcvTp5H+/dNFb6TLgXp6qBGsDpUiKrWS1B5FYW9YSIi5KNAKhpIWO2uInWfZCVgfIOXKLz8boPK65hH//MWA//VXPGKzxTgvnY0AGk4eC/XxuLhM84Z6gf2FMCXEyZT2qG51UETSJ9xc/j1Hw2xe2eEsr2RJjSGayh4QG5Q07rbavTfgboxfENuPBChflDNVabsqLrhlCKnZbMBzRjb92PRIvGCyOAbiArXmhIi07DteDm/TVU8u6Uvmz9rPyXFSmPiWCLTuklWJbs104BqyXS2lFqpfhbu+Jjc2jJR1tNh/cfxHEcEtQeBdyYq7g4UkrJ5MEcM5Jg4mIP3qfbq6Nku6waImbF0A+6XC4D2t/StHvD3gwIJAVtR7D3eYlvJLy7irNCZnY05+D5a/x1UenyVIjWEGar9wvQCEw99wJV+qxLz1hcKXzBqTURom3zIsGkwpQPiYq6MNv2BQhGUUmPg+zNKI1vR73tBqVibT2A33JEX3z/oGy5PEHbMPn/YN+mv9iI7mF/qw1G/mywBmXm2Oeh7S0zM6nSPWzWKMvZC/EU5TmH3d6AvYb17jOlBBXqhrAfw8kGWYb69A/qxSXmMCO/ZAtKA4llmb1wLnAqQd2MLanZintxpOU1GB2iCVispq/QCRnqX+IuTGSGMXj0KtttU3fGNE3yZeMElxG1p4H5bTv+gIO8ufpEMHlcJfpg8W4UBEferxIP/tcOb2+5AKA2lYhYGbahT4yVbmVwItDzpNnyaK7qIHz4yqy87/sO0JBTXUIdZmsyAFuIgxcyoPXAv5AtiKoibBEE83XwVPwEvDkq98nIURvOjV1O8IRxJQVpgOwvaixp4F7yAJPTdwHSjXA=="
        private const val ENCRYPTED_OLD = "oaKjpKWmp6ipqqusWkBP6BHtuz8Ke7TKzVgzlES/XfP8bBOab44cFSb1F3AK0PwOi2v4uO4bVi9BT2JHKcxe94VWHlv0RHCE5/giL3PyhJKoJCUfj9MHVd+czwj/zk0jwFj7sv7KiepFPa6KmS5fM0Hut5UlJq0TdwdanddBw018A3oz4249QcxrZ6fif1zRdrX8RJ7BvfbM31vnyE+0Cl1jp3uObokvGFSHzD9u5/1StNZ5HZDST3DAcA7upqqeiVrL0WC7TSOSJqTK/kElk1rN/FdNk6x26yMSiRUstAr5YxGylOegOVm1AoSpkedmhC9Akrz00Y4qO9jTan+mZWG/EtHQ2EbsihOSeSDM8gjfmNpt4cvVSIal1e5nUEj110erl7Z5u/vu+2vfgpGi87anPTtW0wswSNOKM76AQ5IqHhD/Zq5QTUFOhBs/nmPuSRqGo+DvO/f97KssdHhlz2YlT306J9oyowo6eEX1UEv+Awvyr2k1fPeNnoMkP6QyVqVIyZO6IvJkFkQsgq4EeU3edyhbFzA3cQUmowR06up+BofgvlImzee9/AarS/fnwl6vk+MXjevio3Tl6xfg/21FVrEMHivCfLSw3cbqKEgkjtRLcsutduNK0z/4jDfb8pkmaZPD3rW3dYZ5w3z1u+Du1vK7mP2o6RwAHCLvdoTVQHArMR6nBgZgTy/h8C9sW1a9xoVEZiMRezb2FzFPgMDy868e9ELgrqY2xaPVCFJ4JyJreMi8vQeNhVKCr2gSHNRDJ9DCxWIY+0pKmU0Gai+GHhUsv+S+PgYUEag7rdSsLkL8L6spwn+DpWmz0H8W+BJ11M25Pa7KAky9X81AYa4TrXhRXCV4ZXgHDB/7q6csvHIrJQLBfrXx0j3824JFLnXVLMAqqws+0L+JjQ99p27WFBRC3oIY0jSgRbPXE0SBb0Qu"
    }
}
