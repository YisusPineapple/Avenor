with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "r") as f:
    content = f.read()

on_start_command = """
    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val player = mediaSession?.player
        when (intent?.action) {
            NexoWidgetProvider.ACTION_PLAY_PAUSE -> {
                if (player?.isPlaying == true) player.pause() else player?.play()
            }
            NexoWidgetProvider.ACTION_NEXT -> {
                player?.seekToNext()
            }
            NexoWidgetProvider.ACTION_PREV -> {
                player?.seekToPrevious()
            }
        }
        return START_STICKY
    }
"""

if "fun onStartCommand" not in content:
    content = content.replace("override fun onDestroy() {", on_start_command + "\n    override fun onDestroy() {")

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "w") as f:
    f.write(content)
