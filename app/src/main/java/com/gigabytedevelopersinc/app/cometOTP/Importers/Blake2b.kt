@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

/**
 * BLAKE2b (RFC 7693), unkeyed, with any digest length from 1 to 64 bytes: the hash Argon2 is
 * built on. Nothing on the platform offers it before Android 12 (and then only at 512 bits).
 */
object Blake2b {

    private val IV = longArrayOf(
        0x6a09e667f3bcc908L, -0x4498517a7b3558c5L, 0x3c6ef372fe94f82bL, -0x5ab00ac5a0e2c90fL,
        0x510e527fade682d1L, -0x64fa9773d4c193e1L, 0x1f83d9abfb41bd6bL, 0x5be0cd19137e2179L
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3)
    )

    fun hash(input: ByteArray, outLength: Int): ByteArray {
        require(outLength in 1..64) { "Digest length must be 1 to 64 bytes" }

        val h = IV.copyOf()
        // Parameter block: digest length, no key, fan-out 1, depth 1.
        h[0] = h[0] xor (0x01010000L or outLength.toLong())

        val m = LongArray(16)
        var offset = 0
        var counter = 0L

        // Every full block but the last is compressed as it comes; the last block, full or
        // padded, is compressed with the final flag.
        while (input.size - offset > 128) {
            counter += 128
            readBlock(input, offset, m)
            compress(h, m, counter, false)
            offset += 128
        }

        val remaining = input.size - offset
        val last = ByteArray(128)
        System.arraycopy(input, offset, last, 0, remaining)
        counter += remaining
        readBlock(last, 0, m)
        compress(h, m, counter, true)

        val out = ByteArray(outLength)
        for (i in 0 until outLength)
            out[i] = (h[i / 8] ushr (8 * (i % 8))).toByte()
        return out
    }

    private fun readBlock(b: ByteArray, offset: Int, m: LongArray) {
        for (i in 0 until 16) {
            var w = 0L
            for (k in 7 downTo 0)
                w = (w shl 8) or (b[offset + i * 8 + k].toLong() and 0xFF)
            m[i] = w
        }
    }

    private fun compress(h: LongArray, m: LongArray, counter: Long, last: Boolean) {
        val v = LongArray(16)
        System.arraycopy(h, 0, v, 0, 8)
        System.arraycopy(IV, 0, v, 8, 8)
        v[12] = v[12] xor counter
        // The high word of the counter: inputs here are far below 2^64 bytes.
        if (last)
            v[14] = v[14].inv()

        for (round in 0 until 12) {
            val s = SIGMA[round]
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            g(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            g(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            g(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            g(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }

        for (i in 0 until 8)
            h[i] = h[i] xor v[i] xor v[i + 8]
    }

    private fun g(v: LongArray, a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
        v[a] = v[a] + v[b] + x
        v[d] = java.lang.Long.rotateRight(v[d] xor v[a], 32)
        v[c] = v[c] + v[d]
        v[b] = java.lang.Long.rotateRight(v[b] xor v[c], 24)
        v[a] = v[a] + v[b] + y
        v[d] = java.lang.Long.rotateRight(v[d] xor v[a], 16)
        v[c] = v[c] + v[d]
        v[b] = java.lang.Long.rotateRight(v[b] xor v[c], 63)
    }
}
