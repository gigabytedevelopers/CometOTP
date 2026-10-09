@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * The password-protected exports were built outside the code under test with Node 26's OpenSSL
 * bindings, to Bitwarden's layout: PBKDF2-SHA256 over the base64 salt text, or Argon2id over its
 * SHA-256, stretched by HKDF into an encryption and a MAC key; AES-256-CBC with an HMAC-SHA256
 * over IV and ciphertext, written as "2.iv|data|mac".
 */
class BitwardenImportTest {

    private val password = "export-password"

    private fun read(text: String, password: String? = null) = BitwardenImport.read(text.toByteArray(), password)

    private fun assertFails(kind: ImportException.Kind, text: String, password: String? = null) {
        try {
            read(text, password)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    private fun assertVault(tokens: List<ImportedToken>) {
        // Items with no key, and items that are not logins, are not accounts and are not listed.
        assertEquals(5, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!\u00de\u00ad\u00be\u00ef".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)

        // A bare secret: the item's name and username name the account.
        assertEquals("My Bank", tokens[1].issuer)
        assertEquals("alice", tokens[1].label)
        assertArrayEquals("Hello!".toByteArray(), tokens[1].secret)

        assertEquals(Entry.OTPType.STEAM, tokens[2].type)
        assertEquals(5, tokens[2].digits)
        assertEquals("Steam", tokens[2].issuer)

        assertArrayEquals("Hello!".toByteArray(), tokens[3].secret)

        // A link that names no issuer takes the item's.
        assertEquals("Fallback", tokens[4].issuer)
        assertEquals("dan", tokens[4].label)
        assertEquals(HashAlgorithm.SHA256, tokens[4].algorithm)
        assertEquals(8, tokens[4].digits)

        assertEquals(5, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun readsAJsonExport() {
        assertVault(read(PLAIN))
    }

    @Test
    fun readsACsvExport() {
        val tokens = read(CSV)
        assertEquals(2, tokens.size)
        assertEquals("GitHub", tokens[0].issuer)
        assertEquals("bob", tokens[0].label)
        assertEquals("My \"Bank\"", tokens[1].issuer)
        assertEquals("alice", tokens[1].label)
        assertArrayEquals("Hello!".toByteArray(), tokens[1].secret)
    }

    @Test
    fun opensAPasswordProtectedExport() {
        assertVault(read(PROTECTED_PBKDF2, password))
        assertVault(read(PROTECTED_ARGON2, password))
    }

    @Test
    fun asksForThePasswordAndRejectsAWrongOne() {
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, PROTECTED_PBKDF2)
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, PROTECTED_ARGON2)
        assertFails(ImportException.Kind.WRONG_PASSWORD, PROTECTED_PBKDF2, "export-passwor")
        assertFails(ImportException.Kind.WRONG_PASSWORD, PROTECTED_ARGON2, "export-passwor")
    }

    @Test
    fun cannotOpenAnExportEncryptedWithTheAccountKey() {
        assertFails(ImportException.Kind.ENCRYPTION_UNSUPPORTED, ACCOUNT_ENCRYPTED, password)
    }

    @Test
    fun refusesWhatIsNotABitwardenExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "{\"services\":[]}")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "name,secret\nGitHub,JBSWY3DPEE\n")
        assertFails(ImportException.Kind.EMPTY, "{\"encrypted\":false,\"items\":[{\"type\":1,\"name\":\"x\",\"login\":{\"totp\":null}}]}")
        assertFails(ImportException.Kind.EMPTY, "folder,name,login_totp\n,x,\n")
    }

