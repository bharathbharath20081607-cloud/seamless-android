package org.webrtc

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log

/**
 * Unlocks hardware-accelerated H.264 (Constrained Baseline) and VP8 encoding across ALL
 * modern Android chipsets — including MediaTek (`c2.mtk.*`, `OMX.MTK.*`), Qualcomm Codec2
 * (`c2.qti.*`, `OMX.qcom.*`), Samsung Exynos / Google Tensor (`c2.exynos.*`, `OMX.Exynos.*`),
 * HiSilicon (`c2.hisi.*`, `OMX.hisi.*`), and Unisoc (`c2.unisoc.*`, `OMX.sprd.*`).
 *
 * Upstream [HardwareVideoEncoderFactory] only whitelists `OMX.qcom.` and `OMX.Exynos.`,
 * which causes non-OMX/MediaTek/Codec2 phones to drop H.264 from WebRTC SDP or fall back
 * to CPU software encoding.
 */
class UniversalHardwareVideoEncoderFactory(
    sharedContext: EglBase.Context?,
    enableIntelVp8Encoder: Boolean = true,
    enableH264HighProfile: Boolean = true
) : VideoEncoderFactory {

    companion object {
        private const val TAG = "UniversalHwEncoder"
        private const val KEYFRAME_INTERVAL_SEC = 4
    }

    private val egl14Context: EglBase14.Context? = sharedContext as? EglBase14.Context
    private val upstreamHwFactory = HardwareVideoEncoderFactory(
        sharedContext,
        enableIntelVp8Encoder,
        enableH264HighProfile
    )
    private val primaryFactory = DefaultAlignedVideoEncoderFactory(
        sharedContext,
        enableIntelVp8Encoder,
        enableH264HighProfile,
        ResolutionAdjustment.MULTIPLE_OF_16
    )
    private val softwareFactory = SoftwareVideoEncoderFactory()

    override fun getSupportedCodecs(): Array<VideoCodecInfo> {
        val codecs = LinkedHashMap<String, VideoCodecInfo>()
        for (c in primaryFactory.supportedCodecs) {
            val key = "${c.name.uppercase()}:${c.params}"
            codecs[key] = c
        }

        val hasH264 = codecs.values.any { it.name.equals("H264", ignoreCase = true) }
        if (!hasH264 && findUniversalHardwareCodec(VideoCodecMimeType.H264) != null) {
            val baseline = H264Utils.DEFAULT_H264_BASELINE_PROFILE_CODEC
            codecs["H264:${baseline.params}"] = baseline
        }

        val hasVp8 = codecs.values.any { it.name.equals("VP8", ignoreCase = true) }
        if (!hasVp8 && findUniversalHardwareCodec(VideoCodecMimeType.VP8) != null) {
            codecs["VP8:{}"] = VideoCodecInfo("VP8", HashMap<String, String>(), ArrayList<String>())
        }

        return codecs.values.toTypedArray()
    }

    override fun createEncoder(info: VideoCodecInfo): VideoEncoder? {
        val upstreamHasHw = upstreamHwFactory.supportedCodecs.any {
            it.name.equals(info.name, ignoreCase = true) && it.params == info.params
        }
        if (upstreamHasHw) {
            primaryFactory.createEncoder(info)?.let { return it }
        }
        val mimeType = runCatching { VideoCodecMimeType.valueOf(info.name.uppercase()) }.getOrNull()
        if (mimeType == VideoCodecMimeType.H264 || mimeType == VideoCodecMimeType.VP8) {
            val hwEncoder = createUniversalHardwareEncoder(info, mimeType)
            val swEncoder = softwareFactory.createEncoder(info)
            if (hwEncoder != null) {
                return if (swEncoder != null) {
                    VideoEncoderFallback(swEncoder, hwEncoder)
                } else {
                    hwEncoder
                }
            }
        }
        return softwareFactory.createEncoder(info)
    }

    private fun createUniversalHardwareEncoder(
        info: VideoCodecInfo,
        type: VideoCodecMimeType
    ): VideoEncoder? {
        val codecInfo = findUniversalHardwareCodec(type) ?: return null
        val codecName = codecInfo.name
        val mime = type.mimeType()
        val caps = runCatching { codecInfo.getCapabilitiesForType(mime) }.getOrNull() ?: return null
        val surfaceColorFormat = MediaCodecUtils.selectColorFormat(
            MediaCodecUtils.TEXTURE_COLOR_FORMATS,
            caps
        )
        val yuvColorFormat = MediaCodecUtils.selectColorFormat(
            MediaCodecUtils.ENCODER_COLOR_FORMATS,
            caps
        )
        if (surfaceColorFormat == null && yuvColorFormat == null) return null

        val lower = codecName.lowercase()
        val adjuster: BitrateAdjuster = when {
            lower.startsWith("c2.") -> BaseBitrateAdjuster()
            lower.contains("omx.exynos") -> DynamicBitrateAdjuster()
            else -> BaseBitrateAdjuster()
        }

        Log.i(TAG, "⚡ Created universal hardware encoder: $codecName for ${info.name}")
        return HardwareVideoEncoder(
            MediaCodecWrapperFactoryImpl(),
            codecName,
            type,
            surfaceColorFormat,
            yuvColorFormat,
            info.params,
            KEYFRAME_INTERVAL_SEC,
            0,
            adjuster,
            egl14Context
        )
    }

    private fun findUniversalHardwareCodec(type: VideoCodecMimeType): MediaCodecInfo? {
        val codecInfos = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        }.getOrElse { emptyArray() }
        var fallbackMatch: MediaCodecInfo? = null
        for (info in codecInfos) {
            if (!info.isEncoder) continue
            if (!MediaCodecUtils.codecSupportsType(info, type)) continue
            if (!MediaCodecUtils.isHardwareAccelerated(info)) continue
            val caps = runCatching { info.getCapabilitiesForType(type.mimeType()) }.getOrNull() ?: continue
            val hasTexture = MediaCodecUtils.selectColorFormat(MediaCodecUtils.TEXTURE_COLOR_FORMATS, caps) != null
            val hasYuv = MediaCodecUtils.selectColorFormat(MediaCodecUtils.ENCODER_COLOR_FORMATS, caps) != null
            if (hasTexture || hasYuv) {
                if (info.name.lowercase().startsWith("c2.")) {
                    return info
                }
                if (fallbackMatch == null) fallbackMatch = info
            }
        }
        return fallbackMatch
    }
}
