package com.abhilash.matinee.tv.player

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.abhilash.matinee.tv.MatineeApp
import com.abhilash.matinee.tv.data.Http
import com.abhilash.matinee.tv.data.Library
import com.abhilash.matinee.tv.data.MetaPreview
import com.abhilash.matinee.tv.data.Repo
import com.abhilash.matinee.tv.data.Settings
import com.abhilash.matinee.tv.data.SubtitleTrack
import com.abhilash.matinee.tv.ui.MatineeTheme
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.buildWithAssSupport
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class PlayRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val title: String,
    val subtitle: String? = null,
    val metaId: String? = null,
    val type: String? = null,
    val videoId: String? = null,
    val poster: String? = null,
    val resumeMs: Long = 0,
) {
    fun meta() = if (metaId != null && type != null) MetaPreview(metaId, type, title, poster, null, null, null, null, null, emptyList()) else null

    fun intent(context: Context) = Intent(context, PlayerActivity::class.java)
        .putExtra("url", url).putExtra("headers", HashMap(headers)).putExtra("title", title).putExtra("subtitle", subtitle)
        .putExtra("metaId", metaId).putExtra("type", type).putExtra("videoId", videoId).putExtra("poster", poster).putExtra("resumeMs", resumeMs)

    companion object {
        @Suppress("UNCHECKED_CAST", "DEPRECATION")
        fun from(i: Intent) = PlayRequest(
            url = i.getStringExtra("url")!!,
            headers = (i.getSerializableExtra("headers") as? HashMap<String, String>).orEmpty(),
            title = i.getStringExtra("title").orEmpty(),
            subtitle = i.getStringExtra("subtitle"),
            metaId = i.getStringExtra("metaId"),
            type = i.getStringExtra("type"),
            videoId = i.getStringExtra("videoId"),
            poster = i.getStringExtra("poster"),
            resumeMs = i.getLongExtra("resumeMs", 0),
        )
    }
}

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity(), PlayerActions {
    private lateinit var request: PlayRequest
    private lateinit var playerView: PlayerView
    private val ui = PlayerUi()
    private var player: ExoPlayer? = null
    private var selector: DefaultTrackSelector? = null
    private val handler = Handler(Looper.getMainLooper())

    private var positionMs = 0L
    private var outputKey = ""
    private var audioAutoPicked = false
    private var audioPickedByUser = false
    private var modeSwitched = false
    private var addonSubtitles: List<SubtitleTrack>? = null
    private var sideLoaded: List<SubtitleTrack> = emptyList()
    private var pendingSubtitleId: String? = null
    private var videoDecoder = ""
    private var audioDecoder = ""
    private var audioOutput = ""
    private var droppedFrames = 0
    private var dropWindow = ArrayDeque<Pair<Long, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = PlayRequest.from(intent)
        positionMs = request.resumeMs
        playerView = PlayerView(this).apply {
            useController = false
            keepScreenOn = true
            setShutterBackgroundColor(android.graphics.Color.BLACK)
            subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * Settings.subtitleScale)
        }
        setContent { MatineeTheme { PlayerScreen(playerView, ui, this, request.title, request.subtitle) } }
        getSystemService(AudioManager::class.java).registerAudioDeviceCallback(deviceCallback, handler)
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { preparePlayer() }
        handler.post(tick)
    }

    override fun onStop() {
        saveProgress()
        releasePlayer()
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    override fun onDestroy() {
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(deviceCallback)
        super.onDestroy()
    }

    private suspend fun preparePlayer() {
        if (player != null) return
        val type = request.type
        val videoId = request.videoId
        val subtitleLanguage = Settings.subtitleLanguage
        // With a subtitle language set, addon subtitles in it are added up front (waiting at most 1.5 s for them)
        if (subtitleLanguage != null && type != null && videoId != null && addonSubtitles == null) {
            addonSubtitles = withTimeoutOrNull(1_500) { Repo.subtitles(type, videoId) }
            sideLoaded = addonSubtitles.orEmpty().filter { sameLanguage(it.lang, subtitleLanguage) }.take(3)
        }
        if (isFinishing || player != null) return
        val p = buildPlayer()
        player = p
        playerView.player = p
        p.addListener(listener)
        p.addAnalyticsListener(analytics)
        p.setVideoFrameMetadataListener(frameRateProbe)
        p.setMediaItem(mediaItem(), positionMs)
        p.prepare()
        p.playWhenReady = true
        ui.error = null
    }

    private fun buildPlayer(): ExoPlayer {
        outputKey = Capabilities.outputKey(this)
        val forcePcm = Settings.forcePcm(outputKey)
        val audio = Capabilities.audio(this)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink {
                val builder = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                if (forcePcm) builder.setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                return builder.build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON).setEnableDecoderFallback(true)

        val trackSelector = DefaultTrackSelector(this).also { selector = it }
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setTunnelingEnabled(Settings.tunneling)
            .setPreferredAudioLanguage(Settings.audioLanguage)
            .setPreferredAudioMimeTypes(*audioMimePreference(audio.passthrough, audio.maxPcmChannels).toTypedArray())
            .setPreferredTextLanguage(Settings.subtitleLanguage)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, Settings.subtitleLanguage == null)
            .build()

        // Buffers live on the Java heap: sized from what this device allows (largeHeap), so a 1 GB stick is safe
        val heapMb = getSystemService(ActivityManager::class.java).largeMemoryClass
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_000, 4_000)
            .setTargetBufferBytes((heapMb / 3).coerceIn(48, 256) * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        val http = OkHttpDataSource.Factory(Http.client).setDefaultRequestProperties(request.headers)
        return ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            // Matinee switches the display mode itself (FrameRate.kt), which also works before Android 11
            .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            .buildWithAssSupport(
                this, AssRenderType.OVERLAY_CANVAS, AssHandlerConfig(), playerView.subtitleView,
                DefaultDataSource.Factory(this, http), DefaultExtractorsFactory(), renderers,
            )
    }

    private fun mediaItem() = MediaItem.Builder()
        .setUri(request.url)
        .setSubtitleConfigurations(sideLoaded.map { s ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
                .setId("addon:${s.id}")
                .setMimeType(subtitleMime(s.url))
                .setLanguage(s.lang)
                .setLabel("${languageName(s.lang)} · addon")
                .build()
        })
        .build()

    private fun releasePlayer() {
        val p = player ?: return
        positionMs = p.currentPosition
        p.removeListener(listener)
        p.release()
        player = null
        playerView.player = null
    }

    private fun restart(message: String? = null) {
        player?.let { positionMs = it.currentPosition }
        releasePlayer()
        audioAutoPicked = false
        message?.let { ui.toast = it }
        lifecycleScope.launch { preparePlayer() }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ui.playing = isPlaying
            if (!isPlaying) saveProgress()
        }

        override fun onPlaybackStateChanged(state: Int) {
            ui.buffering = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
            if (state == Player.STATE_ENDED) {
                saveProgress()
                finish()
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            ui.tracks = tracks
            autoPickAudio(tracks)
            selectPendingSubtitle(tracks)
            selectedFormat(tracks, C.TRACK_TYPE_VIDEO)?.frameRate?.takeIf { it > 0 }?.let { matchFrameRate(it) }
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private val analytics = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            videoDecoder = decoderName
        }

        override fun onAudioDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            audioDecoder = decoderName
        }

        override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, config: AudioSink.AudioTrackConfig) {
            val channels = Integer.bitCount(config.channelConfig)
            audioOutput = if (isPcm(config.encoding)) "Decoded on this device to $channels-channel PCM"
            else "Passthrough (${encodingName(config.encoding)}) to your TV/receiver"
        }

        // A stream that can't keep up is a failure, not "high quality": say so once, so another version can be picked
        override fun onDroppedVideoFrames(eventTime: AnalyticsListener.EventTime, count: Int, elapsedMs: Long) {
            droppedFrames += count
            val now = System.currentTimeMillis()
            dropWindow.addLast(now to count)
            while (dropWindow.isNotEmpty() && now - dropWindow.first().first > 10_000) dropWindow.removeFirst()
            val position = player?.currentPosition ?: 0
            if (!ui.dropWarning && position - positionMs > 10_000 && dropWindow.sumOf { it.second } >= 30) ui.dropWarning = true
        }
    }

    // Containers like MKV often don't state a frame rate: measure it from the first frames' timestamps instead
    private val frameRateProbe = object : VideoFrameMetadataListener {
        private val stamps = ArrayList<Long>()

        override fun onVideoFrameAboutToBeRendered(presentationTimeUs: Long, releaseTimeNs: Long, format: Format, mediaFormat: android.media.MediaFormat?) {
            if (modeSwitched || stamps.size > 48) return
            if (stamps.isNotEmpty() && presentationTimeUs <= stamps.last()) stamps.clear()
            stamps += presentationTimeUs
            if (stamps.size == 48) {
                val fps = 47 * 1_000_000f / (stamps.last() - stamps.first())
                handler.post { matchFrameRate(fps) }
            }
        }
    }

    private fun matchFrameRate(fps: Float) {
        if (!Settings.matchFrameRate || modeSwitched) return
        val display = Capabilities.display(this)
        val current = display.mode
        val modes = display.supportedModes.map { ModeInfo(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate) }
        val target = modeForFrameRate(fps, ModeInfo(current.modeId, current.physicalWidth, current.physicalHeight, current.refreshRate), modes)
        modeSwitched = true
        if (target == null) return
        val p = player ?: return
        val resume = p.playWhenReady
        p.playWhenReady = false
        ui.toast = "Matching the TV to %.3f fps".format(fps)
        val displays = getSystemService(DisplayManager::class.java)
        val resumeAfterSwitch = Runnable { player?.playWhenReady = resume }
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                displays.unregisterDisplayListener(this)
                handler.removeCallbacks(resumeAfterSwitch)
                handler.postDelayed(resumeAfterSwitch, 800) // let the TV lock onto the new signal
            }
        }
        displays.registerDisplayListener(displayListener, handler)
        handler.postDelayed({ displays.unregisterDisplayListener(displayListener); resumeAfterSwitch.run() }, 4_000)
        window.attributes = window.attributes.apply { preferredDisplayModeId = target.id }
    }

    private fun autoPickAudio(tracks: Tracks) {
        if (audioAutoPicked || audioPickedByUser) return
        val options = audioTracks(tracks)
        if (options.isEmpty()) return
        audioAutoPicked = true
        val audio = Capabilities.audio(this)
        val picked = pickAudio(
            options.map { (g, i) ->
                val f = g.getTrackFormat(i)
                AudioOption(f.language, f.sampleMimeType, f.channelCount, (f.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0)
            },
            Settings.audioLanguage,
            audioMimePreference(audio.passthrough, audio.maxPcmChannels),
        ) ?: return
        val (group, index) = options[picked]
        if (!group.isTrackSelected(index)) applyOverride(group, index)
    }

    private fun audioTracks(tracks: Tracks) =
        tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { g to it } }

    private fun applyOverride(group: Tracks.Group, index: Int) {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(group.type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
            .build()
    }

    private fun selectedFormat(tracks: Tracks, type: Int): Format? {
        val group = tracks.groups.firstOrNull { it.type == type && it.isSelected } ?: return null
        return (0 until group.length).firstOrNull { group.isTrackSelected(it) }?.let { group.getTrackFormat(it) }
    }

    private fun handleError(error: PlaybackException) {
        val audioFailure = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        if (audioFailure && !Settings.forcePcm(outputKey)) {
            Settings.setForcePcm(outputKey, true)
            restart("Your audio device didn't take this format, so Matinee decodes it instead")
            return
        }
        ui.error = when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED, PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                "This device can't decode this stream's video. Go back and pick another version."
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Couldn't load the stream (${error.errorCodeName}). The link may have expired: try again or pick another one."
            else -> "Playback failed: ${error.errorCodeName}"
        }
    }

    private val tick = object : Runnable {
        private var sinceSave = 0
        override fun run() {
            player?.let {
                ui.position = it.currentPosition
                ui.duration = it.duration.coerceAtLeast(0)
                ui.buffered = it.bufferedPosition
                if (it.isPlaying && ++sinceSave >= 60) { sinceSave = 0; saveProgress() }
            }
            handler.postDelayed(this, 500)
        }
    }

    private fun saveProgress() {
        val p = player ?: return
        val meta = request.meta() ?: return
        val videoId = request.videoId ?: return
        val duration = p.duration.takeIf { it > 0 } ?: return
        val position = p.currentPosition
        MatineeApp.scope.launch { runCatching { Library.saveProgress(meta, videoId, position, duration) } }
    }

    // A soundbar switched on, Bluetooth headphones connected…: re-decide the audio for the new output
    private val deviceCallback = object : AudioDeviceCallback() {
        private val recheck = Runnable {
            val key = Capabilities.outputKey(this@PlayerActivity)
            if (key == outputKey || player == null) return@Runnable
            val pcmChanged = Settings.forcePcm(key) != Settings.forcePcm(outputKey)
            outputKey = key
            if (pcmChanged) return@Runnable restart()
            val audio = Capabilities.audio(this@PlayerActivity)
            selector?.let { s -> s.parameters = s.buildUponParameters().setPreferredAudioMimeTypes(*audioMimePreference(audio.passthrough, audio.maxPcmChannels).toTypedArray()).build() }
            audioAutoPicked = false
            player?.let { autoPickAudio(it.currentTracks) }
        }

        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = schedule()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = schedule()
        private fun schedule() {
            handler.removeCallbacks(recheck)
            handler.postDelayed(recheck, 700)
        }
    }

    // PlayerActions, called from the overlay

    override fun togglePlay() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    override fun seekBy(ms: Long) {
        player?.let { it.seekTo((it.currentPosition + ms).coerceIn(0, it.duration.coerceAtLeast(0))) }
    }

    override fun selectTrack(group: Tracks.Group, index: Int) {
        if (group.type == C.TRACK_TYPE_AUDIO) audioPickedByUser = true
        applyOverride(group, index)
    }

    override fun subtitlesOff() {
        val p = player ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
    }

    override suspend fun addonSubtitles(): List<SubtitleTrack> {
        val type = request.type ?: return emptyList()
        val videoId = request.videoId ?: return emptyList()
        return addonSubtitles ?: Repo.subtitles(type, videoId).also { addonSubtitles = it }
    }

    // Adding a subtitle file means re-preparing at the same spot; it's selected once its track appears
    override fun useAddonSubtitle(track: SubtitleTrack) {
        if (sideLoaded.none { it.url == track.url }) sideLoaded = sideLoaded + track
        pendingSubtitleId = "addon:${track.id}"
        val p = player ?: return
        p.setMediaItem(mediaItem(), p.currentPosition)
        p.prepare()
    }

    private fun selectPendingSubtitle(tracks: Tracks) {
        val id = pendingSubtitleId ?: return
        for (g in tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }) {
            for (i in 0 until g.length) {
                if (g.getTrackFormat(i).id?.contains(id) == true) {
                    pendingSubtitleId = null
                    applyOverride(g, i)
                    return
                }
            }
        }
    }

    override fun decodingForced() = Settings.forcePcm(outputKey)

    override fun toggleDecoding() {
        val force = !Settings.forcePcm(outputKey)
        Settings.setForcePcm(outputKey, force)
        restart(if (force) "Decoding audio on this device" else "Sending audio untouched to your TV/receiver again")
    }

    override fun retry() = restart()

    override fun exit() = finish()

    override fun infoLines(): List<String> {
        val p = player ?: return emptyList()
        val video = p.videoFormat
        val audio = p.audioFormat
        val mode = Capabilities.display(this).mode
        return listOfNotNull(
            video?.let { "Video: ${videoLabel(it)}" },
            videoDecoder.takeIf { it.isNotEmpty() }?.let { "Video decoder: $it" },
            "Display: ${mode.physicalWidth}x${mode.physicalHeight} @ ${"%.3f".format(mode.refreshRate)} Hz",
            audio?.let { "Audio: ${audioLabel(it)}" },
            audioOutput.takeIf { it.isNotEmpty() },
            audioDecoder.takeIf { it.isNotEmpty() }?.let { "Audio decoder: $it" },
            ffmpegLine(),
            "Dropped frames: $droppedFrames",
            "Buffered: ${((ui.buffered - ui.position) / 1000).coerceAtLeast(0)} s",
            p.videoDecoderCounters?.let { c: DecoderCounters -> "Frames rendered: ${c.renderedOutputBufferCount}" },
        )
    }
}

