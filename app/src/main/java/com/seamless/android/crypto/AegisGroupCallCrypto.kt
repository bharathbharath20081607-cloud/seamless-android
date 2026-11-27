package com.seamless.android.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Aegis Swarm Conference™ Cryptographic Engine (RFC 9605 SFrame + Epoch Sender-Key Ratchet).
 *
 * Security & Wire Efficiency Properties:
 * - Encrypts each outbound audio/video frame ONCE ($O(1)$ CPU) using per-sender AES-256-GCM
 *   keys so an elected Zero-Knowledge SFU relay hub can forward ciphertext without access to keys.
 * - Compact 21-byte SFrame header + 326B 40ms IMA-ADPCM + 16B GCM tag = 363 bytes total,
 *   fitting inside ONE single 514-byte Tor cell (<= 444B compact payload ceiling).
 * - Symmetric hash ratchet every 250 frames (~10s) provides intra-call forward secrecy.
 * - Hard Epoch Rotation immediately purges & zeroizes prior-epoch keys when a member leaves or
 *   is removed (retainPreviousGraceMs = 0), preventing removed peers from decrypting subsequent media.
 * - Ed25519-authenticated SenderKey announcements bind ephemeral media seeds to identity keys.
 * - Deterministic 4-Emoji Conference SAS across all room participants.
 */
object AegisGroupCallCrypto {

    const val GROUP_SFRAME_AUDIO_MAGIC: Byte = 0x5D
    const val GROUP_SFRAME_AUDIO_FEC_MAGIC: Byte = 0x5E
    const val GROUP_SFRAME_VIDEO_MAGIC: Byte = 0x5F

    // [magic:1][roomHash8:8][senderTag4:4][epoch:2][counter48:6] = 21 bytes
    const val SFRAME_HEADER_BYTES = 21
    const val GCM_TAG_BITS = 128
    const val GCM_TAG_BYTES = 16
    const val SENDER_SEED_BYTES = 32
    const val RATCHET_INTERVAL_FRAMES = 250L // ~10s at 40ms/frame

    private const val ROOM_DOMAIN = "AegisGroupRoom-v1"
    private const val SENDER_TAG_DOMAIN = "AegisSenderTag-v1"
    private const val SFRAME_HKDF_INFO = "AegisSFrame-v1"
    private const val SENDER_SIGN_DOMAIN = "AegisGroupSenderKey/v1"
    private const val GROUP_SAS_DOMAIN = "AegisGroupSAS-v1"

    private const val MAX_RATCHET_GEN_CACHE = 6
    private val secureRandom = SecureRandom()

    fun isGroupMediaMagic(magic: Byte): Boolean =
        magic == GROUP_SFRAME_AUDIO_MAGIC ||
            magic == GROUP_SFRAME_AUDIO_FEC_MAGIC ||
            magic == GROUP_SFRAME_VIDEO_MAGIC

    fun canonicalizeOnion(onion: String): String =
        onion.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore(':')
            .removeSuffix(".onion")
            .let { if (it.isBlank()) "" else "$it.onion" }

