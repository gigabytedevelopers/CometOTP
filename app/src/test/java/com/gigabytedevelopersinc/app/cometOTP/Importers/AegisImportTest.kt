@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/**
 * The vaults here follow Aegis's vault format (version 1 file, version 3 database). The
 * encrypted one was built outside the code under test, with Node 26's OpenSSL bindings: scrypt
 * (N=32768, r=8, p=1) over the password derives the slot key, which wraps the master key with
 * AES-256-GCM, which in turn encrypts the database; the GCM tags sit apart from the ciphertext,
 * as Aegis writes them. A biometric slot, which no password can open, comes first.
 */
class AegisImportTest {

    private val password = "correct horse battery staple"

    private fun read(json: String, password: String? = null) = AegisImport.read(json.toByteArray(), password)

    private fun assertFails(kind: ImportException.Kind, json: String, password: String? = null) {
        try {
            read(json, password)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    private fun assertVault(tokens: List<ImportedToken>) {
        assertEquals(7, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!\u00de\u00ad\u00be\u00ef".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("Example", t.issuer)
        assertEquals("alice@google.com", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)
        assertEquals(6, t.digits)
        assertEquals(30, t.period)

        assertEquals(Entry.OTPType.HOTP, tokens[1].type)
        assertEquals(HashAlgorithm.SHA256, tokens[1].algorithm)
        assertEquals(8, tokens[1].digits)
        assertEquals(42L, tokens[1].counter)

        assertEquals(Entry.OTPType.STEAM, tokens[2].type)
        assertEquals(5, tokens[2].digits)

        assertEquals(HashAlgorithm.SHA512, tokens[3].algorithm)
        assertEquals(60, tokens[3].period)

        assertEquals(Entry.OTPType.MOTP, tokens[4].type)
        assertEquals("0123456789abcdef", String(tokens[4].secret!!))

        // Yandex is a type CometOTP does not generate; a secret that is not base32 is reported too.
        assertNull(tokens[5].type)
        assertEquals("Yandex", tokens[5].issuer)
        assertNull(tokens[6].secret)
        assertEquals("Broken", tokens[6].issuer)

        val converted = TokenImport.convert(tokens)
        assertEquals(5, converted.entries.size)
        assertEquals(listOf(TokenImport.SkipReason.UNSUPPORTED_TYPE, TokenImport.SkipReason.INVALID_SECRET),
                converted.skipped.map { it.reason })
    }

    @Test
    fun readsAnUnencryptedVault() {
        assertVault(read(PLAIN))
    }

    @Test
    fun opensAnEncryptedVaultWithThePassword() {
        assertVault(read(ENCRYPTED, password))
    }

    @Test
    fun asksForThePasswordAndRejectsAWrongOne() {
        assertFails(ImportException.Kind.PASSWORD_REQUIRED, ENCRYPTED)
        assertFails(ImportException.Kind.WRONG_PASSWORD, ENCRYPTED, "correct horse battery stapler")
        assertFails(ImportException.Kind.WRONG_PASSWORD, ENCRYPTED, "")
    }

    @Test
    fun cannotOpenAVaultWithOnlyABiometricSlot() {
        assertFails(ImportException.Kind.ENCRYPTION_UNSUPPORTED, ONLY_BIOMETRIC, password)
    }

    @Test
    fun refusesFilesThatAreNotAegisVaults() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "{\"services\":[]}")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "[]")
        assertFails(ImportException.Kind.DAMAGED, "{\"version\":1,\"header\":{},\"db\":{\"version\":3}}")
        assertFails(ImportException.Kind.EMPTY, "{\"version\":1,\"header\":{},\"db\":{\"version\":3,\"entries\":[]}}")
    }

