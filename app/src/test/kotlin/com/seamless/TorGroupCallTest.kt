package com.seamless

import com.seamless.android.call.ActiveGroupRoomBanner
import com.seamless.android.call.CallFluxFec
import com.seamless.android.call.CallVideoEngine
import com.seamless.android.call.GroupCallAudioMixer
import com.seamless.android.call.GroupCallManager
import com.seamless.android.crypto.AegisGroupCallCrypto
import com.seamless.android.net.ChronoDagGroupSync
import com.seamless.android.net.OnionMessageProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class TorGroupCallTest {

    @Test
    fun sframeAudioPacket_fitsInsideSingleTorCell_andRoundTripsMultiSender() {
        val groupId = "group-alpha-01"
        val callId = "call-uuid-9991"
        val onions = listOf(
            "alice1111111111111111111111111111111111111111111111111.onion",
            "bob222222222222222222222222222222222222222222222222222.onion",
            "charlie33333333333333333333333333333333333333333333333.onion",
            "dave44444444444444444444444444444444444444444444444444.onion",
            "erin55555555555555555555555555555555555555555555555555.onion"
        )

        val sessions = onions.map { me ->
            AegisGroupCallCrypto.GroupCryptoRoomSession(groupId, callId, me).apply {
                ensureLocalEpochKey(1)
            }
        }
        val seeds = sessions.map { it.ensureLocalEpochKey(1) }
        for (i in sessions.indices) {
            for (j in sessions.indices) {
                if (i != j) {
                    sessions[i].installPeerSenderKey(onions[j], 1, seeds[j])
                }
            }
        }

        // 40 ms 16 kHz IMA-ADPCM frame is exact 324..326 bytes -> sealed SFrame must stay <= 444 bytes
        val adpcmFrame = ByteArray(326) { idx -> ((idx * 37) and 0xFF).toByte() }
        val sealedByAlice = sessions[0].sealAudio(adpcmFrame)!!
        val compactMaxPayload = OnionMessageProtocol.COMPACT_CELL_FRAME_SIZE - OnionMessageProtocol.HEADER_SIZE
        assertTrue(
            "Expected sealed SFrame (${sealedByAlice.size}B) <= $compactMaxPayload B single Tor cell limit",
            sealedByAlice.size <= compactMaxPayload
        )
        assertEquals(363, sealedByAlice.size)

        // All 4 recipients decrypt Alice's SFrame cleanly AND replay is rejected!
        for (r in 1..4) {
            val opened = sessions[r].open(sealedByAlice)
            assertNotNull("Recipient $r should decrypt Alice's SFrame", opened)
            assertArrayEquals(adpcmFrame, opened!!.plaintext)
            assertNull("Replayed SFrame must be rejected", sessions[r].open(sealedByAlice))
        }

        // Cross a 250-frame forward-secrecy hash-ratchet boundary seamlessly
        repeat(260) {
            val pkt = sessions[1].sealAudio(adpcmFrame)!!
            val opened = sessions[0].open(pkt)
            assertNotNull(opened)
            assertArrayEquals(adpcmFrame, opened!!.plaintext)
        }

        sessions.forEach { it.zeroize() }
    }

    @Test
    fun epochRotationOnLeaveOrKick_immediatelyLocksOutDepartedMember() {
        val groupId = "group-sec-02"
        val callId = "call-uuid-rotate"
        val aliceOnion = "aliceaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion"
        val bobOnion = "bobbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.onion"
        val eveOnion = "eveeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee.onion"

        val alice = AegisGroupCallCrypto.GroupCryptoRoomSession(groupId, callId, aliceOnion)
        val bob = AegisGroupCallCrypto.GroupCryptoRoomSession(groupId, callId, bobOnion)
        val eve = AegisGroupCallCrypto.GroupCryptoRoomSession(groupId, callId, eveOnion)

        val a1 = alice.ensureLocalEpochKey(1)
        val b1 = bob.ensureLocalEpochKey(1)
        val e1 = eve.ensureLocalEpochKey(1)
        alice.installPeerSenderKey(bobOnion, 1, b1); alice.installPeerSenderKey(eveOnion, 1, e1)
        bob.installPeerSenderKey(aliceOnion, 1, a1); bob.installPeerSenderKey(eveOnion, 1, e1)
        eve.installPeerSenderKey(aliceOnion, 1, a1); eve.installPeerSenderKey(bobOnion, 1, b1)

        val edPair = ChronoDagGroupSync.generateEd25519KeyPair()
        val (pubHex, sigHex) = AegisGroupCallCrypto.signSenderKeyAnnouncement(
            ed25519PrivateKey = edPair.first,
            groupId = groupId,
            callId = callId,
            epoch = 1,
            senderOnion = aliceOnion,
            senderTag4 = alice.localSenderTag4,
            rootSeed = a1
        )
        assertTrue(
            AegisGroupCallCrypto.verifySenderKeyAnnouncement(
                pubHex, sigHex, groupId, callId, 1, aliceOnion, alice.localSenderTag4, a1
            )
        )

        // Remove Eve and hard-rotate to Epoch 2 with 0 grace period
        val a2 = alice.removePeerAndRotateEpoch(eveOnion, newEpoch = 2)
        val b2 = bob.removePeerAndRotateEpoch(eveOnion, newEpoch = 2)
        alice.installPeerSenderKey(bobOnion, 2, b2)
        bob.installPeerSenderKey(aliceOnion, 2, a2)

        val epoch2Msg = alice.sealAudio(ByteArray(326) { 0x42 })!!
        assertNotNull("Bob in Epoch 2 must decrypt Alice", bob.open(epoch2Msg))
        assertNull("Evicted Eve must NEVER decrypt Epoch 2 packets", eve.open(epoch2Msg))

        val eveSpoofEpoch1 = eve.sealAudio(ByteArray(326) { 0x77 })!!
        assertNull("Alice & Bob must immediately reject Epoch 1 frames from evicted Eve", alice.open(eveSpoofEpoch1))
        assertNull(bob.open(eveSpoofEpoch1))

        alice.zeroize(); bob.zeroize(); eve.zeroize()
    }

    @Test
    fun conferenceFourEmojiSas_isIdenticalRegardlessOfMemberInsertionOrder() {
        val commitsOrderA = listOf(
            "charlie.onion" to "commit_c",
            "alice.onion" to "commit_a",
            "bob.onion" to "commit_b"
        )
        val commitsOrderB = listOf(
            "bob.onion" to "commit_b",
            "charlie.onion" to "commit_c",
            "alice.onion" to "commit_a"
        )
        val sasA = AegisGroupCallCrypto.deriveConferenceEmojis("grp-1", "call-1", 3, commitsOrderA)
        val sasB = AegisGroupCallCrypto.deriveConferenceEmojis("grp-1", "call-1", 3, commitsOrderB)
        assertEquals(4, sasA.size)
        assertEquals(sasA, sasB)

        val sasEpoch4 = AegisGroupCallCrypto.deriveConferenceEmojis("grp-1", "call-1", 4, commitsOrderA)
        assertNotEquals("Rotating epoch must change verification emojis", sasA, sasEpoch4)
    }

    @Test
    fun multiSpeakerSoftKneeLimiter_preservesSingleSpeakerUnity_andPreventsFourSpeakerClipping() {
        val sampleCount = 640
        // Single moderate speaker (peak ~8000, |x| ~ 0.24 <= 0.72 knee) -> unity transparent passthrough
        val singleSpeaker = ShortArray(sampleCount) { i ->
            (8_000.0 * sin(2.0 * PI * i / 32.0)).toInt().toShort()
        }
        val mixedSingle = GroupCallAudioMixer.mixActiveFramesSoftLimited(listOf(singleSpeaker), sampleCount)
        for (i in 0 until sampleCount) {
            assertTrue(abs(mixedSingle[i] - singleSpeaker[i]) <= 1)
        }

        // 4 simultaneous full-scale speakers (±30_000 each => raw sum up to ±120_000) + 1 quiet 5th noise floor
        val hotSpeakers = (0 until 4).map { k ->
            ShortArray(sampleCount) { i ->
                (30_000.0 * sin(2.0 * PI * i / (24.0 + k))).toInt().toShort()
            }
        }
        val quietFifth = ShortArray(sampleCount) { 40 }
        val mixedFour = GroupCallAudioMixer.mixActiveFramesSoftLimited(
            hotSpeakers + listOf(quietFifth),
            sampleCount,
            maxActiveSpeakers = 4
        )
        var maxPeak = 0
        for (sample in mixedFour) {
            val v = abs(sample.toInt())
            assertTrue("Mixed sample must never wrap or exceed Int16 bounds", v <= 32767)
            if (v > maxPeak) maxPeak = v
        }
        assertTrue("Limiter output should remain strong & audible", maxPeak in 24_000..32767)

        // Verify CallFluxFec 2D XOR parity recovery works cleanly with ADPCM-sized payloads
        val fecEnc = CallFluxFec.Encoder()
        val fecDec = CallFluxFec.Decoder()
        val f0 = ByteArray(326) { 0x11 }
        val f1 = ByteArray(326) { 0x22 }
        fecEnc.onVoiceFrame2D(0, f0, fecEnabled = true, burstMode2D = true)
        val p1 = fecEnc.onVoiceFrame2D(1, f1, fecEnabled = true, burstMode2D = true)
        fecDec.onVoiceFrameMulti(0, f0)
        val recovered = p1.flatMap { fecDec.onFecPacketMulti(it) }
        assertTrue(recovered.any { it.first == 1L && it.second.contentEquals(f1) })
    }

    @Test
    fun swarmSfuHubElection_deterministicallySelectsLowestRttAndFailsOverToStandby() {
        val candidates = listOf(
            GroupCallManager.Companion.HubCandidateMetrics("peer-tor-slow.onion", 240, 0, true, 100L),
            GroupCallManager.Companion.HubCandidateMetrics("peer-udp-fast.onion", 28, 3, true, 150L),
            GroupCallManager.Companion.HubCandidateMetrics("peer-udp-backup.onion", 42, 2, true, 200L),
            GroupCallManager.Companion.HubCandidateMetrics("peer-no-keys.onion", 12, 1, false, 90L),
            GroupCallManager.Companion.HubCandidateMetrics("peer-5.onion", 180, 0, true, 300L),
            GroupCallManager.Companion.HubCandidateMetrics("peer-6.onion", 190, 0, true, 400L)
        )
        val (primary, standby) = GroupCallManager.electSwarmHubs(candidates)
        assertEquals("peer-udp-fast.onion", primary)
        assertEquals("peer-udp-backup.onion", standby)

        // Same set in reversed order yields identical election on all peers
        val (primaryRev, standbyRev) = GroupCallManager.electSwarmHubs(candidates.reversed())
        assertEquals(primary, primaryRev)
        assertEquals(standby, standbyRev)

        // Removing primary cleanly promotes standby!
        val (afterLeave, _) = GroupCallManager.electSwarmHubs(
            candidates.filter { it.onionAddress != "peer-udp-fast.onion" }
        )
        assertEquals("peer-udp-backup.onion", afterLeave)
    }

    @Test
    fun perParticipantVolumeBoost_scalesQuietSpeakerAndNeverClipsAt200Percent() {
        val sampleCount = 640
        val quietSpeaker = ShortArray(sampleCount) { i ->
            (4_000.0 * sin(2.0 * PI * i / 32.0)).toInt().toShort()
        }
        val silencedSpeaker = ShortArray(sampleCount) { i ->
            (18_000.0 * sin(2.0 * PI * i / 19.0)).toInt().toShort()
        }

        // Gain = 2.0f boosts quietSpeaker to 2x while gain = 0.0f silences silencedSpeaker completely
        val boostedAndMuted = GroupCallAudioMixer.mixActiveFramesSoftLimited(
            frames = listOf(quietSpeaker, silencedSpeaker),
            sampleCount = sampleCount,
            gains = listOf(2.0f, 0.0f)
        )
        for (i in 0 until sampleCount) {
            val expected = (quietSpeaker[i].toInt() * 2)
            assertTrue("Expected boosted sample ~2x, got ${boostedAndMuted[i]} vs $expected", abs(boostedAndMuted[i] - expected) <= 2)
        }

        // 4 simultaneous hot speakers ALL boosted at 200% (2.0f) must remain bounded within [-32767, 32767]
        val hotSpeakers = (0 until 4).map { k ->
            ShortArray(sampleCount) { i ->
                (28_000.0 * sin(2.0 * PI * i / (20.0 + k))).toInt().toShort()
            }
        }
        val limitedHot = GroupCallAudioMixer.mixActiveFramesSoftLimited(
            frames = hotSpeakers,
            sampleCount = sampleCount,
            gains = listOf(2.0f, 2.0f, 2.0f, 2.0f)
        )
        for (sample in limitedHot) {
            assertTrue("200%-boosted 4-speaker mix must never exceed Int16 bounds", abs(sample.toInt()) <= 32767)
        }
    }

    @Test
    fun selectiveVideoSubscription_skipsMinimizedListenersWhileKeepingSubscribedPeers() {
        val targets = listOf(
            "alice.onion" to true,
            "bob-minimized.onion" to false,
            "charlie.onion" to true,
            "dave-minimized.onion" to false
        )
        val filtered = GroupCallManager.filterVideoTargetOnions(targets)
        assertEquals(listOf("alice.onion", "charlie.onion"), filtered)
    }

    @Test
    fun staleRoomBannerPruning_removesExpiredAnnouncementsAndKeepsActiveSessionGroup() {
        val now = 1_000_000L
        val banners = mapOf(
            "grp-expired" to ActiveGroupRoomBanner("grp-expired", "Old Room", "c1", "Eve", false, 2, now - 60_000L, lastUpdatedAtMs = now - 45_000L),
            "grp-fresh" to ActiveGroupRoomBanner("grp-fresh", "Fresh Room", "c2", "Maya", true, 4, now - 12_000L, lastUpdatedAtMs = now - 10_000L),
            "grp-active-even-if-old" to ActiveGroupRoomBanner("grp-active-even-if-old", "My Active", "c3", "You", false, 3, now - 90_000L, lastUpdatedAtMs = now - 60_000L)
        )
        val pruned = GroupCallManager.pruneStaleBanners(
            current = banners,
            activeSessionGroupId = "grp-active-even-if-old",
            nowMs = now,
            maxAgeMs = 35_000L
        )
        assertEquals(setOf("grp-fresh", "grp-active-even-if-old"), pruned.keys)
    }

    @Test
    fun groupVideoFragments_stayUnder4gLteUdpMtu_andRoundTripThroughSFrame() {
        val aliceOnion = "aliceaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.onion"
        val bobOnion = "bobbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb.onion"
        val alice = AegisGroupCallCrypto.GroupCryptoRoomSession("grp-vid", "call-vid", aliceOnion)
        val bob = AegisGroupCallCrypto.GroupCryptoRoomSession("grp-vid", "call-vid", bobOnion)

        val aSeed = alice.ensureLocalEpochKey(1)
        val bSeed = bob.ensureLocalEpochKey(1)
        alice.installPeerSenderKey(bobOnion, 1, bSeed)
        bob.installPeerSenderKey(aliceOnion, 1, aSeed)

        val rawKeyframe = ByteArray(22 * 1024) { i -> ((i * 73 + 19) and 0xFF).toByte() }
        val fragments = CallVideoEngine.fragmentFrame(
            frameSeq = 42,
            ptsMs = 12_345,
            flags = CallVideoEngine.FLAG_KEYFRAME or CallVideoEngine.FLAG_SELF_CONTAINED_JPEG,
            rotationDeg = 90,
            frameBytes = rawKeyframe,
            maxFragmentPayload = CallVideoEngine.MAX_FRAGMENT_PAYLOAD
        )
        assertTrue("Expected multi-fragment video frame", fragments.size > 15)

        val reassembler = CallVideoEngine.VideoFrameReassembler(onRequestKeyframe = {})
        var decoded: CallVideoEngine.ReassembledFrame? = null
        for (frag in fragments) {
            val sealed = alice.sealVideoFragment(frag)!!
            // 1 byte UDP_MAGIC_SFRAME + sealed SFrame must stay safely under 4G/LTE & IPv6 1280B MTU
            assertTrue(
                "UDP video datagram (${1 + sealed.size}B) exceeds 1200B mobile MTU",
                1 + sealed.size <= 1200
            )
            val opened = bob.open(sealed)!!
            val candidate = reassembler.onFragment(opened.plaintext)
            if (candidate != null) decoded = candidate
        }

        assertNotNull("All SFrame video fragments should reassemble", decoded)
        assertArrayEquals(rawKeyframe, decoded!!.data)

        alice.zeroize()
        bob.zeroize()
    }

    @Test
    fun torHeartbeatAckRtt_doesNotOverwriteFreshDirectUdpRtt() {
        val now = 500_000L
        assertEquals(
            false,
            GroupCallManager.shouldApplyTorHeartbeatRtt(
                hasVerifiedUdp = true,
                lastUdpVerifiedAtMs = now - 1_500L,
                nowMs = now
            )
        )
        assertEquals(
            true,
            GroupCallManager.shouldApplyTorHeartbeatRtt(
                hasVerifiedUdp = true,
                lastUdpVerifiedAtMs = now - (GroupCallManager.DIRECT_UDP_STALE_MS + 1_000L),
                nowMs = now
            )
        )
        assertEquals(
            true,
            GroupCallManager.shouldApplyTorHeartbeatRtt(
                hasVerifiedUdp = false,
                lastUdpVerifiedAtMs = 0L,
                nowMs = now
            )
        )
    }

    @Test
    fun groupUdpControlSignal_encryptsAndAuthenticatesRoomJoinAndRejectsTamper() {
        val groupId = "grp-wifi-fast"
        val callId = "call-fast-1234"
        val payload = """{"scope":"GROUP_CALL","type":"G_ROOM_JOIN","epoch":1}""".toByteArray(Charsets.UTF_8)

        val sealed = GroupCallManager.sealUdpControlSignal(groupId, callId, payload)
        assertNotNull(sealed)

        val opened = GroupCallManager.openUdpControlSignal(groupId, callId, sealed!!)
        assertArrayEquals(payload, opened)

        // Wrong room or tampered ciphertext must be rejected cleanly
        assertNull(GroupCallManager.openUdpControlSignal("other-group", callId, sealed))
        val tampered = sealed.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 0xFF).toByte() }
        assertNull(GroupCallManager.openUdpControlSignal(groupId, callId, tampered))
    }

    @Test
    fun lowLatency20msAnd40msFrames_mixCleanlyWithoutClipping() {
        val p2pSamples = GroupCallAudioMixer.P2P_SAMPLES_PER_FRAME
        assertEquals(320, p2pSamples)

        val speakerA = ShortArray(p2pSamples) { i -> (14_000.0 * sin(2.0 * PI * i / 16.0)).toInt().toShort() }
        val speakerB = ShortArray(p2pSamples) { i -> (14_000.0 * sin(2.0 * PI * i / 21.0)).toInt().toShort() }
        val mixed20ms = GroupCallAudioMixer.mixActiveFramesSoftLimited(
            frames = listOf(speakerA, speakerB),
            sampleCount = p2pSamples
        )
        assertEquals(p2pSamples, mixed20ms.size)
        assertTrue(mixed20ms.all { abs(it.toInt()) <= 32767 })
    }
}
