package io.github.yisus.avenor.desktop

import io.github.yisus.avenor.shared.playback.AvenorPlaybackController
import io.github.yisus.avenor.shared.playback.CurrentMediaItem
import io.github.yisus.avenor.shared.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.media.Meta
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent

typealias AudioMediaPlayerComponent = AudioPlayerComponent

class DesktopPlaybackController : AvenorPlaybackController {

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<CurrentMediaItem?>(null)
    override val currentMediaItem: StateFlow<CurrentMediaItem?> = _currentMediaItem.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionFlow: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationFlow: StateFlow<Long> = _durationMs.asStateFlow()

    override val currentPositionMs: Long
        get() = _currentPositionMs.value

    private val mediaPlayerFactory: MediaPlayerFactory?
    private val audioMediaPlayerComponent: AudioMediaPlayerComponent?

    private val eventListener = object : MediaPlayerEventAdapter() {
        override fun opening(mediaPlayer: MediaPlayer) {
            _playbackState.value = PlaybackState.Buffering
        }

        override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
            if (newCache < 100f && _playbackState.value != PlaybackState.Playing) {
                _playbackState.value = PlaybackState.Buffering
            }
        }

        override fun playing(mediaPlayer: MediaPlayer) {
            _playbackState.value = PlaybackState.Playing
        }

        override fun paused(mediaPlayer: MediaPlayer) {
            _playbackState.value = PlaybackState.Paused
        }

        override fun stopped(mediaPlayer: MediaPlayer) {
            _playbackState.value = PlaybackState.Idle
            _currentPositionMs.value = 0L
        }

        override fun finished(mediaPlayer: MediaPlayer) {
            _playbackState.value = PlaybackState.Paused
            _currentPositionMs.value = 0L
        }

        override fun error(mediaPlayer: MediaPlayer) {
            _playbackState.value = PlaybackState.Error(
                message = "VLCJ native playback error occurred"
            )
        }

        override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
            _currentPositionMs.value = newTime.coerceAtLeast(0L)
        }

        override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
            _durationMs.value = newLength.coerceAtLeast(0L)
        }

        override fun mediaChanged(mediaPlayer: MediaPlayer, media: MediaRef) {
            val mrl = mediaPlayer.media().info()?.mrl().orEmpty()
            val meta = mediaPlayer.media().meta()
            val title = meta?.get(Meta.TITLE).orEmpty()
            val artist = meta?.get(Meta.ARTIST).orEmpty()

            val existing = _currentMediaItem.value
            if (existing == null || (mrl.isNotEmpty() && existing.uri != mrl)) {
                _currentMediaItem.value = CurrentMediaItem(
                    id = mrl.ifEmpty { System.currentTimeMillis().toString() },
                    title = title.ifEmpty { mrl.substringAfterLast('/').substringAfterLast('\\') },
                    artist = artist.ifEmpty { "Unknown Artist" },
                    uri = mrl
                )
            }
        }
    }

    init {
        var factory: MediaPlayerFactory? = null
        var component: AudioMediaPlayerComponent? = null
        try {
            factory = MediaPlayerFactory("--no-video")
            component = AudioMediaPlayerComponent(factory)
            component.mediaPlayer().events().addMediaPlayerEventListener(eventListener)
        } catch (t: Throwable) {
            _playbackState.value = PlaybackState.Error(
                message = "Failed to initialize libVLC / VLCJ: ${t.message}",
                cause = t
            )
            component?.release()
            factory?.release()
            factory = null
            component = null
        }
        this.mediaPlayerFactory = factory
        this.audioMediaPlayerComponent = component
    }

    fun loadAndPlay(mediaItem: CurrentMediaItem) {
        _currentMediaItem.value = mediaItem
        _currentPositionMs.value = 0L
        val player = audioMediaPlayerComponent?.mediaPlayer()
        if (player == null) {
            _playbackState.value = PlaybackState.Error("VLCJ MediaPlayer is not initialized")
            return
        }
        val started = player.media().play(mediaItem.uri)
        if (!started) {
            _playbackState.value = PlaybackState.Error("Unable to start playback for URI: ${mediaItem.uri}")
        }
    }

    fun playTestFile(filePath: String) {
        val fileName = filePath.substringAfterLast('/').substringAfterLast('\\').ifEmpty { "Test Audio" }
        val testItem = CurrentMediaItem(
            id = filePath,
            title = fileName,
            artist = "Local Test Track",
            uri = filePath
        )
        loadAndPlay(testItem)
    }

    fun setVolume(volumePercent: Float) {
        val vlcVolume = (volumePercent.coerceIn(0f, 1f) * 100).toInt()
        audioMediaPlayerComponent?.mediaPlayer()?.audio()?.setVolume(vlcVolume)
    }

    override fun play() {
        val player = audioMediaPlayerComponent?.mediaPlayer() ?: return
        val currentUri = _currentMediaItem.value?.uri
        if (!player.status().isPlayable && !currentUri.isNullOrBlank()) {
            player.media().play(currentUri)
        } else {
            player.controls().play()
        }
    }

    override fun pause() {
        audioMediaPlayerComponent?.mediaPlayer()?.controls()?.setPause(true)
    }

    override fun skipToNext() {
        println("DesktopPlaybackController: skipToNext() invoked - queue managed by UI layer")
    }

    override fun skipToPrevious() {
        println("DesktopPlaybackController: skipToPrevious() invoked - queue managed by UI layer")
    }

    override fun seekTo(positionMs: Long) {
        val targetMs = positionMs.coerceAtLeast(0L)
        audioMediaPlayerComponent?.mediaPlayer()?.controls()?.setTime(targetMs)
        _currentPositionMs.value = targetMs
    }

    fun release() {
        try {
            audioMediaPlayerComponent?.mediaPlayer()?.events()?.removeMediaPlayerEventListener(eventListener)
            audioMediaPlayerComponent?.mediaPlayer()?.controls()?.stop()
        } catch (_: Throwable) {
        }
        try {
            audioMediaPlayerComponent?.release()
        } catch (_: Throwable) {
        }
        try {
            mediaPlayerFactory?.release()
        } catch (_: Throwable) {
        }
        _playbackState.value = PlaybackState.Idle
    }
}