    companion object {
        private const val PLAIN = """{"version":1,"header":{"slots":null,"params":null},"db":{"version":3,"entries":[{"type":"totp","uuid":"1","name":"alice@google.com","issuer":"Example","note":"","favorite":false,"icon":null,"info":{"secret":"JBSWY3DPEHPK3PXP","algo":"SHA1","digits":6,"period":30}},{"type":"hotp","uuid":"2","name":"bob","issuer":"Counter","note":"","favorite":true,"icon":null,"info":{"secret":"JBSWY3DPEE","algo":"SHA256","digits":8,"counter":42}},{"type":"steam","uuid":"3","name":"gamer","issuer":"Steam","note":"","favorite":false,"icon":null,"info":{"secret":"JBSWY3DPEE","algo":"SHA1","digits":5,"period":30}},{"type":"totp","uuid":"4","name":"carol","issuer":"Slow","note":"","favorite":false,"icon":null,"info":{"secret":"JBSWY3DPEE","algo":"SHA512","digits":6,"period":60}},{"type":"motp","uuid":"5","name":"vpn","issuer":"Office","note":"","favorite":false,"icon":null,"info":{"secret":"0123456789abcdef","algo":"MD5","digits":6,"period":10,"pin":"1234"}},{"type":"yandex","uuid":"6","name":"ya","issuer":"Yandex","note":"","favorite":false,"icon":null,"info":{"secret":"JBSWY3DPEE","algo":"SHA256","digits":8,"period":30,"pin":"1234"}},{"type":"totp","uuid":"7","name":"bad","issuer":"Broken","note":"","favorite":false,"icon":null,"info":{"secret":"not base32!","algo":"SHA1","digits":6,"period":30}}],"groups":[]}}"""

        private const val ENCRYPTED = """{"version":1,"header":{"slots":[{"type":2,"uuid":"bio","key":"40e1de02f9d6cabf2035c3316186830a050e904e4d3f8be4086220b9a640aad6","key_params":{"nonce":"b1b2b3b4b5b6b7b8b9babbbc","tag":"26a2ca74eac62e11b240bc0d01306ea0"}},{"type":1,"uuid":"pw","key":"40108e4d07c66134fcc7864d65935189de71adc83e273730fbbea0cc05724cb4","key_params":{"nonce":"a1a2a3a4a5a6a7a8a9aaabac","tag":"a14105456e05c5dfa15f5d6dbbbe4c5e"},"n":32768,"r":8,"p":1,"salt":"0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0","repaired":true,"is_backup":false}],"params":{"nonce":"c1c2c3c4c5c6c7c8c9cacbcc","tag":"a6b46ea02f88af06de4b77a17f30fba9"}},"db":"DmbflS1m8DGwCaBI0/RVbcbJLD73zL0WR3bE6FQTyk3s+e0M1+oNoJJm++yOZv/83+vznP2uKuIi1jqLViSWRr4nz15DU8nRa9gV4nNygTTugE0fChnRUDr9JT3YdbaOvLCliYD9/RP89ZNXnt/S7k1h2EhsivW1eXn8CKqTESYaZpWb6qbVob8DIOxCGR/9gHDtT9jQbVtT5/U2Knjlv9sj19Z439evIOCcmiUR99IUaLk9xs3ds+6kmMMpB01vocYQ6jmWdPY5phaP8dDiaPPecCSHcHfz3P9FVI4CGvtu6T5hToe7F6Ji1S28NNUIA2eoteX5Eam3YcE3++f5NRPsKzWF7/D3uvU1Std6qDy6G3NIyVupi90vBxXa4asp6q2o9tpjEMAV99tY6PWV76/E37/ntMxzyRUHnur324mrKDa2Har1YDH7DxMZvgArNKF72e2oUEUb8+ev2CGLvYBv6KIzvdyACMkn2jjhzL81WxEc9x4czWXqs2YK8yeNTzmR+eAV5AXUaV22yx+Uu4wCavJfcenr/L+/7P6NU5n55KCPyy0IEQQhZeTcavzQ8TWHWo3j8gl0sd/fk/2s/vxF7IpWMpTxRbHrNQP76H6FZMs9UHlJQ218bBVwABNDrent4IzCIW893H1wrcFf+h0XRK9/ijC3if5Jxw5iWhQS4tynA/6W7c/ncGr2j8jSshCTNvaS/WOeqXvYmuru3yaang9c1Q4jLjSE3c1qw+uhCnWhYONA02aQ90XRY2PuClOKt7Tv8ML+Bw3l8c+8buIzsm1JO8SI9u9AVCDFDJKW/thRkDajH6dOsY3funVjWoBMiCWL4C/m6HAKEqfSYN7u4MG5bD3XGZiOHEJBfv3G2lETKTwKvtf0nkl2SjLvucIKNZAzGU9ksnfe2/DMDNIgacpCKf0yWqLRv8pkAhw8O7S6Ao/GIeAaJA32dYNFdd/Vx/mZYKKc9tGF03kgfvzs98Tnu2InJCeSgRO4DzlxPlPWyhQZeD9X/lRF8JDX+KaBJJouTSTPq8q9ikNJOwrlg9JErO34PYFwa6ji+td7XWvM8eGhPp/Y11H7264v3H3dPeN9EqyYY8GntUuKL+mhJNztbVSo57icPf45u/PHLcdR1q4PTZrcjDCN4SPIxxFkEhUeMVsXbJVvOBdYUyLnXCJgI2czJ6dLFAEojeiuzJudHjHz4oxzLzKUQNrl5tpYNxD0+IlTKkJgbfLws1e6xrf+Jsy5kx2jfQV3193eozy0YsFolNsJ4GzuqD3kPMclglcEZIZWHRsnTDUJssf0qpocjs8AMgDc4mGwQgF1f2xuUFrt7I5On1lm5uqQJy5mUq0OTEipAQ08D7PgfAuiMTwuJgUCINVDkXjqWReng14eJfYP4yWSO0GFKkspGHl9Mc8CDbmrr+Ffaj6VTtM7sMHqpPMxRcuvcRAr1Y50utcYyMdKbNw9XFEPoJn1Yfoieb5i3dViCZOes5t3Xgg9lZbOd8SqKRyFVFXZZn8oPR2oO31MKHG+ZJ3NgqpW6N91kJhaGLD8VUsKYrF68Uhng75jWLP2pU5VYJxDEysLJFei8c9DJB4l6Qa3qcMbXXiE78EvJe4E1EpI3D++gUc03Rn+pU/W6DEvItvDDsocuyV2Js4zr6cMXswoek4+6xwoDsID"}"""

        private const val ONLY_BIOMETRIC = """{"version":1,"header":{"slots":[{"type":2,"uuid":"bio","key":"40e1de02f9d6cabf2035c3316186830a050e904e4d3f8be4086220b9a640aad6","key_params":{"nonce":"b1b2b3b4b5b6b7b8b9babbbc","tag":"26a2ca74eac62e11b240bc0d01306ea0"}}],"params":{"nonce":"c1c2c3c4c5c6c7c8c9cacbcc","tag":"a6b46ea02f88af06de4b77a17f30fba9"}},"db":"DmbflS1m8DGwCaBI0/RVbcbJLD73zL0WR3bE6FQTyk3s+e0M1+oNoJJm++yOZv/83+vznP2uKuIi1jqLViSWRr4nz15DU8nRa9gV4nNygTTugE0fChnRUDr9JT3YdbaOvLCliYD9/RP89ZNXnt/S7k1h2EhsivW1eXn8CKqTESYaZpWb6qbVob8DIOxCGR/9gHDtT9jQbVtT5/U2Knjlv9sj19Z439evIOCcmiUR99IUaLk9xs3ds+6kmMMpB01vocYQ6jmWdPY5phaP8dDiaPPecCSHcHfz3P9FVI4CGvtu6T5hToe7F6Ji1S28NNUIA2eoteX5Eam3YcE3++f5NRPsKzWF7/D3uvU1Std6qDy6G3NIyVupi90vBxXa4asp6q2o9tpjEMAV99tY6PWV76/E37/ntMxzyRUHnur324mrKDa2Har1YDH7DxMZvgArNKF72e2oUEUb8+ev2CGLvYBv6KIzvdyACMkn2jjhzL81WxEc9x4czWXqs2YK8yeNTzmR+eAV5AXUaV22yx+Uu4wCavJfcenr/L+/7P6NU5n55KCPyy0IEQQhZeTcavzQ8TWHWo3j8gl0sd/fk/2s/vxF7IpWMpTxRbHrNQP76H6FZMs9UHlJQ218bBVwABNDrent4IzCIW893H1wrcFf+h0XRK9/ijC3if5Jxw5iWhQS4tynA/6W7c/ncGr2j8jSshCTNvaS/WOeqXvYmuru3yaang9c1Q4jLjSE3c1qw+uhCnWhYONA02aQ90XRY2PuClOKt7Tv8ML+Bw3l8c+8buIzsm1JO8SI9u9AVCDFDJKW/thRkDajH6dOsY3funVjWoBMiCWL4C/m6HAKEqfSYN7u4MG5bD3XGZiOHEJBfv3G2lETKTwKvtf0nkl2SjLvucIKNZAzGU9ksnfe2/DMDNIgacpCKf0yWqLRv8pkAhw8O7S6Ao/GIeAaJA32dYNFdd/Vx/mZYKKc9tGF03kgfvzs98Tnu2InJCeSgRO4DzlxPlPWyhQZeD9X/lRF8JDX+KaBJJouTSTPq8q9ikNJOwrlg9JErO34PYFwa6ji+td7XWvM8eGhPp/Y11H7264v3H3dPeN9EqyYY8GntUuKL+mhJNztbVSo57icPf45u/PHLcdR1q4PTZrcjDCN4SPIxxFkEhUeMVsXbJVvOBdYUyLnXCJgI2czJ6dLFAEojeiuzJudHjHz4oxzLzKUQNrl5tpYNxD0+IlTKkJgbfLws1e6xrf+Jsy5kx2jfQV3193eozy0YsFolNsJ4GzuqD3kPMclglcEZIZWHRsnTDUJssf0qpocjs8AMgDc4mGwQgF1f2xuUFrt7I5On1lm5uqQJy5mUq0OTEipAQ08D7PgfAuiMTwuJgUCINVDkXjqWReng14eJfYP4yWSO0GFKkspGHl9Mc8CDbmrr+Ffaj6VTtM7sMHqpPMxRcuvcRAr1Y50utcYyMdKbNw9XFEPoJn1Yfoieb5i3dViCZOes5t3Xgg9lZbOd8SqKRyFVFXZZn8oPR2oO31MKHG+ZJ3NgqpW6N91kJhaGLD8VUsKYrF68Uhng75jWLP2pU5VYJxDEysLJFei8c9DJB4l6Qa3qcMbXXiE78EvJe4E1EpI3D++gUc03Rn+pU/W6DEvItvDDsocuyV2Js4zr6cMXswoek4+6xwoDsID"}"""
    }
}
