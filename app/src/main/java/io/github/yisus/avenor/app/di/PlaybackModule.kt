package io.github.yisus.avenor.app.di

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.yisus.avenor.app.playback.Media3ControllerImpl
import io.github.yisus.avenor.shared.playback.AvenorPlaybackController
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun providePlayer(@ApplicationContext context: Context): Player {
        return ExoPlayer.Builder(context).build()
    }

    @Provides
    @Singleton
    fun provideAvenorPlaybackController(player: Player): AvenorPlaybackController {
        return Media3ControllerImpl(player)
    }
}
