package io.github.yisus.avenor.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.yisus.avenor.app.data.repository.LocalAudioRepository
import io.github.yisus.avenor.app.data.repository.LocalAudioRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AudioDataModule {

    @Binds
    @Singleton
    fun bindLocalAudioRepository(
        impl: LocalAudioRepositoryImpl
    ): LocalAudioRepository
}
