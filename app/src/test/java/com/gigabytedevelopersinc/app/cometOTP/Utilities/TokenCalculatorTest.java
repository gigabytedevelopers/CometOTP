package com.gigabytedevelopersinc.app.cometOTP.Utilities;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * TokenCalculator touches no Android API, so its test vectors run on the JVM in a second or two
 * rather than waiting on an emulator. These are the published vectors: if this class ever stops
 * agreeing with them, every code the app shows is wrong.
 */
public class TokenCalculatorTest {

    private static final byte[] KEY_SHA1 =
            "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEY_SHA256 =
            "12345678901234567890123456789012".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEY_SHA512 =
            "1234567890123456789012345678901234567890123456789012345678901234".getBytes(StandardCharsets.US_ASCII);

    /** RFC 6238, appendix B. */
    @Test
    public void totpMatchesRfc6238() {
        assertTotp(94287082, 46119246, 90693936, 59L);
        assertTotp(7081804, 68084774, 25091201, 1111111109L);
        assertTotp(14050471, 67062674, 99943326, 1111111111L);
        assertTotp(89005924, 91819424, 93441116, 1234567890L);
        assertTotp(69279037, 90698825, 38618901, 2000000000L);
        assertTotp(65353130, 77737706, 47863826, 20000000000L);
    }

    /** The previous code is the one before it, which is what the card shows underneath. */
    @Test
    public void totpHonoursTheOffset() {
        assertEquals(84755224, TokenCalculator.TOTP_RFC6238(KEY_SHA1,
                TokenCalculator.TOTP_DEFAULT_PERIOD, 59L, 8, TokenCalculator.HashAlgorithm.SHA1, -1));
    }

    /** RFC 4226, appendix D. */
    @Test
    public void hotpMatchesRfc4226() {
        String[] expected = {"755224", "287082", "359152", "969429", "338314",
                             "254676", "287922", "162583", "399871", "520489"};
        for (int counter = 0; counter < expected.length; counter++) {
            assertEquals("counter " + counter, expected[counter],
                    TokenCalculator.HOTP(KEY_SHA1, counter, 6, TokenCalculator.HashAlgorithm.SHA1));
        }
    }

    private void assertTotp(int sha1, int sha256, int sha512, long time) {
        assertEquals("SHA1 at " + time, sha1, TokenCalculator.TOTP_RFC6238(
                KEY_SHA1, TokenCalculator.TOTP_DEFAULT_PERIOD, time, 8, TokenCalculator.HashAlgorithm.SHA1));
        assertEquals("SHA256 at " + time, sha256, TokenCalculator.TOTP_RFC6238(
                KEY_SHA256, TokenCalculator.TOTP_DEFAULT_PERIOD, time, 8, TokenCalculator.HashAlgorithm.SHA256));
        assertEquals("SHA512 at " + time, sha512, TokenCalculator.TOTP_RFC6238(
                KEY_SHA512, TokenCalculator.TOTP_DEFAULT_PERIOD, time, 8, TokenCalculator.HashAlgorithm.SHA512));
    }
}
