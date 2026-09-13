import re

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "r") as f:
    content = f.read()

# Replace the builder block to inject error recovery and crossfade
builder_block = """
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            
        errorRecoveryManager = ErrorRecoveryManager(player)
        crossfadeManager = CrossfadeManager(player)
"""

content = re.sub(r'val player = ExoPlayer\.Builder.*?\.build\(\)', builder_block.strip(), content, flags=re.DOTALL)

# Now inject the custom notification provider class
notification_provider_code = """
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
"""

content = re.sub(r'// Explicitly setup default provider.*setMediaNotificationProvider\(provider\)', notification_provider_code.strip(), content, flags=re.DOTALL)

# Add onDestroy releases
content = content.replace("equalizer?.release()", "equalizer?.release()\n        crossfadeManager.release()")

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "w") as f:
    f.write(content)
