package com.seamless

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.seamless.android.call.CallAudioEngine
import com.seamless.android.call.CallFluxFec
import com.seamless.android.call.SeamlessCallManager
import com.seamless.android.call.WebRtcVideoCallEngine
import com.seamless.android.crypto.AegisCallCrypto
import com.seamless.android.crypto.AegisTorCrypto
import com.seamless.android.crypto.PadmeCellPadder
import com.seamless.android.crypto.X25519KeyPair
import com.seamless.android.data.AppDatabase
import com.seamless.android.data.GroupMessageEntity
import com.seamless.android.data.PrivateGroupEntity
import com.seamless.android.identity.SecureIdentityStateManager
import com.seamless.android.net.ChronoDagGroupSync
import com.seamless.android.net.OnionContactManager
import com.seamless.android.net.TorContactTrustState
import com.seamless.android.net.TorGroupMessaging
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class AegisFluxHardeningTest {

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase
    private lateinit var myOnionHost: String
    private lateinit var myOnionEndpoint: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setInstanceForTesting(testDb)
        TorGroupMessaging.resetForTesting()
        myOnionHost = validV3OnionHost(0)
        myOnionEndpoint = "$myOnionHost:18888"
        runCatching {
            SecureIdentityStateManager(context).storeSecureValue("tor_hs_onion_address", myOnionHost)
        }
    }

    @After
    fun tearDown() {
        TorGroupMessaging.resetForTesting()
        AppDatabase.setInstanceForTesting(null)
        runCatching { testDb.close() }
    }

    private fun validV3OnionHost(index: Int): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz234567"
        val sb = StringBuilder(56)
        var x = index + 17
        for (i in 0 until 56) {
            x = (x * 1103515245 + 12345) and 0x7FFFFFFF
            sb.append(alphabet[(x + i + index) % alphabet.length])
        }
        return "$sb.onion"
    }

    private fun validHexKey(seed: Int): String {
        val hex = "0123456789abcdef"
        val sb = StringBuilder(64)
        var x = seed + 31
        for (i in 0 until 64) {
            x = (x * 1664525 + 1013904223) and 0x7FFFFFFF
            sb.append(hex[(x + i) and 0x0F])
        }
        return sb.toString()
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(bytes) }
        return baos.toByteArray()
    }

    // ─────────────────────────── 1. Aegis-Tor v2 & Padmé ───────────────────────────

    @Test
    fun stdpV2_roundTrip_directionSeparation_and_replayWindow64() {
        val alice = X25519KeyPair.generate()
        val bob = X25519KeyPair.generate()
        val aad = "frame-header-aad".toByteArray()
        val plaintext = "Confidential Tor P2P payload".toByteArray()

        val sealed = AegisTorCrypto.sealDirectV2(
            plaintext = plaintext,
            frameAad = aad,
            senderStaticPriv = alice.privateKey,
            senderStaticPub = alice.publicKey,
            recipientStaticPub = bob.publicKey,
            counter = 7L
        )

        // Bob decrypts Alice -> Bob cleanly
        val opened = AegisTorCrypto.openDirectV2(
            payload = sealed,
            frameAad = aad,
            recipientStaticPriv = bob.privateKey,
            recipientStaticPub = bob.publicKey,
            senderStaticPub = alice.publicKey
        )
        assertNotNull(opened)
        assertArrayEquals(plaintext, opened!!.plaintext)
        assertEquals(7L, opened.counter)

        // Reflection attack: reflecting Alice's ciphertext back to Alice MUST fail due to directional HKDF + AAD
        val reflected = AegisTorCrypto.openDirectV2(
            payload = sealed,
            frameAad = aad,
            recipientStaticPriv = alice.privateKey,
            recipientStaticPub = alice.publicKey,
            senderStaticPub = bob.publicKey
        )
        assertNull("Reflected STDP v2 ciphertext must fail authentication", reflected)

        // Tampered AAD must fail
        val badAad = AegisTorCrypto.openDirectV2(
            payload = sealed,
            frameAad = "wrong-aad".toByteArray(),
            recipientStaticPriv = bob.privateKey,
            recipientStaticPub = bob.publicKey,
            senderStaticPub = alice.publicKey
        )
        assertNull(badAad)

        // ReplayWindow64: accepts monotonic + out-of-order within 64, rejects duplicates & stale >= 64
        val window = AegisTorCrypto.ReplayWindow64()
        assertFalse(window.checkAndMark(-1L))
        assertTrue(window.checkAndMark(10L))
        assertFalse("Duplicate sequence must be rejected", window.checkAndMark(10L))
        assertTrue("Out-of-order within window must succeed", window.checkAndMark(5L))
        assertFalse("Duplicate out-of-order must be rejected", window.checkAndMark(5L))
        assertTrue(window.checkAndMark(75L))
        assertFalse("Sequence older than 64 behind max must be rejected", window.checkAndMark(11L))
        assertTrue("Sequence exactly 63 behind max must still succeed", window.checkAndMark(12L))
    }

    @Test
    fun stmbV2_noiseIkMailboxEnvelope_authenticatesSender_and_rejectsTamper() {
        val alice = X25519KeyPair.generate()
        val bob = X25519KeyPair.generate()
        val mallory = X25519KeyPair.generate()
        val plaintext = "Offline store-and-forward payload".toByteArray()

        val envelope = AegisTorCrypto.sealMailboxV2(
            plaintext = plaintext,
            senderStaticPriv = alice.privateKey,
            senderStaticPub = alice.publicKey,
            recipientStaticPub = bob.publicKey
        )

        val opened = AegisTorCrypto.openMailboxV2(
            payload = envelope,
            recipientStaticPriv = bob.privateKey,
            recipientStaticPub = bob.publicKey
        )
        assertNotNull(opened)
        assertArrayEquals(plaintext, opened!!.plaintext)
        assertArrayEquals(alice.publicKey, opened.senderPublicKey)

        // Wrong recipient cannot open
        assertNull(
            AegisTorCrypto.openMailboxV2(
                payload = envelope,
                recipientStaticPriv = mallory.privateKey,
                recipientStaticPub = mallory.publicKey
            )
        )

        // Tampering with masked sender key or ciphertext invalidates AEAD tag
        val tampered = envelope.copyOf()
        tampered[tampered.size - 1] = (tampered.last().toInt() xor 0x01).toByte()
        assertNull(AegisTorCrypto.openMailboxV2(tampered, bob.privateKey, bob.publicKey))
    }

    @Test
    fun pairwiseOutboxToken_isSymmetric_and_lowOrderSharedSecretRejected() {
        val alice = X25519KeyPair.generate()
        val bob = X25519KeyPair.generate()
        val eve = X25519KeyPair.generate()
        val folder = "abc123folder"

        val tokenA = AegisTorCrypto.derivePairwiseOutboxToken(alice.privateKey, bob.publicKey, folder)
        val tokenB = AegisTorCrypto.derivePairwiseOutboxToken(bob.privateKey, alice.publicKey, folder.uppercase())
        val tokenEve = AegisTorCrypto.derivePairwiseOutboxToken(eve.privateKey, bob.publicKey, folder)

        assertEquals(64, tokenA.length)
        assertEquals("Pairwise outbox token must be symmetric", tokenA, tokenB)
        assertNotEquals("Third party cannot derive pairwise outbox token", tokenA, tokenEve)

        assertTrue(AegisTorCrypto.isAllZero(ByteArray(32)))
        try {
            AegisTorCrypto.requireNonZeroSharedSecret(ByteArray(32))
            fail("All-zero X25519 shared secret must throw SecurityException")
        } catch (_: SecurityException) {
            // expected
        }
    }

    @Test
    fun padmeCellPadder_alignsTo498ByteTorCells_and_roundTripsSizes() {
        val testSizes = listOf(0, 1, 7, 63, 494, 495, 1_024, 3_699, 10_000)
        for (size in testSizes) {
            val data = ByteArray(size) { ((it * 19 + size) and 0xFF).toByte() }
            val padded = PadmeCellPadder.pad(data, alignToTorCell = true)
            assertEquals(
                "Padded length for $size must be exact multiple of 498-byte Tor RELAY_DATA cell",
                0,
                padded.size % PadmeCellPadder.TOR_RELAY_CELL_PAYLOAD_BYTES
            )
            assertTrue(padded.size >= data.size + 4)
            val unpadded = PadmeCellPadder.unpad(padded)
            assertNotNull(unpadded)
            assertArrayEquals(data, unpadded)
        }
        // Malformed headers must return null
        assertNull(PadmeCellPadder.unpad(byteArrayOf(0, 1, 2)))
        assertNull(PadmeCellPadder.unpad(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x01, 0x02)))
    }

    @Test
    fun safetyNumber_isSymmetricAcrossPeers() {
        val keyA = validHexKey(1)
        val keyB = validHexKey(2)
        val contactBOnA = OnionContactManager.OnionContact(
            id = "b",
            nickname = "Bob",
            onionAddress = "${validV3OnionHost(2)}:18888",
            publicKey = keyB
        )
        val contactAOnB = OnionContactManager.OnionContact(
            id = "a",
            nickname = "Alice",
            onionAddress = "${validV3OnionHost(1)}:18888",
            publicKey = keyA
        )
        assertEquals(contactBOnA.safetyNumber(keyA), contactAOnB.safetyNumber(keyB))
    }

    // ─────────────────────────── 2. Chrono-DAG Tor Groups ───────────────────────────

    @Test
    fun chronoDag_ed25519Signing_verifiesAuthenticity_and_blocksTamperAndKeySubstitution() {
        val (alicePriv, alicePub) = ChronoDagGroupSync.generateEd25519KeyPair()
        val (malloryPriv, _) = ChronoDagGroupSync.generateEd25519KeyPair()
        val groupId = UUID.randomUUID().toString()
        val aliceOnion = "${validV3OnionHost(10)}:18888"

        val aliceDag = ChronoDagGroupSync()
        aliceDag.initializeGroup(groupId, aliceOnion, listOf(aliceOnion to validHexKey(10)))
        val signed = aliceDag.createAndRecordLocalEvent(
            groupId = groupId,
            privateKeyBytes = alicePriv,
            eventId = UUID.randomUUID().toString(),
            type = "message",
            authorCanonicalOnion = aliceOnion,
            timestamp = 1_700_000_000_000L,
            payloadJson = """{"messageText":"hello dag"}"""
        )

        assertTrue(ChronoDagGroupSync.verifySignature(signed))
        assertFalse(
            "Tampered payload must fail Ed25519 verification",
            ChronoDagGroupSync.verifySignature(signed.copy(payloadJson = """{"messageText":"forged"}"""))
        )

        val bobDag = ChronoDagGroupSync()
        bobDag.initializeGroup(groupId, aliceOnion, listOf(aliceOnion to validHexKey(10)))
        assertTrue(bobDag.acceptRemoteEvent(signed))
        assertEquals(ChronoDagGroupSync.toHex(alicePub), bobDag.getPinnedSigningKey(groupId, aliceOnion))

        // Mallory signs an event claiming to be aliceOnion with Mallory's key -> blocked by pinned key!
        val spoofed = ChronoDagGroupSync.signEvent(
            privateKeyBytes = malloryPriv,
            eventId = UUID.randomUUID().toString(),
            groupId = groupId,
            epoch = 1L,
            lamportClock = 2L,
            parentIds = listOf(signed.eventId),
            type = "message",
            authorOnion = aliceOnion,
            timestamp = 1_700_000_001_000L,
            payloadJson = """{"messageText":"spoofed"}"""
        )
        assertTrue("Signature alone is structurally valid", ChronoDagGroupSync.verifySignature(spoofed))
        assertFalse("Pinned author signing key must reject key substitution", bobDag.acceptRemoteEvent(spoofed))
    }

    @Test
    fun chronoDag_peerAssistedGossipSync_healsOfflinePartition_and_epochRatchets() {
        val (alicePriv, _) = ChronoDagGroupSync.generateEd25519KeyPair()
        val (bobPriv, _) = ChronoDagGroupSync.generateEd25519KeyPair()
        val groupId = UUID.randomUUID().toString()
        val aliceOnion = "${validV3OnionHost(21)}:18888"
        val bobOnion = "${validV3OnionHost(22)}:18888"
        val carolOnion = "${validV3OnionHost(23)}:18888"
        val initialRoster = listOf(
            aliceOnion to validHexKey(21),
            bobOnion to validHexKey(22),
            carolOnion to validHexKey(23)
        )

        val aliceDag = ChronoDagGroupSync().apply { initializeGroup(groupId, aliceOnion, initialRoster) }
        val bobDag = ChronoDagGroupSync().apply { initializeGroup(groupId, aliceOnion, initialRoster) }
        val carolDag = ChronoDagGroupSync().apply { initializeGroup(groupId, aliceOnion, initialRoster) }

        // Alice sends 5 events while Carol is offline; Bob receives them
        val aliceEvents = (1..5).map { idx ->
            aliceDag.createAndRecordLocalEvent(
                groupId = groupId,
                privateKeyBytes = alicePriv,
                eventId = UUID.randomUUID().toString(),
                type = "message",
                authorCanonicalOnion = aliceOnion,
                timestamp = 1_700_000_000_000L + idx * 1000L,
                payloadJson = """{"messageText":"msg-$idx-${"x".repeat(180)}"}"""
            )
        }
        aliceEvents.forEach { assertTrue(bobDag.acceptRemoteEvent(it)) }

        // Bob also creates an event referencing Alice's latest head
        val bobEvent = bobDag.createAndRecordLocalEvent(
            groupId = groupId,
            privateKeyBytes = bobPriv,
            eventId = UUID.randomUUID().toString(),
            type = "message",
            authorCanonicalOnion = bobOnion,
            timestamp = 1_700_000_010_000L,
            payloadJson = """{"messageText":"bob-followup"}"""
        )

        // Carol comes online while Alice is offline; Bob sends SyncOffer -> Carol computes missing -> Bob sends bounded batches
        val offer = bobDag.buildSyncOffer(groupId)
        assertNotNull(offer)
        val missing = carolDag.computeMissingEventIds(groupId, offer!!)
        assertEquals(6, missing.size)

        val batches = bobDag.buildSyncBatches(groupId, missing)
        assertTrue("Batches should be non-empty", batches.isNotEmpty())
        val gson = com.google.gson.Gson()
        batches.forEach { batch ->
            val bytes = gson.toJson(batch).toByteArray(Charsets.UTF_8).size
            assertTrue("Sync batch $bytes exceeded 2,400B wire cap", bytes <= ChronoDagGroupSync.MAX_SYNC_BATCH_JSON_BYTES)
            batch.events.forEach { ev ->
                assertTrue("Carol must verify & accept relayed signed event", carolDag.acceptRemoteEvent(ev))
            }
        }
        assertTrue(carolDag.hasEvent(groupId, bobEvent.eventId))
        aliceEvents.forEach { assertTrue(carolDag.hasEvent(groupId, it.eventId)) }

        // Epoch ratchet on member removal changes epoch monotonically & changes commitment
        val preEpoch = aliceDag.getEpoch(groupId)
        val evBefore = aliceDag.createAndRecordLocalEvent(
            groupId = groupId,
            privateKeyBytes = alicePriv,
            eventId = UUID.randomUUID().toString(),
            type = "message",
            authorCanonicalOnion = aliceOnion,
            timestamp = 1_700_000_020_000L,
            payloadJson = """{"messageText":"pre-removal"}"""
        )
        val postEpoch = aliceDag.advanceEpoch(groupId, listOf(aliceOnion to validHexKey(21), bobOnion to validHexKey(22)))
        assertEquals(preEpoch + 1L, postEpoch)
        val evAfter = aliceDag.createAndRecordLocalEvent(
            groupId = groupId,
            privateKeyBytes = alicePriv,
            eventId = UUID.randomUUID().toString(),
            type = "message",
            authorCanonicalOnion = aliceOnion,
            timestamp = 1_700_000_030_000L,
            payloadJson = """{"messageText":"post-removal"}"""
        )
        assertEquals(postEpoch, evAfter.epoch)
        assertNotEquals(evBefore.epochCommitment, evAfter.epochCommitment)
    }

    @Test
    fun torGroupMessaging_32MemberInvite_compressesAndChunksUnder3500B_and_reassembles() = runBlocking {
        val groupMessaging = TorGroupMessaging.getInstance(context)
        val groupId = UUID.randomUUID().toString()
        val creatorOnion = "${validV3OnionHost(50)}:18888"
        val creatorKey = validHexKey(50)

        val members = buildList {
            add(
                TorGroupMessaging.WireMember(
                    onionAddress = creatorOnion,
                    nickname = "Creator-Alice-LongDisplayName",
                    publicKey = creatorKey,
                    mailboxOnion = "${validV3OnionHost(150)}:18889",
                    avatarId = "aurora",
                    signingPubKey = validHexKey(250)
                )
            )
            add(
                TorGroupMessaging.WireMember(
                    onionAddress = myOnionEndpoint,
                    nickname = "Local-Device-User",
                    publicKey = OnionContactManager.getInstance(context).getMyContactInfo().publicKey,
                    mailboxOnion = "${validV3OnionHost(151)}:18889",
                    avatarId = "tide",
                    signingPubKey = validHexKey(251)
                )
            )
            for (i in 2 until 32) {
                add(
                    TorGroupMessaging.WireMember(
                        onionAddress = "${validV3OnionHost(50 + i)}:18888",
                        nickname = "Member-$i-HighEntropyName",
                        publicKey = validHexKey(50 + i),
                        mailboxOnion = "${validV3OnionHost(150 + i)}:18889",
                        avatarId = "mint",
                        signingPubKey = validHexKey(250 + i)
                    )
                )
            }
        }
        assertEquals(32, members.size)

        val inviteEvent = TorGroupMessaging.WireEvent(
            version = 2,
            type = "invite",
            groupId = groupId,
            groupName = "32-Member Aegis Group",
            creatorOnion = creatorOnion,
            creatorNickname = "Creator-Alice",
            members = members,
            eventId = UUID.randomUUID().toString(),
            epoch = 1L,
            admins = listOf(creatorOnion)
        )

        val payloads = groupMessaging.encodeInvitePayloads(inviteEvent)
        assertTrue("32-member invite must be split into chunks", payloads.size >= 2)
        payloads.forEach { frame ->
            val byteLen = frame.toByteArray(Charsets.UTF_8).size
            assertTrue("Each invite_part must stay <= 3,500B (was $byteLen)", byteLen <= 3_500)
        }

        val sender = OnionContactManager.OnionContact(
            id = "creator-50",
            nickname = "Creator-Alice",
            onionAddress = creatorOnion,
            publicKey = creatorKey,
            trustState = TorContactTrustState.VERIFIED
        )
        payloads.forEach { part ->
            val res = groupMessaging.handleIncoming(
                sender = sender,
                messageId = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                payload = part
            )
            assertEquals(TorGroupMessaging.InboundResult.HANDLED, res)
        }

        val dao = AppDatabase.getDatabase(context).groupDao()
        val storedGroup = dao.getGroup(groupId)
        assertNotNull("Assembled 32-member group must be persisted", storedGroup)
        assertEquals(32, dao.getMembersForGroupNow(groupId).size)
        groupMessaging.deleteLocal(groupId)
    }

    @Test
    fun torGroupMessaging_gzipBomb_blockedSenders_deferredBeforeInvite_and_tombstone() = runBlocking {
        val groupMessaging = TorGroupMessaging.getInstance(context)
        val groupId = UUID.randomUUID().toString()
        val creatorOnion = "${validV3OnionHost(90)}:18888"
        val creatorKey = validHexKey(90)

        // 1. SGRP2 Gzip bomb (> 64 KB decompressed) must be rejected cleanly without OOM
        val bombCompressed = gzip(ByteArray(96 * 1024) { 'A'.code.toByte() })
        val bombPayload = "SGRP2:" + Base64.encodeToString(
            bombCompressed,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        assertNull("Decompression bomb >64KB must decode to null", groupMessaging.decode(bombPayload))

        // 2. BLOCKED and KEY_CHANGED senders rejected at inbound boundary
        val validPayload = groupMessaging.encode(
            TorGroupMessaging.WireEvent(
                version = 2,
                type = "message",
                groupId = groupId,
                messageText = "hello",
                groupMessageId = UUID.randomUUID().toString()
            )
        )
        val blockedSender = OnionContactManager.OnionContact(
            id = "blk",
            nickname = "Blocked",
            onionAddress = creatorOnion,
            publicKey = creatorKey,
            trustState = TorContactTrustState.BLOCKED
        )
        val keyChangedSender = blockedSender.copy(id = "kc", trustState = TorContactTrustState.KEY_CHANGED)
        assertEquals(
            TorGroupMessaging.InboundResult.REJECTED,
            groupMessaging.handleIncoming(blockedSender, UUID.randomUUID().toString(), System.currentTimeMillis(), validPayload)
        )
        assertEquals(
            TorGroupMessaging.InboundResult.REJECTED,
            groupMessaging.handleIncoming(keyChangedSender, UUID.randomUUID().toString(), System.currentTimeMillis(), validPayload)
        )

        // 3. Message arriving BEFORE invite returns DEFERRED and replays once invite arrives
        val verifiedSender = OnionContactManager.OnionContact(
            id = "alice-90",
            nickname = "Alice",
            onionAddress = creatorOnion,
            publicKey = creatorKey,
            trustState = TorContactTrustState.VERIFIED
        )
        val earlyMsgId = UUID.randomUUID().toString()
        val earlyMsgPayload = groupMessaging.encode(
            TorGroupMessaging.WireEvent(
                version = 2,
                type = "message",
                groupId = groupId,
                messageText = "Arrived before invite!",
                groupMessageId = earlyMsgId
            )
        )
        val deferredRes = groupMessaging.handleIncoming(
            sender = verifiedSender,
            messageId = earlyMsgId,
            timestamp = System.currentTimeMillis(),
            payload = earlyMsgPayload
        )
        assertEquals(TorGroupMessaging.InboundResult.DEFERRED, deferredRes)

        val invitePayload = groupMessaging.encode(
            TorGroupMessaging.WireEvent(
                version = 2,
                type = "invite",
                groupId = groupId,
                groupName = "Out-of-Order Group",
                creatorOnion = creatorOnion,
                creatorNickname = "Alice",
                members = listOf(
                    TorGroupMessaging.WireMember(creatorOnion, "Alice", creatorKey),
                    TorGroupMessaging.WireMember(
                        myOnionEndpoint,
                        "Me",
                        OnionContactManager.getInstance(context).getMyContactInfo().publicKey
                    )
                ),
                eventId = UUID.randomUUID().toString(),
                epoch = 1L,
                admins = listOf(creatorOnion)
            )
        )
        assertEquals(
            TorGroupMessaging.InboundResult.HANDLED,
            groupMessaging.handleIncoming(verifiedSender, UUID.randomUUID().toString(), System.currentTimeMillis(), invitePayload)
        )
        val dao = AppDatabase.getDatabase(context).groupDao()
        val replayed = dao.getGroupMessage(earlyMsgId)
        assertNotNull("Deferred message must replay automatically once invite arrives", replayed)
        assertEquals("Arrived before invite!", replayed!!.text)

        // 4. Tombstoned group rejects subsequent messages
        groupMessaging.chronoDag.tombstoneGroup(groupId)
        val postTombstonePayload = groupMessaging.encode(
            TorGroupMessaging.WireEvent(
                version = 2,
                type = "message",
                groupId = groupId,
                messageText = "Should be rejected",
                groupMessageId = UUID.randomUUID().toString()
            )
        )
        assertEquals(
            TorGroupMessaging.InboundResult.REJECTED,
            groupMessaging.handleIncoming(verifiedSender, UUID.randomUUID().toString(), System.currentTimeMillis(), postTombstonePayload)
        )
        groupMessaging.deleteLocal(groupId)
    }

    @Test
    fun torGroupMessaging_idempotentReactions_lwwResolution() = runBlocking {
        val groupMessaging = TorGroupMessaging.getInstance(context)
        val groupId = UUID.randomUUID().toString()
        val creatorOnion = "${validV3OnionHost(95)}:18888"
        val creatorKey = validHexKey(95)
        val sender = OnionContactManager.OnionContact(
            id = "alice-95",
            nickname = "Alice",
            onionAddress = creatorOnion,
            publicKey = creatorKey,
            trustState = TorContactTrustState.VERIFIED
        )
        val invitePayload = groupMessaging.encode(
            TorGroupMessaging.WireEvent(
                version = 2,
                type = "invite",
                groupId = groupId,
                groupName = "Reaction Group",
                creatorOnion = creatorOnion,
                creatorNickname = "Alice",
                members = listOf(
                    TorGroupMessaging.WireMember(creatorOnion, "Alice", creatorKey),
                    TorGroupMessaging.WireMember(myOnionEndpoint, "Me", OnionContactManager.getInstance(context).getMyContactInfo().publicKey)
                ),
                eventId = UUID.randomUUID().toString(),
                epoch = 1L,
                admins = listOf(creatorOnion)
            )
        )
        groupMessaging.handleIncoming(sender, UUID.randomUUID().toString(), System.currentTimeMillis(), invitePayload)

        val targetMsgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val addPayload = groupMessaging.encode(
            TorGroupMessaging.WireEvent(
                version = 2,
                type = "reaction",
                groupId = groupId,
                targetMessageId = targetMsgId,
                reactionEmoji = "🔥",
                reactionAction = "add",
                eventTimestamp = now
            )
        )
        // Delivering duplicate "add" MUST NOT toggle it off!
        groupMessaging.handleIncoming(sender, UUID.randomUUID().toString(), now, addPayload)
        groupMessaging.handleIncoming(sender, UUID.randomUUID().toString(), now, addPayload)
        val activeAfterDup = groupMessaging.reactionsFlow.first()[targetMsgId]
        assertEquals("🔥", activeAfterDup?.get(creatorOnion))

        // Explicit "remove" with newer timestamp removes cleanly
        val removePayload = groupMessaging.encode(
            TorGroupMessaging.WireEvent(
                version = 2,
                type = "reaction",
                groupId = groupId,
                targetMessageId = targetMsgId,
                reactionEmoji = "🔥",
                reactionAction = "remove",
                eventTimestamp = now + 1000L
            )
        )
        groupMessaging.handleIncoming(sender, UUID.randomUUID().toString(), now + 1000L, removePayload)
        assertNull(groupMessaging.reactionsFlow.first()[targetMsgId]?.get(creatorOnion))
        groupMessaging.deleteLocal(groupId)
    }

    @Test
    fun groupDao_excludesSystemBannersFromUnread_ordersByActivity_and_expiresMessages() = runBlocking {
        val dao = testDb.groupDao()
        val gOld = UUID.randomUUID().toString()
        val gNew = UUID.randomUUID().toString()
        dao.insertGroup(PrivateGroupEntity(gOld, "Older Group", myOnionEndpoint, "Me", true, createdAt = 1_000L))
        dao.insertGroup(PrivateGroupEntity(gNew, "Newer Group", myOnionEndpoint, "Me", true, createdAt = 2_000L))

        // Insert a system banner + a real peer message in gOld with a newer timestamp than gNew
        dao.insertGroupMessage(
            GroupMessageEntity("sys-1", gOld, TorGroupMessaging.SYSTEM_SENDER, "system", "Alice joined", 5_000L, false, TorGroupMessaging.STATE_DELIVERED)
        )
        dao.insertGroupMessage(
            GroupMessageEntity("msg-1", gOld, "${validV3OnionHost(77)}:18888", "Alice", "Hello!", 6_000L, false, TorGroupMessaging.STATE_DELIVERED)
        )

        // Unread count must exclude system banners (1 real message, NOT 2)
        assertEquals(1, dao.getUnreadCount(gOld, since = 0L).first())

        // Ordering by recent activity puts gOld (last msg at 6000) ahead of gNew (created at 2000)
        val ordered = dao.getGroupsOrderedByRecentActivity().first()
        assertEquals(gOld, ordered.first().groupId)

        // Edit & delete-for-everyone & disappearing TTL expiration work cleanly
        dao.updateGroupMessageText("msg-1", "Edited text")
        assertEquals("Edited text", dao.getGroupMessage("msg-1")?.text)
        dao.markGroupMessageDeleted("msg-1", TorGroupMessaging.DELETED_MESSAGE_TEXT)
        assertEquals(TorGroupMessaging.DELETED_MESSAGE_TEXT, dao.getGroupMessage("msg-1")?.text)

        val deletedCount = dao.deleteExpiredGroupMessages(gOld, cutoffTimestamp = 10_000L)
        assertEquals("Only non-system expired messages should be deleted", 1, deletedCount)
        assertNotNull("System banners must be preserved by expiration query", dao.getGroupMessage("sys-1"))
    }

    // ─────────────────────────── 3. Aegis-Flux Voice & Video Calling ───────────────────────────

    @Test
    fun aegisCallCrypto_ephemeralPfs_directionalKeys_boundSas_and_zeroize() {
        val callerEph = AegisCallCrypto.generateEphemeralKeyPair()
        val calleeEph = AegisCallCrypto.generateEphemeralKeyPair()
        val callerId = validHexKey(201)
        val calleeId = validHexKey(202)
        val callId = UUID.randomUUID().toString()

        val callerSession = AegisCallCrypto.derivePfsSession(
            callId = callId,
            isCaller = true,
            localEphemeralPriv = callerEph.privateKey,
            localEphemeralPub = callerEph.publicKey,
            remoteEphemeralPub = calleeEph.publicKey,
            localIdentityPubHex = callerId,
            remoteIdentityPubHex = calleeId
        )
        val calleeSession = AegisCallCrypto.derivePfsSession(
            callId = callId,
            isCaller = false,
            localEphemeralPriv = calleeEph.privateKey,
            localEphemeralPub = calleeEph.publicKey,
            remoteEphemeralPub = callerEph.publicKey,
            localIdentityPubHex = calleeId,
            remoteIdentityPubHex = callerId
        )

        assertTrue(callerSession.isPfs && calleeSession.isPfs)
        assertArrayEquals("caller.sendKey must equal callee.recvKey", callerSession.sendKey, calleeSession.recvKey)
        assertArrayEquals("callee.sendKey must equal caller.recvKey", calleeSession.sendKey, callerSession.recvKey)
        assertFalse(
            "Directional keys must differ to block reflection attacks",
            callerSession.sendKey.contentEquals(callerSession.recvKey)
        )

        val dtlsSdp = "v=0\r\na=fingerprint:sha-256 AA:BB:CC:DD\r\na=fingerprint:sha-256 11:22:33:44\r\n"
        val fps = AegisCallCrypto.extractDtlsFingerprintsFromSdp(dtlsSdp)
        assertEquals(2, fps.size)

        val sasCaller = AegisCallCrypto.deriveBoundSasEmojis(callerSession.sasSecret, callId, callerId, calleeId, fps)
        val sasCallee = AegisCallCrypto.deriveBoundSasEmojis(calleeSession.sasSecret, callId, calleeId, callerId, fps.reversed())
        assertEquals(4, sasCaller.size)
        assertEquals("Bound SAS must match identically on both peers", sasCaller, sasCallee)

        // MITM altering DTLS fingerprint or identity key changes Bound SAS
        val sasMitm = AegisCallCrypto.deriveBoundSasEmojis(
            callerSession.sasSecret,
            callId,
            callerId,
            calleeId,
            listOf("sha-256 ff:ee:dd:cc")
        )
        assertNotEquals("Altered DTLS fingerprint must change Bound SAS", sasCaller, sasMitm)

        // Explicit zeroization clears all key bytes
        callerSession.zeroize()
        assertTrue(callerSession.zeroized)
        assertTrue(AegisTorCrypto.isAllZero(callerSession.sendKey))
        assertTrue(AegisTorCrypto.isAllZero(callerSession.recvKey))
        assertTrue(AegisTorCrypto.isAllZero(callerSession.sasSecret))
    }

    @Test
    fun callFluxFec_zeroOverheadWhenDisabled_and_recoversDroppedVariableLengthFrames() {
        val encoder = CallFluxFec.Encoder()
        val decoder = CallFluxFec.Decoder()

        val f0 = ByteArray(166) { ((it * 7) and 0xFF).toByte() }
        val f1 = ByteArray(210) { ((it * 13 xor 0x5A) and 0xFF).toByte() }

        // Disabled => 0% overhead
        assertNull(encoder.onVoiceFrame(0L, f0, fecEnabled = false))
        assertNull(encoder.onVoiceFrame(1L, f1, fecEnabled = false))

        // Enabled => parity emitted on odd sequence
        assertNull(encoder.onVoiceFrame(2L, f0, fecEnabled = true))
        val parity23 = encoder.onVoiceFrame(3L, f1, fecEnabled = true)
        assertNotNull(parity23)
        assertTrue(CallFluxFec.isFecPacket(parity23!!))

        // Scenario A: Frame 3 dropped -> Frame 2 + Parity recovers exact Frame 3
        assertNull(decoder.onVoiceFrame(2L, f0))
        val recovered3 = decoder.onFecPacket(parity23)
        assertNotNull(recovered3)
        assertEquals(3L, recovered3!!.first)
        assertArrayEquals(f1, recovered3.second)

        // Scenario B: Frame 4 dropped, Parity arrives BEFORE Frame 5 -> Frame 5 triggers recovery of exact Frame 4
        val f4 = ByteArray(326) { ((it * 29) and 0xFF).toByte() }
        val f5 = ByteArray(120) { ((it * 43) and 0xFF).toByte() }
        assertNull(encoder.onVoiceFrame(4L, f4, fecEnabled = true))
        val parity45 = encoder.onVoiceFrame(5L, f5, fecEnabled = true)!!
        assertNull(decoder.onFecPacket(parity45))
        val recovered4 = decoder.onVoiceFrame(5L, f5)
        assertNotNull(recovered4)
        assertEquals(4L, recovered4!!.first)
        assertArrayEquals(f4, recovered4.second)
    }

    @Test
    fun callAudioEngine_pitchEstimation_wsola_plc_dtxSid_and_hwAgc() {
        // Generate clean 200 Hz sine wave at 16 kHz => exact period = 16000 / 200 = 80 samples
        val pcm = ShortArray(640) { i ->
            (sin(2.0 * PI * 200.0 * i / 16_000.0) * 14_000.0).toInt().toShort()
        }
        val estimatedPeriod = CallAudioEngine.estimatePitchPeriod(pcm)
        assertTrue("Expected ~80 sample period for 200Hz at 16kHz, got $estimatedPeriod", abs(estimatedPeriod - 80) <= 2)

        // WSOLA removes exactly one pitch period without harsh clipping discontinuities
        val compressed = CallAudioEngine.wsolaTimeCompressPcm(pcm)
        assertEquals(pcm.size - estimatedPeriod, compressed.size)
        var maxDelta = 0
        for (i in 1 until compressed.size) {
            val d = abs(compressed[i].toInt() - compressed[i - 1].toInt())
            if (d > maxDelta) maxDelta = d
        }
        // 200 Hz sine at amplitude 14000 has max sample-to-sample slope ~1100; WSOLA should stay smooth
        assertTrue("WSOLA output should not introduce click discontinuities (maxDelta=$maxDelta)", maxDelta < 2_500)

        // Pitch-matched PLC produces smooth attenuated continuation
        val plc1 = CallAudioEngine.synthesizePitchMatchedPlc(pcm, plcIndex = 1, noiseSeed = 12345)
        val plc2 = CallAudioEngine.synthesizePitchMatchedPlc(pcm, plcIndex = 2, noiseSeed = 12345)
        assertEquals(pcm.size, plc1.size)
        val energy1 = plc1.sumOf { abs(it.toInt()) }
        val energy2 = plc2.sumOf { abs(it.toInt()) }
        assertTrue("PLC frame 1 should contain voiced pitch energy", energy1 > 100_000)
        assertTrue("PLC frame 2 must attenuate relative to frame 1", energy2 < energy1)

        // DTX SID comfort frame round-trip
        val sid = CallAudioEngine.encodeDtxSidFrame()
        assertEquals(6, sid.size)
        assertTrue(CallAudioEngine.isDtxSidFrame(sid))
        assertFalse(CallAudioEngine.isDtxSidFrame(ByteArray(6) { 0x10 }))

        // Hardware AGC flag skips redundant software boost
        val softCopy = pcm.copyOf()
        val hwCopy = pcm.copyOf()
        CallAudioEngine.applyDcBlockAndSmoothSpeechGain(softCopy, softCopy.size, CallAudioEngine.Companion.MicDspState(), hardwareAgcActive = false)
        CallAudioEngine.applyDcBlockAndSmoothSpeechGain(hwCopy, hwCopy.size, CallAudioEngine.Companion.MicDspState(), hardwareAgcActive = true)
        val peakSoft = softCopy.maxOf { abs(it.toInt()) }
        val peakHw = hwCopy.maxOf { abs(it.toInt()) }
        assertTrue("Software AGC boosts speech while hardwareAgcActive keeps unity gain", peakSoft > peakHw)
    }

    @Test
    fun seamlessCallManager_strictIpLiteralParsing_and_boundedSdpDecompress() {
        // Valid IPv4 & bracketed IPv6 literals accepted without DNS
        val v4 = SeamlessCallManager.parseStrictIpLiteralSocketAddress("198.51.100.42:51820")
        assertNotNull(v4)
        assertEquals(51820, v4!!.port)
        assertEquals("198.51.100.42", v4.address.hostAddress)

        val v6 = SeamlessCallManager.parseStrictIpLiteralSocketAddress("[2001:db8::1]:4433")
        assertNotNull(v6)
        assertEquals(4433, v6!!.port)

        // Hostnames (DNS leak vectors), unspecified, loopback, multicast, invalid ports MUST be rejected
        assertNull(SeamlessCallManager.parseStrictIpLiteralSocketAddress("leak.attacker.invalid:443"))
        assertNull(SeamlessCallManager.parseStrictIpLiteralSocketAddress("0.0.0.0:1234"))
        assertNull(SeamlessCallManager.parseStrictIpLiteralSocketAddress("127.0.0.1:1234"))
        assertNull(SeamlessCallManager.parseStrictIpLiteralSocketAddress("224.0.0.1:1234"))
        assertNull(SeamlessCallManager.parseStrictIpLiteralSocketAddress("198.51.100.42:0"))
        assertNull(SeamlessCallManager.parseStrictIpLiteralSocketAddress("198.51.100.42:70000"))

        // Bounded SDP Gzip round-trip & decompression bomb rejection
        val sampleSdp = "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n"
        val validB64 = Base64.encodeToString(gzip(sampleSdp.toByteArray()), Base64.NO_WRAP)
        assertEquals(sampleSdp, SeamlessCallManager.decompressSdpGzipBase64Bounded(validB64))

        val sdpBombB64 = Base64.encodeToString(gzip(ByteArray(96 * 1024) { 'Z'.code.toByte() }), Base64.NO_WRAP)
        assertEquals("", SeamlessCallManager.decompressSdpGzipBase64Bounded(sdpBombB64))
    }

    @Test
    fun webRtcSdpHints_injectsVideoBitrateAndOpusFecDtxParams() {
        val rawSdp = listOf(
            "v=0",
            "m=audio 9 UDP/TLS/RTP/SAVPF 111",
            "a=rtpmap:111 opus/48000/2",
            "a=fmtp:111 minptime=10",
            "m=video 9 UDP/TLS/RTP/SAVPF 96 98",
            "a=rtpmap:96 H264/90000",
            "a=fmtp:96 level-asymmetry-allowed=1",
            "a=rtpmap:98 VP8/90000"
        ).joinToString("\r\n")

        val patched = WebRtcVideoCallEngine.injectVideoBitrateSdpHints(rawSdp)
        assertTrue(patched.contains("a=fmtp:111 minptime=10;useinbandfec=1;usedtx=1;maxaveragebitrate=32000"))
        assertTrue(patched.contains("a=fmtp:96 level-asymmetry-allowed=1;x-google-start-bitrate=1400;x-google-min-bitrate=350;x-google-max-bitrate=2500"))
        assertTrue(patched.contains("a=fmtp:98 x-google-start-bitrate=1400;x-google-min-bitrate=350;x-google-max-bitrate=2500"))
    }
}
