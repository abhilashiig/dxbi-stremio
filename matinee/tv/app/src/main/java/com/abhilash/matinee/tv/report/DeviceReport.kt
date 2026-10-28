package com.abhilash.matinee.tv.report

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import android.media.AudioAttributes as PlatformAudioAttributes

class Section(val title: String, val lines: List<String>)

// What this device can play: the facts Phase 1 (the player) is designed around.
fun deviceReport(activity: Activity): List<Section> =
    listOf(device(activity), display(activity), videoDecoders(), audioOutputs(activity), passthrough(activity), audioDecoders())

private fun device(context: Context): Section {
    val am = context.getSystemService(ActivityManager::class.java)
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
    return Section("Device", listOfNotNull(
        "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
        "Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}" +
            if (context.packageManager.hasSystemFeature("amazon.hardware.fire_tv")) ", Fire OS" else "",
        "Chip: $soc",
        "RAM: ${mem.totalMem / 1_048_576} MB, app heap ${am.memoryClass} MB / large ${am.largeMemoryClass} MB" +
            if (am.isLowRamDevice) ", low-RAM device" else "",
    ))
}

private fun display(activity: Activity): Section {
    @Suppress("DEPRECATION")
    val display: Display = if (Build.VERSION.SDK_INT >= 30) activity.display!! else activity.windowManager.defaultDisplay
    val mode = display.mode
    val lines = mutableListOf("Current: ${mode.physicalWidth}x${mode.physicalHeight} @ ${"%.3f".format(mode.refreshRate)} Hz")
    lines += "Modes: " + display.supportedModes.joinToString { "${it.physicalWidth}x${it.physicalHeight}@${"%.3f".format(it.refreshRate)}" }
    @Suppress("DEPRECATION")
    val hdr = display.hdrCapabilities
    lines += "HDR: " + (hdr?.supportedHdrTypes?.takeIf { it.isNotEmpty() }?.joinToString { hdrName(it) } ?: "none")
    if (hdr != null && hdr.desiredMaxLuminance > 0) lines += "Peak brightness: ${hdr.desiredMaxLuminance.toInt()} nits"
    if (Build.VERSION.SDK_INT >= 34) lines += "HDR in current mode: " + mode.supportedHdrTypes.joinToString { hdrName(it) }.ifEmpty { "none" }
    if (Build.VERSION.SDK_INT >= 31) {
        val pref = activity.getSystemService(DisplayManager::class.java).matchContentFrameRateUserPreference
        lines += "Match content frame rate: " + when (pref) {
            DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER -> "never"
            DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY -> "seamless only"
            DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS -> "always"
            else -> "unknown"
        }
    }
    return Section("Display", lines)
}

private fun hdrName(type: Int) = when (type) {
    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
    Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
    Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
    else -> "type $type"
}

private val VIDEO_TYPES = listOf(
    "video/avc" to "AVCProfile", "video/hevc" to "HEVCProfile", "video/x-vnd.on2.vp9" to "VP9Profile",
    "video/av01" to "AV1Profile", "video/dolby-vision" to "DolbyVisionProfile",
)

private fun videoDecoders(): Section {
    val codecs = decoders()
    val lines = VIDEO_TYPES.flatMap { (mime, prefix) ->
        val found = codecs.mapNotNull { info -> info.supportedTypes.firstOrNull { it.equals(mime, true) }?.let { info to it } }
        if (found.isEmpty()) return@flatMap listOf("$mime: no decoder")
        found.map { (info, type) ->
            val caps = info.getCapabilitiesForType(type)
            val vc = caps.videoCapabilities
            val uhd = listOf(24, 30, 60).joinToString(" ") { fps -> "$fps" + if (vc.areSizeAndRateSupported(3840, 2160, fps.toDouble())) "✓" else "✗" }
            val profiles = caps.profileLevels.map { profileName(prefix, it.profile) }.distinct().joinToString()
            val tunnel = if (caps.isFeatureSupported(CodecCapabilities.FEATURE_TunneledPlayback)) ", tunneling" else ""
            "$mime ${info.name} [${if (isHardware(info)) "HW" else "SW"}] max ${vc.supportedWidths.upper}x${vc.supportedHeights.upper}, 4K fps $uhd$tunnel — $profiles"
        }
    }
    return Section("Video decoders", lines)
}

// Dolby Vision profiles by their usual numbers (dvhe.05 = profile 5, ...)
private val DV_PROFILE_NUMBERS = mapOf(
    "DvheDtr" to "4", "DvheStn" to "5", "DvheDth" to "6", "DvheDtb" to "7", "DvheSt" to "8", "DvavSe" to "9", "Dvav110" to "10",
)

