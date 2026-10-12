@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.BrowserSync

import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSyncException.Reason
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.concat
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.jsonString
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.u32
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.u64
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.SyncEncoding.utf8
import java.nio.ByteBuffer

/** One service as a browser receives it (protocol §4.3). */
class SyncService(
    /** [SyncVault.serviceId] of the secret. */
    val id: String,
    /** "totp" or "steam". */
    val type: String,
    /** Base32, upper case, without padding. */
    val secret: String,
    /** "SHA1", "SHA256" or "SHA512". */
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val issuer: String,
    val label: String,
    val icon: String? = null,
    val initials: String? = null,
    /** "#RRGGBB". */
    val badge: String? = null,
    val tags: List<String>? = null,
)

/**
 * The vault a paired browser downloads: the payload's JSON, padded and sealed with a key the
 * relay never sees, behind a header with its version (protocol §4).
 */
object SyncVault {

    const val PAD_BLOCK = 4096
    const val MAX_BLOCKS = 255

    /** Header (12), nonce (12) and tag (16) around the padded payload. */
    const val OVERHEAD = 40
    const val MAX_BLOB_LENGTH = OVERHEAD + MAX_BLOCKS * PAD_BLOCK

    /** JavaScript's largest exact integer, which the browser must be able to hold a version in. */
    const val MAX_VERSION = 9_007_199_254_740_991L

    private val VAULT_INFO = utf8("CometOTP vault v1")
    private val SERVICE_ID_LABEL = utf8("CometOTP service v1")
    private val MAGIC = utf8("COV1")

    /** `HKDF(V, vault id, VAULT_INFO, 32)`. */
    fun blobKey(vaultKey: ByteArray, vaultId: ByteArray): ByteArray = SyncCrypto.hkdf(vaultKey, vaultId, VAULT_INFO, 32)

    /** `b64u(SHA256(SERVICE_ID_LABEL ‖ secret)[0:12])`: stays the same when a service is renamed. */
    fun serviceId(secret: ByteArray): String =
        SyncEncoding.toBase64Url(SyncCrypto.sha256(SERVICE_ID_LABEL, secret).copyOf(12))

    /** Checks a blob's shape and returns its version, without the key. */
    fun version(blob: ByteArray): Long {
        val blocks = blob.size - OVERHEAD
        if (blocks <= 0 || blocks % PAD_BLOCK != 0 || blocks / PAD_BLOCK > MAX_BLOCKS)
            throw BrowserSyncException(Reason.BAD_BLOB, "A blob is 40 + 4096 × k bytes, k from 1 to 255")
        if (!blob.copyOf(4).contentEquals(MAGIC))
            throw BrowserSyncException(Reason.BAD_BLOB, "Not a vault blob")
        val version = ByteBuffer.wrap(blob, 4, 8).long
        if (version !in 1..MAX_VERSION)
            throw BrowserSyncException(Reason.BAD_BLOB, "The version is out of range")
        return version
    }

    private fun pad(json: ByteArray): ByteArray {
        val length = (4 + json.size + PAD_BLOCK - 1) / PAD_BLOCK * PAD_BLOCK
        require(length <= MAX_BLOCKS * PAD_BLOCK) { "The payload is too large for a vault" }
        return concat(u32(json.size), json, ByteArray(length - 4 - json.size))
    }

    private fun unpad(padded: ByteArray): ByteArray {
        val length = ByteBuffer.wrap(padded, 0, 4).int
        if (length < 0 || length > padded.size - 4)
            throw BrowserSyncException(Reason.BAD_BLOB, "The payload length is out of range")
        if ((4 + length until padded.size).any { padded[it] != 0.toByte() })
            throw BrowserSyncException(Reason.BAD_BLOB, "The padding is not zeros")
        return padded.copyOfRange(4, 4 + length)
    }

    /** Seals [payloadJson] as vault [version]. */
    fun seal(
        vaultKey: ByteArray,
        vaultId: ByteArray,
        version: Long,
        payloadJson: String,
        nonce: ByteArray = SyncCrypto.randomBytes(12),
    ): ByteArray {
        require(version in 1..MAX_VERSION) { "The version must be from 1 to 2^53 - 1" }
        val header = concat(MAGIC, u64(version))
        val sealed = SyncCrypto.seal(blobKey(vaultKey, vaultId), nonce, concat(header, vaultId), pad(utf8(payloadJson)))
        return concat(header, nonce, sealed)
    }

    /**
     * Opens a blob, refusing one older than [minVersion]. The phone only seals vaults; this is
     * what checks that they open, in the tests and before an upload.
     */
    fun open(vaultKey: ByteArray, vaultId: ByteArray, blob: ByteArray, minVersion: Long = 1): Pair<Long, String> {
        val version = version(blob)
        if (version < minVersion)
            throw BrowserSyncException(Reason.ROLLBACK, "Version $version is older than $minVersion")
        val header = blob.copyOf(12)
        val padded = SyncCrypto.open(
            blobKey(vaultKey, vaultId),
            blob.copyOfRange(12, 24),
            concat(header, vaultId),
            blob.copyOfRange(24, blob.size),
        )
        return Pair(version, SyncEncoding.fromUtf8(unpad(padded)))
    }

    /** The payload's JSON, with its members in the protocol's order. */
    fun payloadJson(phone: String, updated: Long, services: List<SyncService>): String {
        val out = StringBuilder()
        out.append("{\"v\":1,\"phone\":").append(jsonString(phone))
            .append(",\"updated\":").append(updated)
            .append(",\"services\":[")
        services.forEachIndexed { i, s ->
            if (i > 0) out.append(',')
            out.append("{\"id\":").append(jsonString(s.id))
                .append(",\"type\":").append(jsonString(s.type))
                .append(",\"secret\":").append(jsonString(s.secret))
                .append(",\"algorithm\":").append(jsonString(s.algorithm))
                .append(",\"digits\":").append(s.digits)
                .append(",\"period\":").append(s.period)
                .append(",\"issuer\":").append(jsonString(s.issuer))
                .append(",\"label\":").append(jsonString(s.label))
            s.icon?.let { out.append(",\"icon\":").append(jsonString(it)) }
            s.initials?.let { out.append(",\"initials\":").append(jsonString(it)) }
            s.badge?.let { out.append(",\"badge\":").append(jsonString(it)) }
            s.tags?.let { tags -> out.append(",\"tags\":[").append(tags.joinToString(",") { jsonString(it) }).append(']') }
            out.append('}')
        }
        return out.append("]}").toString()
    }
}
