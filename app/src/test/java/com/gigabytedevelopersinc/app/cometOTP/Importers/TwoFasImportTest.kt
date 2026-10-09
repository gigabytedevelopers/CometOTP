@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Backups in 2FAS Auth's schema version 4. The protected one was built outside the code under
 * test with Node 26's OpenSSL bindings: PBKDF2-SHA256 with 10,000 iterations over the password,
 * then AES-256-GCM with the tag after the ciphertext, each part base64 and the three joined with
 * colons, as 2FAS writes servicesEncrypted and reference.
 */
class TwoFasImportTest {

    private val password = "two-fas-secret"

    private fun read(json: String, password: String? = null) = TwoFasImport.read(json.toByteArray(), password)

    private fun assertFails(kind: ImportException.Kind, json: String, password: String? = null) {
        try {
            read(json, password)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    private fun assertBackup(tokens: List<ImportedToken>) {
        assertEquals(5, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!\u00de\u00ad\u00be\u00ef".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)

        // No issuer from the site: the name the user gave the service stands in.
        assertEquals("My Bank", tokens[1].issuer)
        assertEquals("alice", tokens[1].label)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(60, tokens[1].period)

        assertEquals(Entry.OTPType.HOTP, tokens[2].type)
        assertEquals(7L, tokens[2].counter)

        assertEquals(Entry.OTPType.STEAM, tokens[3].type)
        assertEquals(5, tokens[3].digits)

        // An older backup names the account "label".
        assertEquals("old-style", tokens[4].label)

        assertEquals(5, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun readsAnUnprotectedBackup() {
        assertBackup(read(PLAIN))
    }

    @Test
    fun opensAProtectedBackupWithThePassword() {
        assertBackup(read(ENCRYPTED, password))
    }

    @Test
    fun asksForThePasswordAndRejectsAWrongOne() {
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, ENCRYPTED)
        assertFails(ImportException.Kind.WRONG_PASSWORD, ENCRYPTED, "two-fas-secrets")
    }

    @Test
    fun refusesFilesThatAreNot2fasBackups() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "{\"version\":1,\"header\":{},\"db\":{}}")
        assertFails(ImportException.Kind.DAMAGED, "{\"services\":[],\"servicesEncrypted\":\"abc\"}")
        assertFails(ImportException.Kind.EMPTY, "{\"services\":[],\"schemaVersion\":4}")
    }

    companion object {
        private const val PLAIN = """{"services":[{"name":"GitHub","secret":"JBSWY3DPEHPK3PXP","updatedAt":1700000000000,"otp":{"link":"otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&issuer=GitHub","label":"bob","account":"bob","issuer":"GitHub","digits":6,"period":30,"algorithm":"SHA1","tokenType":"TOTP","source":"Link"},"order":{"position":0},"icon":{"selected":"Brand","brand":{"id":"github"},"label":{"text":"GI","backgroundColor":"Default"}},"groupId":null,"badge":{"color":"Default"}},{"name":"My Bank","secret":"JBSWY3DPEE","updatedAt":1700000000000,"otp":{"account":"alice","issuer":null,"digits":8,"period":60,"algorithm":"SHA256","tokenType":"TOTP","source":"Manual"},"order":{"position":1},"icon":{"selected":"Label","label":{"text":"MB","backgroundColor":"Blue"}},"groupId":null},{"name":"Counter","secret":"JBSWY3DPEE","updatedAt":1700000000000,"otp":{"account":"dave","issuer":"Counter","digits":6,"counter":7,"algorithm":"SHA1","tokenType":"HOTP","source":"Manual"},"order":{"position":2},"icon":{"selected":"Label","label":{"text":"CO","backgroundColor":"Default"}},"groupId":null},{"name":"Steam","secret":"JBSWY3DPEE","updatedAt":1700000000000,"otp":{"account":"gamer","issuer":"Steam","digits":5,"period":30,"algorithm":"SHA1","tokenType":"STEAM","source":"Manual"},"order":{"position":3},"icon":{"selected":"Label","label":{"text":"ST","backgroundColor":"Default"}},"groupId":null},{"name":"Legacy","secret":"JBSWY3DPEE","updatedAt":1700000000000,"otp":{"label":"old-style","issuer":"Legacy","digits":6,"period":30,"algorithm":"SHA1","tokenType":"TOTP","source":"Manual"},"order":{"position":4},"icon":{"selected":"Label","label":{"text":"LE","backgroundColor":"Default"}},"groupId":null}],"groups":[],"updatedAt":1700000000000,"schemaVersion":4,"appVersionCode":5000020,"appVersionName":"5.0.20","appOrigin":"android"}"""

        private const val ENCRYPTED = """{"services":[],"groups":[],"updatedAt":1700000000000,"schemaVersion":4,"appVersionCode":5000020,"appVersionName":"5.0.20","appOrigin":"android","servicesEncrypted":"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:WltcXV5fYGFiY2RlZmdoaQ==:AQIDBAUGBwgJCgsM","reference":"tZ85nki9sv3QhJY72BzDHy5/WAGTqxCF+E9E07qnlSfu5ZgikbSBOf7t4LsBejSMhyCNEnUMiV3+ynNkakvi2njo1Z0WPU34KcMDUcScjPOAJtrawzoSShZt5XPackeOOhjr0nebUkZm5P2G50xGZnt98ovdBnNjfLBK6KxcZ8wHzCfSOTC16KlgsEj6DBunzpXowZz1PI3bjaJVRKA/rljGW1KVXBAjxBANiubzREKWrlblE640P3WKfv+CL3q05yUIZJvxWiSUD4JNHyLIAzMKxGPgf6t1wXP7v/FEnrj5IShnKvWOb9M7qSbnJOvIXESfenCF3DwDKlVnlEWMqbHZjbhlOo//KGoeIEAJh8V9rIne7rVxSHdeuG58UB0vCOwZ2jS+WCP4y4ey+f2rLfWAkpGlzsn/PRabCc8124KUMBPk4bdjx5gKP0AWaYXZY9BDgIQ7To6VWA2XrT1KibD/1AApD4SIVvSJ9kj2LVSkIk4BJRP6xGJQHmv6O+/QDJjf1R1tMOM2nMLPJE4nJjJjhWamvDveRwrNgEehwR6PrXDmLDN4+XLDO5+qMJ7i0euwUpW2VqQqXrM3qxRU5gj92DfCH+TO46PNLy9+RxrDFCFsUQIM7Efp6PhjjuT4RBbh6/FleZEIEjy00SXTh0YuufcXk1o8y9tgaDtTPx7bUj36JKuu2+eL3AAjHRSPfny59ycMqgxqIYxE0/axbVD8UDB2FEuy6OvYSE4N6IWRSwqpuvmmOw68IMgQL9hNTZqU5FERKjzZG096gJko22jS+sIGmsYy5lqsqH9qFgBAHwIKOf54hjYs544K8cmHQ5nz5Y66XNRj3ipLsDSy35BatjLhJb7YYvbdVAymqubmyV6sMAHecjtYsW+316imtJDMP50HgPDGyZ/WzFwjyBuCSiD17HfSKFvVbnV7rAo6Gr8e3uabFMdjSm6/atB1d0LmSI5yJ7M8DViRnubqQGY6FFw5L4fTOKDbfq1DaZfClZtMMvhpgtoFgIOilhFv6AttoI6HtoioyOYuw8UmGEdA3O/fxfkcinlnCGP8PHYGO1sT64OsP5vO5mNHHLRbg60peNA0/GQI5Q132N7qUetO5IFAOvaja6o3+UpmGYcaixFEXJd/vsIC80BjhqI7PkA9qFBJ1LkX1FUZ6wHWG0C3XWgkcMRhf20THogCuo2/ddxXBwCPAV8yNQ5xwKExBTaMFRci5m/iVTUJKe6uSGCVt/Ea1AQjPuQs3ubS5FBeg2KTZaNm1TGlTIBeqYroF9Fkp2oz9qsrvBf6JRwPh4I4kHWTtpr7On8VX891VQiuvHdNMDSkiPq197kTZsciN6c4c/0en5lO4paw+YxhESzVHA6HB7G/xbVanck7hZkWkGiGwDFwqU8128Mas8Wb+9R7nQ==:WltcXV5fYGFiY2RlZmdoaQ==:oaKjpKWmp6ipqqus"}"""
    }
}
