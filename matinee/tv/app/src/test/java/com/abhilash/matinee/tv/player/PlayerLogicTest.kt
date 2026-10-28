package com.abhilash.matinee.tv.player

import com.abhilash.matinee.tv.data.StreamInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLogicTest {
    private fun stream(name: String, description: String = "") = StreamInfo("addon", "https://x/$name", name, description, emptyMap(), null, null)

    private val dvTv = RankCaps(2160, dolbyVision = true, hdr10Plus = false, hdr10 = true, hlg = true, hevc4k = true, av1MaxHeight = 2160,
        passthrough = setOf(MIME_AC3, MIME_EAC3, MIME_EAC3_JOC), maxPcmChannels = 8)
    private val hd10Stick = RankCaps(1080, dolbyVision = false, hdr10Plus = true, hdr10 = true, hlg = true, hevc4k = false, av1MaxHeight = 0,
        passthrough = setOf(MIME_AC3, MIME_EAC3), maxPcmChannels = 2)

    @Test fun tagsFromTorrentioStyleNames() {
        val t = streamTags("[RD+] Torrentio\n4k DV | HDR10\nMovie.2160p.BluRay.REMUX.HEVC.TrueHD.Atmos.7.1\n💾 58.3 GB")
        assertEquals(2160, t.height)
        assertTrue(t.dolbyVision && t.hdr10 && !t.dolbyVisionOnly)
        assertTrue(t.hevc && t.trueHd && t.atmos)
        assertEquals(58_300_000_000, t.sizeBytes)
        assertEquals(true, t.cached)
        assertTrue(streamTags("Torrentio\n4k DV\nx.2160p.WEB-DL.DDP5.1.Atmos.DV.H.265").dolbyVisionOnly)
        assertEquals(false, streamTags("[RD download] Torrentio\n1080p").cached)
        assertFalse(streamTags("1080p HDTV").hdr10)
    }

    @Test fun dolbyVisionProfile5SinksOnTvsWithoutDolbyVision() {
        val p5 = stream("4k DV", "x.2160p.DV.HEVC.DDP5.1")
        val hdr = stream("1080p HDR10", "x.1080p.HDR.HEVC.DDP5.1")
        assertEquals(listOf(hdr, p5), rankStreams(listOf(p5, hdr), hd10Stick.copy(hevc4k = true, maxHeight = 2160)).map { it.stream })
        assertNull(rankStreams(listOf(p5), dvTv).single().warning)
    }

    @Test fun atmosThatReachesTheSoundbarWinsTies() {
        val trueHd = stream("[RD+] 4k HDR", "x.2160p.HDR.HEVC.TrueHD.Atmos")
        val ddp = stream("[RD+] 4k HDR", "x.2160p.HDR.HEVC.DDP5.1.Atmos")
        // This TV passes DD+ Atmos but not TrueHD, so only the DD+ one is heard as Atmos
        assertEquals(ddp, rankStreams(listOf(trueHd, ddp), dvTv).first().stream)
    }

    @Test fun instantStreamsFirstAndResolutionCappedAtTheScreen() {
        val big = stream("[RD download] 4k", "x.2160p")
        val ready = stream("[RD+] 1080p", "x.1080p")
        assertEquals(ready, rankStreams(listOf(big, ready), dvTv).first().stream)
        val uhd = stream("[RD+] 4k", "x.2160p.x264")
        val fhd = stream("[RD+] 1080p", "x.1080p.x264.DDP5.1")
        // A 1080p screen can't show 4K, so the better audio decides
        assertEquals(fhd, rankStreams(listOf(uhd, fhd), hd10Stick).first().stream)
    }

    @Test fun audioPreferenceFollowsTheOutput() {
        assertEquals(MIME_EAC3_JOC, audioMimePreference(dvTv.passthrough, 8).first())
        assertEquals(MIME_TRUEHD, audioMimePreference(emptySet(), 8).first())
        assertEquals(MIME_EAC3_JOC, audioMimePreference(emptySet(), 2).first())
        assertEquals(MIME_TRUEHD, audioMimePreference(setOf(MIME_TRUEHD, MIME_EAC3_JOC), 8).first())
    }

    @Test fun pickAudioKeepsLanguageThenPicksFormat() {
        val tracks = listOf(
            AudioOption("en", MIME_TRUEHD, 8, isDefault = true),
            AudioOption("en", MIME_EAC3_JOC, 6, isDefault = false),
            AudioOption("hi", MIME_AC3, 6, isDefault = false),
        )
        assertEquals(1, pickAudio(tracks, null, audioMimePreference(dvTv.passthrough, 8)))
        assertEquals(0, pickAudio(tracks, null, audioMimePreference(setOf(MIME_TRUEHD), 8)))
        assertEquals(2, pickAudio(tracks, "hi", audioMimePreference(emptySet(), 2)))
        assertEquals(1, pickAudio(tracks, "fr", audioMimePreference(emptySet(), 2)))
    }

    @Test fun frameRateMatching() {
        val modes = listOf(ModeInfo(1, 3840, 2160, 60f), ModeInfo(2, 3840, 2160, 23.976f), ModeInfo(3, 3840, 2160, 24f),
            ModeInfo(4, 3840, 2160, 50f), ModeInfo(5, 1920, 1080, 23.976f))
        val at60 = modes[0]
        assertEquals(2, modeForFrameRate(23.976f, at60, modes)?.id)
        assertEquals(3, modeForFrameRate(24f, at60, modes)?.id)
        assertEquals(4, modeForFrameRate(25f, at60, modes)?.id)
        assertNull(modeForFrameRate(30f, at60, modes))
        assertNull(modeForFrameRate(23.976f, modes[1], modes))
        // Only 24 Hz available: still far better than 60 Hz for 23.976 fps
        assertEquals(3, modeForFrameRate(23.976f, at60, modes.filter { it.id != 2 })?.id)
    }
}
