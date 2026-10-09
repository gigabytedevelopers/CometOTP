@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

/**
 * scrypt (RFC 7914), as Aegis derives its vault key with.
 *
 * Neither the platform nor the JDK offers it, and a crypto library would be a large dependency
 * for one function. Written straight from the RFC, on little-endian words throughout: the tests
 * check it against the RFC's own vectors.
 */
object Scrypt {

    /**
     * @param n CPU/memory cost, a power of two greater than 1
     * @param r block size
     * @param p parallelisation
     * @param length bytes of key to derive
     */
    fun derive(password: ByteArray, salt: ByteArray, n: Int, r: Int, p: Int, length: Int): ByteArray {
        require(n > 1 && n and (n - 1) == 0) { "N must be a power of two greater than 1" }
        require(r > 0 && p > 0) { "r and p must be positive" }
        // 128 * r * (N + 2 * p) bytes of working memory; refuse what a phone cannot hold.
        require(r.toLong() * 128 * (n.toLong() + 2L * p) <= 512L * 1024 * 1024) { "Parameters need too much memory" }

        val blockWords = 32 * r
        val b = Pbkdf2.sha256(password, salt, 1, p * 128 * r)
        val words = IntArray(p * blockWords)
        for (i in words.indices)
            words[i] = readLittleEndian(b, i * 4)

        val v = IntArray(n * blockWords)
        val x = IntArray(blockWords)
        val y = IntArray(blockWords)
        val t = IntArray(16)
        for (i in 0 until p)
            roMix(words, i * blockWords, r, n, v, x, y, t)

        val out = ByteArray(words.size * 4)
        for (i in words.indices)
            writeLittleEndian(words[i], out, i * 4)

        return Pbkdf2.sha256(password, out, 1, length)
    }

    private fun roMix(b: IntArray, offset: Int, r: Int, n: Int, v: IntArray, x: IntArray, y: IntArray, t: IntArray) {
        val blockWords = 32 * r
        System.arraycopy(b, offset, x, 0, blockWords)

        for (i in 0 until n) {
            System.arraycopy(x, 0, v, i * blockWords, blockWords)
            blockMix(x, y, r, t)
        }

        for (i in 0 until n) {
            // Integerify: the first word of the last 64-byte block, modulo N.
            val j = x[(2 * r - 1) * 16] and (n - 1)
            val vOffset = j * blockWords
            for (k in 0 until blockWords)
                x[k] = x[k] xor v[vOffset + k]
            blockMix(x, y, r, t)
        }

        System.arraycopy(x, 0, b, offset, blockWords)
    }

    /** BlockMix in place: [x] holds 2r blocks of 16 words going in and coming out; [y] and [t] are scratch. */
    private fun blockMix(x: IntArray, y: IntArray, r: Int, t: IntArray) {
        // X = B[2r - 1]
        System.arraycopy(x, (2 * r - 1) * 16, t, 0, 16)

        for (i in 0 until 2 * r) {
            for (k in 0 until 16)
                t[k] = t[k] xor x[i * 16 + k]
            salsa20_8(t)
            // Even blocks go to the first half, odd blocks to the second.
            val dest = if (i % 2 == 0) (i / 2) * 16 else (r + i / 2) * 16
            System.arraycopy(t, 0, y, dest, 16)
        }

        System.arraycopy(y, 0, x, 0, 2 * r * 16)
    }

    /** Salsa20/8 core on 16 words, in place. */
    private fun salsa20_8(b: IntArray) {
        var x0 = b[0]; var x1 = b[1]; var x2 = b[2]; var x3 = b[3]
        var x4 = b[4]; var x5 = b[5]; var x6 = b[6]; var x7 = b[7]
        var x8 = b[8]; var x9 = b[9]; var x10 = b[10]; var x11 = b[11]
        var x12 = b[12]; var x13 = b[13]; var x14 = b[14]; var x15 = b[15]

        for (round in 0 until 4) {
            // Column rounds
            x4 = x4 xor Integer.rotateLeft(x0 + x12, 7); x8 = x8 xor Integer.rotateLeft(x4 + x0, 9)
            x12 = x12 xor Integer.rotateLeft(x8 + x4, 13); x0 = x0 xor Integer.rotateLeft(x12 + x8, 18)
            x9 = x9 xor Integer.rotateLeft(x5 + x1, 7); x13 = x13 xor Integer.rotateLeft(x9 + x5, 9)
            x1 = x1 xor Integer.rotateLeft(x13 + x9, 13); x5 = x5 xor Integer.rotateLeft(x1 + x13, 18)
            x14 = x14 xor Integer.rotateLeft(x10 + x6, 7); x2 = x2 xor Integer.rotateLeft(x14 + x10, 9)
            x6 = x6 xor Integer.rotateLeft(x2 + x14, 13); x10 = x10 xor Integer.rotateLeft(x6 + x2, 18)
            x3 = x3 xor Integer.rotateLeft(x15 + x11, 7); x7 = x7 xor Integer.rotateLeft(x3 + x15, 9)
            x11 = x11 xor Integer.rotateLeft(x7 + x3, 13); x15 = x15 xor Integer.rotateLeft(x11 + x7, 18)
            // Row rounds
            x1 = x1 xor Integer.rotateLeft(x0 + x3, 7); x2 = x2 xor Integer.rotateLeft(x1 + x0, 9)
            x3 = x3 xor Integer.rotateLeft(x2 + x1, 13); x0 = x0 xor Integer.rotateLeft(x3 + x2, 18)
            x6 = x6 xor Integer.rotateLeft(x5 + x4, 7); x7 = x7 xor Integer.rotateLeft(x6 + x5, 9)
            x4 = x4 xor Integer.rotateLeft(x7 + x6, 13); x5 = x5 xor Integer.rotateLeft(x4 + x7, 18)
            x11 = x11 xor Integer.rotateLeft(x10 + x9, 7); x8 = x8 xor Integer.rotateLeft(x11 + x10, 9)
            x9 = x9 xor Integer.rotateLeft(x8 + x11, 13); x10 = x10 xor Integer.rotateLeft(x9 + x8, 18)
            x12 = x12 xor Integer.rotateLeft(x15 + x14, 7); x13 = x13 xor Integer.rotateLeft(x12 + x15, 9)
            x14 = x14 xor Integer.rotateLeft(x13 + x12, 13); x15 = x15 xor Integer.rotateLeft(x14 + x13, 18)
        }

        b[0] += x0; b[1] += x1; b[2] += x2; b[3] += x3
        b[4] += x4; b[5] += x5; b[6] += x6; b[7] += x7
        b[8] += x8; b[9] += x9; b[10] += x10; b[11] += x11
        b[12] += x12; b[13] += x13; b[14] += x14; b[15] += x15
    }

    private fun readLittleEndian(b: ByteArray, offset: Int): Int =
        (b[offset].toInt() and 0xFF) or
        ((b[offset + 1].toInt() and 0xFF) shl 8) or
        ((b[offset + 2].toInt() and 0xFF) shl 16) or
        ((b[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLittleEndian(value: Int, b: ByteArray, offset: Int) {
        b[offset] = value.toByte()
        b[offset + 1] = (value ushr 8).toByte()
        b[offset + 2] = (value ushr 16).toByte()
        b[offset + 3] = (value ushr 24).toByte()
    }
}
