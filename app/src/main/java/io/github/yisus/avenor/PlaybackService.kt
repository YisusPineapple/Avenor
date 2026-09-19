
package io.github.yisus.avenor
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
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
import io.github.yisus.avenor.playback.PlaybackCoordinator

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private lateinit var errorRecoveryManager: ErrorRecoveryManager
    private lateinit var crossfadeManager: CrossfadeManager
    private lateinit var playbackCoordinator: PlaybackCoordinator
    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    
    private var showLikeButton = true
    private var showShuffleButton = true
    private var showRepeatButton = true

    override fun onCreate() {
        super.onCreate()
        playbackCoordinator = PlaybackCoordinator.getInstance(this)
        
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            
        val appSettings = kotlinx.coroutines.runBlocking {
            AppDatabase.getDatabase(this@PlaybackService).musicDao().getSettings().firstOrNull()
        }
        val bufferMs = DeviceProfileManager.getOptimalBufferMs(appSettings?.performanceMode ?: "VIVID")
        
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                bufferMs,
                bufferMs,
                androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
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
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        equalizer?.release()
                        crossfadeManager.release()
                        equalizer = Equalizer(0, audioSessionId)
                        equalizer?.enabled = true
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                serviceScope.launch {
                    playbackCoordinator.updatePlaybackParams(isPlaying = isPlaying)
                    if (!isPlaying) {
                        playbackCoordinator.updatePosition(player.currentPosition, force = true)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                serviceScope.launch {
                    playbackCoordinator.updateTrackTransition(mediaItem?.mediaId, player.currentMediaItemIndex)
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
                    }
                }
            }
        }

        // Periodic playback position updater (persists every 5s during active playback)
        serviceScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(5000L)
                if (player.isPlaying) {
                    playbackCoordinator.updatePosition(player.currentPosition)
                }
            }
        }
            
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(SessionCommand("ACTION_LIKE", Bundle.EMPTY))
                        .add(SessionCommand("SET_EQ_BAND", Bundle.EMPTY))
                        .add(SessionCommand("SET_NOTIFICATION_PREFS", Bundle.EMPTY))
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
                    when (customCommand.customAction) {
                        "SET_EQ_BAND" -> {
                            try {
                                val band = args.getShort("band")
                                val level = args.getShort("level")
                                equalizer?.setBandLevel(band, level)
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        "SET_NOTIFICATION_PREFS" -> {
                            try {
                                showLikeButton = args.getBoolean("showLike", true)
                                showShuffleButton = args.getBoolean("showShuffle", true)
                                showRepeatButton = args.getBoolean("showRepeat", true)
                                session.setCustomLayout(controller, buildCustomLayout())
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                        "ACTION_LIKE" -> {
                            val currentMediaId = player.currentMediaItem?.mediaId?.toLongOrNull()
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
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
            
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
            mediaSession?.player?.let { p ->
                kotlinx.coroutines.runBlocking {
                    playbackCoordinator.updatePosition(p.currentPosition, force = true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        equalizer?.release()
        crossfadeManager.release()
        super.onDestroy()
    }
}
