@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

/**
 * Argon2id (RFC 9106, version 0x13), which Bitwarden and Stratum can protect their exports with.
 *
 * Nothing on the platform offers it, and the libraries that do are large or native. Written from
 * the RFC and checked against its test vector and against OpenSSL's implementation. Argon2id
 * only: Argon2i and Argon2d are not used by any export CometOTP reads.
 */
object Argon2 {

    private const val VERSION = 0x13
    private const val TYPE_ID = 2
    private const val BLOCK_WORDS = 128
    private const val SYNC_POINTS = 4
    private const val ADDRESSES_PER_BLOCK = 128

    /**
     * @param memory in KiB
     * @param secret the optional key (K in the RFC); empty for every export
     * @param associatedData the optional extra data (X in the RFC); empty for every export
     */
    fun id(password: ByteArray, salt: ByteArray, iterations: Int, memory: Int, parallelism: Int, tagLength: Int,
           secret: ByteArray = ByteArray(0), associatedData: ByteArray = ByteArray(0)): ByteArray {
        require(iterations >= 1) { "At least one pass" }
        require(parallelism in 1..0xFFFFFF) { "Parallelism out of range" }
        require(memory >= 8 * parallelism) { "Memory must be at least 8 KiB per lane" }
        require(tagLength >= 4) { "Tag too short" }
        // 1 GiB of blocks would not fit in a phone's heap; nothing exports with that much.
        require(memory <= 1024 * 1024) { "Memory too large" }

        val h0 = initialHash(password, salt, iterations, memory, parallelism, tagLength, secret, associatedData)

        val blocksPerLane = (memory / (SYNC_POINTS * parallelism)) * SYNC_POINTS
        val segmentLength = blocksPerLane / SYNC_POINTS
        val totalBlocks = blocksPerLane * parallelism
        val memoryBlocks = LongArray(totalBlocks * BLOCK_WORDS)

        // The first two blocks of every lane come straight from H0.
        for (lane in 0 until parallelism) {
            for (i in 0 until 2) {
                val seed = h0 + le32(i) + le32(lane)
                val block = hashLong(seed, 1024)
                readBlock(block, memoryBlocks, (lane * blocksPerLane + i) * BLOCK_WORDS)
            }
        }

        val state = Lane(memoryBlocks, blocksPerLane, segmentLength, parallelism, iterations, totalBlocks)
        for (pass in 0 until iterations)
            for (slice in 0 until SYNC_POINTS)
                for (lane in 0 until parallelism)
                    state.fillSegment(pass, slice, lane)

        // The tag is H' of the XOR of every lane's last block.
        val finalBlock = LongArray(BLOCK_WORDS)
        for (lane in 0 until parallelism) {
            val offset = (lane * blocksPerLane + blocksPerLane - 1) * BLOCK_WORDS
            for (i in 0 until BLOCK_WORDS)
                finalBlock[i] = finalBlock[i] xor memoryBlocks[offset + i]
        }
        val out = hashLong(writeBlock(finalBlock), tagLength)
        memoryBlocks.fill(0)
        return out
    }

    private fun initialHash(password: ByteArray, salt: ByteArray, iterations: Int, memory: Int, parallelism: Int,
                            tagLength: Int, secret: ByteArray, associatedData: ByteArray): ByteArray {
        val input = le32(parallelism) + le32(tagLength) + le32(memory) + le32(iterations) + le32(VERSION) + le32(TYPE_ID) +
                le32(password.size) + password +
                le32(salt.size) + salt +
                le32(secret.size) + secret +
                le32(associatedData.size) + associatedData
        return Blake2b.hash(input, 64)
    }