// Matinee's own decoders, used when the device can neither decode nor pass a format through
fun ffmpegLine(): String {
    if (!FfmpegLibrary.isAvailable()) return "FFmpeg audio decoders: not loaded"
    val formats = listOf("TrueHD" to MIME_TRUEHD, "DTS-HD" to MIME_DTS_HD, "DTS" to MIME_DTS, "E-AC-3" to MIME_EAC3, "AC-3" to MIME_AC3)
        .filter { FfmpegLibrary.supportsFormat(it.second) }.joinToString { it.first }
    return "FFmpeg ${FfmpegLibrary.getVersion()} audio decoders: $formats"
}

private fun isPcm(encoding: Int) = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT ||
    encoding == C.ENCODING_PCM_24BIT || encoding == C.ENCODING_PCM_32BIT || encoding == C.ENCODING_PCM_8BIT

private fun encodingName(encoding: Int) = when (encoding) {
    C.ENCODING_AC3 -> "Dolby Digital"
    C.ENCODING_E_AC3 -> "Dolby Digital Plus"
    C.ENCODING_E_AC3_JOC -> "Dolby Digital Plus with Atmos"
    C.ENCODING_DOLBY_TRUEHD -> "Dolby TrueHD"
    C.ENCODING_DTS -> "DTS"
    C.ENCODING_DTS_HD -> "DTS-HD"
    C.ENCODING_DTS_UHD_P2 -> "DTS:X"
    C.ENCODING_AC4 -> "AC-4"
    else -> "encoding $encoding"
}

