package com.seamless.android.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Aegis-Tor v2 Cryptographic Engine.
 *
 * Upgrades Seamless Tor Direct and Mailbox transports with:
 * - STDP v2: per-frame ephemeral + static dual-DH (K = HKDF(DH_ss || DH_es)) with non-sorted
 *   direction-bound key derivation (eliminating bidirectional reflection attacks), deterministic
 *   counter nonces (zero birthday collisions), and a 64-packet sliding anti-replay window.
 * - STMB v2: Noise-IK style sender-authenticated sealed mailbox envelopes where the sender's
 *   static public key is XOR-masked against the untrusted mailbox server via DH_es while being
 *   cryptographically authenticated to the recipient via DH_ss + bound AAD.
 * - Constant-time all-zero X25519 small-subgroup rejection and pairwise outbox tokens.
 */
object AegisTorCrypto {

    const val DIRECT_V2_VERSION: Byte = 2
    const val MAILBOX_V2_VERSION: Byte = 2

    private val DIRECT_MAGIC = byteArrayOf(0x53, 0x54, 0x44, 0x50) // STDP
    private val MAILBOX_MAGIC = byteArrayOf(0x53, 0x54, 0x4D, 0x42) // STMB

    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16
    private const val GCM_NONCE_BYTES = 12
    private const val X25519_KEY_BYTES = 32
    private const val MAX_AEAD_PAYLOAD = 16 * 1024 * 1024

    private val INFO_DIRECT_V2 = "SeamlessAegisDirect/v2".toByteArray(Charsets.UTF_8)
    private val INFO_MAILBOX_MASK_V2 = "SeamlessAegisMailboxMask/v2".toByteArray(Charsets.UTF_8)
    private val INFO_MAILBOX_V2 = "SeamlessAegisMailbox/v2".toByteArray(Charsets.UTF_8)

    private val NONCE_PREFIX_DIRECT_V2 = byteArrayOf(
        'S'.code.toByte(),
        'D'.code.toByte(),
        'V'.code.toByte(),
        '2'.code.toByte()
    )

    private val secureRandom = SecureRandom()

    fun isAllZero(bytes: ByteArray): Boolean {
        var acc = 0
        for (b in bytes) {
            acc = acc or (b.toInt() and 0xFF)
        }
        return acc == 0
    }

    fun requireNonZeroSharedSecret(secret: ByteArray) {
        if (secret.size != X25519_KEY_BYTES || isAllZero(secret)) {
            throw SecurityException("Invalid low-order X25519 shared secret")
        }
    }

