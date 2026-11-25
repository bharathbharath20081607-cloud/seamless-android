package com.seamless.android.net

import com.google.gson.Gson
import com.seamless.android.crypto.AegisTorCrypto
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Chrono-DAG Peer-Assisted Tor Group Synchronization Engine.
 *
 * Replaces fragile pairwise creator-only group delivery with an Ed25519-signed causal Merkle-DAG,
 * monotonic membership epochs with forward secret ratcheting, and bounded anti-entropy
 * sync offers/requests/batches so any online group member can heal offline partitions.
 */
class ChronoDagGroupSync {

    companion object {
        const val MAX_DAG_EVENTS_PER_GROUP = 200
        const val MAX_HEADS = 8
        const val MAX_OFFER_IDS = 48
        const val MAX_REQUEST_IDS = 24
        const val MAX_SYNC_BATCH_JSON_BYTES = 2_400

        private const val SIGN_DOMAIN = "SeamlessChronoDag/v1"
        private const val EPOCH_COMMIT_DOMAIN = "SeamlessEpochCommit/v1"
        private const val MAX_TOMBSTONED_GROUPS = 256
        private const val MAX_SEEN_CONTROL_IDS = 1_024
        private val HEX_32B_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private val HEX_64B_REGEX = Regex("^[0-9a-fA-F]{128}$")
        private val secureRandom = SecureRandom()
        private val gson = Gson()

        fun generateEd25519KeyPair(): Pair<ByteArray, ByteArray> {
            val gen = Ed25519KeyPairGenerator()
            gen.init(Ed25519KeyGenerationParameters(secureRandom))
            val pair = gen.generateKeyPair()
            val priv = (pair.private as Ed25519PrivateKeyParameters).encoded
            val pub = (pair.public as Ed25519PublicKeyParameters).encoded
            return priv to pub
        }

        fun derivePublicKey(privateKeyBytes: ByteArray): ByteArray {
            require(privateKeyBytes.size == 32) { "Ed25519 private key must be 32 bytes" }
            val priv = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            return priv.generatePublicKey().encoded
        }

        fun canonicalSigningBytes(
            eventId: String,
            groupId: String,
            epoch: Long,
            lamportClock: Long,
            parentIds: List<String>,
            type: String,
            authorOnion: String,
            authorSigningPubKey: String,
            timestamp: Long,
            payloadJson: String,
            epochCommitment: String
        ): ByteArray {
            val payloadDigest = MessageDigest.getInstance("SHA-256")
                .digest(payloadJson.toByteArray(Charsets.UTF_8))
            val baos = ByteArrayOutputStream(256)
            DataOutputStream(baos).use { out ->
                fun writeStr(value: String) {
                    val bytes = value.toByteArray(Charsets.UTF_8)
                    out.writeInt(bytes.size)
                    out.write(bytes)
                }
                writeStr(SIGN_DOMAIN)
                writeStr(eventId)
                writeStr(groupId)
                out.writeLong(epoch)
                out.writeLong(lamportClock)
                out.writeInt(parentIds.size)
                parentIds.forEach { writeStr(it) }
                writeStr(type)
                writeStr(authorOnion.trim().lowercase())
                writeStr(authorSigningPubKey.trim().lowercase())
                out.writeLong(timestamp)
                out.writeInt(payloadDigest.size)
                out.write(payloadDigest)
                writeStr(epochCommitment.trim().lowercase())
            }
            return baos.toByteArray()
        }

        fun signEvent(
            privateKeyBytes: ByteArray,
            eventId: String,
            groupId: String,
            epoch: Long,
            lamportClock: Long,
            parentIds: List<String>,
            type: String,
            authorOnion: String,
            timestamp: Long,
            payloadJson: String,
            epochCommitment: String = ""
        ): SignedDagEvent {
            val priv = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
            val pubHex = toHex(priv.generatePublicKey().encoded)
            val normalizedOnion = authorOnion.trim().lowercase()
            val message = canonicalSigningBytes(
                eventId = eventId,
                groupId = groupId,
                epoch = epoch,
                lamportClock = lamportClock,
                parentIds = parentIds,
                type = type,
                authorOnion = normalizedOnion,
                authorSigningPubKey = pubHex,
                timestamp = timestamp,
                payloadJson = payloadJson,
                epochCommitment = epochCommitment
            )
            val signer = Ed25519Signer()
            signer.init(true, priv)
            signer.update(message, 0, message.size)
            val signatureHex = toHex(signer.generateSignature())
            return SignedDagEvent(
                eventId = eventId,
                groupId = groupId,
                epoch = epoch,
                lamportClock = lamportClock,
                parentIds = parentIds,
                type = type,
                authorOnion = normalizedOnion,
                authorSigningPubKey = pubHex,
                timestamp = timestamp,
                payloadJson = payloadJson,
                epochCommitment = epochCommitment,
                signatureHex = signatureHex
            )
        }

        fun verifySignature(event: SignedDagEvent): Boolean {
            if (event.eventId.isBlank() ||
                event.groupId.isBlank() ||
                event.type.isBlank() ||
                event.authorOnion.isBlank() ||
                event.epoch < 1L ||
                event.lamportClock < 0L ||
                event.parentIds.size > MAX_HEADS * 2
            ) {
                return false
            }
            if (!event.authorSigningPubKey.matches(HEX_32B_REGEX) ||
                !event.signatureHex.matches(HEX_64B_REGEX)
            ) {
                return false
            }
            val pubBytes = fromHex(event.authorSigningPubKey) ?: return false
            val sigBytes = fromHex(event.signatureHex) ?: return false
            if (pubBytes.size != 32 || sigBytes.size != 64) return false

            return try {
                val message = canonicalSigningBytes(
                    eventId = event.eventId,
                    groupId = event.groupId,
                    epoch = event.epoch,
                    lamportClock = event.lamportClock,
                    parentIds = event.parentIds,
                    type = event.type,
                    authorOnion = event.authorOnion,
                    authorSigningPubKey = event.authorSigningPubKey,
                    timestamp = event.timestamp,
                    payloadJson = event.payloadJson,
                    epochCommitment = event.epochCommitment
                )
                val verifier = Ed25519Signer()
                verifier.init(false, Ed25519PublicKeyParameters(pubBytes, 0))
                verifier.update(message, 0, message.size)
                verifier.verifySignature(sigBytes)
            } catch (_: Exception) {
                false
            }
        }

        fun deriveRosterHash(members: Collection<Pair<String, String>>): ByteArray {
            val canonical = members
                .map { (onion, pub) -> "${onion.trim().lowercase()}|${pub.trim().lowercase()}" }
                .distinct()
                .sorted()
            val digest = MessageDigest.getInstance("SHA-256")
            canonical.forEach { entry ->
                digest.update(entry.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
            }
            return digest.digest()
        }

        fun deriveEpochSecret(
            previousSecret: ByteArray?,
            groupId: String,
            epoch: Long,
            rosterHash: ByteArray
        ): ByteArray {
            val base = previousSecret?.takeIf { it.isNotEmpty() } ?: ByteArray(32)
            val ikm = ByteArray(base.size + rosterHash.size)
            System.arraycopy(base, 0, ikm, 0, base.size)
            System.arraycopy(rosterHash, 0, ikm, base.size, rosterHash.size)
            return AegisTorCrypto.hkdfSha256(
                ikm = ikm,
                salt = "SeamlessChronoDagEpoch/v1/$groupId/$epoch".toByteArray(Charsets.UTF_8),
                info = "epoch-key".toByteArray(Charsets.UTF_8),
                length = 32
            )
        }

        fun computeEpochCommitment(
            epochSecret: ByteArray,
            groupId: String,
            epoch: Long,
            eventId: String
        ): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(EPOCH_COMMIT_DOMAIN.toByteArray(Charsets.UTF_8))
            digest.update(epochSecret)
            digest.update(groupId.toByteArray(Charsets.UTF_8))
            digest.update(epoch.toString().toByteArray(Charsets.UTF_8))
            digest.update(eventId.toByteArray(Charsets.UTF_8))
            return toHex(digest.digest().copyOfRange(0, 16))
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
            if (clean.length % 2 != 0) return null
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

    data class SignedDagEvent(
        val eventId: String,
        val groupId: String,
        val epoch: Long,
        val lamportClock: Long,
        val parentIds: List<String>,
        val type: String,
        val authorOnion: String,
        val authorSigningPubKey: String,
        val timestamp: Long,
        val payloadJson: String,
        val epochCommitment: String = "",
        val signatureHex: String
    )

    data class SyncOffer(
        val groupId: String,
        val epoch: Long,
        val lamportClock: Long,
        val knownEventIds: List<String>
    )

    data class SyncRequest(
        val groupId: String,
        val requestedEventIds: List<String>
    )

    data class SyncBatch(
        val groupId: String,
        val events: List<SignedDagEvent>
    )

    data class GroupDagSnapshot(
        val groupId: String,
        var epoch: Long = 1L,
        var lamportClock: Long = 0L,
        val heads: LinkedHashSet<String> = linkedSetOf(),
        val events: LinkedHashMap<String, SignedDagEvent> = linkedMapOf(),
        val pinnedSigningKeys: MutableMap<String, String> = mutableMapOf(),
        var epochSecretHex: String = "",
        val admins: MutableSet<String> = mutableSetOf(),
        var disappearingTtlMs: Long = 0L
    )

    private data class PersistedDagState(
        val groups: List<PersistedGroup> = emptyList(),
        val tombstonedGroups: List<String> = emptyList(),
        val seenControlEventIds: List<String> = emptyList()
    )

    private data class PersistedGroup(
        val groupId: String,
        val epoch: Long = 1L,
        val lamportClock: Long = 0L,
        val heads: List<String> = emptyList(),
        val events: List<SignedDagEvent> = emptyList(),
        val pinnedSigningKeys: Map<String, String> = emptyMap(),
        val epochSecretHex: String = "",
        val admins: List<String> = emptyList(),
        val disappearingTtlMs: Long = 0L
    )

    private val lock = Any()
    private val groups = mutableMapOf<String, GroupDagSnapshot>()
    private val tombstonedGroups = linkedSetOf<String>()
    private val seenControlEventIds = linkedSetOf<String>()

    fun initializeGroup(
        groupId: String,
        creatorCanonicalOnion: String,
        initialMembers: Collection<Pair<String, String>>,
        epoch: Long = 1L,
        admins: Set<String> = setOf(creatorCanonicalOnion),
        disappearingTtlMs: Long = 0L
    ): GroupDagSnapshot = synchronized(lock) {
        tombstonedGroups.remove(groupId)
        val snap = groups.getOrPut(groupId) { GroupDagSnapshot(groupId = groupId) }
        snap.epoch = maxOf(snap.epoch, epoch.coerceAtLeast(1L))
        snap.admins.addAll(admins.map { it.trim().lowercase() }.filter { it.isNotBlank() })
        if (creatorCanonicalOnion.isNotBlank()) {
            snap.admins.add(creatorCanonicalOnion.trim().lowercase())
        }
        if (disappearingTtlMs > 0L && snap.disappearingTtlMs == 0L) {
            snap.disappearingTtlMs = disappearingTtlMs
        }
        if (snap.epochSecretHex.isBlank()) {
            val rosterHash = deriveRosterHash(initialMembers)
            snap.epochSecretHex = toHex(deriveEpochSecret(null, groupId, snap.epoch, rosterHash))
        }
        snap
    }

    fun advanceEpoch(
        groupId: String,
        activeMembers: Collection<Pair<String, String>>,
        targetEpoch: Long? = null
    ): Long = synchronized(lock) {
        val snap = groups.getOrPut(groupId) { GroupDagSnapshot(groupId = groupId) }
        val nextEpoch = if (targetEpoch != null) {
            maxOf(snap.epoch + 1L, targetEpoch)
        } else {
            snap.epoch + 1L
        }
        val prevSecret = fromHex(snap.epochSecretHex)
        val rosterHash = deriveRosterHash(activeMembers)
        val nextSecret = deriveEpochSecret(prevSecret, groupId, nextEpoch, rosterHash)
        snap.epoch = nextEpoch
        snap.epochSecretHex = toHex(nextSecret)
        nextEpoch
    }

    fun pinAuthorSigningKey(
        groupId: String,
        canonicalOnion: String,
        signingPubKeyHex: String
    ): Boolean = synchronized(lock) {
        val cleanKey = signingPubKeyHex.trim().lowercase()
        if (!cleanKey.matches(HEX_32B_REGEX)) return false
        val normalizedOnion = canonicalOnion.trim().lowercase()
        if (normalizedOnion.isBlank()) return false
        val snap = groups.getOrPut(groupId) { GroupDagSnapshot(groupId = groupId) }
        val existing = snap.pinnedSigningKeys[normalizedOnion]
        if (existing != null && !existing.equals(cleanKey, ignoreCase = true)) {
            return false
        }
        snap.pinnedSigningKeys[normalizedOnion] = cleanKey
        true
    }

    fun getPinnedSigningKey(groupId: String, canonicalOnion: String): String? = synchronized(lock) {
        groups[groupId]?.pinnedSigningKeys?.get(canonicalOnion.trim().lowercase())
    }

    fun createAndRecordLocalEvent(
        groupId: String,
        privateKeyBytes: ByteArray,
        eventId: String,
        type: String,
        authorCanonicalOnion: String,
        timestamp: Long,
        payloadJson: String
    ): SignedDagEvent = synchronized(lock) {
        val snap = groups.getOrPut(groupId) { GroupDagSnapshot(groupId = groupId) }
        snap.lamportClock += 1L
        val parents = snap.heads.toList().takeLast(2)
        val secret = fromHex(snap.epochSecretHex)
        val commitment = if (secret != null && secret.isNotEmpty()) {
            computeEpochCommitment(secret, groupId, snap.epoch, eventId)
        } else {
            ""
        }
        val event = signEvent(
            privateKeyBytes = privateKeyBytes,
            eventId = eventId,
            groupId = groupId,
            epoch = snap.epoch,
            lamportClock = snap.lamportClock,
            parentIds = parents,
            type = type,
            authorOnion = authorCanonicalOnion,
            timestamp = timestamp,
            payloadJson = payloadJson,
            epochCommitment = commitment
        )
        snap.pinnedSigningKeys.putIfAbsent(event.authorOnion, event.authorSigningPubKey)
        recordEventLocked(snap, event)
        event
    }

    fun acceptRemoteEvent(
        event: SignedDagEvent,
        expectedPinnedKey: String? = null
    ): Boolean = synchronized(lock) {
        if (event.groupId in tombstonedGroups) return false
        if (!verifySignature(event)) return false
        val expectedNormalized = expectedPinnedKey?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (expectedNormalized != null && !expectedNormalized.equals(event.authorSigningPubKey, ignoreCase = true)) {
            return false
        }
        val snap = groups.getOrPut(event.groupId) { GroupDagSnapshot(groupId = event.groupId) }
        val normalizedAuthor = event.authorOnion.trim().lowercase()
        val existingPinned = snap.pinnedSigningKeys[normalizedAuthor]
        if (existingPinned != null && !existingPinned.equals(event.authorSigningPubKey, ignoreCase = true)) {
            return false
        }
        if (snap.events.containsKey(event.eventId)) {
            return false
        }
        snap.pinnedSigningKeys[normalizedAuthor] = event.authorSigningPubKey.trim().lowercase()
        recordEventLocked(snap, event)
        true
    }

    private fun recordEventLocked(snap: GroupDagSnapshot, event: SignedDagEvent) {
        snap.events[event.eventId] = event
        snap.lamportClock = maxOf(snap.lamportClock, event.lamportClock)
        snap.epoch = maxOf(snap.epoch, event.epoch)
        event.parentIds.forEach { snap.heads.remove(it) }
        snap.heads.add(event.eventId)
        while (snap.heads.size > MAX_HEADS) {
            val first = snap.heads.firstOrNull() ?: break
            snap.heads.remove(first)
        }
        while (snap.events.size > MAX_DAG_EVENTS_PER_GROUP) {
            val oldest = snap.events.entries.firstOrNull()?.key ?: break
            snap.events.remove(oldest)
            snap.heads.remove(oldest)
        }
    }

    fun hasEvent(groupId: String, eventId: String): Boolean = synchronized(lock) {
        groups[groupId]?.events?.containsKey(eventId) == true
    }

    fun markControlEventSeen(eventId: String): Boolean = synchronized(lock) {
        if (eventId.isBlank() || eventId in seenControlEventIds) return false
        seenControlEventIds.add(eventId)
        while (seenControlEventIds.size > MAX_SEEN_CONTROL_IDS) {
            val oldest = seenControlEventIds.firstOrNull() ?: break
            seenControlEventIds.remove(oldest)
        }
        true
    }

    fun tombstoneGroup(groupId: String) = synchronized(lock) {
        tombstonedGroups.add(groupId)
        while (tombstonedGroups.size > MAX_TOMBSTONED_GROUPS) {
            val oldest = tombstonedGroups.firstOrNull() ?: break
            tombstonedGroups.remove(oldest)
        }
    }

    fun isGroupTombstoned(groupId: String): Boolean = synchronized(lock) {
        groupId in tombstonedGroups
    }

    fun untombstoneGroup(groupId: String) = synchronized(lock) {
        tombstonedGroups.remove(groupId)
    }

    fun isAdmin(groupId: String, canonicalOnion: String): Boolean = synchronized(lock) {
        canonicalOnion.trim().lowercase() in (groups[groupId]?.admins ?: emptySet())
    }

    fun getAdmins(groupId: String): Set<String> = synchronized(lock) {
        groups[groupId]?.admins?.toSet() ?: emptySet()
    }

    fun addAdmin(groupId: String, canonicalOnion: String) = synchronized(lock) {
        val clean = canonicalOnion.trim().lowercase()
        if (clean.isNotBlank()) {
            groups.getOrPut(groupId) { GroupDagSnapshot(groupId = groupId) }.admins.add(clean)
        }
    }

    fun removeAdmin(groupId: String, canonicalOnion: String) = synchronized(lock) {
        groups[groupId]?.admins?.remove(canonicalOnion.trim().lowercase())
    }

    fun setAdmins(groupId: String, admins: Set<String>) = synchronized(lock) {
        val snap = groups.getOrPut(groupId) { GroupDagSnapshot(groupId = groupId) }
        snap.admins.clear()
        snap.admins.addAll(admins.map { it.trim().lowercase() }.filter { it.isNotBlank() })
    }

    fun getEpoch(groupId: String): Long = synchronized(lock) {
        groups[groupId]?.epoch ?: 1L
    }

    fun getDisappearingTtlMs(groupId: String): Long = synchronized(lock) {
        groups[groupId]?.disappearingTtlMs ?: 0L
    }

    fun setDisappearingTtlMs(groupId: String, ttlMs: Long) = synchronized(lock) {
        groups.getOrPut(groupId) { GroupDagSnapshot(groupId = groupId) }.disappearingTtlMs = ttlMs.coerceAtLeast(0L)
    }

    fun getAllEpochs(): Map<String, Long> = synchronized(lock) {
        groups.mapValues { it.value.epoch }
    }

    fun getAllAdmins(): Map<String, Set<String>> = synchronized(lock) {
        groups.mapValues { it.value.admins.toSet() }
    }

    fun getAllDisappearingTtls(): Map<String, Long> = synchronized(lock) {
        groups.mapValues { it.value.disappearingTtlMs }
    }

    fun removeGroupState(groupId: String) = synchronized(lock) {
        groups.remove(groupId)
    }

    fun buildSyncOffer(groupId: String): SyncOffer? = synchronized(lock) {
        val snap = groups[groupId] ?: return null
        if (snap.events.isEmpty()) return null
        val newestIds = snap.events.keys.toList().takeLast(MAX_OFFER_IDS)
        SyncOffer(
            groupId = groupId,
            epoch = snap.epoch,
            lamportClock = snap.lamportClock,
            knownEventIds = newestIds
        )
    }

    fun computeMissingEventIds(groupId: String, offer: SyncOffer): List<String> = synchronized(lock) {
        if (groupId != offer.groupId || groupId in tombstonedGroups) return emptyList()
        val snap = groups[groupId] ?: GroupDagSnapshot(groupId = groupId)
        offer.knownEventIds
            .asSequence()
            .filter { it.isNotBlank() && !snap.events.containsKey(it) }
            .distinct()
            .take(MAX_REQUEST_IDS)
            .toList()
    }

    fun buildSyncBatches(
        groupId: String,
        requestedIds: List<String>,
        maxBatchJsonBytes: Int = MAX_SYNC_BATCH_JSON_BYTES
    ): List<SyncBatch> = synchronized(lock) {
        val snap = groups[groupId] ?: return emptyList()
        val candidateEvents = requestedIds
            .asSequence()
            .distinct()
            .take(MAX_REQUEST_IDS)
            .mapNotNull { snap.events[it] }
            .toList()
        if (candidateEvents.isEmpty()) return emptyList()

        val batches = mutableListOf<SyncBatch>()
        val current = mutableListOf<SignedDagEvent>()
        for (ev in candidateEvents) {
            val tentative = current + ev
            val size = gson.toJson(SyncBatch(groupId, tentative)).toByteArray(Charsets.UTF_8).size
            if (size <= maxBatchJsonBytes) {
                current.add(ev)
            } else {
                if (current.isNotEmpty()) {
                    batches.add(SyncBatch(groupId, current.toList()))
                    current.clear()
                }
                val singleSize = gson.toJson(SyncBatch(groupId, listOf(ev))).toByteArray(Charsets.UTF_8).size
                if (singleSize <= maxBatchJsonBytes) {
                    current.add(ev)
                }
            }
        }
        if (current.isNotEmpty()) {
            batches.add(SyncBatch(groupId, current.toList()))
        }
        batches
    }

    fun exportStateJson(): String = synchronized(lock) {
        val persisted = PersistedDagState(
            groups = groups.values.map { snap ->
                PersistedGroup(
                    groupId = snap.groupId,
                    epoch = snap.epoch,
                    lamportClock = snap.lamportClock,
                    heads = snap.heads.toList(),
                    events = snap.events.values.toList(),
                    pinnedSigningKeys = snap.pinnedSigningKeys.toMap(),
                    epochSecretHex = snap.epochSecretHex,
                    admins = snap.admins.toList(),
                    disappearingTtlMs = snap.disappearingTtlMs
                )
            },
            tombstonedGroups = tombstonedGroups.toList(),
            seenControlEventIds = seenControlEventIds.toList()
        )
        gson.toJson(persisted)
    }

    fun importStateJson(json: String) {
        val parsed = runCatching { gson.fromJson(json, PersistedDagState::class.java) }.getOrNull() ?: return
        synchronized(lock) {
            groups.clear()
            tombstonedGroups.clear()
            seenControlEventIds.clear()

            parsed.tombstonedGroups.takeLast(MAX_TOMBSTONED_GROUPS).forEach { tombstonedGroups.add(it) }
            parsed.seenControlEventIds.takeLast(MAX_SEEN_CONTROL_IDS).forEach { seenControlEventIds.add(it) }
            parsed.groups.forEach { pg ->
                val snap = GroupDagSnapshot(
                    groupId = pg.groupId,
                    epoch = pg.epoch.coerceAtLeast(1L),
                    lamportClock = pg.lamportClock.coerceAtLeast(0L),
                    epochSecretHex = pg.epochSecretHex,
                    disappearingTtlMs = pg.disappearingTtlMs.coerceAtLeast(0L)
                )
                pg.heads.takeLast(MAX_HEADS).forEach { snap.heads.add(it) }
                pg.events.takeLast(MAX_DAG_EVENTS_PER_GROUP).forEach { ev -> snap.events[ev.eventId] = ev }
                snap.pinnedSigningKeys.putAll(pg.pinnedSigningKeys)
                snap.admins.addAll(pg.admins.map { it.trim().lowercase() }.filter { it.isNotBlank() })
                groups[pg.groupId] = snap
            }
        }
    }
}
