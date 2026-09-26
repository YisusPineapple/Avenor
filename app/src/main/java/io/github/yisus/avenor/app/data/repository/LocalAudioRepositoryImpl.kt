package io.github.yisus.avenor.app.data.repository

import io.github.yisus.avenor.app.data.scanner.LocalMediaStoreScanner
import io.github.yisus.avenor.app.data.scanner.ScannedAudioItem
import javax.inject.Inject

class LocalAudioRepositoryImpl @Inject constructor(
    private val scanner: LocalMediaStoreScanner
) : LocalAudioRepository {

    override suspend fun getAllLocalAudio(): List<ScannedAudioItem> {
        return scanner.scanLocalAudio()
    }
}
