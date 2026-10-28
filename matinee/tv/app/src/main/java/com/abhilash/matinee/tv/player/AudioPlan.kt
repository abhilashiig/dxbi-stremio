package com.abhilash.matinee.tv.player

const val MIME_TRUEHD = "audio/true-hd"
const val MIME_DTS_UHD = "audio/vnd.dts.uhd"
const val MIME_DTS_HD = "audio/vnd.dts.hd"
const val MIME_DTS = "audio/vnd.dts"
const val MIME_EAC3_JOC = "audio/eac3-joc"
const val MIME_EAC3 = "audio/eac3"
const val MIME_AC3 = "audio/ac3"
const val MIME_AAC = "audio/mp4a-latm"
const val MIME_OPUS = "audio/opus"

// Best to worst when the format reaches the receiver untouched (Atmos and lossless first)
private val PASSTHROUGH_ORDER = listOf(MIME_TRUEHD, MIME_DTS_UHD, MIME_DTS_HD, MIME_EAC3_JOC, MIME_EAC3, MIME_DTS, MIME_AC3)
// Decoded in the app to multichannel PCM (eARC/HDMI carries 8 channels): lossless first
private val MULTICHANNEL_DECODE_ORDER = listOf(MIME_TRUEHD, MIME_DTS_HD, MIME_EAC3_JOC, MIME_EAC3, MIME_DTS, MIME_AC3, MIME_AAC, MIME_OPUS)
// Stereo outputs (TV speakers, Bluetooth): the lightest good track, since everything ends up as two channels
private val STEREO_DECODE_ORDER = listOf(MIME_EAC3_JOC, MIME_EAC3, MIME_AC3, MIME_AAC, MIME_OPUS, MIME_DTS, MIME_DTS_HD, MIME_TRUEHD)

// Preferred audio formats for the current output, most preferred first
fun audioMimePreference(passthrough: Set<String>, maxPcmChannels: Int): List<String> {
    val direct = PASSTHROUGH_ORDER.filter { it in passthrough }
    val decode = if (maxPcmChannels > 2) MULTICHANNEL_DECODE_ORDER else STEREO_DECODE_ORDER
    return (direct + decode).distinct()
}

data class AudioOption(val language: String?, val mime: String?, val channels: Int, val isDefault: Boolean)

// Language first (the chosen one, else the stream's default track's), then the format that sounds best here
fun pickAudio(options: List<AudioOption>, preferredLanguage: String?, mimeOrder: List<String>): Int? {
    if (options.isEmpty()) return null
    fun lang(s: String?) = s?.lowercase()?.substringBefore('-')
    val wanted = lang(preferredLanguage)?.takeIf { w -> options.any { lang(it.language) == w } }
        ?: lang((options.firstOrNull { it.isDefault } ?: options.first()).language)
    val candidates = options.indices.filter { lang(options[it].language) == wanted }.ifEmpty { options.indices.toList() }
    return candidates.minWithOrNull(
        compareBy<Int> { mimeOrder.indexOf(options[it].mime).let { i -> if (i < 0) Int.MAX_VALUE else i } }
            .thenByDescending { options[it].channels }
            .thenByDescending { options[it].isDefault }
    )
}
