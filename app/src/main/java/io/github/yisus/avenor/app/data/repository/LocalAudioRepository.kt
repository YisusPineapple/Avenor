package io.github.yisus.avenor.app.data.repository

import io.github.yisus.avenor.app.data.scanner.ScannedAudioItem

interface LocalAudioRepository {
    suspend fun getAllLocalAudio(): List<ScannedAudioItem>
}