    @Test
    fun parsesCsvAsPasswordManagersWriteIt() {
        val rows = Csv.parse("a,b,c\r\n1,\"two, with comma\",\"three \"\"quoted\"\"\"\n\n4,\"line\nbreak\",6")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "two, with comma", "three \"quoted\""), listOf("4", "line\nbreak", "6")), rows)
        assertEquals(listOf(listOf("x")), Csv.parse("x"))
        assertEquals(emptyList<List<String>>(), Csv.parse(""))
    }

    companion object {
        private const val PLAIN = """{"encrypted":false,"folders":[{"id":"f","name":"Work"}],"items":[{"id":"1","organizationId":null,"folderId":null,"type":1,"reprompt":0,"name":"GitHub","notes":null,"favorite":false,"login":{"uris":[{"match":null,"uri":"https://github.com"}],"username":"bob","password":"hunter2","totp":"otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&digits=6&period=30","fido2Credentials":[]},"collectionIds":null},{"id":"2","type":1,"name":"My Bank","login":{"username":"alice","password":"x","totp":"JBSWY3DPEE"}},{"id":"3","type":1,"name":"Steam","login":{"username":"gamer","totp":"steam://JBSWY3DPEE"}},{"id":"4","type":1,"name":"No code","login":{"username":"nobody","totp":null}},{"id":"5","type":2,"name":"A note","secureNote":{"type":0}},{"id":"6","type":1,"name":"Spaced","login":{"username":"carol","totp":"jbsw y3dp ee"}},{"id":"7","type":1,"name":"Fallback","login":{"username":"dan","totp":"otpauth://totp/dan?secret=JBSWY3DPEE&algorithm=SHA256&digits=8"}}]}"""

        private val CSV = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\nWork,,login,GitHub,\"a note, with a comma\",,0,https://github.com,bob,hunter2,otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&issuer=GitHub\n,,login,\"My \"\"Bank\"\"\",\"line one\nline two\",,0,,alice,x,JBSWY3DPEE\n,,login,No code,,,0,,nobody,x,\n,,note,A note,hello,,0,,,,\n"

        private const val PROTECTED_PBKDF2 = """{"encrypted":true,"passwordProtected":true,"salt":"8cffI3JEClLTr2kEnP+Y1g==","kdfType":0,"kdfIterations":600000,"kdfMemory":null,"kdfParallelism":null,"encKeyValidation_DO_NOT_EDIT":"2.DuiLLJAxMmd/q9jyyMdrZg==|paoLtP3VbwtiU9oXhglfTVgow2y5/SxHC5lIeUve1mmFnWEAe4vtJqzXR+OLBDRL|PBPQ7FYc2AiJwqQ3TmaKC76Nb45QVG0wlKtJ6JpWO+I=","data":"2.M3SA4QJOcbuWif1qRbyRKg==|AOG3KsZDTxmtGFoGbWVNfAQ5x7FIdsxRbPau3iCj3ahsErDTsI2Ay4NQkSwi9HfUam0CciEoRKJzrl2KsmayH1MD08IFeYGbfaWAlqemVPKqFpPp8+ai+Og51KK44X5HRvgN8JA+uQQNOTMh3wAuQLjwUuLW6MrL8+iGU2oY7wLYY44jgloNRyR73Et+kSUg+jWvoLAMrgf94TOPjrItSzOwNhE+meF80J3M156lJUNbBp2Jk6qdwUP0Auhf1k//R27ST9JLftP5EdkzGeQUcxrceGjfjNZwqjCUnCd2YhXpxLA6zOlRtSbTyyM94no3PclhOPCLCGzbUzloKztU8X9nbibfV3OrRBPZl0CRc64AadIRIooQB0H4zq4oYXv3N7UvRi0cXVqCw3dVLJSfI4+JgZ30ZQ4/QYKG00wNhGD77hY6LGdxNoJAHwltmJlmpK9NzpPiiCqpyH+7jbUqwuGRGNGfZv63V3cTN8G5fxkBl3hMTmjupoqPp4BTVr0WCx2Ls+ziXSXmUjYTrSGlPkp4Pkn/pVmeWx9qDRVKM5P6Kpv4itI6Z681662MvbuNsG+iSsqWutJU07E2dQOoC4nff4hAygqFyotB/bmwSBAN5qIo4/cfJPPpSEQiJfcNldX9vhLs/LgwxqRj6i7FhxZsmJ975hBAxKh95FKfu1fQLCE2T0P7vayU9hhm4mNMQG1qUE2liAcI5n6ygZAB2ZYgeJ0Tf4pYSvUlN6Wy9mqpU6pRjpcDgF0vb7x5iKeydp2T6F0GEepHtrFHb5x3doz0LAtj3S7A+c+3UBR2xDvGtmr+io/aQOdVLUb/vkPFwDb5ZyNSFRmD+XFh7T+q9InnokwgJFGooAf97kocot+DeEzuyLatAOtxJcKMTI77IJOJ+8vQBJXrF5aGxA6C4gB1ZKTutAABONIGlr/ShXwCO0kpwbVGYVlP+ZdW0KmGD9X4A7tN4Hy1y8GJMod27NwOCS6JiZ0Yq4JTAaIz5Y3r0HT+BLfqSfgZFuw6yB5kbwd0xULOEG4Tbqy7mCYna+TTYnmq+HNs87HSjW7c/JWyoPw+RKNKXSf6EXWihaon0XMdpFLn1k0IMpUKaiBkyFWpor/xwLw11/0ytdDDBpvXujsEppnVrjg4RbK7T5+c9BWBz8DJ+sKMfA3sMinz9kv3s8qjgYMHoaFZ/KADESlHfq38hiyQ8Ol0ExDs1yxqpKwvXjHCfAaiEICAa532xgOhB3fZoLRCtVgdRbM2XBjc9ZLqUPZD+OJqtPQBoKUWRU04HdwFgLhoxnEOFWg+ww==|Le5um44kirdAGvp6Jc9wTyDlKJDLol1ySki8MTM0erg="}"""

        private const val PROTECTED_ARGON2 = """{"encrypted":true,"passwordProtected":true,"salt":"8cffI3JEClLTr2kEnP+Y1g==","kdfType":1,"kdfIterations":3,"kdfMemory":16,"kdfParallelism":4,"encKeyValidation_DO_NOT_EDIT":"2.agzKD/BFbADBq2IhBi3F9Q==|WcNfX9fKJFJS59OALJofYpt16OJYnwC8E2qJQEX1DtgJNSdOHj+cDV/FTJW+iO6I|ndywCOvEF5D8ha6pL1sYlbZ8s4uTDhyyN80tX8qHB90=","data":"2.Af3Cih2aP3lAcFqWNPgI/A==|rAPuES8+HSg5cbbxwAX+0LZt1nX4TmC72IM8b31gutmwhYpynj9Sp2PGQBF7ZsD1oe9T4LCVAzvFviOcSu2BJCdfhfo/7IYnjuS9Y5zCYj3UqSB+dHNW2ceVV8VCvztqmLtOW+IM70KzeE6zLUlkLzc6MLKsfLzBfYF5BWq9kYkGXRjNB3LGymXr08QTNS4FwfEkZ6P82gZYmAw1+OYzvmb0MQqUQh6QoI4mqPX+BdcshL6TIuS1H08GlfC1YuiVTkZqZxoupnZTEAJcOAvY3YazagNz74fBUUEQbwJYcmP+YX1ACE2eQr/Fp/Tpg/1l8mhu+edgaJceCn8hYcYAPRyFp8r4Io1zbYn5GgVtMb4uXdoUgZ+TABEbTdiAzLrB0LF1stesrKpvhgvcpXBxIpL3En2O4rgBoAVSJDfa30yFDobhekCMoUy5ex3u+mvMZ0ON4jEapxJ7+OWVMtNLq/tX1n5Wbmn65wf3XNAT9Z9p9m9GnxlKoTC/xKiFxVNceVdlZLenUMVxtFvGLaqSXrIkz/LuYcNdmfeFEalqT7bXmClpBVoVqEvBZGzvTEkjE9mUwAnhVLH7WKQHyarShI7UUAxfsDdBvrb57b2oCzNHTkg7++IKZ+K1zeNhXh/a/EwTrdwefwzqf6V5M0A5DYYpObpUuL4YaDgB3PvaG7qCWL0dsCH2lDGkr10Z7mBdbBpPE+U3wiksLXFD0SHZAhxyY4MLcG5KL/XJxp/9WPZg2+Ns/3/8JJAl6GSO5fbqbGuceudS6kTKKdEXhYWptJbAvDuk6e+ypJaQin2LJc371RRy5+FZssrqjux9iG28vvIXpDckZkQBdZpZ4DUmWMyMxjo7ZYFiF0J46BFMFq7vSo6FlKiwGIIYLFrm3DE3MKkOt/pjnIYaRmzMFrkA+vv5VsEKMJcgwNwhgzaDiHhD5zz4VpGVx2nTwVBdki9Kn4lRhWVxLHxeLdeYfRyzJi5ij6jTmowKr4pkSc6NFWVovdowrj9SVAKyyzMrycXBOiZ2TdyH5hqSZ9Nqob/JVMDtil4Ijb5q0Faea9hIQ3kZPkVkr6Cu13flpyuZ4sx+ONF/505MHS4s6YNChEDndG/mJjsbUUA6nQ0fXB0C8z/k5zrXVBg+mTXq77INs6LansPfpDTP2qLRtztiZPBdT5LWpyOD8UX/oXvdGXavqUajno38/0J5HAeX7PRHyHGaNA4N9jJ39lTm8YG+kZBF3WfKpKAPuJMXYA2rbeU4lXT7ivDBH3TOshN1TxSSellifzlolxap/DpZyXB8SGQsUQ==|jqmCX4CM+xuY9pFo0mqkkBSqysb/ZAtlpCHfJxBTldg="}"""

        private const val ACCOUNT_ENCRYPTED = """{"encrypted":true,"encKeyValidation_DO_NOT_EDIT":"2.abc|def|ghi","data":"2.abc|def|ghi"}"""
    }
}
