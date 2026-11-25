package com.seamless

import android.Manifest
import android.app.Application
import android.app.NotificationManager as AndroidNotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.seamless.android.call.CallAudioEngine
import com.seamless.android.call.CallVideoEngine
import com.seamless.android.call.ImaAdpcmCodec
import com.seamless.android.call.WebRtcVideoCallEngine
import com.seamless.android.data.MessageDao
import com.seamless.android.data.MessageRepository
import com.seamless.android.model.BitchatMessage
import com.seamless.android.net.OnionMessageProtocol
import com.seamless.android.net.OnionMessageQueue
import com.seamless.android.net.TorAttachmentTransfer
import com.seamless.android.net.TorDaemonManager
import com.seamless.android.net.TorMessageNotifier
import com.seamless.android.ui.ChannelManager
import com.seamless.android.ui.ChatState
import com.seamless.android.ui.DataManager
import com.seamless.android.ui.MeshDelegateHandler
import com.seamless.android.ui.MessageManager
import com.seamless.android.ui.NoiseSessionDelegate
import com.seamless.android.ui.NotificationManager as SeamlessNotificationManager
import com.seamless.android.ui.PrivateChatManager
import com.seamless.android.ui.onion.prepareOptimizedImageForTor
import com.seamless.android.util.NotificationIntervalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.webrtc.CameraEnumerationAndroid
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class TorBackendHardeningTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fileMeta_encodesAsCompactTorCell_andRoundTrips() {
        val transfer = TorAttachmentTransfer(context)
        val meta = transfer.createMeta(
            transferId = UUID.randomUUID(),
            mimeType = "image/webp",
            totalBytes = 42_000,
            senderPublicKey = "a".repeat(64)
        )
        val metaFrame = OnionMessageProtocol.OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 1L,
            timestamp = 1_700_000_000_000L,
            frameType = OnionMessageProtocol.FrameType.FILE_META,
            payload = transfer.encodeMeta(meta)
        )
        val wireBytes = metaFrame.encode(padToFixedSize = true)
        assertEquals(OnionMessageProtocol.COMPACT_CELL_FRAME_SIZE, wireBytes.size)

        val decoded = OnionMessageProtocol.OnionFrame.decode(wireBytes)
        assertNotNull(decoded)
        assertEquals(OnionMessageProtocol.FrameType.FILE_META, decoded!!.frameType)
        assertEquals(meta.transferId, transfer.decodeMeta(decoded.payload)?.transferId)
        assertEquals("image/webp", transfer.decodeMeta(decoded.payload)?.mimeType)
    }

    @Test
    fun deduplicator_rejectsReplaysAcrossAmortizedPruneThreshold() {
        val dedup = OnionMessageProtocol.MessageDeduplicator(maxSize = 512)
        val tracked = List(160) { UUID.randomUUID() }
        tracked.forEach { id ->
            assertFalse("First encounter should not be duplicate", dedup.isDuplicate(id))
        }
        // Check again past the 128-check prune boundary
        assertTrue(dedup.isDuplicate(tracked.first()))
        assertTrue(dedup.isDuplicate(tracked.last()))
    }

    @Test
    fun queuePrioritizesControlAndTextBeforeAttachmentSliceAndRespectsCap() = runBlocking {
        val queue = OnionMessageQueue.getInstance(context)
        val peer = "priority-${UUID.randomUUID()}.onion"
        val now = System.currentTimeMillis()

        val metaFrame = OnionMessageProtocol.OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 1L,
            timestamp = now,
            frameType = OnionMessageProtocol.FrameType.FILE_META,
            payload = ByteArray(48) { 0x11 }
        )
        queue.enqueue(peer, metaFrame, completesUiMessage = false)

        repeat(25) { idx ->
            val chunkFrame = OnionMessageProtocol.OnionFrame(
                messageId = UUID.randomUUID(),
                sequence = (idx + 2).toLong(),
                timestamp = now + idx + 1,
                frameType = OnionMessageProtocol.FrameType.FILE_CHUNK,
                payload = ByteArray(256) { idx.toByte() }
            )
            queue.enqueue(peer, chunkFrame, completesUiMessage = idx == 24)
        }

        val textFrame = OnionMessageProtocol.OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 30L,
            timestamp = now + 100,
            frameType = OnionMessageProtocol.FrameType.MESSAGE,
            payload = "hi".toByteArray()
        )
        val signalFrame = OnionMessageProtocol.OnionFrame(
            messageId = UUID.randomUUID(),
            sequence = 31L,
            timestamp = now + 101,
            frameType = OnionMessageProtocol.FrameType.CALL_SIGNAL,
            payload = "sig".toByteArray()
        )
        queue.enqueue(peer, textFrame)
        queue.enqueue(peer, signalFrame)

        val pending = queue.getPendingMessages(peer)
        // Expected order: CALL_SIGNAL, MESSAGE, then bounded attachment slice (<= ATTACHMENT_DISPATCH_SLICE = 12)
        assertTrue(pending.size <= 2 + OnionMessageQueue.ATTACHMENT_DISPATCH_SLICE)
        assertEquals(signalFrame.messageId.toString(), pending[0].id)
        assertEquals(textFrame.messageId.toString(), pending[1].id)
        assertEquals(metaFrame.messageId.toString(), pending[2].id)
    }

    @Test
    fun attachmentSplit_preservesExactChunkBytesAndContentWithoutBoxing() {
        val transfer = TorAttachmentTransfer(context)
        assertEquals(0, transfer.split(ByteArray(0)).size)
        assertEquals(1, transfer.split(ByteArray(1)).size)
        assertEquals(1, transfer.split(ByteArray(TorAttachmentTransfer.CHUNK_BYTES)).size)

        val original = ByteArray(10_001) { i -> ((i * 31) xor 0x3D).toByte() }
        val chunks = transfer.split(original)
        assertEquals(4, chunks.size)
        assertEquals(TorAttachmentTransfer.CHUNK_BYTES, chunks[0].size)
        assertEquals(TorAttachmentTransfer.CHUNK_BYTES, chunks[1].size)
        assertEquals(TorAttachmentTransfer.CHUNK_BYTES, chunks[2].size)
        assertEquals(1001, chunks[3].size)

        val reassembled = ByteArray(original.size)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, reassembled, offset, chunk.size)
            offset += chunk.size
        }
        assertArrayEquals(original, reassembled)
    }

    @Test
    fun callAudio_adpcmRoundTripAndSmoothDcBlockedGain() {
        val sampleCount = CallAudioEngine.TOR_SAMPLES_PER_FRAME
        val sinePcm = ShortArray(sampleCount) { i ->
            (sin(2.0 * PI * 440.0 * i / CallAudioEngine.SAMPLE_RATE_HZ) * 6000.0).toInt().toShort()
        }
        val encoded = ImaAdpcmCodec.Encoder().encode(sinePcm, sampleCount)
        assertEquals(4 + sampleCount / 2, encoded.size)
        val decoded = ImaAdpcmCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(sampleCount, decoded!!.samples.size)

        // Verify smooth envelope has no step jump around +/-90 amplitude
        val ramp = ShortArray(40) { i -> (75 + i).toShort() }
        val dspState = CallAudioEngine.Companion.MicDspState()
        // Prime steady AC baseline then inspect transitions across ~85..95 range
        CallAudioEngine.applyDcBlockAndSmoothSpeechGain(ramp, ramp.size, dspState)
        for (i in 1 until ramp.size) {
            val step = abs(ramp[i].toInt() - ramp[i - 1].toInt())
            assertTrue("Discontinuity detected at idx=$i step=$step", step < 12)
        }

        // Verify 1st-order ~82 Hz DC blocker removes constant offset over 100ms (1600 samples)
        val dcBias = ShortArray(1600) { 2000 }
        CallAudioEngine.applyDcBlockAndSmoothSpeechGain(dcBias, dcBias.size, CallAudioEngine.Companion.MicDspState())
        assertTrue("DC offset did not decay cleanly: tail=${dcBias.last()}", abs(dcBias.last().toInt()) < 200)
    }

    @Test
    fun callVideo_fragmentFrameSupportsDefaultAndTorMtu() {
        val frame = ByteArray(8_000) { (it * 17 + 5).toByte() }

        val udpFrags = CallVideoEngine.fragmentFrame(1, 100, CallVideoEngine.FLAG_KEYFRAME, 0, frame)
        assertEquals(8, udpFrags.size)

        val torFrags = CallVideoEngine.fragmentFrame(
            frameSeq = 2,
            ptsMs = 200,
            flags = CallVideoEngine.FLAG_KEYFRAME,
            rotationDeg = 0,
            frameBytes = frame,
            maxFragmentPayload = 2900
        )
        assertEquals(3, torFrags.size)

        val reassembler = CallVideoEngine.VideoFrameReassembler {}
        var assembled: CallVideoEngine.ReassembledFrame? = null
        torFrags.forEach { frag ->
            assembled = reassembler.onFragment(frag, nowMs = 1_000L) ?: assembled
        }
        assertNotNull(assembled)
        assertArrayEquals(frame, assembled!!.data)
    }

    @Test
    fun prepareOptimizedImageForTor_decodesAndCompressesValidFileUri() {
        val tempFile = File.createTempFile("seamless_img_test_", ".png", context.cacheDir)
        try {
            val bmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
            FileOutputStream(tempFile).use { out ->
                assertTrue(bmp.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
            bmp.recycle()

            val prepared = prepareOptimizedImageForTor(context, Uri.fromFile(tempFile))
            assertNotNull("Expected prepared image from valid file URI", prepared)
            assertTrue(prepared!!.bytes.isNotEmpty())
            assertTrue(prepared.bytes.size <= TorAttachmentTransfer.MAX_IMAGE_BYTES)
            assertTrue(
                "Unexpected mimeType=${prepared.mimeType}",
                prepared.mimeType in setOf("image/webp", "image/jpeg")
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun injectVideoBitrateSdpHints_onlyAnnotatesVideoCodecsAndPreservesRtx() {
        val sampleSdp = listOf(
            "v=0",
            "o=- 1 2 IN IP4 127.0.0.1",
            "s=-",
            "t=0 0",
            "m=video 9 UDP/TLS/RTP/SAVPF 96 97 98 99",
            "a=rtpmap:96 H264/90000",
            "a=fmtp:96 level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f",
            "a=rtpmap:97 rtx/90000",
            "a=fmtp:97 apt=96",
            "a=rtpmap:98 VP8/90000",
            "a=rtpmap:99 rtx/90000",
            "a=fmtp:99 apt=98"
        ).joinToString("\r\n")

        val updated = WebRtcVideoCallEngine.injectVideoBitrateSdpHints(sampleSdp)
        val updatedLines = updated.split("\r\n")

        val h264Fmtp = updatedLines.firstOrNull { it.startsWith("a=fmtp:96 ") }
        assertNotNull(h264Fmtp)
        assertTrue(h264Fmtp!!.contains("x-google-start-bitrate=1400;x-google-min-bitrate=350;x-google-max-bitrate=2500"))

        val vp8Fmtp = updatedLines.firstOrNull { it.startsWith("a=fmtp:98 ") }
        assertNotNull("Expected VP8 bitrate fmtp line to be added", vp8Fmtp)
        assertTrue(vp8Fmtp!!.contains("x-google-start-bitrate=1400;x-google-min-bitrate=350;x-google-max-bitrate=2500"))

        assertTrue("RTX 97 fmtp must remain untouched", updatedLines.contains("a=fmtp:97 apt=96"))
        assertTrue("RTX 99 fmtp must remain untouched", updatedLines.contains("a=fmtp:99 apt=98"))
    }

    @Test
    fun selectBestCaptureFormat_prefersHd720pAt30Fps() {
        val mixed = listOf(
            CameraEnumerationAndroid.CaptureFormat(320, 240, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(640, 480, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(1280, 720, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(1920, 1080, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(4000, 3000, 7_000, 15_000)
        )
        val hd = WebRtcVideoCallEngine.selectBestCaptureFormat(mixed)
        assertEquals(1280, hd.width)
        assertEquals(720, hd.height)
        assertEquals(30, hd.fps)

        val no720p = listOf(
            CameraEnumerationAndroid.CaptureFormat(320, 240, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(640, 480, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(1280, 960, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(1920, 1080, 15_000, 30_000)
        )
        val nearHd = WebRtcVideoCallEngine.selectBestCaptureFormat(no720p)
        assertEquals(1280, nearHd.width)
        assertEquals(960, nearHd.height)
        assertEquals(30, nearHd.fps)

        val sdOnly = listOf(
            CameraEnumerationAndroid.CaptureFormat(640, 480, 15_000, 30_000),
            CameraEnumerationAndroid.CaptureFormat(4000, 3000, 7_000, 15_000)
        )
        val fallbackSd = WebRtcVideoCallEngine.selectBestCaptureFormat(sdOnly)
        assertEquals(640, fallbackSd.width)
        assertEquals(480, fallbackSd.height)
        assertEquals(30, fallbackSd.fps)
    }

    @Test
    @Config(sdk = [33])
    fun torMessageNotifier_usesStableRawSoundUriAndCreatesHighImportanceChannel() {
        val sysNotif = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        val notifier = TorMessageNotifier(context)
        notifier.ensureChannelsCreated()

        val channel = sysNotif.getNotificationChannel(TorMessageNotifier.CHANNEL_ID)
        assertNotNull("Tor v3 notification channel must be created eagerly", channel)
        assertTrue(
            "Tor channel importance must be DEFAULT or HIGH",
            channel!!.importance >= AndroidNotificationManager.IMPORTANCE_DEFAULT
        )
        assertEquals(
            "android.resource://${context.packageName}/raw/seamless_notification",
            channel.sound?.toString()
        )
        assertTrue("Tor channel must enable vibration", channel.shouldVibrate())
    }

    @Test
    @Config(sdk = [33])
    fun meshNotificationManager_usesStableSoundUriAndSilentActivePeersChannel() {
        Shadows.shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val sysNotif = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        sysNotif.cancelAll()

        @Suppress("DEPRECATION")
        val fakeResources = object : android.content.res.Resources(
            context.assets,
            context.resources.displayMetrics,
            context.resources.configuration
        ) {
            override fun getText(id: Int): CharSequence = when (id) {
                com.seamless.android.R.string.notification_active_peers_title -> "Nearby Peers"
                com.seamless.android.R.string.notification_active_peers_one -> "1 peer nearby"
                com.seamless.android.R.string.notification_active_peers_many -> "%d peers nearby"
                else -> runCatching { context.resources.getText(id) }.getOrDefault("")
            }

            override fun getString(id: Int): String = getText(id).toString()

            override fun getString(id: Int, vararg formatArgs: Any?): String =
                String.format(getText(id).toString(), *formatArgs)
        }
        val wrappedContext = object : android.content.ContextWrapper(context) {
            override fun getResources(): android.content.res.Resources = fakeResources
        }

        val manager = SeamlessNotificationManager(
            context = wrappedContext,
            notificationManager = NotificationManagerCompat.from(wrappedContext),
            notificationIntervalManager = NotificationIntervalManager()
        )

        val expectedSoundUri = "android.resource://${context.packageName}/raw/seamless_notification"
        val dmChannel = sysNotif.getNotificationChannel(SeamlessNotificationManager.CHANNEL_ID)
        val geohashChannel = sysNotif.getNotificationChannel(SeamlessNotificationManager.GEOHASH_CHANNEL_ID)
        val activePeersChannel = sysNotif.getNotificationChannel(SeamlessNotificationManager.ACTIVE_PEERS_CHANNEL_ID)

        assertNotNull(dmChannel)
        assertEquals(expectedSoundUri, dmChannel!!.sound?.toString())
        assertTrue(dmChannel.importance >= AndroidNotificationManager.IMPORTANCE_DEFAULT)

        assertNotNull(geohashChannel)
        assertEquals(expectedSoundUri, geohashChannel!!.sound?.toString())
        assertTrue(geohashChannel.importance >= AndroidNotificationManager.IMPORTANCE_DEFAULT)

        assertNotNull(activePeersChannel)
        assertNull("Active peers status channel must stay silent", activePeersChannel!!.sound)
        assertTrue(activePeersChannel.importance <= AndroidNotificationManager.IMPORTANCE_LOW)

        manager.setAppBackgroundState(true)
        manager.showActiveUserNotification(listOf("peer-active-1"))
        val posted = Shadows.shadowOf(sysNotif).allNotifications
        assertTrue(
            "Active peer notification must post on silent active-peers channel",
            posted.any { it.channelId == SeamlessNotificationManager.ACTIVE_PEERS_CHANNEL_ID }
        )
    }

    @Test
    fun torDaemon_extractV3OnionId_validatesStrict56CharBase32() {
        val valid56 = "abcdefghijklmnopqrstuvwxyz234567abcdefghijklmnopqrstuvwx"
        assertEquals(valid56, TorDaemonManager.extractV3OnionId(valid56))
        assertEquals(valid56, TorDaemonManager.extractV3OnionId("  ${valid56.uppercase()}.ONION:18888  "))
        assertNull(TorDaemonManager.extractV3OnionId("not-available.onion:18888"))
        assertNull(TorDaemonManager.extractV3OnionId(""))
        assertNull(TorDaemonManager.extractV3OnionId("1".repeat(56) + ".onion"))
        assertNull(TorDaemonManager.extractV3OnionId("a".repeat(55) + ".onion"))
        assertNull(TorDaemonManager.extractV3OnionId("a".repeat(57) + ".onion"))
    }

    @Test
    @Config(sdk = [33])
    fun meshDelegateHandler_ignoresOwnSentPrivateMessageAndNotifiesIncoming() {
        Shadows.shadowOf(context.applicationContext as Application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val sysNotif = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
        sysNotif.cancelAll()

        val state = ChatState()
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        val mockDao = Mockito.mock(MessageDao::class.java)
        Mockito.`when`(mockDao.getAllMessages()).thenReturn(kotlinx.coroutines.flow.emptyFlow())
        val repo = MessageRepository(mockDao)
        val messageManager = MessageManager(state, repo, scope)
        val dataManager = DataManager(context)
        val channelManager = ChannelManager(state, messageManager, dataManager, scope)
        val myPeerId = "my-peer-01"
        val noiseDelegate = object : NoiseSessionDelegate {
            override fun hasEstablishedSession(peerID: String): Boolean = true
            override fun initiateHandshake(peerID: String) {}
            override fun getMyPeerID(): String = myPeerId
        }
        val privateChatManager = PrivateChatManager(state, messageManager, dataManager, noiseDelegate)
        val notificationManager = SeamlessNotificationManager(
            context = context,
            notificationManager = NotificationManagerCompat.from(context),
            notificationIntervalManager = NotificationIntervalManager()
        )
        notificationManager.setAppBackgroundState(true)

        var hapticCount = 0
        val handler = MeshDelegateHandler(
            state = state,
            messageManager = messageManager,
            channelManager = channelManager,
            privateChatManager = privateChatManager,
            notificationManager = notificationManager,
            coroutineScope = scope,
            onHapticFeedback = { hapticCount++ },
            getMyPeerID = { myPeerId },
            getMeshService = { null }
        )

        val ownEcho = BitchatMessage(
            id = "own-pm-1",
            sender = "Me",
            content = "Outgoing echo should be ignored",
            timestamp = Date(),
            isPrivate = true,
            recipientNickname = "Alice",
            senderPeerID = myPeerId
        )
        handler.didReceiveMessage(ownEcho)
        assertEquals("Own outgoing PM must not trigger haptic", 0, hapticCount)
        assertTrue(
            "Own outgoing PM must not post notification",
            Shadows.shadowOf(sysNotif).allNotifications.isEmpty()
        )

        val incoming = BitchatMessage(
            id = "incoming-pm-1",
            sender = "Alice",
            content = "Hello from peer!",
            timestamp = Date(),
            isPrivate = true,
            recipientNickname = "Me",
            senderPeerID = "peer-alice"
        )
        handler.didReceiveMessage(incoming)
        assertEquals("Incoming PM must trigger haptic once", 1, hapticCount)
        assertTrue(
            "Incoming PM must post DM notification",
            Shadows.shadowOf(sysNotif).allNotifications.any {
                it.channelId == SeamlessNotificationManager.CHANNEL_ID
            }
        )
    }
}