    fun roomHash8(groupId: String, callId: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(ROOM_DOMAIN.toByteArray(Charsets.UTF_8))
            update(0.toByte())
            update(groupId.trim().toByteArray(Charsets.UTF_8))
            update(0.toByte())
            update(callId.trim().toByteArray(Charsets.UTF_8))
        }.digest()
        return ByteBuffer.wrap(digest, 0, 8).long
    }

    fun senderTag4(onionAddress: String): Int {
        val canon = canonicalizeOnion(onionAddress).ifBlank { onionAddress.trim().lowercase() }
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(SENDER_TAG_DOMAIN.toByteArray(Charsets.UTF_8))
            update(0.toByte())
            update(canon.toByteArray(Charsets.UTF_8))
        }.digest()
        val raw = ByteBuffer.wrap(digest, 0, 4).int
        return if (raw == 0) 1 else raw
    }

    fun generateRootSeed(): ByteArray =
        ByteArray(SENDER_SEED_BYTES).also { secureRandom.nextBytes(it) }

    fun commitmentHex(rootSeed: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update("AegisSeedCommit/v1".toByteArray(Charsets.UTF_8))
            update(rootSeed)
        }.digest()
        return AegisCallCrypto.toHex(digest.copyOfRange(0, 16))
    }

    private fun senderKeySigningBytes(
        groupId: String,
        callId: String,
        epoch: Int,
        senderCanonicalOnion: String,
        senderTag4: Int,
        seedCommitmentHex: String
    ): ByteArray {
        val baos = ByteArrayOutputStream(160)
        DataOutputStream(baos).use { out ->
            fun writeUtf(s: String) {
                val b = s.toByteArray(Charsets.UTF_8)
                out.writeInt(b.size)
                out.write(b)
            }
            writeUtf(SENDER_SIGN_DOMAIN)
            writeUtf(groupId.trim())
            writeUtf(callId.trim())
            out.writeInt(epoch)
            writeUtf(canonicalizeOnion(senderCanonicalOnion).ifBlank { senderCanonicalOnion.trim().lowercase() })
            out.writeInt(senderTag4)
            writeUtf(seedCommitmentHex.trim().lowercase())
        }
        return baos.toByteArray()
    }

    fun signSenderKeyAnnouncement(
        ed25519PrivateKey: ByteArray,
        groupId: String,
        callId: String,
        epoch: Int,
        senderOnion: String,
        senderTag4: Int,
        rootSeed: ByteArray
    ): Pair<String, String> {
        require(ed25519PrivateKey.size == 32) { "Ed25519 private key must be 32 bytes" }
        val priv = Ed25519PrivateKeyParameters(ed25519PrivateKey, 0)
        val pubHex = AegisCallCrypto.toHex(priv.generatePublicKey().encoded)
        val commit = commitmentHex(rootSeed)
        val msg = senderKeySigningBytes(groupId, callId, epoch, senderOnion, senderTag4, commit)
        val signer = Ed25519Signer().apply {
            init(true, priv)
            update(msg, 0, msg.size)
        }
        return pubHex to AegisCallCrypto.toHex(signer.generateSignature())
    }

    fun verifySenderKeyAnnouncement(
        ed25519PublicKeyHex: String,
        signatureHex: String,
        groupId: String,
        callId: String,
        epoch: Int,
        senderOnion: String,
        senderTag4: Int,
        rootSeed: ByteArray
    ): Boolean {
        val pubBytes = AegisCallCrypto.fromHex(ed25519PublicKeyHex) ?: return false
        val sigBytes = AegisCallCrypto.fromHex(signatureHex) ?: return false
        if (pubBytes.size != 32 || sigBytes.size != 64) return false
        val expectedTag = senderTag4(senderOnion)
        if (expectedTag != senderTag4) return false
        return try {
            val commit = commitmentHex(rootSeed)
            val msg = senderKeySigningBytes(groupId, callId, epoch, senderOnion, senderTag4, commit)
            val verifier = Ed25519Signer().apply {
                init(false, Ed25519PublicKeyParameters(pubBytes, 0))
                update(msg, 0, msg.size)
            }
            verifier.verifySignature(sigBytes)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deterministic 4-emoji Conference Short Authentication String derived over the room ID,
     * current epoch, and sorted participant (canonicalOnion, keyCommitment) tuples.
     */
    fun deriveConferenceEmojis(
        groupId: String,
        callId: String,
        epoch: Int,
        participantCommitments: List<Pair<String, String>>,
        alphabet: List<String> = AegisCallCrypto.DEFAULT_SAS_EMOJIS
    ): List<String> {
        if (participantCommitments.isEmpty() || alphabet.size < 64) return emptyList()
        val sorted = participantCommitments
            .map { (onion, commit) ->
                val canon = canonicalizeOnion(onion).ifBlank { onion.trim().lowercase() }
                "$canon|${commit.trim().lowercase()}"
            }
            .distinct()
            .sorted()

        val digest = MessageDigest.getInstance("SHA-256").run {
            update(GROUP_SAS_DOMAIN.toByteArray(Charsets.UTF_8))
            update(0.toByte())
            update(groupId.trim().toByteArray(Charsets.UTF_8))
            update(0.toByte())
            update(callId.trim().toByteArray(Charsets.UTF_8))
            update(0.toByte())
            update(epoch.toString().toByteArray(Charsets.UTF_8))
            sorted.forEach { entry ->
                update(0.toByte())
                update(entry.toByteArray(Charsets.UTF_8))
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

    data class SFrameHeader(
        val magic: Byte,
        val roomHash8: Long,
        val senderTag4: Int,
        val epoch: Int,
        val counter: Long
    )

    data class OpenedSFrame(
        val header: SFrameHeader,
        val plaintext: ByteArray
    )

    fun parseHeader(packet: ByteArray): SFrameHeader? {
        if (packet.size < SFRAME_HEADER_BYTES + GCM_TAG_BYTES) return null
        val magic = packet[0]
        if (!isGroupMediaMagic(magic)) return null
        val bb = ByteBuffer.wrap(packet, 1, SFRAME_HEADER_BYTES - 1)
        val roomHash = bb.long
        val senderTag = bb.int
        val epoch = bb.short.toInt() and 0xFFFF
        var counter = 0L
        for (i in 0 until 6) {
            counter = (counter shl 8) or (bb.get().toLong() and 0xFFL)
        }
        return SFrameHeader(
            magic = magic,
            roomHash8 = roomHash,
            senderTag4 = senderTag,
            epoch = epoch,
            counter = counter
        )
    }

    /**
     * Stateful symmetric hash-ratchet sender/receiver key material for a single `(senderTag4, epoch)`.
     */
    class SenderKeyState(
        val senderOnion: String,
        val senderTag4: Int,
        val epoch: Int,
        roomHash8: Long,
        rootSeed: ByteArray
    ) {
        private class StepMaterial(
            val chainKey: ByteArray,
            val mediaKey: ByteArray,
            val keySpec: SecretKeySpec
        ) {
            fun zeroize() {
                AegisTorCrypto.zeroize(chainKey, mediaKey)
            }
        }

        private val ivSalt12: ByteArray
        private val chainKey0: ByteArray
        private val stepCache = LinkedHashMap<Long, StepMaterial>(MAX_RATCHET_GEN_CACHE + 2)
        private val audioReplay = AegisTorCrypto.ReplayWindow512()
        private val fecReplay = AegisTorCrypto.ReplayWindow512()
        private val videoReplay = AegisTorCrypto.ReplayWindow512()
        private val lock = Any()

        @Volatile
        private var zeroized = false

        val seedCommitment: String = commitmentHex(rootSeed)

        init {
            require(rootSeed.size == SENDER_SEED_BYTES) { "Root seed must be 32 bytes" }
            val info = ByteBuffer.allocate(SFRAME_HKDF_INFO.length + 8 + 4 + 2)
                .put(SFRAME_HKDF_INFO.toByteArray(Charsets.UTF_8))
                .putLong(roomHash8)
                .putInt(senderTag4)
                .putShort((epoch and 0xFFFF).toShort())
                .array()
            val derived = AegisTorCrypto.hkdfSha256(
                ikm = rootSeed,
                salt = ByteArray(0),
                info = info,
                length = 44
            )
            chainKey0 = derived.copyOfRange(0, 32)
            ivSalt12 = derived.copyOfRange(32, 44)
            AegisTorCrypto.zeroize(derived)
            stepCache[0L] = deriveMaterial(chainKey0)
        }

        private fun hmacOne(key: ByteArray, tagByte: Byte): ByteArray {
            val mac = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(key, "HmacSHA256"))
                update(tagByte)
            }
            return mac.doFinal()
        }

        private fun deriveMaterial(chainKey: ByteArray): StepMaterial {
            val mediaKey = hmacOne(chainKey, 0x02)
            return StepMaterial(
                chainKey = chainKey,
                mediaKey = mediaKey,
                keySpec = SecretKeySpec(mediaKey, "AES")
            )
        }

        private fun getKeySpecForCounterLocked(counter: Long): SecretKeySpec? {
            if (zeroized || counter < 0L) return null
            val targetGen = (counter / RATCHET_INTERVAL_FRAMES).coerceAtLeast(0L)
            stepCache[targetGen]?.let { return it.keySpec }

            // Start from highest cached generation <= targetGen, else from chainKey0
            val baseEntry = stepCache.entries
                .filter { it.key <= targetGen }
                .maxByOrNull { it.key }

            var curGen = baseEntry?.key ?: 0L
            var curChain = baseEntry?.value?.chainKey?.copyOf() ?: chainKey0.copyOf()
            if (baseEntry == null) {
                stepCache[0L] = deriveMaterial(curChain.copyOf())
            }

            while (curGen < targetGen) {
                val nextChain = hmacOne(curChain, 0x01)
                AegisTorCrypto.zeroize(curChain)
                curChain = nextChain
                curGen++
                if (!stepCache.containsKey(curGen) && targetGen - curGen < MAX_RATCHET_GEN_CACHE) {
                    stepCache[curGen] = deriveMaterial(curChain.copyOf())
                }
            }
            val finalMat = deriveMaterial(curChain)
            stepCache[targetGen] = finalMat
            while (stepCache.size > MAX_RATCHET_GEN_CACHE) {
                val oldestKey = stepCache.keys.minOrNull() ?: break
                if (oldestKey == targetGen) break
                stepCache.remove(oldestKey)?.zeroize()
            }
            return finalMat.keySpec
        }

        private fun buildNonce(epoch: Int, counter: Long): ByteArray {
            val iv = ivSalt12.copyOf()
            // XOR lower 8 bytes with [epoch:2B][counter:6B]
            iv[4] = (iv[4].toInt() xor ((epoch ushr 8) and 0xFF)).toByte()
            iv[5] = (iv[5].toInt() xor (epoch and 0xFF)).toByte()
            iv[6] = (iv[6].toInt() xor ((counter ushr 40) and 0xFF).toInt()).toByte()
            iv[7] = (iv[7].toInt() xor ((counter ushr 32) and 0xFF).toInt()).toByte()
            iv[8] = (iv[8].toInt() xor ((counter ushr 24) and 0xFF).toInt()).toByte()
            iv[9] = (iv[9].toInt() xor ((counter ushr 16) and 0xFF).toInt()).toByte()
            iv[10] = (iv[10].toInt() xor ((counter ushr 8) and 0xFF).toInt()).toByte()
            iv[11] = (iv[11].toInt() xor (counter and 0xFF).toInt()).toByte()
            return iv
        }

        fun seal(
            magic: Byte,
            roomHash8: Long,
            counter: Long,
            plaintext: ByteArray
        ): ByteArray? {
            if (zeroized || counter < 0L) return null
            val keySpec = synchronized(lock) { getKeySpecForCounterLocked(counter) } ?: return null
            val out = ByteArray(SFRAME_HEADER_BYTES + plaintext.size + GCM_TAG_BYTES)
            out[0] = magic
            val hdr = ByteBuffer.wrap(out, 1, SFRAME_HEADER_BYTES - 1)
            hdr.putLong(roomHash8)
            hdr.putInt(senderTag4)
            hdr.putShort((epoch and 0xFFFF).toShort())
            for (shift in 40 downTo 0 step 8) {
                hdr.put(((counter ushr shift) and 0xFF).toByte())
            }

            val nonce = buildNonce(epoch, counter)
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
                cipher.updateAAD(out, 0, SFRAME_HEADER_BYTES)
                val written = cipher.doFinal(plaintext, 0, plaintext.size, out, SFRAME_HEADER_BYTES)
                if (SFRAME_HEADER_BYTES + written == out.size) {
                    out
                } else {
                    out.copyOfRange(0, SFRAME_HEADER_BYTES + written)
                }
            } catch (_: Exception) {
                null
            } finally {
                AegisTorCrypto.zeroize(nonce)
            }
        }

        fun open(header: SFrameHeader, packet: ByteArray): ByteArray? {
            if (zeroized || header.epoch != epoch || header.senderTag4 != senderTag4) return null
            val replay = when (header.magic) {
                GROUP_SFRAME_AUDIO_MAGIC -> audioReplay
                GROUP_SFRAME_AUDIO_FEC_MAGIC -> fecReplay
                GROUP_SFRAME_VIDEO_MAGIC -> videoReplay
                else -> return null
            }
            val keySpec = synchronized(lock) { getKeySpecForCounterLocked(header.counter) } ?: return null
            val nonce = buildNonce(epoch, header.counter)
            val plaintext = try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(GCM_TAG_BITS, nonce))
                cipher.updateAAD(packet, 0, SFRAME_HEADER_BYTES)
                cipher.doFinal(packet, SFRAME_HEADER_BYTES, packet.size - SFRAME_HEADER_BYTES)
            } catch (_: Exception) {
                null
            } finally {
                AegisTorCrypto.zeroize(nonce)
            }
            if (plaintext == null) return null
            if (!replay.checkAndMark(header.counter)) {
                return null
            }
            return plaintext
        }

        fun zeroize() {
            synchronized(lock) {
                zeroized = true
                AegisTorCrypto.zeroize(chainKey0, ivSalt12)
                stepCache.values.forEach { it.zeroize() }
                stepCache.clear()
            }
        }
    }

    /**
     * Room-scoped SFrame keyring supporting zero-drop join transitions (`retainPreviousGraceMs > 0`)
     * and immediate forward-secret eviction on leave/kick (`retainPreviousGraceMs = 0L`).
     */
    class GroupCryptoRoomSession(
        val groupId: String,
        val callId: String,
        val localOnion: String
    ) {
        val roomHash8: Long = roomHash8(groupId, callId)
        val localSenderTag4: Int = senderTag4(localOnion)

        @Volatile
        var currentEpoch: Int = 1
            private set

        @Volatile
        private var graceEpoch: Int = -1

        @Volatile
        private var graceDeadlineMs: Long = 0L

        @Volatile
        private var zeroized: Boolean = false

        private val lock = Any()
        private var audioCounter = 0L
        private var fecCounter = 0L
        private var videoCounter = 0L

        private val localSendSeeds = ConcurrentHashMap<Int, ByteArray>() // epoch -> rootSeed
        private val senderStates = ConcurrentHashMap<Long, SenderKeyState>() // packKey(tag, epoch) -> state

        private fun packKey(senderTag4: Int, epoch: Int): Long =
            ((senderTag4.toLong() and 0xFFFFFFFFL) shl 16) or (epoch.toLong() and 0xFFFFL)

        fun ensureLocalEpochKey(epoch: Int): ByteArray {
            synchronized(lock) {
                require(!zeroized) { "Room crypto session has been zeroized" }
                val existing = localSendSeeds[epoch]
                if (existing != null) return existing.copyOf()
                val seed = generateRootSeed()
                localSendSeeds[epoch] = seed
                val state = SenderKeyState(
                    senderOnion = canonicalizeOnion(localOnion).ifBlank { localOnion.trim().lowercase() },
                    senderTag4 = localSenderTag4,
                    epoch = epoch,
                    roomHash8 = roomHash8,
                    rootSeed = seed
                )
                senderStates[packKey(localSenderTag4, epoch)] = state
                return seed.copyOf()
            }
        }

        fun installPeerSenderKey(
            senderOnion: String,
            epoch: Int,
            rootSeed: ByteArray
        ) {
            if (zeroized || rootSeed.size != SENDER_SEED_BYTES) return
            val canon = canonicalizeOnion(senderOnion).ifBlank { senderOnion.trim().lowercase() }
            val tag = senderTag4(canon)
            synchronized(lock) {
                if (epoch < currentEpoch && !(epoch == graceEpoch && System.currentTimeMillis() <= graceDeadlineMs)) {
                    return
                }
                val key = packKey(tag, epoch)
                senderStates.remove(key)?.zeroize()
                senderStates[key] = SenderKeyState(
                    senderOnion = canon,
                    senderTag4 = tag,
                    epoch = epoch,
                    roomHash8 = roomHash8,
                    rootSeed = rootSeed
                )
            }
        }

        /**
         * Advances the conference room to [newEpoch].
         * - Passing [retainPreviousGraceMs] = 0L (leave or kick!) immediately purges & zeroizes
         *   all prior-epoch keys so evicted peers cannot decrypt or inject another frame.
         */
        fun rotateEpoch(
            newEpoch: Int,
            retainPreviousGraceMs: Long = 0L,
            nowMs: Long = System.currentTimeMillis()
        ): ByteArray {
            synchronized(lock) {
                val safeNewEpoch = newEpoch.coerceIn(1, 0xFFFF)
                val oldEpoch = currentEpoch
                if (retainPreviousGraceMs > 0L && oldEpoch in 1 until safeNewEpoch) {
                    graceEpoch = oldEpoch
                    graceDeadlineMs = nowMs + retainPreviousGraceMs
                } else {
                    graceEpoch = -1
                    graceDeadlineMs = 0L
                }
                currentEpoch = safeNewEpoch
                audioCounter = 0L
                fecCounter = 0L
                videoCounter = 0L

                val stateIter = senderStates.entries.iterator()
                while (stateIter.hasNext()) {
                    val entry = stateIter.next()
                    val ep = entry.value.epoch
                    val keepAsGrace = (ep == graceEpoch && retainPreviousGraceMs > 0L)
                    if (ep < safeNewEpoch && !keepAsGrace) {
                        entry.value.zeroize()
                        stateIter.remove()
                    }
                }
                val seedIter = localSendSeeds.entries.iterator()
                while (seedIter.hasNext()) {
                    val entry = seedIter.next()
                    if (entry.key < safeNewEpoch) {
                        AegisTorCrypto.zeroize(entry.value)
                        seedIter.remove()
                    }
                }
                return ensureLocalEpochKey(safeNewEpoch)
            }
        }

        fun removePeerAndRotateEpoch(
            departedOnion: String,
            newEpoch: Int = currentEpoch + 1,
            nowMs: Long = System.currentTimeMillis()
        ): ByteArray {
            val tag = senderTag4(departedOnion)
            synchronized(lock) {
                val iter = senderStates.entries.iterator()
                while (iter.hasNext()) {
                    val entry = iter.next()
                    if (entry.value.senderTag4 == tag) {
                        entry.value.zeroize()
                        iter.remove()
                    }
                }
                return rotateEpoch(newEpoch = newEpoch, retainPreviousGraceMs = 0L, nowMs = nowMs)
            }
        }

        fun sealAudio(plaintextAdpcm: ByteArray): ByteArray? = sealWithMagic(GROUP_SFRAME_AUDIO_MAGIC, plaintextAdpcm)
        fun sealAudioFec(plaintextFec: ByteArray): ByteArray? = sealWithMagic(GROUP_SFRAME_AUDIO_FEC_MAGIC, plaintextFec)
        fun sealVideoFragment(plaintextFragment: ByteArray): ByteArray? = sealWithMagic(GROUP_SFRAME_VIDEO_MAGIC, plaintextFragment)

        private fun sealWithMagic(magic: Byte, plaintext: ByteArray): ByteArray? {
            if (zeroized) return null
            val ep: Int
            val state: SenderKeyState
            val ctr: Long
            synchronized(lock) {
                ep = currentEpoch
                ensureLocalEpochKey(ep)
                state = senderStates[packKey(localSenderTag4, ep)] ?: return null
                ctr = when (magic) {
                    GROUP_SFRAME_AUDIO_MAGIC -> audioCounter++
                    GROUP_SFRAME_AUDIO_FEC_MAGIC -> fecCounter++
                    GROUP_SFRAME_VIDEO_MAGIC -> videoCounter++
                    else -> return null
                }
            }
            return state.seal(magic, roomHash8, ctr, plaintext)
        }

        fun open(packet: ByteArray, nowMs: Long = System.currentTimeMillis()): OpenedSFrame? {
            if (zeroized) return null
            val header = parseHeader(packet) ?: return null
            if (header.roomHash8 != roomHash8) return null
            if (header.senderTag4 == localSenderTag4) return null // Reject reflected self-packets

            val state = synchronized(lock) {
                if (header.epoch != currentEpoch) {
                    val inGrace = header.epoch == graceEpoch && nowMs <= graceDeadlineMs
                    if (!inGrace) return null
                }
                senderStates[packKey(header.senderTag4, header.epoch)]
            } ?: return null

            val plaintext = state.open(header, packet) ?: return null
            return OpenedSFrame(header, plaintext)
        }

        fun getCommitmentMap(activeParticipantOnions: Collection<String>): List<Pair<String, String>> {
            val epoch = currentEpoch
            return synchronized(lock) {
                activeParticipantOnions.mapNotNull { onion ->
                    val canon = canonicalizeOnion(onion).ifBlank { onion.trim().lowercase() }
                    val tag = senderTag4(canon)
                    val commit = senderStates[packKey(tag, epoch)]?.seedCommitment ?: return@mapNotNull null
                    canon to commit
                }
            }
        }

        fun hasKeyForPeer(senderOnion: String, epoch: Int = currentEpoch): Boolean {
            val tag = senderTag4(senderOnion)
            return senderStates.containsKey(packKey(tag, epoch))
        }

        fun zeroize() {
            synchronized(lock) {
                zeroized = true
                senderStates.values.forEach { it.zeroize() }
                senderStates.clear()
                localSendSeeds.values.forEach { AegisTorCrypto.zeroize(it) }
                localSendSeeds.clear()
            }
        }
    }
}
