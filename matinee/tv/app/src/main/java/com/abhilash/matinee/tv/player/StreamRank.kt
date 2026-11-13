package com.abhilash.matinee.tv.player

import androidx.compose.runtime.Immutable
import com.abhilash.matinee.tv.data.StreamInfo

// What a stream says about itself (addons put it in the name, description and filename)
data class StreamTags(
    val height: Int,
    val dolbyVision: Boolean,
    val hdr10Plus: Boolean,
    val hdr10: Boolean,
    val hlg: Boolean,
    val hevc: Boolean,
    val av1: Boolean,
    val atmos: Boolean,
    val trueHd: Boolean,
    val dtsHd: Boolean,
    val dts: Boolean,
    val ddPlus: Boolean,
    val dd: Boolean,
    val sizeBytes: Long?,
    val cached: Boolean?,
) {
    val hdr get() = dolbyVision || hdr10Plus || hdr10 || hlg
    // DV without an HDR10 base layer is almost always profile 5: green/purple on anything that can't do DV
    val dolbyVisionOnly get() = dolbyVision && !hdr10Plus && !hdr10 && !hlg
}

// What this TV/box and its audio output can do (see Capabilities.kt)
data class RankCaps(
    val maxHeight: Int,
    val dolbyVision: Boolean,
    val hdr10Plus: Boolean,
    val hdr10: Boolean,
    val hlg: Boolean,
    val hevc4k: Boolean,
    val av1MaxHeight: Int,
    val passthrough: Set<String>,
    val maxPcmChannels: Int,
)

@Immutable
data class RankedStream(val stream: StreamInfo, val tags: StreamTags, val badges: List<String>, val warning: String?)

private fun has(text: String, pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)

fun streamTags(text: String, size: Long? = null): StreamTags {
    val height = when {
        has(text, """\b(2160p|4k|uhd)\b""") -> 2160
        has(text, """\b1440p\b""") -> 1440
        has(text, """\b(1080p|fhd)\b""") -> 1080
        has(text, """\b720p\b""") -> 720
        has(text, """\b(576p|480p|sd)\b""") -> 480
        else -> 0
    }
    val hdr10Plus = has(text, """hdr10(\+|plus)""")
    val sizeMatch = Regex("""(\d+(?:[.,]\d+)?)\s*(TB|GB|MB)\b""", RegexOption.IGNORE_CASE).find(text)
    val parsedSize = sizeMatch?.let {
        val n = it.groupValues[1].replace(',', '.').toDouble()
        val unit = when (it.groupValues[2].uppercase()) { "TB" -> 1e12; "GB" -> 1e9; else -> 1e6 }
        (n * unit).toLong()
    }
    val uncached = has(text, """uncached|download]|⏳""")
    val cached = when {
        uncached -> false
        has(text, """\+]|⚡|\bcached\b|instant""") -> true
        else -> null
    }
    return StreamTags(
        height = height,
        dolbyVision = has(text, """\b(dv|dovi|dolby[ .]?vision)\b"""),
        hdr10Plus = hdr10Plus,
        hdr10 = !hdr10Plus && has(text, """\bhdr(10)?\b"""),
        hlg = has(text, """\bhlg\b"""),
        hevc = has(text, """\b(hevc|x265|h\.?265)\b"""),
        av1 = has(text, """\bav1\b"""),
        atmos = has(text, """\batmos\b"""),
        trueHd = has(text, """\btrue-?hd\b"""),
        dtsHd = has(text, """\bdts[- ]?(hd|x|ma)\b|dts:x"""),
        dts = has(text, """\bdts\b"""),
        ddPlus = has(text, """\b(ddp|dd\+|e-?ac-?3)|\bdd\+"""),
        dd = has(text, """\b(dd|ac-?3)\b|\bdd5"""),
        sizeBytes = size ?: parsedSize,
        cached = cached,
    )
}