private fun profileName(prefix: String, value: Int): String {
    val name = CodecProfileLevel::class.java.fields
        .firstOrNull { it.name.startsWith(prefix) && it.type == Int::class.javaPrimitiveType && it.getInt(null) == value }
        ?.name?.removePrefix(prefix) ?: return "0x${value.toString(16)}"
    return DV_PROFILE_NUMBERS[name]?.let { "P$it ($name)" } ?: if (name.first().isDigit()) "Profile$name" else name
}

private fun audioOutputs(context: Context): Section {
    val am = context.getSystemService(AudioManager::class.java)
    val lines = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { d ->
        val enc = d.encodings.takeIf { it.isNotEmpty() }?.joinToString { encodingName(it) } ?: "any"
        val ch = d.channelCounts.takeIf { it.isNotEmpty() }?.joinToString("/") ?: "any"
        "${constantName(AudioDeviceInfo::class.java, "TYPE_", d.type)} \"${d.productName}\" — channels $ch — $enc"
    }.toMutableList()
    if (Build.VERSION.SDK_INT >= 33) {
        val attrs = PlatformAudioAttributes.Builder()
            .setUsage(PlatformAudioAttributes.USAGE_MEDIA).setContentType(PlatformAudioAttributes.CONTENT_TYPE_MOVIE).build()
        lines += "Direct playback (Android 13+): " + am.getDirectProfilesForAttributes(attrs)
            .joinToString { p -> "${encodingName(p.format)} ${p.channelMasks.maxOfOrNull { Integer.bitCount(it) } ?: "?"}ch" }.ifEmpty { "none" }
    }
    return Section("Audio outputs", lines)
}

// What ExoPlayer would pass through untouched to the current output (TV, HDMI, eARC soundbar…)
@OptIn(UnstableApi::class)
private fun passthrough(context: Context): Section {
    @Suppress("DEPRECATION")
    val caps = AudioCapabilities.getCapabilities(context)
    val formats = listOf(
        "Dolby Digital" to C.ENCODING_AC3, "Dolby Digital Plus" to C.ENCODING_E_AC3, "DD+ Atmos" to C.ENCODING_E_AC3_JOC,
        "AC-4" to C.ENCODING_AC4, "TrueHD (Atmos)" to C.ENCODING_DOLBY_TRUEHD, "DTS" to C.ENCODING_DTS,
        "DTS-HD" to C.ENCODING_DTS_HD, "DTS:X (UHD P2)" to C.ENCODING_DTS_UHD_P2,
    )
    return Section("Passthrough (ExoPlayer)", listOf(
        "Max PCM channels: ${caps.maxChannelCount}",
    ) + formats.map { (name, enc) -> "$name: ${if (caps.supportsEncoding(enc)) "yes" else "no"}" })
}

private val AUDIO_TYPES = listOf(
    "audio/ac3", "audio/eac3", "audio/eac3-joc", "audio/ac4", "audio/true-hd", "audio/vnd.dts", "audio/vnd.dts.hd",
    "audio/vnd.dts.uhd", "audio/mp4a-latm", "audio/opus", "audio/flac", "audio/vorbis",
)

private fun audioDecoders(): Section {
    val codecs = decoders()
    return Section("Audio decoders", AUDIO_TYPES.map { mime ->
        val found = codecs.filter { info -> info.supportedTypes.any { it.equals(mime, true) } }
        "$mime: " + found.joinToString { info ->
            val ch = info.getCapabilitiesForType(info.supportedTypes.first { it.equals(mime, true) }).audioCapabilities?.maxInputChannelCount
            "${info.name} [${if (isHardware(info)) "HW" else "SW"}, ${ch}ch]"
        }.ifEmpty { "none" }
    })
}

private fun decoders(): List<MediaCodecInfo> =
    MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder && !(Build.VERSION.SDK_INT >= 29 && it.isAlias) }

private fun isHardware(info: MediaCodecInfo): Boolean =
    if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated else isHardwareCodecName(info.name)

// Before Android 10 there's no flag, so go by name. Fire OS 7 (Android 9) relies on this.
fun isHardwareCodecName(name: String): Boolean {
    val n = name.lowercase()
    return !(n.startsWith("omx.google.") || n.startsWith("c2.android.") || n.startsWith("omx.ffmpeg.") ||
        n.startsWith("c2.ffmpeg.") || n.contains(".sw.") || n.endsWith(".sw"))
}

private fun encodingName(value: Int) = constantName(AudioFormat::class.java, "ENCODING_", value)

private fun constantName(cls: Class<*>, prefix: String, value: Int): String =
    cls.fields.firstOrNull { it.name.startsWith(prefix) && it.type == Int::class.javaPrimitiveType && it.getInt(null) == value }
        ?.name?.removePrefix(prefix) ?: "$value"
