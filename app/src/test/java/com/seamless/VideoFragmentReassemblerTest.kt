package com.seamless

import com.seamless.android.call.CallVideoEngine
import com.seamless.android.call.WebRtcVideoCallEngine
import com.seamless.android.net.OnionMessageProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VideoFragmentReassemblerTest {

    @Test
    fun single_fragment_frame_reassembles_cleanly_with_flags_and_rotation() {
        val pliCount = AtomicInteger(0)
        val reassembler = CallVideoEngine.VideoFrameReassembler { pliCount.incrementAndGet() }

        val original = ByteArray(640) { (it * 7 + 3).toByte() }
        val flags = CallVideoEngine.FLAG_KEYFRAME or CallVideoEngine.FLAG_HEVC or CallVideoEngine.FLAG_CONFIG
        val fragments = CallVideoEngine.fragmentFrame(
            frameSeq = 1,
            ptsMs = 33,
            flags = flags,
            rotationDeg = 270,
            frameBytes = original
        )

        assertEquals(1, fragments.size)
        val assembled = reassembler.onFragment(fragments.first(), nowMs = 1000L)
        assertNotNull(assembled)
        assertEquals(1, assembled!!.frameSeq)
        assertEquals(33, assembled.ptsMs)
        assertEquals(flags, assembled.flags)
        assertEquals(270, assembled.rotationDeg)
        assertArrayEquals(original, assembled.data)
        assertEquals(0, pliCount.get())
    }

    @Test
    fun large_idr_keyframe_slices_under_mtu_and_reassembles_out_of_order() {
        val pliCount = AtomicInteger(0)
        val reassembler = CallVideoEngine.VideoFrameReassembler { pliCount.incrementAndGet() }

        val keyframe = ByteArray(15_200) { ((it * 13) xor 0x5A).toByte() }
        val fragments = CallVideoEngine.fragmentFrame(
            frameSeq = 42,
            ptsMs = 1400,
            flags = CallVideoEngine.FLAG_KEYFRAME or CallVideoEngine.FLAG_CONFIG,
            rotationDeg = 90,
            frameBytes = keyframe
        )

        assertEquals(14, fragments.size)
        fragments.forEach { frag ->
            assertTrue(frag.size <= CallVideoEngine.FRAG_HEADER_SIZE + CallVideoEngine.MAX_FRAGMENT_PAYLOAD)
        }

        // Deliver out of order (reverse order, duplicating the first fragment once)
        val reordered = fragments.reversed() + listOf(fragments.last())
        var result: CallVideoEngine.ReassembledFrame? = null
        reordered.forEach { pkt ->
            val out = reassembler.onFragment(pkt, nowMs = 5000L)
            if (out != null) result = out
        }

        assertNotNull(result)
        assertEquals(42, result!!.frameSeq)
        assertEquals(90, result!!.rotationDeg)
        assertArrayEquals(keyframe, result!!.data)
        assertEquals(0, pliCount.get())
    }

    @Test
    fun missing_fragment_never_emits_partial_frame_and_triggers_rate_limited_pli() {
        val pliCount = AtomicInteger(0)
        val reassembler = CallVideoEngine.VideoFrameReassembler(
            pliMinIntervalMs = 250L,
            onRequestKeyframe = { pliCount.incrementAndGet() }
        )

        val frame1 = ByteArray(2500) { 0x11 }
        val frags1 = CallVideoEngine.fragmentFrame(10, 100, 0, 0, frame1)
        assertEquals(3, frags1.size)

        // Drop fragment index 1 intentionally; only fragments 0 and 2 arrive
        assertNull(reassembler.onFragment(frags1[0], nowMs = 1000L))
        assertNull(reassembler.onFragment(frags1[2], nowMs = 1010L))

        // Frame 11 arrives completely; gap/stale drop on frame 10 triggers PLI
        val frame2 = ByteArray(400) { 0x22 }
        val frags2 = CallVideoEngine.fragmentFrame(11, 133, 0, 0, frame2)
        val out2 = reassembler.onFragment(frags2[0], nowMs = 1020L)
        assertNotNull(out2)
        assertEquals(11, out2!!.frameSeq)
        assertEquals(1, pliCount.get())

        // Sequence jump 11 -> 15 within 250ms rate limit does not spam extra PLIs
        val frame15 = ByteArray(400) { 0x33 }
        val frags15 = CallVideoEngine.fragmentFrame(15, 266, 0, 0, frame15)
        assertNotNull(reassembler.onFragment(frags15[0], nowMs = 1080L))
        assertEquals(1, pliCount.get())

        // Sequence jump after >250ms fires PLI again cleanly
        val frame20 = ByteArray(400) { 0x44 }
        val frags20 = CallVideoEngine.fragmentFrame(20, 400, 0, 0, frame20)
        assertNotNull(reassembler.onFragment(frags20[0], nowMs = 1400L))
        assertEquals(2, pliCount.get())
    }

    @Test
    fun tor_call_video_frame_encodes_unpadded_and_roundtrips() {
        val fragment = ByteArray(780) { (it and 0xFF).toByte() }
        val frame = OnionMessageProtocol.OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 99L,
            timestamp = 1_700_000_000_000L,
            frameType = OnionMessageProtocol.FrameType.CALL_VIDEO,
            payload = fragment
        )

        val encoded = frame.encode()
        assertEquals(OnionMessageProtocol.HEADER_SIZE + fragment.size, encoded.size)

        val decoded = OnionMessageProtocol.OnionFrame.decode(encoded)
        assertNotNull(decoded)
        assertEquals(OnionMessageProtocol.FrameType.CALL_VIDEO, decoded!!.frameType)
        assertEquals(99L, decoded.sequence)
        assertArrayEquals(fragment, decoded.payload)
    }

    @Test
    fun aes_256_gcm_video_fragment_roundtrip_and_tamper_rejection() {
        val random = SecureRandom()
        val sessionKey = ByteArray(32).also { random.nextBytes(it) }
        val callId = UUID.randomUUID()
        val magic: Byte = 0x5B
        val seq = 7L
        val ts = 1_700_000_001_000L

        val fragment = ByteArray(950) { (it * 3).toByte() }
        val aad = ByteBuffer.allocate(1 + 16 + 8 + 8)
            .put(magic)
            .putLong(callId.mostSignificantBits)
            .putLong(callId.leastSignificantBits)
            .putLong(seq)
            .putLong(ts)
            .array()
        val nonce = ByteArray(12).also { random.nextBytes(it) }

        val encCipher = Cipher.getInstance("AES/GCM/NoPadding")
        encCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(128, nonce))
        encCipher.updateAAD(aad)
        val sealed = encCipher.doFinal(fragment)

        // Valid decrypt succeeds
        val decCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(128, nonce))
        decCipher.updateAAD(aad)
        assertArrayEquals(fragment, decCipher.doFinal(sealed))

        // 1-bit tamper in ciphertext fails authentication
        val tampered = sealed.copyOf()
        tampered[5] = (tampered[5].toInt() xor 0x01).toByte()
        val badCipher = Cipher.getInstance("AES/GCM/NoPadding")
        badCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(128, nonce))
        badCipher.updateAAD(aad)
        val failed = runCatching { badCipher.doFinal(tampered) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun self_contained_jpeg_frame_reassembles_without_pli_on_drop() {
        val pliCount = AtomicInteger(0)
        val reassembler = CallVideoEngine.VideoFrameReassembler(
            pliMinIntervalMs = 250L,
            onRequestKeyframe = { pliCount.incrementAndGet() }
        )

        val selfFlags = CallVideoEngine.FLAG_KEYFRAME or
            CallVideoEngine.FLAG_SELF_CONTAINED_JPEG or
            CallVideoEngine.FLAG_FRONT_CAMERA

        // Incomplete frame 1 (drop fragment 1 of 2)
        val incomplete = CallVideoEngine.fragmentFrame(1, 100, selfFlags, 270, ByteArray(1800) { 0x41 })
        assertEquals(2, incomplete.size)
        assertNull(reassembler.onFragment(incomplete[0], nowMs = 1000L))

        // Self-contained frame 2 arrives completely across 4 fragments -> renders immediately with 0 PLIs
        val frame2Bytes = ByteArray(3900) { (it * 5).toByte() }
        val frags2 = CallVideoEngine.fragmentFrame(2, 190, selfFlags, 270, frame2Bytes)
        assertEquals(4, frags2.size)
        var assembled: CallVideoEngine.ReassembledFrame? = null
        frags2.reversed().forEach { pkt ->
            val out = reassembler.onFragment(pkt, nowMs = 1100L)
            if (out != null) assembled = out
        }

        assertNotNull(assembled)
        assertEquals(2, assembled!!.frameSeq)
        assertEquals(selfFlags, assembled!!.flags)
        assertEquals(270, assembled!!.rotationDeg)
        assertArrayEquals(frame2Bytes, assembled!!.data)
        assertEquals(0, pliCount.get())
    }

    @Test
    fun webrtc_stats_quality_label_and_unpadded_call_signal_frame() {
        val hdStats = WebRtcVideoCallEngine.Stats(
            rttMs = 34,
            inboundWidth = 1280,
            inboundHeight = 720,
            inboundFps = 30,
            codec = "H264"
        )
        assertEquals("720p HD 30fps", hdStats.qualityLabel)

        val midStats = WebRtcVideoCallEngine.Stats(
            rttMs = 85,
            inboundWidth = 640,
            inboundHeight = 360,
            inboundFps = 24,
            codec = "VP8"
        )
        assertEquals("360p 24fps", midStats.qualityLabel)

        val signalPayload = """{"type":"WEBRTC_SDP","sdpType":"offer","generation":1}""".toByteArray(Charsets.UTF_8)
        val signalFrame = OnionMessageProtocol.OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 12L,
            timestamp = 1_700_000_005_000L,
            frameType = OnionMessageProtocol.FrameType.CALL_SIGNAL,
            payload = signalPayload
        )
        val encoded = signalFrame.encode()
        assertEquals(OnionMessageProtocol.HEADER_SIZE + signalPayload.size, encoded.size)
    }
}