// Quality first, but only quality this setup can actually show and hear. Ties keep the addon's own order.
fun rankStreams(streams: List<StreamInfo>, caps: RankCaps): List<RankedStream> {
    val ranked = streams.map { s ->
        val t = streamTags(s.text, s.size)
        val warning = when {
            t.dolbyVisionOnly && !caps.dolbyVision -> "Dolby Vision only: colours will be wrong on this TV"
            t.av1 && t.height > caps.av1MaxHeight -> "AV1 at this resolution isn't hardware-decoded here, so it may stutter"
            t.hevc && t.height > 1080 && !caps.hevc4k -> "This device can't decode 4K HEVC"
            t.hdr && !caps.hdr10 && !caps.dolbyVision -> "HDR on a screen without HDR: colours may look washed out"
            else -> null
        }
        RankedStream(s, t, badges(t), warning)
    }
    return ranked.sortedWith(
        compareByDescending<RankedStream> { if (it.warning == null) 2 else if (it.warning.startsWith("HDR on")) 1 else 0 }
            .thenByDescending { when (it.tags.cached) { true -> 2; null -> 1; false -> 0 } }
            .thenByDescending { minOf(it.tags.height, caps.maxHeight) }
            .thenByDescending { hdrScore(it.tags, caps) }
            .thenByDescending { audioScore(it.tags, caps) }
    )
}

private fun hdrScore(t: StreamTags, caps: RankCaps) = when {
    t.dolbyVision && caps.dolbyVision -> 4
    t.hdr10Plus && caps.hdr10Plus -> 3
    (t.hdr10 || t.hdr10Plus || (t.dolbyVision && !t.dolbyVisionOnly)) && caps.hdr10 -> 2
    t.hlg && caps.hlg -> 2
    !t.hdr -> 1
    else -> 0
}

private fun audioScore(t: StreamTags, caps: RankCaps): Int {
    val atmosHeard = t.atmos && ((t.trueHd && MIME_TRUEHD in caps.passthrough) || (t.ddPlus && MIME_EAC3_JOC in caps.passthrough))
    val lossless = (t.trueHd || t.dtsHd) && caps.maxPcmChannels > 2
    return when {
        atmosHeard -> 4
        lossless || (t.dtsHd && MIME_DTS_HD in caps.passthrough) -> 3
        t.ddPlus || t.dd || t.dts -> 2
        else -> 1
    }
}

private fun badges(t: StreamTags) = buildList {
    when (t.height) { 2160 -> add("4K"); 1440 -> add("1440p"); 1080 -> add("1080p"); 720 -> add("720p"); 480 -> add("SD") }
    if (t.dolbyVision) add("Dolby Vision")
    if (t.hdr10Plus) add("HDR10+") else if (t.hdr10) add("HDR10")
    if (t.hlg) add("HLG")
    when {
        t.atmos -> add("Atmos")
        t.trueHd -> add("TrueHD")
        t.dtsHd -> add("DTS-HD")
        t.ddPlus -> add("DD+")
        t.dts -> add("DTS")
        t.dd -> add("DD")
    }
    if (t.cached == true) add("Instant")
    sizeLabel(t.sizeBytes)?.let { add(it) }
}

fun sizeLabel(bytes: Long?) = bytes?.let { if (it >= 1e9) "%.1f GB".format(it / 1e9) else "%.0f MB".format(it / 1e6) }

fun hdrLabel(t: StreamTags) = when {
    t.dolbyVision -> "Dolby Vision"
    t.hdr10Plus -> "HDR10+"
    t.hdr10 -> "HDR10"
    t.hlg -> "HLG"
    else -> null
}

