import re

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "r") as f:
    content = f.read()

# Add DefaultLoadControl
old_player = """        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractorsFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()"""

new_player = """        val appSettings = kotlinx.coroutines.runBlocking {
            AppDatabase.getDatabase(this@PlaybackService).musicDao().getSettings().kotlinx.coroutines.flow.firstOrNull()
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
            .build()"""

if "setLoadControl" not in content:
    content = content.replace(old_player, new_player)
    content = "import kotlinx.coroutines.flow.firstOrNull\n" + content

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "w") as f:
    f.write(content)