    /** H' in the RFC: BLAKE2b stretched to any length. */
    internal fun hashLong(input: ByteArray, length: Int): ByteArray {
        val prefixed = le32(length) + input
        if (length <= 64)
            return Blake2b.hash(prefixed, length)

        val out = ByteArray(length)
        var v = Blake2b.hash(prefixed, 64)
        System.arraycopy(v, 0, out, 0, 32)
        var produced = 32
        while (length - produced > 64) {
            v = Blake2b.hash(v, 64)
            System.arraycopy(v, 0, out, produced, 32)
            produced += 32
        }
        v = Blake2b.hash(v, length - produced)
        System.arraycopy(v, 0, out, produced, v.size)
        return out
    }

    private class Lane(
        val memory: LongArray,
        val blocksPerLane: Int,
        val segmentLength: Int,
        val lanes: Int,
        val passes: Int,
        val totalBlocks: Int
    ) {
        private val zero = LongArray(BLOCK_WORDS)
        private val addressInput = LongArray(BLOCK_WORDS)
        private val addresses = LongArray(BLOCK_WORDS)
        private val r = LongArray(BLOCK_WORDS)
        private val tmp = LongArray(BLOCK_WORDS)

        fun fillSegment(pass: Int, slice: Int, lane: Int) {
            // Argon2id: data-independent addressing for the first half of the first pass.
            val independent = pass == 0 && slice < SYNC_POINTS / 2

            if (independent) {
                addressInput.fill(0)
                addressInput[0] = pass.toLong()
                addressInput[1] = lane.toLong()
                addressInput[2] = slice.toLong()
                addressInput[3] = totalBlocks.toLong()
                addressInput[4] = passes.toLong()
                addressInput[5] = TYPE_ID.toLong()
            }

            var start = 0
            if (pass == 0 && slice == 0) {
                start = 2
                if (independent)
                    nextAddresses()
            }

            var current = lane * blocksPerLane + slice * segmentLength + start
            for (index in start until segmentLength) {
                if (independent && index % ADDRESSES_PER_BLOCK == 0)
                    nextAddresses()

                val previous = if (current % blocksPerLane == 0) current + blocksPerLane - 1 else current - 1
                val pseudoRandom = if (independent) addresses[index % ADDRESSES_PER_BLOCK] else memory[previous * BLOCK_WORDS]

                var refLane = ((pseudoRandom ushr 32) % lanes).toInt()
                if (pass == 0 && slice == 0)
                    refLane = lane
                val refIndex = referenceIndex(pass, slice, index, pseudoRandom and 0xFFFFFFFFL, refLane == lane)
                val reference = refLane * blocksPerLane + refIndex

                fillBlock(previous * BLOCK_WORDS, reference * BLOCK_WORDS, current * BLOCK_WORDS, pass > 0)
                current++
            }
        }

        private fun nextAddresses() {
            addressInput[6]++
            compute(zero, 0, addressInput, 0, addresses, false)
            compute(zero, 0, addresses, 0, addresses, false)
        }

        private fun referenceIndex(pass: Int, slice: Int, index: Int, pseudoRandom: Long, sameLane: Boolean): Int {
            val referenceArea: Long = if (pass == 0) {
                when {
                    slice == 0 -> (index - 1).toLong()
                    sameLane -> (slice * segmentLength + index - 1).toLong()
                    else -> (slice * segmentLength + if (index == 0) -1 else 0).toLong()
                }
            } else {
                if (sameLane)
                    (blocksPerLane - segmentLength + index - 1).toLong()
                else
                    (blocksPerLane - segmentLength + if (index == 0) -1 else 0).toLong()
            }

            var relative = pseudoRandom
            relative = (relative * relative) ushr 32
            relative = referenceArea - 1 - ((referenceArea * relative) ushr 32)

            val startPosition = if (pass != 0 && slice != SYNC_POINTS - 1) (slice + 1) * segmentLength else 0
            return ((startPosition + relative) % blocksPerLane).toInt()
        }

        private fun fillBlock(previous: Int, reference: Int, next: Int, xorWithExisting: Boolean) {
            compute(memory, previous, memory, reference, tmp, false)
            if (xorWithExisting) {
                for (i in 0 until BLOCK_WORDS)
                    memory[next + i] = memory[next + i] xor tmp[i]
            } else {
                System.arraycopy(tmp, 0, memory, next, BLOCK_WORDS)
            }
        }

        /** G in the RFC: out = P-permuted (x xor y) xor (x xor y). [out] may be the same array as [y]. */
        private fun compute(x: LongArray, xOffset: Int, y: LongArray, yOffset: Int, out: LongArray, unused: Boolean) {
            for (i in 0 until BLOCK_WORDS)
                r[i] = x[xOffset + i] xor y[yOffset + i]
            System.arraycopy(r, 0, tmp, 0, BLOCK_WORDS)

            // Rows: eight permutations over 16 consecutive words.
            for (i in 0 until 8)
                permute(r, i * 16, 1)
            // Columns: eight permutations over words two apart in every row.
            for (i in 0 until 8)
                permute(r, i * 2, 16)

            for (i in 0 until BLOCK_WORDS)
                out[i] = tmp[i] xor r[i]
        }

        /**
         * P in the RFC, over the 16 words at v[start + k * stride]. The row form (stride 1) takes
         * words 0..15 in order; the column form (stride 16) takes the pairs (2i, 2i+1) of each row,
         * which is why the pairs are addressed as k and k+1 below.
         */
        private fun permute(v: LongArray, start: Int, stride: Int) {
            fun at(k: Int) = if (stride == 1) start + k else start + (k / 2) * 16 + (k % 2)

            gb(v, at(0), at(4), at(8), at(12))
            gb(v, at(1), at(5), at(9), at(13))
            gb(v, at(2), at(6), at(10), at(14))
            gb(v, at(3), at(7), at(11), at(15))
            gb(v, at(0), at(5), at(10), at(15))
            gb(v, at(1), at(6), at(11), at(12))
            gb(v, at(2), at(7), at(8), at(13))
            gb(v, at(3), at(4), at(9), at(14))
        }

        private fun gb(v: LongArray, a: Int, b: Int, c: Int, d: Int) {
            v[a] = v[a] + v[b] + 2 * (v[a] and 0xFFFFFFFFL) * (v[b] and 0xFFFFFFFFL)
            v[d] = java.lang.Long.rotateRight(v[d] xor v[a], 32)
            v[c] = v[c] + v[d] + 2 * (v[c] and 0xFFFFFFFFL) * (v[d] and 0xFFFFFFFFL)
            v[b] = java.lang.Long.rotateRight(v[b] xor v[c], 24)
            v[a] = v[a] + v[b] + 2 * (v[a] and 0xFFFFFFFFL) * (v[b] and 0xFFFFFFFFL)
            v[d] = java.lang.Long.rotateRight(v[d] xor v[a], 16)
            v[c] = v[c] + v[d] + 2 * (v[c] and 0xFFFFFFFFL) * (v[d] and 0xFFFFFFFFL)
            v[b] = java.lang.Long.rotateRight(v[b] xor v[c], 63)
        }
    }

    private fun le32(value: Int): ByteArray =
        byteArrayOf(value.toByte(), (value ushr 8).toByte(), (value ushr 16).toByte(), (value ushr 24).toByte())

    private fun readBlock(bytes: ByteArray, into: LongArray, offset: Int) {
        for (i in 0 until BLOCK_WORDS) {
            var w = 0L
            for (k in 7 downTo 0)
                w = (w shl 8) or (bytes[i * 8 + k].toLong() and 0xFF)
            into[offset + i] = w
        }
    }

    private fun writeBlock(block: LongArray): ByteArray {
        val out = ByteArray(BLOCK_WORDS * 8)
        for (i in 0 until BLOCK_WORDS)
            for (k in 0 until 8)
                out[i * 8 + k] = (block[i] ushr (8 * k)).toByte()
        return out
    }
}
