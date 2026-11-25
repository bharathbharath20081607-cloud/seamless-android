package com.seamless.android.crypto

import java.security.MessageDigest
import javax.crypto.spec.SecretKeySpec

/**
 * Aegis-Flux Ephemeral Call Cryptographic Engine.
 *
 * Upgrades voice and video calls with:
 * - True Per-Call Perfect Forward Secrecy (PFS) via fresh ephemeral X25519 DH exchange
 *   with constant-time small-subgroup all-zero shared-secret rejection.
 * - Direction-separated AES-256-GCM keys (caller->callee vs callee->caller) derived via
 *   RFC 5869 HKDF-SHA256, eliminating reflection attacks.
 * - Per-stream 64-packet sliding anti-replay windows (audio, video, control).
 * - MITM-proof Bound Short Authentication Strings (SAS) cryptographically bound to
 *   ephemeral DH secret, sorted peer static identity keys, callId, and WebRTC DTLS fingerprints.
 * - Explicit in-memory zeroization of all key material when a call ends.
 */
object AegisCallCrypto {

    private const val SALT_DOMAIN = "SeamlessAegisCallSalt/v2"
    private const val SAS_DOMAIN = "SeamlessAegisCallSAS/v2"
    private val INFO_CALLER_TO_CALLEE = "caller-to-callee".toByteArray(Charsets.UTF_8)
    private val INFO_CALLEE_TO_CALLER = "callee-to-caller".toByteArray(Charsets.UTF_8)
    private val INFO_BOUND_SAS = "bound-sas".toByteArray(Charsets.UTF_8)

    val DEFAULT_SAS_EMOJIS: List<String> = listOf(
        "🦊", "🐼", "🐙", "🦄", "🐢", "🦋", "🐝", "🐬",
        "🦉", "🐧", "🦁", "🐯", "🐸", "🐳", "🦀", "🐌",
        "🌵", "🌻", "🍄", "🌙", "⭐", "🔥", "🌈", "❄️",
        "🍎", "🍋", "🍇", "🍉", "🍒", "🥑", "🌽", "🍩",
        "🚀", "⚓", "🎈", "🎸", "🎲", "🎯", "🔑", "💎",
        "⏰", "📷", "🔔", "🎧", "🧭", "🪁", "🛸", "🚲",
        "🏔️", "🌊", "🌋", "🏝️", "🗿", "🎪", "🏰", "⛵",
        "🍀", "🌸", "🍁", "🌴", "🐞", "🦜", "🐿️", "🦔"
    )

    class CallKeySession(
        val callId: String,
        val isCaller: Boolean,
        val isPfs: Boolean,
        val sendKey: ByteArray,
        val recvKey: ByteArray,
        val sasSecret: ByteArray,
        val audioReplayWindow: AegisTorCrypto.ReplayWindow512 = AegisTorCrypto.ReplayWindow512(),
        val videoReplayWindow: AegisTorCrypto.ReplayWindow512 = AegisTorCrypto.ReplayWindow512(),
        val ctrlReplayWindow: AegisTorCrypto.ReplayWindow512 = AegisTorCrypto.ReplayWindow512()
    ) {
        val sendKeySpec: SecretKeySpec = SecretKeySpec(sendKey, "AES")
        val recvKeySpec: SecretKeySpec = SecretKeySpec(recvKey, "AES")

        @Volatile
        var zeroized: Boolean = false
            private set

        fun zeroize() {
            zeroized = true
            AegisTorCrypto.zeroize(sendKey, recvKey, sasSecret)
        }
    }

    fun generateEphemeralKeyPair(): X25519KeyPair = X25519KeyPair.generate()

