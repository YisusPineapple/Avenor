
package io.github.yisus.avenor
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yisus.avenor.dsp.EqBandSettings
import io.github.yisus.avenor.dsp.EqPreferences
import io.github.yisus.avenor.dsp.EqualizerAudioProcessor
import io.github.yisus.avenor.playback.PlaybackCoordinator

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    internal var mediaSession: MediaSession? = null
    internal lateinit var errorRecoveryManager: ErrorRecoveryManager
    internal lateinit var crossfadeManager: CrossfadeManager
    internal lateinit var playbackCoordinator: PlaybackCoordinator
    internal lateinit var eqPreferences: EqPreferences
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    
    val universalDownmixAudioProcessor = io.github.yisus.avenor.dsp.UniversalDownmixAudioProcessor()
    val replayGainAudioProcessor = io.github.yisus.avenor.replaygain.ReplayGainAudioProcessor()
    val equalizerAudioProcessor = EqualizerAudioProcessor()
    val safeLimiterAudioProcessor = io.github.yisus.avenor.replaygain.SafeLimiterAudioProcessor()
    var replayGainMode: io.github.yisus.avenor.replaygain.ReplayGainMode = io.github.yisus.avenor.replaygain.ReplayGainMode.TRACK
    var replayGainPreampDb: Float = 0.0f

    private var showLikeButton = true
    private var showShuffleButton = true
    private var showRepeatButton = true

    override fun onCreate() {
        super.onCreate()
        playbackCoordinator = PlaybackCoordinator.getInstance(this)
        eqPreferences = EqPreferences(this)

        // Restore persisted EQ configuration outside hot path
        val persistedEnabled = eqPreferences.isEnabled()
        val persistedBands = eqPreferences.getBands()
        equalizerAudioProcessor.setSettings(EqBandSettings.of(persistedEnabled, persistedBands))
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val renderersFactory = io.github.yisus.avenor.audio.AvenorRenderersFactory(
            context = this,
            universalDownmixAudioProcessor = universalDownmixAudioProcessor,
            replayGainAudioProcessor = replayGainAudioProcessor,
            equalizerAudioProcessor = equalizerAudioProcessor,
            safeLimiterAudioProcessor = safeLimiterAudioProcessor
        )
        val extractorsFactory = renderersFactory.buildExtractorsFactory()
        renderersFactory.setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            
        // Configure LoadControl:
        // - Buffer durations define the forward buffering policy for the active MediaItem.
        // - Sequential preparation of consecutive tracks depends on ExoPlayer's internal MediaPeriodQueue
        //   processing the loaded playlist (setMediaItems). Avenor does NOT implement a custom PreloadManager.
        // - setBackBuffer(30000, true) retains up to 30s of ALREADY PLAYED audio (past/historical buffer)
        //   to enable fast backward seeking without re-requesting disk I/O. It is NOT forward preloading of Track B.
        val defaultBufferMs = DeviceProfileManager.getOptimalBufferMs("VIVID")
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                defaultBufferMs,
                defaultBufferMs,
                androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setBackBuffer(30000, true)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractorsFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .build()
            
        errorRecoveryManager = ErrorRecoveryManager(player)
        crossfadeManager = CrossfadeManager(player, this)
            
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                // Platform Equalizer removed in favor of software EqualizerAudioProcessor in DefaultAudioSink
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val currentPosition = player.currentPosition
                serviceScope.launch {
                    playbackCoordinator.updatePlaybackParams(isPlaying = isPlaying)
                    if (!isPlaying) {
                        playbackCoordinator.updatePosition(currentPosition, force = true)
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val isPlaying = player.isPlaying
                val currentPosition = player.currentPosition
                serviceScope.launch {
                    playbackCoordinator.updatePlaybackParams(isPlaying = isPlaying)
                    if (!playWhenReady) {
                        playbackCoordinator.updatePosition(currentPosition, force = true)
                    }
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                val isPlaying = player.isPlaying
                val currentPosition = player.currentPosition
                serviceScope.launch {
                    playbackCoordinator.updatePlaybackParams(isPlaying = isPlaying)
                    if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                        playbackCoordinator.updatePosition(currentPosition, force = true)
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlaybackService", "ExoPlayer error occurred: ${error.errorCodeName}", error)
                val currentPosition = player.currentPosition
                serviceScope.launch {
                    playbackCoordinator.updatePlaybackParams(isPlaying = false)
                    playbackCoordinator.updatePosition(currentPosition, force = true)
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                val itemIndex = player.currentMediaItemIndex
                serviceScope.launch {
                    playbackCoordinator.updateTrackTransition(mediaId, itemIndex)
                    applyReplayGainForMediaItem(mediaItem)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    serviceScope.launch {
                        playbackCoordinator.updatePosition(newPosition.positionMs, force = true)
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                serviceScope.launch {
                    playbackCoordinator.updatePlaybackParams(shuffleMode = shuffleModeEnabled)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                serviceScope.launch {
                    playbackCoordinator.updatePlaybackParams(repeatMode = repeatMode)
                }
            }
        })

        // Register AudioDeviceCallback for deterministic audio routing & disconnection tracking
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && audioManager != null) {
            audioDeviceCallback = object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    android.util.Log.d("PlaybackService", "Audio device connected: ${addedDevices?.size ?: 0} devices")
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                    android.util.Log.d("PlaybackService", "Audio device disconnected: ${removedDevices?.size ?: 0} devices")
                    val hasOutputDisconnection = removedDevices?.any { device ->
                        device.isSink && (
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            device.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            device.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET ||
                            device.type == android.media.AudioDeviceInfo.TYPE_USB_DEVICE
                        )
                    } == true

                    if (hasOutputDisconnection && player.isPlaying) {
                        android.util.Log.i("PlaybackService", "Audio output device disconnected during playback, pausing.")
                        player.pause()
                    }
                }
            }
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        }

        // Asynchronously load settings to configure notification actions and layout without blocking Main
        serviceScope.launch {
            try {
                val appSettings = AppDatabase.getDatabase(this@PlaybackService).musicDao().getSettings().firstOrNull()
                if (appSettings != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        showLikeButton = appSettings.showLike
                        showShuffleButton = appSettings.showShuffle
                        showRepeatButton = appSettings.showRepeat
                        mediaSession?.setCustomLayout(buildCustomLayout())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Error loading settings asynchronously in onCreate", e)
            }
        }

        // Restore persisted queue and playback state from Room
        serviceScope.launch {
            val restored = playbackCoordinator.restorePersistedState()
            if (restored.queue.isNotEmpty()) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (player.mediaItemCount == 0) {
                        val mediaItems = restored.queue.map { song ->
                            androidx.media3.common.MediaItem.Builder()
                                .setMediaId(song.id.toString())
                                .setUri(song.uri)
                                .setMediaMetadata(
                                    androidx.media3.common.MediaMetadata.Builder()
                                        .setTitle(song.title)
                                        .setArtist(song.artist)
                                        .setAlbumTitle(song.album)
                                        .build()
                                )
                                .build()
                        }
                        player.setMediaItems(mediaItems, restored.currentIndex.coerceAtLeast(0), restored.currentPositionMs)
                        player.shuffleModeEnabled = restored.shuffleMode
                        player.repeatMode = restored.repeatMode
                        player.prepare()
                        applyReplayGainForMediaItem(player.currentMediaItem)
                    }
                }
            }
        }

        // Periodic playback position updater (persists every 5s during active playback)
        serviceScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(5000L)
                val isPlaying = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { player.isPlaying }
                if (isPlaying) {
                    val pos = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { player.currentPosition }
                    playbackCoordinator.updatePosition(pos)
                }
            }
        }
            
        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand("ACTION_LIKE", Bundle.EMPTY))
                    .add(SessionCommand("SET_EQ_BAND", Bundle.EMPTY))
                    .add(SessionCommand("SET_NOTIFICATION_PREFS", Bundle.EMPTY))
                    .add(SessionCommand("SET_REPLAY_GAIN_CONFIG", Bundle.EMPTY))
                    .add(SessionCommand("SET_CROSSFADE_CONFIG", Bundle.EMPTY))
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setAvailablePlayerCommands(MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS)
                    .setCustomLayout(buildCustomLayout())
                    .build()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                return Futures.immediateFuture(handleCustomCommand(customCommand.customAction, args, session, controller))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setId("AvenorMediaSession_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}")
            .setCallback(sessionCallback)
            .build()
            
        // Explicitly setup default provider for stability in low ram
        val provider = object : DefaultMediaNotificationProvider(this) {
            override fun getMediaButtons(
                session: MediaSession,
                playerCommands: Player.Commands,
                customLayout: ImmutableList<CommandButton>,
                showPauseButton: Boolean
            ): ImmutableList<CommandButton> {
                // Synchronize state: dynamically read player states and modify icons to prevent 'phantom' states
                val syncedLayout = mutableListOf<CommandButton>()
                for (button in customLayout) {
                    if (button.playerCommand == Player.COMMAND_SET_SHUFFLE_MODE) {
                        val isShuffle = session.player.shuffleModeEnabled
                        syncedLayout.add(
                            CommandButton.Builder()
                                .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                                .setIconResId(if (isShuffle) androidx.media3.ui.R.drawable.exo_icon_shuffle_on else androidx.media3.ui.R.drawable.exo_icon_shuffle_off)
                                .setDisplayName("Shuffle")
                                .build()
                        )
                    } else if (button.playerCommand == Player.COMMAND_SET_REPEAT_MODE) {
                        val repeatMode = session.player.repeatMode
                        val iconRes = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> androidx.media3.ui.R.drawable.exo_icon_repeat_one
                            Player.REPEAT_MODE_ALL -> androidx.media3.ui.R.drawable.exo_icon_repeat_all
                            else -> androidx.media3.ui.R.drawable.exo_icon_repeat_off
                        }
                        syncedLayout.add(
                            CommandButton.Builder()
                                .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
                                .setIconResId(iconRes)
                                .setDisplayName("Repeat")
                                .build()
                        )
                    } else {
                        syncedLayout.add(button)
                    }
                }
                return super.getMediaButtons(session, playerCommands, ImmutableList.copyOf(syncedLayout), showPauseButton)
            }
        }
        provider.setSmallIcon(R.mipmap.ic_launcher)
        setMediaNotificationProvider(provider)
    }
    
    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        val layout = mutableListOf<CommandButton>()
        if (showLikeButton) {
            layout.add(
                CommandButton.Builder()
                    .setDisplayName("Like")
                    .setIconResId(R.drawable.ic_favorite_notification)
                    .setSessionCommand(SessionCommand("ACTION_LIKE", Bundle.EMPTY))
                    .build()
            )
        }
        if (showShuffleButton) {
            layout.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                    .setIconResId(androidx.media3.ui.R.drawable.exo_icon_shuffle_off)
                    .setDisplayName("Shuffle")
                    .build()
            )
        }
        if (showRepeatButton) {
            layout.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
                    .setIconResId(androidx.media3.ui.R.drawable.exo_icon_repeat_off)
                    .setDisplayName("Repeat")
                    .build()
            )
        }
        return ImmutableList.copyOf(layout)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    
    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val player = mediaSession?.player
        when (intent?.action) {
            AvenorWidgetProvider.ACTION_PLAY_PAUSE -> {
                if (player?.isPlaying == true) player.pause() else player?.play()
            }
            AvenorWidgetProvider.ACTION_NEXT -> {
                player?.seekToNext()
            }
            AvenorWidgetProvider.ACTION_PREV -> {
                player?.seekToPrevious()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            val player = mediaSession?.player
            val currentPos = player?.currentPosition ?: 0L
            // 1. Capture state and request structured persistence on coordinator without blocking Main
            playbackCoordinator.persistPositionAsync(currentPos)
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Error persisting position on destroy", e)
        }

        // 2. Release transition engine & audio effects
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && audioDeviceCallback != null) {
                val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
                audioDeviceCallback = null
            }
            errorRecoveryManager.release()
            crossfadeManager.release()
            universalDownmixAudioProcessor.reset()
            equalizerAudioProcessor.reset()
            replayGainAudioProcessor.reset()
            safeLimiterAudioProcessor.reset()
        } catch (e: Exception) {
            android.util.Log.e("PlaybackService", "Error releasing audio effects on destroy", e)
        }

        // 3. Release player and media session
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }

        // 4. Cancel service-bound scope
        serviceScope.cancel()

        super.onDestroy()
    }

    private suspend fun applyReplayGainForMediaItem(mediaItem: androidx.media3.common.MediaItem?) {
        val songId = mediaItem?.mediaId?.toLongOrNull()
        if (songId != null) {
            val song = try {
                AppDatabase.getDatabase(this@PlaybackService).musicDao().getSongById(songId)
            } catch (e: Exception) { null }

            val effectiveGainDb = io.github.yisus.avenor.replaygain.ReplayGainPolicy.calculateEffectiveGainDb(
                mode = replayGainMode,
                trackGain = song?.replayGainTrack,
                albumGain = song?.replayGainAlbum,
                preampDb = replayGainPreampDb
            )
            replayGainAudioProcessor.setEffectiveGainDb(effectiveGainDb)
            val peak = if (replayGainMode == io.github.yisus.avenor.replaygain.ReplayGainMode.ALBUM) {
                song?.replayGainAlbum
            } else {
                song?.replayGainTrack
            }
            safeLimiterAudioProcessor.setAnticipatedGain(effectiveGainDb, peak)
        } else {
            replayGainAudioProcessor.setEffectiveGainDb(0.0f)
            safeLimiterAudioProcessor.setAnticipatedGain(0.0f, null)
        }
    }

    internal fun handleCustomCommand(
        customAction: String,
        args: Bundle,
        session: MediaSession? = mediaSession,
        controller: MediaSession.ControllerInfo? = null
    ): SessionResult {
        when (customAction) {
            "SET_REPLAY_GAIN_CONFIG" -> {
                try {
                    val modeStr = args.getString("mode")
                    if (modeStr != null) {
                        replayGainMode = try {
                            io.github.yisus.avenor.replaygain.ReplayGainMode.valueOf(modeStr)
                        } catch (e: Exception) {
                            replayGainMode
                        }
                    }
                    if (args.containsKey("preampDb")) {
                        replayGainPreampDb = args.getFloat("preampDb")
                    }
                    if (args.containsKey("limiterEnabled")) {
                        safeLimiterAudioProcessor.isEnabled = args.getBoolean("limiterEnabled")
                    }
                    val currentItem = mediaSession?.player?.currentMediaItem
                    serviceScope.launch {
                        applyReplayGainForMediaItem(currentItem)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Error setting ReplayGain config", e)
                }
            }
            "SET_CROSSFADE_CONFIG" -> {
                try {
                    if (args.containsKey("enabled")) {
                        crossfadeManager.isCrossfadeEnabled = args.getBoolean("enabled")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Error setting crossfade config", e)
                }
            }
            "SET_EQ_BAND" -> {
                try {
                    val band = if (args.containsKey("band")) args.getShort("band").toInt() else -1
                    val levelDb: Float = when {
                        args.containsKey("levelDb") -> args.getFloat("levelDb")
                        args.containsKey("level") -> args.getShort("level").toFloat() / 100.0f
                        else -> 0.0f
                    }
                    if (band in 0 until EqBandSettings.BAND_COUNT && !levelDb.isNaN() && !levelDb.isInfinite()) {
                        val currentGains = equalizerAudioProcessor.getSettings().copyGains()
                        currentGains[band] = levelDb.coerceIn(-12.0f, 12.0f)
                        val newSettings = EqBandSettings.of(equalizerAudioProcessor.getSettings().isEnabled, currentGains)
                        equalizerAudioProcessor.setSettings(newSettings)
                        eqPreferences.setBands(currentGains)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Error setting EQ band", e)
                }
            }
            "SET_EQ_CONFIG" -> {
                try {
                    val currentSettings = equalizerAudioProcessor.getSettings()
                    val newEnabled = if (args.containsKey("enabled")) args.getBoolean("enabled") else currentSettings.isEnabled
                    val newGains = if (args.containsKey("bands")) {
                        args.getFloatArray("bands") ?: currentSettings.copyGains()
                    } else {
                        currentSettings.copyGains()
                    }
                    if (newGains.size == EqBandSettings.BAND_COUNT) {
                        val newSettings = EqBandSettings.of(newEnabled, newGains)
                        equalizerAudioProcessor.setSettings(newSettings)
                        eqPreferences.setEnabled(newEnabled)
                        eqPreferences.setBands(newGains)
                    }
                    if (args.containsKey("presetName")) {
                        val pName = args.getString("presetName")
                        if (!pName.isNullOrBlank()) {
                            eqPreferences.setCurrentPresetName(pName)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PlaybackService", "Error setting EQ config", e)
                }
            }
            "SET_NOTIFICATION_PREFS" -> {
                try {
                    showLikeButton = args.getBoolean("showLike", true)
                    showShuffleButton = args.getBoolean("showShuffle", true)
                    showRepeatButton = args.getBoolean("showRepeat", true)
                    if (session != null && controller != null) {
                        session.setCustomLayout(controller, buildCustomLayout())
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            "ACTION_LIKE" -> {
                val currentMediaId = mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull()
                if (currentMediaId != null) {
                    serviceScope.launch {
                        try {
                            val db = AppDatabase.getDatabase(applicationContext)
                            val repo = DatabaseRepository(db.musicDao())
                            repo.toggleFavorite(currentMediaId)
                        } catch (e: Exception) {
                            android.util.Log.e("PlaybackService", "Error toggling favorite", e)
                        }
                    }
                }
            }
        }
        return SessionResult(SessionResult.RESULT_SUCCESS)
    }
}
