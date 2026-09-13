import re

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "r") as f:
    content = f.read()

old_builder = """
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
"""

new_builder = """
        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this, extractorsFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
"""

content = content.replace(old_builder.strip(), new_builder.strip())

with open("app/src/main/java/io/github/yisus/nexo/PlaybackService.kt", "w") as f:
    f.write(content)

