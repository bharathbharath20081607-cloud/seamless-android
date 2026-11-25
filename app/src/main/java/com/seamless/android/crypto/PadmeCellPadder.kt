package com.seamless.android.crypto

import java.nio.ByteBuffer

/**
 * USENIX Security Padmé deterministic length padding + Tor 498-byte RELAY_DATA cell alignment.
 *
 * Bounds payload-length leakage to O(log log M) bits (<=12% overhead on small frames,
 * <=3% on large blobs) and aligns padded wire payloads to multiples of Tor's 498-byte cell
 * payload so variable plaintext lengths look identical across Tor guards.
 */
object PadmeCellPadder {

    const val TOR_RELAY_CELL_PAYLOAD_BYTES = 498
    private const val HEADER_LEN_BYTES = 4
    private const val MAX_PADME_INPUT = 64 * 1024 * 1024 // 64 MiB safety cap

    /**
     * Computes the Padmé padded length for [rawLen] per PURBs / USENIX Security 2019:
     * preserves top 1+floor(log2(floor(log2(L)))) bits and rounds up to the next bucket boundary.
     */
    fun padmeLength(rawLen: Int): Int {
        if (rawLen <= 0) return 0
        val l = rawLen.coerceAtMost(MAX_PADME_INPUT)
        val e = 31 - Integer.numberOfLeadingZeros(l)
        if (e <= 1) return l
        val s = 31 - Integer.numberOfLeadingZeros(e) + 1
        val z = e - s
        if (z <= 0) return l
        val m = (1 shl z) - 1
        return ((l + m) and m.inv())
    }

    /**
     * Computes total frame size (4-byte length header + padded body) aligned both via Padmé
     * buckets and 498-byte Tor RELAY_DATA cell payloads.
     */
    fun torCellAlignedLength(rawLen: Int, cellPayload: Int = TOR_RELAY_CELL_PAYLOAD_BYTES): Int {
        val withHeader = (rawLen.coerceAtLeast(0)) + HEADER_LEN_BYTES
        val padmeTotal = padmeLength(withHeader).coerceAtLeast(withHeader)
        if (cellPayload <= 0) return padmeTotal
        val cells = (padmeTotal + cellPayload - 1) / cellPayload
        return cells.coerceAtLeast(1) * cellPayload
    }

    /**
     * Pads [data] into `[4B big-endian originalLength][data][zero bytes]`.
     */
    fun pad(data: ByteArray, alignToTorCell: Boolean = true): ByteArray {
        val totalSize = if (alignToTorCell) {
            torCellAlignedLength(data.size)
        } else {
            padmeLength(data.size + HEADER_LEN_BYTES).coerceAtLeast(data.size + HEADER_LEN_BYTES)
        }
        val out = ByteArray(totalSize)
        ByteBuffer.wrap(out).putInt(data.size).put(data)
        return out
    }

    /**
     * Extracts original payload from a Padmé-padded byte array, returning `null` if malformed.
     */
    fun unpad(padded: ByteArray): ByteArray? {
        if (padded.size < HEADER_LEN_BYTES) return null
        val origLen = ByteBuffer.wrap(padded, 0, HEADER_LEN_BYTES).int
        if (origLen < 0 || origLen > padded.size - HEADER_LEN_BYTES) return null
        return padded.copyOfRange(HEADER_LEN_BYTES, HEADER_LEN_BYTES + origLen)
    }
}