fun subtitleMime(url: String): String {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        path.endsWith(".ttml") || path.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

// Addons use 3-letter codes (eng), settings and containers 2-letter ones (en)
fun sameLanguage(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    fun iso3(s: String) = runCatching { Locale(s.substringBefore('-')).isO3Language }.getOrDefault(s).lowercase()
    return iso3(a) == iso3(b)
}

fun languageName(code: String?): String {
    if (code.isNullOrEmpty() || code == "und") return "Unknown"
    return Locale(code.substringBefore('-')).displayLanguage.ifEmpty { code }
}

fun videoLabel(f: Format): String {
    val codec = when {
        f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision" + (f.codecs?.let { " ($it)" } ?: "")
        f.sampleMimeType == MimeTypes.VIDEO_H265 -> "HEVC"
        f.sampleMimeType == MimeTypes.VIDEO_H264 -> "H.264"
        f.sampleMimeType == MimeTypes.VIDEO_AV1 -> "AV1"
        f.sampleMimeType == MimeTypes.VIDEO_VP9 -> "VP9"
        else -> f.sampleMimeType ?: "?"
    }
    val hdr = when (f.colorInfo?.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> " · HDR (PQ)"
        C.COLOR_TRANSFER_HLG -> " · HLG"
        else -> ""
    }
    val fps = if (f.frameRate > 0) " · %.3f fps".format(f.frameRate) else ""
    return "$codec ${f.width}x${f.height}$hdr$fps"
}

fun audioLabel(f: Format): String {
    val codec = when (f.sampleMimeType) {
        MIME_TRUEHD -> "Dolby TrueHD"
        MIME_EAC3_JOC -> "Dolby Digital Plus · Atmos"
        MIME_EAC3 -> "Dolby Digital Plus"
        MIME_AC3 -> "Dolby Digital"
        MIME_DTS_HD -> "DTS-HD"
        MIME_DTS_UHD -> "DTS:X"
        MIME_DTS -> "DTS"
        MIME_AAC -> "AAC"
        MIME_OPUS -> "Opus"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        else -> f.sampleMimeType ?: "?"
    }
    val channels = when (f.channelCount) { 1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; Format.NO_VALUE -> ""; else -> "${f.channelCount} ch" }
    val atmos = if (f.label?.contains("atmos", true) == true && "Atmos" !in codec) " · Atmos" else ""
    return listOf(languageName(f.language), "$codec$atmos", channels).filter { it.isNotEmpty() }.joinToString(" · ")
}
