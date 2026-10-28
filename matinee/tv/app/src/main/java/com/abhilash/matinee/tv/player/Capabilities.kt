package com.abhilash.matinee.tv.player

import android.app.Activity
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import com.abhilash.matinee.tv.data.Settings
import com.abhilash.matinee.tv.report.isHardwareCodecName

data class AudioCaps(val passthrough: Set<String>, val maxPcmChannels: Int)

object Capabilities {
    @Suppress("DEPRECATION")
    fun display(activity: Activity): Display =
        if (Build.VERSION.SDK_INT >= 30) activity.display!! else activity.windowManager.defaultDisplay

    fun rank(activity: Activity): RankCaps {
        val display = display(activity)
        @Suppress("DEPRECATION")
        val hdr = display.hdrCapabilities?.supportedHdrTypes?.toSet().orEmpty()
        val decoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        fun decodersFor(mime: String) = decoders.filter { d -> d.supportedTypes.any { it.equals(mime, true) } }
        val hevc4k = decodersFor("video/hevc").any { d ->
            d.getCapabilitiesForType("video/hevc").videoCapabilities?.areSizeAndRateSupported(3840, 2160, 24.0) == true
        }
        val av1MaxHeight = decodersFor("video/av01").filter { hardware(it) }
            .maxOfOrNull { it.getCapabilitiesForType("video/av01").videoCapabilities?.supportedHeights?.upper ?: 0 } ?: 0
        val audio = audio(activity)
        return RankCaps(
            maxHeight = display.supportedModes.maxOf { it.physicalHeight },
            dolbyVision = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in hdr && decodersFor("video/dolby-vision").isNotEmpty(),
            hdr10Plus = Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in hdr,
            hdr10 = Display.HdrCapabilities.HDR_TYPE_HDR10 in hdr,
            hlg = Display.HdrCapabilities.HDR_TYPE_HLG in hdr,
            hevc4k = hevc4k,
            av1MaxHeight = av1MaxHeight,
            passthrough = audio.passthrough,
            maxPcmChannels = audio.maxPcmChannels,
        )
    }

    private fun hardware(info: MediaCodecInfo) =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else isHardwareCodecName(info.name)

    private val ENCODINGS = mapOf(
        C.ENCODING_AC3 to MIME_AC3, C.ENCODING_E_AC3 to MIME_EAC3, C.ENCODING_E_AC3_JOC to MIME_EAC3_JOC,
        C.ENCODING_DOLBY_TRUEHD to MIME_TRUEHD, C.ENCODING_DTS to MIME_DTS, C.ENCODING_DTS_HD to MIME_DTS_HD,
        C.ENCODING_DTS_UHD_P2 to MIME_DTS_UHD,
    )

    // What the current output takes untouched, and how many PCM channels it takes. Outputs where passthrough
    // failed before are treated as PCM-only.
    @OptIn(UnstableApi::class)
    fun audio(context: Context): AudioCaps {
        if (Settings.forcePcm(outputKey(context))) return AudioCaps(emptySet(), 2)
        val caps = AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null)
        return AudioCaps(ENCODINGS.filterKeys { caps.supportsEncoding(it) }.values.toSet(), caps.maxChannelCount)
    }

    // Identifies "this TV's speakers" vs "the soundbar on eARC" vs "Bluetooth headphones"
    fun outputKey(context: Context): String {
        val outputs = context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val external = outputs.filter { it.type in EXTERNAL_OUTPUTS }
        return (external.ifEmpty { outputs.toList() }).map { "${it.type}:${it.productName}" }.sorted().joinToString("|")
    }

    private val EXTERNAL_OUTPUTS = buildSet {
        addAll(listOf(AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_DIGITAL))
        if (Build.VERSION.SDK_INT >= 31) addAll(listOf(AudioDeviceInfo.TYPE_HDMI_EARC, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER))
    }
}