// What tells one version from another: the filename after its resolution, dots turned to spaces
// ("Show.S01E01.Pilot.2160p.AMZN.WEB-DL.DDP5.1.HEVC-GRP.mkv" -> "AMZN WEB-DL DDP5.1 HEVC-GRP")
fun releaseLabel(s: StreamInfo): String {
    val raw = s.filename ?: s.description.lines().firstOrNull { it.isNotBlank() } ?: s.name
    val name = raw.substringAfterLast('/').replace(Regex("""\.(mkv|mp4|avi|m4v|ts|webm)$""", RegexOption.IGNORE_CASE), "")
    val spaced = name.replace(Regex("""_|(?<!\d)(?<!\bH)\.|\.(?!\d)"""), " ").replace(Regex("""\s+"""), " ").trim()
    val after = Regex("""\b(2160p|1440p|1080p|720p|576p|480p|4k|uhd)\b""", RegexOption.IGNORE_CASE).find(spaced)?.let { spaced.substring(it.range.last + 1).trim() }
    val label = after?.takeIf { it.length >= 3 } ?: spaced
    // "Show (1080p WEB-DL x265 GRP).mkv" leaves the closing bracket behind
    return if (label.endsWith(")") && '(' !in label) label.dropLast(1).trim() else label
}

fun videoSummary(t: StreamTags, text: String) = listOfNotNull(
    when { t.av1 -> "AV1"; t.hevc -> "HEVC"; has(text, """\b(x264|h\.?264|avc)\b""") -> "H.264"; else -> null },
    hdrLabel(t) ?: "SDR",
).joinToString(" · ")

fun audioSummary(t: StreamTags, text: String): String? {
    val format = when {
        t.trueHd -> "Dolby TrueHD"
        t.dtsHd -> "DTS-HD"
        t.ddPlus -> "Dolby Digital Plus"
        t.dts -> "DTS"
        t.dd -> "Dolby Digital"
        has(text, """\baac\b""") -> "AAC"
        else -> null
    }?.let { if (t.atmos) "$it Atmos" else it }
    val channels = Regex("""(?<!\d)([2-7])\.([01])(?!\d)""").find(text)?.value
    return listOfNotNull(format, channels).joinToString(" · ").ifEmpty { null }
}

private val SOURCES = mapOf(
    "remux" to "REMUX", "bluray" to "Blu-ray", "blu-ray" to "Blu-ray", "bdrip" to "BDRip", "brrip" to "BRRip", "web-dl" to "WEB-DL",
    "webdl" to "WEB-DL", "webrip" to "WEBRip", "web" to "WEB", "hdtv" to "HDTV", "dvdrip" to "DVDRip", "hdrip" to "HDRip", "cam" to "CAM",
)
private val SERVICES = mapOf(
    "AMZN" to "Amazon", "NF" to "Netflix", "DSNP" to "Disney+", "ATVP" to "Apple TV+", "HMAX" to "HBO Max", "MAX" to "Max",
    "HULU" to "Hulu", "PCOK" to "Peacock", "PMTP" to "Paramount+",
)

fun sourceLabel(text: String): String? {
    val source = Regex("""\b(remux|blu-?ray|bdrip|brrip|web-?dl|webrip|web|hdtv|dvdrip|hdrip|cam)\b""", RegexOption.IGNORE_CASE)
        .find(text)?.value?.lowercase()?.let { SOURCES[it] }
    val service = Regex("""\b(AMZN|NF|DSNP|ATVP|HMAX|MAX|HULU|PCOK|PMTP)\b""").find(text)?.value?.let { SERVICES[it] }
    return listOfNotNull(source, service).joinToString(" · ").ifEmpty { null }
}

val QUALITIES = listOf("4K", "1440p", "1080p", "720p", "SD", "Other")

fun quality(t: StreamTags) = when (t.height) { 2160 -> "4K"; 1440 -> "1440p"; 1080 -> "1080p"; 720 -> "720p"; 480 -> "SD"; else -> "Other" }

// Instant = known to be cached by the debrid service. Addons that never say either way (they usually list only
// what's ready) count as instant; unmarked streams from an addon that does flag uncached ones don't.
fun instantOnly(streams: List<RankedStream>): List<RankedStream> {
    val flagsUncached = streams.filter { it.tags.cached == false }.map { it.stream.addon }.toSet()
    return streams.filter { it.tags.cached == true || (it.tags.cached == null && it.stream.addon !in flagsUncached) }
}
