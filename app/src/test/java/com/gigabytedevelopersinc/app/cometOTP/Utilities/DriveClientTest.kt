@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class DriveClientTest {

    @Test
    fun quotesAndBackslashesAreEscapedInQueries() {
        assertEquals("CometOTP Backups", DriveClient.escapeQuery("CometOTP Backups"))
        assertEquals("Emmanuel\\'s backups", DriveClient.escapeQuery("Emmanuel's backups"))
        assertEquals("a\\\\b", DriveClient.escapeQuery("a\\b"))
        // The backslash is escaped first, so an escaped quote is not escaped twice.
        assertEquals("\\\\\\'", DriveClient.escapeQuery("\\'"))
    }

    @Test
    fun multipartBodyHasTheMetadataThenTheBytes() {
        val data = byteArrayOf(0, 1, 2, -1)
        val body = DriveClient.multipartBody("b0undary", """{"name":"x"}""", data)

        val head = "--b0undary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"x\"}\r\n" +
            "--b0undary\r\nContent-Type: application/octet-stream\r\n\r\n"
        val tail = "\r\n--b0undary--\r\n"
        val expected = head.toByteArray(StandardCharsets.UTF_8) + data + tail.toByteArray(StandardCharsets.UTF_8)

        assertArrayEquals(expected, body)
    }

    @Test
    fun onlyTemporaryFailuresAreRetried() {
        assertTrue(DriveClient.isRetryableStatus(500, ""))
        assertTrue(DriveClient.isRetryableStatus(503, ""))
        assertTrue(DriveClient.isRetryableStatus(429, ""))
        assertTrue(DriveClient.isRetryableStatus(403, """{"error":{"errors":[{"reason":"userRateLimitExceeded"}]}}"""))
        assertFalse(DriveClient.isRetryableStatus(403, """{"error":{"errors":[{"reason":"insufficientPermissions"}]}}"""))
        assertFalse(DriveClient.isRetryableStatus(400, ""))
        assertFalse(DriveClient.isRetryableStatus(404, ""))
    }

    @Test
    fun driveTimesAreReadAsUtc() {
        // 2026-09-29 06:23:15.123 UTC
        assertEquals(1790662995123L, DriveClient.parseTime("2026-09-29T06:23:15.123Z"))
        assertEquals(0, DriveClient.parseTime(""))
        assertEquals(0, DriveClient.parseTime(null))
        assertEquals(0, DriveClient.parseTime("yesterday"))
    }
}