    fun derivePfsSession(
        callId: String,
        isCaller: Boolean,
        localEphemeralPriv: ByteArray,
        localEphemeralPub: ByteArray,
        remoteEphemeralPub: ByteArray,
        localIdentityPubHex: String,
        remoteIdentityPubHex: String
    ): CallKeySession {
        require(localEphemeralPriv.size == 32 && localEphemeralPub.size == 32 && remoteEphemeralPub.size == 32) {
            "Ephemeral X25519 keys must be 32 bytes"
        }
        val shared = X25519.computeSharedSecret(localEphemeralPriv, remoteEphemeralPub)
        try {
            AegisTorCrypto.requireNonZeroSharedSecret(shared)
            val callerEph = if (isCaller) localEphemeralPub else remoteEphemeralPub
            val calleeEph = if (isCaller) remoteEphemeralPub else localEphemeralPub
            val sortedIds = listOf(
                localIdentityPubHex.trim().lowercase(),
                remoteIdentityPubHex.trim().lowercase()
            ).sorted()

            val salt = MessageDigest.getInstance("SHA-256").run {
                update(SALT_DOMAIN.toByteArray(Charsets.UTF_8))
                update(callId.toByteArray(Charsets.UTF_8))
                update(callerEph)
                update(calleeEph)
                sortedIds.forEach {
                    update(it.toByteArray(Charsets.UTF_8))
                    update(0.toByte())
                }
                digest()
            }
            try {
                val c2e = AegisTorCrypto.hkdfSha256(shared, salt, INFO_CALLER_TO_CALLEE, 32)
                val e2c = AegisTorCrypto.hkdfSha256(shared, salt, INFO_CALLEE_TO_CALLER, 32)
                val sasSecret = AegisTorCrypto.hkdfSha256(shared, salt, INFO_BOUND_SAS, 32)
                val sendKey = if (isCaller) c2e else e2c
                val recvKey = if (isCaller) e2c else c2e
                return CallKeySession(
                    callId = callId,
                    isCaller = isCaller,
                    isPfs = true,
                    sendKey = sendKey,
                    recvKey = recvKey,
                    sasSecret = sasSecret
                )
            } finally {
                AegisTorCrypto.zeroize(salt)
            }
        } finally {
            AegisTorCrypto.zeroize(shared)
        }
    }

    fun deriveLegacySession(
        callId: String,
        isCaller: Boolean,
        legacySharedKey: ByteArray
    ): CallKeySession {
        require(legacySharedKey.size == 32) { "Legacy session key must be 32 bytes" }
        return CallKeySession(
            callId = callId,
            isCaller = isCaller,
            isPfs = false,
            sendKey = legacySharedKey.copyOf(),
            recvKey = legacySharedKey.copyOf(),
            sasSecret = legacySharedKey.copyOf()
        )
    }

    fun deriveBoundSasEmojis(
        sasSecret: ByteArray,
        callId: String,
        localIdentityPubHex: String,
        remoteIdentityPubHex: String,
        dtlsFingerprints: Collection<String> = emptyList(),
        alphabet: List<String> = DEFAULT_SAS_EMOJIS
    ): List<String> {
        if (sasSecret.size != 32 || alphabet.size < 64) return emptyList()
        val sortedIds = listOf(
            localIdentityPubHex.trim().lowercase(),
            remoteIdentityPubHex.trim().lowercase()
        ).sorted()
        val sortedDtls = dtlsFingerprints
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        val digest = MessageDigest.getInstance("SHA-256").run {
            update(SAS_DOMAIN.toByteArray(Charsets.UTF_8))
            update(sasSecret)
            update(callId.toByteArray(Charsets.UTF_8))
            sortedIds.forEach {
                update(it.toByteArray(Charsets.UTF_8))
                update(0.toByte())
            }
            sortedDtls.forEach {
                update(it.toByteArray(Charsets.UTF_8))
                update(0.toByte())
            }
            digest()
        }
        val bits = ((digest[0].toInt() and 0xFF) shl 16) or
            ((digest[1].toInt() and 0xFF) shl 8) or
            (digest[2].toInt() and 0xFF)
        return List(4) { i ->
            alphabet[(bits shr (18 - i * 6)) and 0x3F]
        }
    }

    fun extractDtlsFingerprintsFromSdp(sdp: String): List<String> {
        if (sdp.isBlank()) return emptyList()
        return sdp.replace("\r\n", "\n")
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("a=fingerprint:", ignoreCase = true) }
            .map { it.substringAfter(':').trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()
    }

    fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { b ->
            val v = b.toInt() and 0xFF
            append("0123456789abcdef"[v ushr 4])
            append("0123456789abcdef"[v and 0x0F])
        }
    }

    fun fromHex(hex: String): ByteArray? {
        val clean = hex.trim()
        if (clean.length % 2 != 0 || clean.isEmpty()) return null
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) return null
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
