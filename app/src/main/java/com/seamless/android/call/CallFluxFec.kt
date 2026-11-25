package com.seamless.android.call

import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Seamless Flux Adaptive 2D Staggered XOR Forward Error Correction (Burst-FEC).
 *
 * Combines:
 * 1. Adjacent parity (`0xFE`, F_{2k} xor F_{2k+1}) for single-frame loss recovery at 0 delay.
 * 2. Diagonal Stride-2 parity (`0xFD`, F_{n-2} xor F_n) with iterative peeling decoding to recover
 *    up to 2 consecutive dropped voice frames bit-identically across independent Tor circuits.
 *
 * - 0% bandwidth overhead on clean links (encoder stays dormant until triggered).
 */
object CallFluxFec {

    const val FEC_MAGIC: Byte = 0xFE.toByte()
    const val FEC_STRIDE_MAGIC: Byte = 0xFD.toByte()
    const val HEADER_BYTES = 1 + 8 + 2 + 2
    const val MAX_FRAME_BYTES = 2_048

    fun isFecPacket(payload: ByteArray): Boolean =
        payload.size >= HEADER_BYTES && (payload[0] == FEC_MAGIC || payload[0] == FEC_STRIDE_MAGIC)

    private fun buildXorParityPacket(
        magic: Byte,
        baseSeq: Long,
        frameA: ByteArray,
        frameB: ByteArray
    ): ByteArray {
        val maxLen = max(frameA.size, frameB.size)
        val out = ByteArray(HEADER_BYTES + maxLen)
        val bb = ByteBuffer.wrap(out)
        bb.put(magic)
        bb.putLong(baseSeq)
        bb.putShort(frameA.size.toShort())
        bb.putShort(frameB.size.toShort())
        for (i in 0 until maxLen) {
            val a = if (i < frameA.size) frameA[i].toInt() else 0
            val b = if (i < frameB.size) frameB[i].toInt() else 0
            out[HEADER_BYTES + i] = (a xor b).toByte()
        }
        return out
    }

    private fun xorRecoverFrame(xorPayload: ByteArray, knownFrame: ByteArray, targetLen: Int): ByteArray {
        val recovered = ByteArray(targetLen)
        for (i in 0 until targetLen) {
            val k = if (i < knownFrame.size) knownFrame[i].toInt() else 0
            recovered[i] = (xorPayload[i].toInt() xor k).toByte()
        }
        return recovered
    }

    class Encoder {
        private var cachedEvenSeq: Long = -1L
        private var cachedEvenPayload: ByteArray? = null
        private val historySeq = LongArray(4) { -1L }
        private val historyPayload = arrayOfNulls<ByteArray>(4)

        @Synchronized
        fun reset() {
            cachedEvenSeq = -1L
            cachedEvenPayload = null
            historySeq.fill(-1L)
            historyPayload.fill(null)
        }

        /**
         * Feeds an outbound encoded voice frame. Returns an XOR parity packet
         * on odd frames when [fecEnabled] is true and the preceding even frame matches.
         */
        @Synchronized
        fun onVoiceFrame(sequence: Long, encodedPayload: ByteArray, fecEnabled: Boolean): ByteArray? {
            if (!fecEnabled || encodedPayload.isEmpty() || encodedPayload.size > MAX_FRAME_BYTES) {
                if ((sequence and 1L) == 0L) {
                    cachedEvenSeq = -1L
                    cachedEvenPayload = null
                }
                return null
            }
            if ((sequence and 1L) == 0L) {
                cachedEvenSeq = sequence
                cachedEvenPayload = encodedPayload.copyOf()
                return null
            }
            val even = cachedEvenPayload
            val evenSeq = cachedEvenSeq
            cachedEvenSeq = -1L
            cachedEvenPayload = null
            if (even == null || evenSeq != sequence - 1L) {
                return null
            }
            return buildXorParityPacket(FEC_MAGIC, evenSeq, even, encodedPayload)
        }

        /**
         * Feeds an outbound voice frame and emits up to two parity packets:
         * - Adjacent parity on odd sequences (`FEC_MAGIC`, stride 1)
         * - Diagonal stride-2 parity (`FEC_STRIDE_MAGIC`, F_{n-2} xor F_n) when [burstMode2D] is true
         */
        @Synchronized
        fun onVoiceFrame2D(
            sequence: Long,
            encodedPayload: ByteArray,
            fecEnabled: Boolean,
            burstMode2D: Boolean
        ): List<ByteArray> {
            val adj = onVoiceFrame(sequence, encodedPayload, fecEnabled)
            if (!fecEnabled || encodedPayload.isEmpty() || encodedPayload.size > MAX_FRAME_BYTES) {
                val slot = (sequence and 3L).toInt()
                historySeq[slot] = -1L
                historyPayload[slot] = null
                return emptyList()
            }
            var diag: ByteArray? = null
            if (burstMode2D && sequence >= 2L) {
                val targetPrevSeq = sequence - 2L
                val prevSlot = (targetPrevSeq and 3L).toInt()
                val prev2 = historyPayload[prevSlot]
                if (prev2 != null && historySeq[prevSlot] == targetPrevSeq) {
                    diag = buildXorParityPacket(FEC_STRIDE_MAGIC, targetPrevSeq, prev2, encodedPayload)
                }
            }
            val curSlot = (sequence and 3L).toInt()
            historySeq[curSlot] = sequence
            historyPayload[curSlot] = encodedPayload.copyOf()

            if (adj == null && diag == null) return emptyList()
            val out = ArrayList<ByteArray>(2)
            if (adj != null) out.add(adj)
            if (diag != null) out.add(diag)
            return out
        }
    }

