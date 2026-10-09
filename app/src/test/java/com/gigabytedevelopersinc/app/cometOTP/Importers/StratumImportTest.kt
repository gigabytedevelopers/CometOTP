@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.apache.commons.codec.binary.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * The backups were built outside the code under test with Node 26's OpenSSL bindings, to
 * Stratum's layouts: the header, salt and IV ahead of AES-256-GCM under an Argon2id key
 * (3 passes, 64 MiB, 4 lanes), and Authenticator Pro's older header, salt and IV ahead of
 * AES-256-CBC under PBKDF2-SHA1 with 64,000 iterations.
 */
class StratumImportTest {

    private val password = "stratum-pass"

    private fun assertFails(kind: ImportException.Kind, data: ByteArray, password: String? = null) {
        try {
            StratumImport.read(data, password)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    private fun assertBackup(tokens: List<ImportedToken>) {
        assertEquals(6, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!\u00de\u00ad\u00be\u00ef".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)
        assertEquals(listOf("Work"), t.tags)

        assertEquals(Entry.OTPType.HOTP, tokens[1].type)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(9L, tokens[1].counter)
        assertEquals(emptyList<String>(), tokens[1].tags)

        assertEquals(Entry.OTPType.STEAM, tokens[2].type)
        assertEquals(5, tokens[2].digits)

        assertEquals(Entry.OTPType.MOTP, tokens[3].type)
        assertEquals("0123456789abcdef", String(tokens[3].secret!!))

        assertNull(tokens[4].type)
        assertEquals("Yandex", tokens[4].issuer)

        assertEquals(HashAlgorithm.SHA512, tokens[5].algorithm)
        assertEquals(60, tokens[5].period)
        assertEquals("", tokens[5].label)

        val converted = TokenImport.convert(tokens)
        assertEquals(5, converted.entries.size)
        assertEquals(listOf(TokenImport.SkipReason.UNSUPPORTED_TYPE), converted.skipped.map { it.reason })
    }

    @Test
    fun readsAnUnencryptedExport() {
        assertBackup(StratumImport.read(PLAIN.toByteArray(), null))
    }

    @Test
    fun opensABackupWithThePassword() {
        assertBackup(StratumImport.read(Base64.decodeBase64(STRONG), password))
    }

    @Test
    fun opensAnAuthenticatorProBackupWithThePassword() {
        assertBackup(StratumImport.read(Base64.decodeBase64(LEGACY), password))
    }

    @Test
    fun asksForThePasswordAndRejectsAWrongOne() {
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, Base64.decodeBase64(STRONG))
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, Base64.decodeBase64(LEGACY))
        assertFails(ImportException.Kind.WRONG_PASSWORD, Base64.decodeBase64(STRONG), "stratum-pas")
        assertFails(ImportException.Kind.WRONG_PASSWORD, Base64.decodeBase64(LEGACY), "stratum-pas")
    }

    @Test
    fun refusesWhatIsNotAStratumBackup() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE".toByteArray())
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "{\"services\":[]}".toByteArray())
        assertFails(ImportException.Kind.DAMAGED, Base64.decodeBase64(STRONG).copyOf(40), password)
        assertFails(ImportException.Kind.EMPTY, "{\"Authenticators\":[],\"Categories\":[]}".toByteArray())
    }

    companion object {
        private const val PLAIN = """{"Authenticators":[{"Type":2,"Icon":"github","Issuer":"GitHub","Username":"bob","Secret":"JBSWY3DPEHPK3PXP","Pin":null,"Algorithm":0,"Digits":6,"Period":30,"Counter":0,"CopyCount":3,"Ranking":0},{"Type":1,"Icon":null,"Issuer":"Counter","Username":"dave","Secret":"JBSWY3DPEE","Pin":null,"Algorithm":1,"Digits":8,"Period":30,"Counter":9,"CopyCount":0,"Ranking":1},{"Type":4,"Icon":"steam","Issuer":"Steam","Username":"gamer","Secret":"JBSWY3DPEE","Pin":null,"Algorithm":0,"Digits":5,"Period":30,"Counter":0,"CopyCount":0,"Ranking":2},{"Type":3,"Icon":null,"Issuer":"Office","Username":"vpn","Secret":"0123456789abcdef","Pin":"1234","Algorithm":0,"Digits":6,"Period":10,"Counter":0,"CopyCount":0,"Ranking":3},{"Type":5,"Icon":null,"Issuer":"Yandex","Username":"ya","Secret":"JBSWY3DPEE","Pin":"1234","Algorithm":1,"Digits":8,"Period":30,"Counter":0,"CopyCount":0,"Ranking":4},{"Type":2,"Icon":null,"Issuer":"Slow","Username":"","Secret":"JBSWY3DPEE","Pin":null,"Algorithm":2,"Digits":6,"Period":60,"Counter":0,"CopyCount":0,"Ranking":5}],"Categories":[{"Id":"c1","Name":"Work","Ranking":0}],"AuthenticatorCategories":[{"CategoryId":"c1","AuthenticatorSecret":"JBSWY3DPEHPK3PXP","Ranking":0}],"CustomIcons":[]}"""
        private const val STRONG = "QVVUSEVOVElDQVRPUlBSTwECAwQFBgcICQoLDA0ODxChoqOkpaanqKmqq6wKFNqFlx6WOhj8mrgGG5cIGOw/6L8I14TkbWsMs4wNiFp7gmJkhR+AbPW1GSyiQ1oiE0Tw3DcVrWaFj5Hrs0d5qRTjTz8V77EbmKB9RIQPZvbrJgRbR0MXTelE41JsBy913OdIR+C2uyWpb2pe32GUhaAx4VRz8rQl1AyMxOHHudDjXOadt4YvIuRWg9Mz7pVMgIXfkP6FNcvqEh+VK7oNQQES1b4n5WAgRGSGqelwTe+5G3Lz7mEwmuwxjBeXH/UH/ZZg7N/lMOxmbK6fpiQvj3+9Mn81FvaH/41m3c/rT6L60YMb0bAnZMAc1u0thImWSMc5ynAMmLj/yEO/B2WQAQwkDcSEBdI8CQY+ihxpAES/TwJleNsYfqgOwRZqBpnfrDur1PT5aOgZV0k296LsoiYJdxLdCUET1M7wMiAqqXW4HuC+WeCOzynWwRNqKStP8amghxq3zp1KTBdS5q3VLk8rmS0B7Y5oCFA1TAAd8Af//MvRI5bXkixk9+ddPwIUgvRaSmYt+x3W/YHvQHa/cWWRS2YSRoOsc1mIH5o5IdZUYgRlGPsMDnF+WTRtJLqoIEDbG6VYgn+EpfSqSh60x57oYE4tvr4mVZsKb+qvCRYx5f8ourvXbQpev/3NgOz6Eh2FWbB1SloolXGfJ1/YCBXfYhRGLXrgMIMqNlI6l3/MDaSI6azCy/AGH4WOPwMaJiODjGcPkPI/AwQpmowSZJy6KLF5pEfZnVlR1qAOIrPw8wCyX2eH/XsedxEy/lQ0G9e/aunHp4eyWaisbYoueXS0oSfxH+J91ZR9640PGg0PWNT08rGvKb+P/MioIKHzsy2+EWS6N53UBqzIHPDiJVSQPxZT90sfosOJ6GbDmZ4s7YsJpOn36222IsMMTRRPcT+OJb+9Hoj0OZEgBdJtLE9NUqZ3Zvwy+/HeeW3/3BKHUDIcWXROuXm1YY3P4IuJ1oEqIHxKeB8LXsa5W9zpOr8A6vXXfK3uqzPbJBjnbLjZK1DfkSOwfViz5H2lBEuzTY/3D+vyapjXDcOoeFsLXnUuUAKrNWgy8bCxCdnPhUhVcE7SEmP/wknm4V0/qG+V6FW921F4swGJfrsbuyaBgJg0nM4nlTo0Zl/rLGwarySZXsGu+GqCkSgRN8kxL82kGI+3UyySp1XJi4ITH6n/xHFeGdfhj86z6nrCvvYQLvfgX7ZLB4yPEgy/2H0pEGZ2inC9Dv9wNeQvsrocGHMz2LKeVduAsHObrO5upyNBS67OWQPPLDnQ+UP9XhhG2Xx3FTJPYn5lJ2pJrvvPqqhnp2sqKqavGsFWJO819a/lYdDUB2JPfb5ok1fRiaQbcpIr4jYD+soErlKgfnqfMgR31Uz7CdKfiG0dFQX4M76ZArVoW0G/3MpcQq8AF/zoi7FaLsTzjEAxZmagoqXJjoXAXjBrpacNND0++qmt21nzkztkdPEeNJfwP24v/XBbe6N8BwLuZb8GxsioXpqk8yPalTesV4wHIvRZLDUTNLQmuz+pAcSJ9CEXPcVXJnyaajzSgoFN8prin2TwAe3wl/BzHgflpQYWRl7J5/LUcPbFntIXuRN4udVSRvjRCaqaKzZF2smX/QX477T+A6KsVjT8RMrXl+Z4Gw=="
        private const val LEGACY = "QXV0aGVudGljYXRvclByb86uQhv8HTTHxdT8oO02OysHpij+gEb4TPrRJaX66229/jh5b26nfl1I5b22iUbPJekCoLKIZSc2aFqJqub23Y7+NPtD/bcBITqE1ZKqTCIbzh8QfcmpzDfTLyp6lNP4beWpm/mtca04ZRqzun8N91TuLCgl7ha2EsyLAy0Gm62HCUtWn62Bg3xJZ1kTM/odRhtvAZI7G4WsvHoVkADsZbWWjxbNxfepDUIo9A/vcQtTephxMSefvvLRTw5DmVVWbXxxpC+5WuHKAnT0WRiJZzBBDsP2wYWtaVu5G6hb68cxlM6Ozmu6crtatO4DbgQrH2WmKeyTEnUKv7xRfcOd3MqS+FrSyis32dHDy1ih4qF2ZDbFRV4RpoYggutiA1XRkiIT8GpeazN7P19orfP2EpkSPJKmVnmjN2wjqg8BIldNoNiyW31maf+WxmulSJ+2dcIk/Z4BRvMG7XrO7r1HVKmcVYtfsoxRjcHPb/rHFYuhNeTQC6BnbfIUHICLwvwGztGQfuJdCb6iS2rG2C+36339NK1r3SQNVgpZFNR9c17z1FVPUVFyKtUVqawcFoz7WFgDwAIiTB08esZinoDxwKr5hD9aoeuHLhUAqtNhwiGKJopkcxfv39iU5oQXl6/egN2tfhN6zvADd0ukgeBM9SM8aGTCBcZCeryn1w7OjWuvH4oMmYNGH7JvEEHPbc0C2GMbBz9/2CJJ6R2wniIM3HiJ64W9gNXxDq0kCjrZ7rA4L6iMA8MSHW5xr0VlmR+xutYSqbYXQT3zc+XHNy+niD84LkqXkja7dWX+YQkP/aCqR53XWpbVJEvc60Av+jK2wFVhHAQlX6582FSobdzk5nDaMC4V62cGWQbB5gMveGk8nzosiWM+jpabh3Ui2KpcDEKxprwbQAy0llDe31WudhZjVllOKqh3xusUxQkGB5GI4twxvmjfXgJ+4RvhZ1pdpE5KcnS3HtY3uqd4/2ZKg/nbZq4sQipRywdEyN779zLw50SpXTFFcpNlaYX9iq7BY+caodbqROvIynlZq06FvGFES4XwA1q6HB+NmoA5i2FdLqFkp/qwwYnaeFeRiaNtyDFGPPk+3rKpg0I4HtEA0LITnmP77WQEETKKfNEaTC7CSl0njx7lODUNhp/2ERp57sPJtVqpf4r851dRUe/3KlLpOeIAJ9anJnY06OVtYiHGGTQbzAYvEXr0vdgWDelIFR6BlW6epUsKZXf+snzhOW1EfHWo2I6nztiRk8ZPwd1xIztFXEcK8LZZsXE1QhM/umyfLGV3g1t9pXE/R+WSNkaxn0BU9WMtDCsViBG3ARjKcgvR38bEt0tW7SWaeXNhXqJK9olh/lr1ijwQfGh3HBdh4TrLU03ua37QFZXRS991s6JLRuHpnrCFZ4pbOajJPawhC/Uf017Zmn/a6IEmFVPyKfwDzeSQLJstQVT+w76vZHXhAlZd3Tv21/wGoaI1gojbY/UIV7/0qqZhYLs4JYEvuieArrt8PRiTXNoJLq9plNd5KfAXXEzb5ZRWLaFoq6Y3VSosFzT6f5RgwIEPJCBbmF6KzkseBdLS/PH9NRvjfJWmIjCCDI/gm+K/iscaBIA1lMtE6K1rBYPfYOSi/wFiAF0ZpLSmOa8y7sEISfzidey00dAx+JO2EiF+p5TWcEJ86BI="
    }
}
