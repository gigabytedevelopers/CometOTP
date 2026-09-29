@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * The few Google Drive v3 REST calls backups need, over HttpURLConnection. With the drive.file
 * scope every call only sees the folders and files this app created.
 *
 * @param token        returns an access token; called again after [invalidateToken]
 * @param invalidateToken tells the token source that [token] was refused (401), so a fresh one is
 *                     fetched for the single retry
 */
class DriveClient(
    private val token: () -> String,
    private val invalidateToken: (String) -> Unit
) {
    class DriveFile(val id: String, val name: String, val modified: Long)

    /** A call that failed. [retryable] failures (no network, a busy server) may work later. */
    class DriveException(val status: Int, val retryable: Boolean, message: String, cause: Throwable? = null) :
        IOException(message, cause)

    /** The address of the account the token belongs to. */
    fun accountEmail(): String {
        val response = request("GET", "$API/about?fields=${encode("user(emailAddress)")}", null, null)
        val user = parseObject(response).optJSONObject("user")
        return user?.optString("emailAddress").orEmpty().ifEmpty {
            throw DriveException(0, false, "Google Drive did not name the account")
        }
    }

    /** A folder of this name that this app created and that is not in the trash. */
    fun findFolder(name: String): String? {
        val q = "name = '${escapeQuery(name)}' and mimeType = '$FOLDER_MIME' and trashed = false"
        return listFiles(q, "files(id)").firstOrNull()?.id
    }

    fun createFolder(name: String): String {
        val metadata = JSONObject().put("name", name).put("mimeType", FOLDER_MIME)
        val response = request("POST", "$API/files?fields=id", metadata.toString().toByteArray(StandardCharsets.UTF_8), JSON_TYPE)
        return parseObject(response).getString("id")
    }

    /** False when the folder is gone or in the trash (the user can do either in Drive). */
    fun isUsableFolder(id: String): Boolean {
        return try {
            val response = request("GET", "$API/files/${encode(id)}?fields=id,trashed", null, null)
            !parseObject(response).optBoolean("trashed", false)
        } catch (e: DriveException) {
            if (e.status == 404) false else throw e
        }
    }

    fun findFile(folderId: String, name: String): String? {
        val q = "name = '${escapeQuery(name)}' and '${escapeQuery(folderId)}' in parents and trashed = false"
        return listFiles(q, "files(id)").firstOrNull()?.id
    }

    fun listFolder(folderId: String): List<DriveFile> {
        val q = "'${escapeQuery(folderId)}' in parents and trashed = false"
        return listFiles(q, "files(id,name,modifiedTime)")
    }

    /** Creates [name] in [folderId]; returns the new file's id. */
    fun create(folderId: String, name: String, data: ByteArray): String {
        val metadata = JSONObject().put("name", name).put("parents", JSONArray().put(folderId))
        val boundary = "cometotp-" + UUID.randomUUID()
        val body = multipartBody(boundary, metadata.toString(), data)
        val response = request("POST", "$UPLOAD_API/files?uploadType=multipart&fields=id", body,
            "multipart/related; boundary=$boundary")
        return parseObject(response).getString("id")
    }

    /** Replaces the contents of file [id]. */
    fun update(id: String, data: ByteArray) {
        request("PATCH", "$UPLOAD_API/files/${encode(id)}?uploadType=media&fields=id", data, BINARY_TYPE)
    }

    /** Moves file [id] to the Drive trash, from where the user can still get it back. */
    fun trash(id: String) {
        val body = JSONObject().put("trashed", true).toString().toByteArray(StandardCharsets.UTF_8)
        request("PATCH", "$API/files/${encode(id)}?fields=id", body, JSON_TYPE)
    }

    private fun listFiles(q: String, fields: String): List<DriveFile> {
        val url = "$API/files?q=${encode(q)}&fields=${encode(fields)}&pageSize=1000&spaces=drive"
        val files = parseObject(request("GET", url, null, null)).optJSONArray("files") ?: return emptyList()

        val result = ArrayList<DriveFile>()
        for (i in 0 until files.length()) {
            val file = files.getJSONObject(i)
            result.add(DriveFile(file.getString("id"), file.optString("name"), parseTime(file.optString("modifiedTime"))))
        }
        return result
    }

    private fun request(method: String, url: String, body: ByteArray?, contentType: String?): ByteArray {
        val accessToken = token()
        try {
            return send(method, url, body, contentType, accessToken)
        } catch (e: DriveException) {
            if (e.status != 401)
                throw e
        }

        // An expired or revoked token: fetch a fresh one and try once more.
        invalidateToken(accessToken)
        return send(method, url, body, contentType, token())
    }

    private fun send(method: String, url: String, body: ByteArray?, contentType: String?, accessToken: String): ByteArray {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw DriveException(0, true, "Cannot reach Google Drive", e)
        }

        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")

            // HttpURLConnection does not know PATCH; Google's APIs take it as an override of POST.
            if (method == "PATCH") {
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                connection.requestMethod = method
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }

            val status = connection.responseCode
            if (status in 200..299)
                return readAll(connection.inputStream)

            val error = connection.errorStream?.let { readAll(it) }?.toString(StandardCharsets.UTF_8) ?: ""
            throw DriveException(status, isRetryableStatus(status, error), "Drive returned $status: $error")
        } catch (e: DriveException) {
            throw e
        } catch (e: IOException) {
            // No network, a dropped connection, a timeout.
            throw DriveException(0, true, "Cannot reach Google Drive", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun readAll(stream: InputStream): ByteArray {
        stream.use {
            val out = ByteArrayOutputStream()
            it.copyTo(out)
            return out.toByteArray()
        }
    }

    private fun parseObject(response: ByteArray): JSONObject {
        return try {
            JSONObject(String(response, StandardCharsets.UTF_8))
        } catch (e: JSONException) {
            throw DriveException(0, true, "Unexpected response from Google Drive", e)
        }
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val JSON_TYPE = "application/json; charset=UTF-8"
        private const val BINARY_TYPE = "application/octet-stream"
        private const val TIMEOUT_MS = 30_000

        /** Escapes a value for a single-quoted string in a Drive search query. */
        fun escapeQuery(value: String): String {
            return value.replace("\\", "\\\\").replace("'", "\\'")
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        /** A multipart/related upload body: the JSON metadata, then the file contents. */
        fun multipartBody(boundary: String, metadataJson: String, data: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            fun text(s: String) = out.write(s.toByteArray(StandardCharsets.UTF_8))

            text("--$boundary\r\nContent-Type: $JSON_TYPE\r\n\r\n$metadataJson\r\n")
            text("--$boundary\r\nContent-Type: $BINARY_TYPE\r\n\r\n")
            out.write(data)
            text("\r\n--$boundary--\r\n")
            return out.toByteArray()
        }

        /**
         * Server errors and rate limits pass; anything else (a bad request, no permission, a
         * missing file) will fail the same way next time.
         */
        fun isRetryableStatus(status: Int, errorBody: String): Boolean {
            if (status >= 500 || status == 429 || status == 408)
                return true
            return status == 403 && (errorBody.contains("rateLimitExceeded") || errorBody.contains("userRateLimitExceeded"))
        }

        /** Drive's RFC 3339 times ("2026-09-29T06:23:15.123Z") in milliseconds; 0 if unreadable. */
        fun parseTime(value: String?): Long {
            if (value.isNullOrEmpty())
                return 0
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return try {
                format.parse(value)?.time ?: 0
            } catch (e: ParseException) {
                0
            }
        }
    }
}