    fun zeroize(vararg arrays: ByteArray?) {
        for (arr in arrays) {
            arr?.fill(0)
        }
    }

    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int = 32
    ): ByteArray {
        require(length in 1..(32 * 255)) { "Invalid HKDF length: $length" }
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prkMac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(effectiveSalt, "HmacSHA256"))
        }
        val prk = prkMac.doFinal(ikm)
        try {
            val out = ByteArray(length)
            var t = ByteArray(0)
            var offset = 0
            var counter = 1
            while (offset < length) {
                val expandMac = Mac.getInstance("HmacSHA256").apply {
                    init(SecretKeySpec(prk, "HmacSHA256"))
                }
                expandMac.update(t)
                expandMac.update(info)
                expandMac.update(counter.toByte())
                t = expandMac.doFinal()
                val copyLen = minOf(t.size, length - offset)
                System.arraycopy(t, 0, out, offset, copyLen)
                offset += copyLen
                counter++
            }
            zeroize(t)
            return out
        } finally {
            zeroize(prk)
        }
    }

    /**
     * Thread-safe 64-packet sliding anti-replay window for monotonic STDP v2 transport counters.
     */
    class ReplayWindow64 {
        private var initialized = false
        private var maxSeq = -1L
        private var bitmap = 0L

        @Synchronized
        fun checkAndMark(seq: Long): Boolean {
            if (seq < 0L) return false
            if (!initialized) {
                initialized = true
                maxSeq = seq
                bitmap = 1L
                return true
            }
            if (seq > maxSeq) {
                val diff = seq - maxSeq
                bitmap = if (diff >= 64L) {
                    1L
                } else {
                    (bitmap shl diff.toInt()) or 1L
                }
                maxSeq = seq
                return true
            }
            val delta = maxSeq - seq
            if (delta >= 64L) return false
            val bit = 1L shl delta.toInt()
            if ((bitmap and bit) != 0L) return false
            bitmap = bitmap or bit
            return true
        }
    }

    /**
     * Thread-safe 512-packet sliding anti-replay window for multi-circuit split-stream
     * (Dual-Plane Warp-Lanes & burst FEC) frames where out-of-order skew across independent
     * 6-hop Tor circuits can exceed 64 packets.
     */
    class ReplayWindow512 {
        private var initialized = false
        private var maxSeq = -1L
        private val words = LongArray(8)

        @Synchronized
        fun checkAndMark(seq: Long): Boolean {
            if (seq < 0L) return false
            if (!initialized) {
                initialized = true
                maxSeq = seq
                words.fill(0L)
                words[0] = 1L
                return true
            }
            if (seq > maxSeq) {
                val diff = seq - maxSeq
                if (diff >= 512L) {
                    words.fill(0L)
                } else {
                    val wordShift = (diff / 64L).toInt()
                    val bitShift = (diff % 64L).toInt()
                    for (i in 7 downTo 0) {
                        if (i < wordShift) {
                            words[i] = 0L
                        } else if (bitShift == 0) {
                            words[i] = words[i - wordShift]
                        } else {
                            val hi = words[i - wordShift] shl bitShift
                            val lo = if (i - wordShift - 1 >= 0) {
                                words[i - wordShift - 1] ushr (64 - bitShift)
                            } else {
                                0L
                            }
                            words[i] = hi or lo
                        }
                    }
                }
                words[0] = words[0] or 1L
                maxSeq = seq
                return true
            }
            val delta = maxSeq - seq
            if (delta >= 512L) return false
            val wIdx = (delta / 64L).toInt()
            val mask = 1L shl (delta % 64L).toInt()
            if ((words[wIdx] and mask) != 0L) return false
            words[wIdx] = words[wIdx] or mask
            return true
        }
    }

    data class OpenedDirect(
        val plaintext: ByteArray,
        val counter: Long,
        val senderEphemeralPub: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OpenedDirect) return false
            return counter == other.counter &&
                plaintext.contentEquals(other.plaintext) &&
                senderEphemeralPub.contentEquals(other.senderEphemeralPub)
        }

        override fun hashCode(): Int = counter.hashCode() * 31 + plaintext.contentHashCode()
    }

    data class OpenedMailbox(
        val plaintext: ByteArray,
        val senderPublicKey: ByteArray,
        val senderPublicKeyHex: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OpenedMailbox) return false
            return senderPublicKeyHex == other.senderPublicKeyHex &&
                plaintext.contentEquals(other.plaintext)
        }

        override fun hashCode(): Int = senderPublicKeyHex.hashCode() * 31 + plaintext.contentHashCode()
    }

    /**
     * Seals a direct Tor frame payload using STDP v2:
     * `[4B STDP][1B v=2][32B ephPub][8B counter][4B ctLen][ct]`
     */
    fun sealDirectV2(
        plaintext: ByteArray,
        frameAad: ByteArray,
        senderStaticPriv: ByteArray,
        senderStaticPub: ByteArray,
        recipientStaticPub: ByteArray,
        counter: Long
    ): ByteArray {
        require(senderStaticPriv.size == X25519_KEY_BYTES) { "Invalid senderStaticPriv size" }
        require(senderStaticPub.size == X25519_KEY_BYTES) { "Invalid senderStaticPub size" }
        require(recipientStaticPub.size == X25519_KEY_BYTES) { "Invalid recipientStaticPub size" }
        require(counter >= 0L) { "Counter must be non-negative" }

        val eph = X25519KeyPair.generate()
        var dhStatic: ByteArray? = null
        var dhEph: ByteArray? = null
        var ikm: ByteArray? = null
        var key: ByteArray? = null
        try {
            dhStatic = X25519.computeSharedSecret(senderStaticPriv, recipientStaticPub)
            requireNonZeroSharedSecret(dhStatic)
            dhEph = X25519.computeSharedSecret(eph.privateKey, recipientStaticPub)
            requireNonZeroSharedSecret(dhEph)

            ikm = dhStatic + dhEph
            val info = INFO_DIRECT_V2 + senderStaticPub + recipientStaticPub
            key = hkdfSha256(ikm = ikm, salt = eph.publicKey, info = info, length = 32)

            val header = ByteBuffer.allocate(DIRECT_MAGIC.size + 1 + X25519_KEY_BYTES + 8)
                .put(DIRECT_MAGIC)
                .put(DIRECT_V2_VERSION)
                .put(eph.publicKey)
                .putLong(counter)
                .array()

            val nonce = ByteBuffer.allocate(GCM_NONCE_BYTES)
                .put(NONCE_PREFIX_DIRECT_V2)
                .putLong(counter)
                .array()

            val aad = frameAad + header + senderStaticPub + recipientStaticPub
            val ciphertext = aesGcmEncrypt(plaintext, key, nonce, aad)

            return ByteBuffer.allocate(header.size + 4 + ciphertext.size)
                .put(header)
                .putInt(ciphertext.size)
                .put(ciphertext)
                .array()
        } finally {
            zeroize(eph.privateKey, dhStatic, dhEph, ikm, key)
        }
    }

    /**
     * Opens and authenticates an STDP v2 direct transport payload.
     */
    fun openDirectV2(
        payload: ByteArray,
        frameAad: ByteArray,
        recipientStaticPriv: ByteArray,
        recipientStaticPub: ByteArray,
        senderStaticPub: ByteArray
    ): OpenedDirect? {
        val headerSize = DIRECT_MAGIC.size + 1 + X25519_KEY_BYTES + 8
        if (payload.size < headerSize + 4 + GCM_TAG_BYTES) return null
        if (recipientStaticPriv.size != X25519_KEY_BYTES ||
            recipientStaticPub.size != X25519_KEY_BYTES ||
            senderStaticPub.size != X25519_KEY_BYTES
        ) {
            return null
        }

        var dhStatic: ByteArray? = null
        var dhEph: ByteArray? = null
        var ikm: ByteArray? = null
        var key: ByteArray? = null
        return try {
            val buffer = ByteBuffer.wrap(payload)
            val magic = ByteArray(DIRECT_MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(DIRECT_MAGIC)) return null

            val version = buffer.get()
            if (version != DIRECT_V2_VERSION) return null

            val ephPub = ByteArray(X25519_KEY_BYTES)
            buffer.get(ephPub)
            val counter = buffer.long
            if (counter < 0L) return null

            val ctLen = buffer.int
            if (ctLen < GCM_TAG_BYTES || ctLen > MAX_AEAD_PAYLOAD || ctLen > buffer.remaining()) {
                return null
            }
            val ciphertext = ByteArray(ctLen)
            buffer.get(ciphertext)

            dhStatic = X25519.computeSharedSecret(recipientStaticPriv, senderStaticPub)
            requireNonZeroSharedSecret(dhStatic)
            dhEph = X25519.computeSharedSecret(recipientStaticPriv, ephPub)
            requireNonZeroSharedSecret(dhEph)

            ikm = dhStatic + dhEph
            val info = INFO_DIRECT_V2 + senderStaticPub + recipientStaticPub
            key = hkdfSha256(ikm = ikm, salt = ephPub, info = info, length = 32)

            val header = payload.copyOfRange(0, headerSize)
            val nonce = ByteBuffer.allocate(GCM_NONCE_BYTES)
                .put(NONCE_PREFIX_DIRECT_V2)
                .putLong(counter)
                .array()
            val aad = frameAad + header + senderStaticPub + recipientStaticPub

            val plaintext = aesGcmDecrypt(ciphertext, key, nonce, aad) ?: return null
            OpenedDirect(plaintext = plaintext, counter = counter, senderEphemeralPub = ephPub)
        } catch (_: Exception) {
            null
        } finally {
            zeroize(dhStatic, dhEph, ikm, key)
        }
    }

    /**
     * Seals a Noise-IK style sender-authenticated mailbox envelope (`STMB v2`):
     * `[4B STMB][1B v=2][32B ephPub][32B maskedSenderPub][12B nonce][4B ctLen][ct]`
     */
    fun sealMailboxV2(
        plaintext: ByteArray,
        senderStaticPriv: ByteArray,
        senderStaticPub: ByteArray,
        recipientStaticPub: ByteArray
    ): ByteArray {
        require(senderStaticPriv.size == X25519_KEY_BYTES) { "Invalid senderStaticPriv size" }
        require(senderStaticPub.size == X25519_KEY_BYTES) { "Invalid senderStaticPub size" }
        require(recipientStaticPub.size == X25519_KEY_BYTES) { "Invalid recipientStaticPub size" }

        val eph = X25519KeyPair.generate()
        var dhEph: ByteArray? = null
        var dhStatic: ByteArray? = null
        var ikm: ByteArray? = null
        var mask: ByteArray? = null
        var key: ByteArray? = null
        try {
            dhEph = X25519.computeSharedSecret(eph.privateKey, recipientStaticPub)
            requireNonZeroSharedSecret(dhEph)
            dhStatic = X25519.computeSharedSecret(senderStaticPriv, recipientStaticPub)
            requireNonZeroSharedSecret(dhStatic)

            mask = hkdfSha256(
                ikm = dhEph,
                salt = eph.publicKey,
                info = INFO_MAILBOX_MASK_V2 + recipientStaticPub,
                length = X25519_KEY_BYTES
            )
            val maskedSenderPub = ByteArray(X25519_KEY_BYTES) { i ->
                (senderStaticPub[i].toInt() xor mask[i].toInt()).toByte()
            }

            ikm = dhEph + dhStatic
            key = hkdfSha256(
                ikm = ikm,
                salt = eph.publicKey,
                info = INFO_MAILBOX_V2 + senderStaticPub + recipientStaticPub,
                length = 32
            )

            val wirePrefix = ByteBuffer.allocate(MAILBOX_MAGIC.size + 1 + X25519_KEY_BYTES + X25519_KEY_BYTES)
                .put(MAILBOX_MAGIC)
                .put(MAILBOX_V2_VERSION)
                .put(eph.publicKey)
                .put(maskedSenderPub)
                .array()

            val nonce = ByteArray(GCM_NONCE_BYTES).also { secureRandom.nextBytes(it) }
            val aad = wirePrefix + senderStaticPub + recipientStaticPub
            val ciphertext = aesGcmEncrypt(plaintext, key, nonce, aad)

            return ByteBuffer.allocate(wirePrefix.size + nonce.size + 4 + ciphertext.size)
                .put(wirePrefix)
                .put(nonce)
                .putInt(ciphertext.size)
                .put(ciphertext)
                .array()
        } finally {
            zeroize(eph.privateKey, dhEph, dhStatic, ikm, mask, key)
        }
    }

    /**
     * Opens and cryptographically authenticates the sender of an `STMB v2` sealed mailbox payload.
     */
    fun openMailboxV2(
        payload: ByteArray,
        recipientStaticPriv: ByteArray,
        recipientStaticPub: ByteArray
    ): OpenedMailbox? {
        val wirePrefixSize = MAILBOX_MAGIC.size + 1 + X25519_KEY_BYTES + X25519_KEY_BYTES
        if (payload.size < wirePrefixSize + GCM_NONCE_BYTES + 4 + GCM_TAG_BYTES) return null
        if (recipientStaticPriv.size != X25519_KEY_BYTES || recipientStaticPub.size != X25519_KEY_BYTES) {
            return null
        }

        var dhEph: ByteArray? = null
        var dhStatic: ByteArray? = null
        var ikm: ByteArray? = null
        var mask: ByteArray? = null
        var key: ByteArray? = null
        return try {
            val buffer = ByteBuffer.wrap(payload)
            val magic = ByteArray(MAILBOX_MAGIC.size)
            buffer.get(magic)
            if (!magic.contentEquals(MAILBOX_MAGIC)) return null

            val version = buffer.get()
            if (version != MAILBOX_V2_VERSION) return null

            val ephPub = ByteArray(X25519_KEY_BYTES)
            buffer.get(ephPub)
            val maskedSenderPub = ByteArray(X25519_KEY_BYTES)
            buffer.get(maskedSenderPub)

            val nonce = ByteArray(GCM_NONCE_BYTES)
            buffer.get(nonce)

            val ctLen = buffer.int
            if (ctLen < GCM_TAG_BYTES || ctLen > MAX_AEAD_PAYLOAD || ctLen > buffer.remaining()) {
                return null
            }
            val ciphertext = ByteArray(ctLen)
            buffer.get(ciphertext)

            dhEph = X25519.computeSharedSecret(recipientStaticPriv, ephPub)
            requireNonZeroSharedSecret(dhEph)

            mask = hkdfSha256(
                ikm = dhEph,
                salt = ephPub,
                info = INFO_MAILBOX_MASK_V2 + recipientStaticPub,
                length = X25519_KEY_BYTES
            )
            val senderStaticPub = ByteArray(X25519_KEY_BYTES) { i ->
                (maskedSenderPub[i].toInt() xor mask[i].toInt()).toByte()
            }

            dhStatic = X25519.computeSharedSecret(recipientStaticPriv, senderStaticPub)
            requireNonZeroSharedSecret(dhStatic)

            ikm = dhEph + dhStatic
            key = hkdfSha256(
                ikm = ikm,
                salt = ephPub,
                info = INFO_MAILBOX_V2 + senderStaticPub + recipientStaticPub,
                length = 32
            )

            val wirePrefix = payload.copyOfRange(0, wirePrefixSize)
            val aad = wirePrefix + senderStaticPub + recipientStaticPub
            val plaintext = aesGcmDecrypt(ciphertext, key, nonce, aad) ?: return null

            val hex = senderStaticPub.joinToString("") { "%02x".format(it) }
            OpenedMailbox(
                plaintext = plaintext,
                senderPublicKey = senderStaticPub,
                senderPublicKeyHex = hex
            )
        } catch (_: Exception) {
            null
        } finally {
            zeroize(dhEph, dhStatic, ikm, mask, key)
        }
    }

    /**
     * Derives a pairwise secret outbox access capability token using static X25519 ECDH + HKDF.
     * Replaces publicly derivable folder tokens with an unforgeable secret shared only by the two contacts.
     */
    fun derivePairwiseOutboxToken(
        localStaticPriv: ByteArray,
        peerStaticPub: ByteArray,
        recipientFolderId: String
    ): String {
        require(localStaticPriv.size == X25519_KEY_BYTES) { "Invalid localStaticPriv size" }
        require(peerStaticPub.size == X25519_KEY_BYTES) { "Invalid peerStaticPub size" }
        var dh: ByteArray? = null
        var derived: ByteArray? = null
        try {
            dh = X25519.computeSharedSecret(localStaticPriv, peerStaticPub)
            requireNonZeroSharedSecret(dh)
            val info = "seamless-outbox-v2:${recipientFolderId.trim().lowercase()}".toByteArray(Charsets.UTF_8)
            derived = hkdfSha256(ikm = dh, salt = ByteArray(0), info = info, length = 32)
            return derived.joinToString("") { "%02x".format(it) }
        } finally {
            zeroize(dh, derived)
        }
    }

    private fun aesGcmEncrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(plaintext)
    }

    private fun aesGcmDecrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: ByteArray
    ): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            if (aad.isNotEmpty()) {
                cipher.updateAAD(aad)
            }
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }
}
