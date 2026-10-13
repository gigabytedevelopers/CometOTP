@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** What creating a vault on the relay came to (protocol §6.3). */
enum class CreateResult {
    CREATED,
    /** The id is taken, or belonged to a deleted vault. */
    EXISTS,
    /** No answer, or one that cannot be used: try again later. */
    FAILED,
}

/** What an upload came to (protocol §6.3, §7.1). */
sealed class UploadResult {
    /** Stored as [version]; [lastRead] is the day (YYYY-MM-DD) the browser last downloaded the vault. */
    data class Stored(val version: Long, val lastRead: String?) : UploadResult()

    /** The relay already has [storedVersion], which is as high or higher. */
    data class Stale(val storedVersion: Long) : UploadResult()

    /** 410: the vault was deleted, so the browser disconnected. */
    object Gone : UploadResult()

    /** 401 or 404: the relay no longer knows the vault, so the browser has to be paired again. */
    object Lost : UploadResult()

    /** No answer, a rate limit or a server error: try again later. */
    object Failed : UploadResult()
}

/** What deleting a vault came to. Gone and lost both mean there is nothing left to delete. */
enum class DeleteResult { DELETED, GONE, LOST, FAILED }

/** The relay's vault calls, as the phone makes them. */
interface Relay {
    fun createVault(relay: String, vaultId: String, writeHash: ByteArray, readHash: ByteArray): CreateResult
    fun upload(relay: String, vaultId: String, writeToken: String, blob: ByteArray): UploadResult
    fun delete(relay: String, vaultId: String, token: String): DeleteResult
}

/**
 * The relay's HTTP API over HttpURLConnection, which the app already uses for Google Drive, so no
 * HTTP library is added. Only called off the main thread.
 */
class RelayClient(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : Relay {

    private class Response(val code: Int, val body: String)

    private fun request(method: String, url: String, token: String?, contentType: String?, body: ByteArray?): Response? {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (token != null)
                connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream: InputStream? = if (code >= 400) connection.errorStream else connection.inputStream
            Response(code, stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "")
        } catch (e: IOException) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun json(body: String): JSONObject? = try {
        JSONObject(body)
    } catch (e: Exception) {
        null
    }

    override fun createVault(relay: String, vaultId: String, writeHash: ByteArray, readHash: ByteArray): CreateResult {
        val body = JSONObject()
            .put("id", vaultId)
            .put("write", SyncEncoding.toBase64Url(writeHash))
            .put("read", SyncEncoding.toBase64Url(readHash))
            .toString()
            .toByteArray(Charsets.UTF_8)
        val response = request("POST", "$relay/v1/vaults", null, "application/json", body) ?: return CreateResult.FAILED
        return when (response.code) {
            201 -> CreateResult.CREATED
            409 -> CreateResult.EXISTS
            else -> CreateResult.FAILED
        }
    }

    override fun upload(relay: String, vaultId: String, writeToken: String, blob: ByteArray): UploadResult {
        val response = request("PUT", "$relay/v1/vault/$vaultId", writeToken, "application/octet-stream", blob)
            ?: return UploadResult.Failed
        val body = json(response.body)
        return when (response.code) {
            200 -> {
                val version = body?.optLong("version", -1) ?: -1
                if (version < 1)
                    return UploadResult.Failed
                val lastRead = if (body != null && !body.isNull("last_read")) body.optString("last_read", null) else null
                UploadResult.Stored(version, lastRead)
            }
            409 -> {
                val stored = body?.optLong("version", -1) ?: -1
                if (body?.optString("error") == "stale_version" && stored >= 0) UploadResult.Stale(stored)
                else UploadResult.Failed
            }
            410 -> UploadResult.Gone
            401, 404 -> UploadResult.Lost
            else -> UploadResult.Failed
        }
    }

    override fun delete(relay: String, vaultId: String, token: String): DeleteResult {
        val response = request("DELETE", "$relay/v1/vault/$vaultId", token, null, null) ?: return DeleteResult.FAILED
        return when (response.code) {
            204 -> DeleteResult.DELETED
            410 -> DeleteResult.GONE
            401, 404 -> DeleteResult.LOST
            else -> DeleteResult.FAILED
        }
    }
}
