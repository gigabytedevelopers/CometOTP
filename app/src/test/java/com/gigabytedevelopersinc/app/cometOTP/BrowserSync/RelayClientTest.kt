@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * RelayClient's requests and how it reads each answer (protocol §6.3), against a stand-in relay on
 * the JDK's own HTTP server that records what it was sent and answers as told.
 */
class RelayClientTest {

    private class Recorded(val method: String, val path: String, val authorization: String?, val contentType: String?, val body: ByteArray)

    private lateinit var server: HttpServer
    private lateinit var relay: String
    private val requests = ArrayList<Recorded>()
    private var answerStatus = 200
    private var answerBody = ""

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange: HttpExchange ->
            requests.add(Recorded(
                exchange.requestMethod,
                exchange.requestURI.path,
                exchange.requestHeaders.getFirst("Authorization"),
                exchange.requestHeaders.getFirst("Content-Type"),
                exchange.requestBody.readBytes(),
            ))
            val bytes = answerBody.toByteArray()
            exchange.sendResponseHeaders(answerStatus, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty())
                exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        relay = "http://127.0.0.1:${server.address.port}"
    }

    @After
    fun stop() = server.stop(0)

    private fun answer(status: Int, body: String = "") {
        answerStatus = status
        answerBody = body
    }

    private val client = RelayClient(connectTimeoutMs = 2_000, readTimeoutMs = 2_000)
    private val id = "AAAAAAAAAAAAAAAAAAAAAA"
    private val token = "W".repeat(43)

    @Test
    fun createsAVaultWithTheTokenHashes() {
        answer(201, "{}")
        val write = ByteArray(32) { 1 }
        val read = ByteArray(32) { 2 }
        assertEquals(CreateResult.CREATED, client.createVault(relay, id, write, read))
        val sent = requests.single()
        assertEquals("POST", sent.method)
        assertEquals("/v1/vaults", sent.path)
        assertEquals(null, sent.authorization)
        assertEquals("application/json", sent.contentType)
        val body = JSONObject(String(sent.body))
        assertEquals(id, body.getString("id"))
        assertEquals(SyncEncoding.toBase64Url(write), body.getString("write"))
        assertEquals(SyncEncoding.toBase64Url(read), body.getString("read"))

        answer(409, """{"error":"exists"}""")
        assertEquals(CreateResult.EXISTS, client.createVault(relay, id, write, read))
        answer(429, """{"error":"rate_limited"}""")
        assertEquals(CreateResult.FAILED, client.createVault(relay, id, write, read))
    }

    @Test
    fun uploadsTheBlobWithTheWriteToken() {
        answer(200, """{"version":3,"last_read":"2026-10-13"}""")
        val blob = ByteArray(4136) { it.toByte() }
        assertEquals(UploadResult.Stored(3, "2026-10-13"), client.upload(relay, id, token, blob))
        val sent = requests.single()
        assertEquals("PUT", sent.method)
        assertEquals("/v1/vault/$id", sent.path)
        assertEquals("Bearer $token", sent.authorization)
        assertEquals("application/octet-stream", sent.contentType)
        assertArrayEquals(blob, sent.body)

        answer(200, """{"version":4,"last_read":null}""")
        assertEquals(UploadResult.Stored(4, null), client.upload(relay, id, token, blob))
    }

    @Test
    fun readsEveryAnswerToAnUpload() {
        val blob = ByteArray(10)
        answer(409, """{"error":"stale_version","version":7}""")
        assertEquals(UploadResult.Stale(7), client.upload(relay, id, token, blob))
        answer(410, """{"error":"gone"}""")
        assertEquals(UploadResult.Gone, client.upload(relay, id, token, blob))
        answer(401, """{"error":"unauthorized"}""")
        assertEquals(UploadResult.Lost, client.upload(relay, id, token, blob))
        answer(404, """{"error":"not_found"}""")
        assertEquals(UploadResult.Lost, client.upload(relay, id, token, blob))
        answer(429, """{"error":"rate_limited"}""")
        assertEquals(UploadResult.Failed, client.upload(relay, id, token, blob))
        answer(500, "")
        assertEquals(UploadResult.Failed, client.upload(relay, id, token, blob))
        answer(200, "not json")
        assertEquals(UploadResult.Failed, client.upload(relay, id, token, blob))
        answer(409, """{"error":"exists"}""")
        assertEquals(UploadResult.Failed, client.upload(relay, id, token, blob))
    }

    @Test
    fun deletesWithAToken() {
        answer(204)
        assertEquals(DeleteResult.DELETED, client.delete(relay, id, token))
        assertEquals("DELETE", requests.single().method)
        assertEquals("Bearer $token", requests.single().authorization)
        answer(410, """{"error":"gone"}""")
        assertEquals(DeleteResult.GONE, client.delete(relay, id, token))
        answer(401, """{"error":"unauthorized"}""")
        assertEquals(DeleteResult.LOST, client.delete(relay, id, token))
        answer(503, "")
        assertEquals(DeleteResult.FAILED, client.delete(relay, id, token))
    }

    @Test
    fun anUnreachableRelayIsAFailureNotACrash() {
        server.stop(0)
        assertEquals(UploadResult.Failed, client.upload(relay, id, token, ByteArray(10)))
        assertEquals(CreateResult.FAILED, client.createVault(relay, id, ByteArray(32), ByteArray(32)))
        assertEquals(DeleteResult.FAILED, client.delete(relay, id, token))
    }
}