    class Decoder(private val windowSize: Int = 64) {
        private data class ParsedFec(
            val baseSeq: Long,
            val stride: Int,
            val lenA: Int,
            val lenB: Int,
            val xorPayload: ByteArray
        )

        private val voiceFrames = LinkedHashMap<Long, ByteArray>(windowSize, 0.75f, false)
        private val adjacentParities = LinkedHashMap<Long, ParsedFec>(windowSize / 2, 0.75f, false)
        private val strideParities = LinkedHashMap<Long, ParsedFec>(windowSize, 0.75f, false)

        @Synchronized
        fun reset() {
            voiceFrames.clear()
            adjacentParities.clear()
            strideParities.clear()
        }

        @Synchronized
        fun onVoiceFrame(sequence: Long, payload: ByteArray): Pair<Long, ByteArray>? =
            onVoiceFrameMulti(sequence, payload).firstOrNull()

        @Synchronized
        fun onFecPacket(fecPacket: ByteArray): Pair<Long, ByteArray>? =
            onFecPacketMulti(fecPacket).firstOrNull()

        @Synchronized
        fun onVoiceFrameMulti(sequence: Long, payload: ByteArray): List<Pair<Long, ByteArray>> {
            if (payload.isEmpty() || payload.size > MAX_FRAME_BYTES) return emptyList()
            storeVoiceLocked(sequence, payload.copyOf())
            return peelRecoveriesLocked()
        }

        @Synchronized
        fun onFecPacketMulti(fecPacket: ByteArray): List<Pair<Long, ByteArray>> {
            if (!isFecPacket(fecPacket)) return emptyList()
            val bb = ByteBuffer.wrap(fecPacket)
            val magic = bb.get()
            val stride = when (magic) {
                FEC_MAGIC -> 1
                FEC_STRIDE_MAGIC -> 2
                else -> return emptyList()
            }
            val baseSeq = bb.long
            val lenA = bb.short.toInt() and 0xFFFF
            val lenB = bb.short.toInt() and 0xFFFF
            if (baseSeq < 0L ||
                (stride == 1 && (baseSeq and 1L) != 0L) ||
                lenA !in 1..MAX_FRAME_BYTES ||
                lenB !in 1..MAX_FRAME_BYTES
            ) {
                return emptyList()
            }
            val expectedLen = max(lenA, lenB)
            if (fecPacket.size != HEADER_BYTES + expectedLen) return emptyList()
            val xorPayload = fecPacket.copyOfRange(HEADER_BYTES, fecPacket.size)
            val parsed = ParsedFec(baseSeq, stride, lenA, lenB, xorPayload)
            val targetMap = if (stride == 1) adjacentParities else strideParities
            val cap = if (stride == 1) (windowSize / 2).coerceAtLeast(16) else windowSize
            targetMap[baseSeq] = parsed
            while (targetMap.size > cap) {
                val oldest = targetMap.entries.firstOrNull()?.key ?: break
                targetMap.remove(oldest)
            }
            return peelRecoveriesLocked()
        }

        private fun peelRecoveriesLocked(): List<Pair<Long, ByteArray>> {
            val recovered = ArrayList<Pair<Long, ByteArray>>()
            var progressed = true
            while (progressed) {
                progressed = false
                if (scanParityMapLocked(adjacentParities, recovered)) progressed = true
                if (scanParityMapLocked(strideParities, recovered)) progressed = true
            }
            return recovered.sortedBy { it.first }
        }

        private fun scanParityMapLocked(
            map: LinkedHashMap<Long, ParsedFec>,
            out: MutableList<Pair<Long, ByteArray>>
        ): Boolean {
            var changed = false
            val iter = map.entries.iterator()
            while (iter.hasNext()) {
                val fec = iter.next().value
                val seqA = fec.baseSeq
                val seqB = fec.baseSeq + fec.stride.toLong()
                val frameA = voiceFrames[seqA]
                val frameB = voiceFrames[seqB]
                when {
                    frameA != null && frameB == null -> {
                        val recB = xorRecoverFrame(fec.xorPayload, frameA, fec.lenB)
                        storeVoiceLocked(seqB, recB)
                        out.add(seqB to recB)
                        iter.remove()
                        changed = true
                    }
                    frameB != null && frameA == null -> {
                        val recA = xorRecoverFrame(fec.xorPayload, frameB, fec.lenA)
                        storeVoiceLocked(seqA, recA)
                        out.add(seqA to recA)
                        iter.remove()
                        changed = true
                    }
                    frameA != null && frameB != null -> {
                        iter.remove()
                    }
                }
            }
            return changed
        }

        private fun storeVoiceLocked(seq: Long, payload: ByteArray) {
            voiceFrames[seq] = payload
            while (voiceFrames.size > windowSize) {
                val oldest = voiceFrames.entries.firstOrNull()?.key ?: break
                voiceFrames.remove(oldest)
            }
        }
    }
}
