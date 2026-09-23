package io.github.yisus.avenor

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToStream
import java.io.BufferedInputStream
import java.io.File
import java.io.OutputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed class BackupResult {
    object Success : BackupResult()
    sealed class Error(val message: String) : BackupResult() {
        class UnreadableFile(val reason: String) : Error("Archivo ilegible: $reason")
        class InvalidZip(val reason: String) : Error("ZIP inválido o corrupto: $reason")
        class InvalidJson(val reason: String) : Error("JSON sintácticamente inválido: $reason")
        class FutureVersion(val foundVersion: Int, val supportedVersion: Int) :
            Error("Versión de backup no soportada: versión $foundVersion (máxima soportada: $supportedVersion)")
        class IncompatibleStructure(val reason: String) : Error("Estructura de backup incompatible: $reason")
        class TransactionError(val reason: String) : Error("Error durante la restauración transaccional: $reason")
        class ChecksumMismatch(val expected: String, val actual: String) :
            Error("Integridad fallida: el checksum calculado ($actual) no coincide con el esperado ($expected)")
        class UnsupportedChecksumAlgorithm(val algorithm: String) :
            Error("Algoritmo de checksum no soportado: $algorithm (solo se soporta SHA-256)")
    }
}

sealed class IntegrityStatus {
    object Verified : IntegrityStatus()
    data class UnverifiedLegacy(val reason: String) : IntegrityStatus()
}

sealed class BackupValidationResult {
    data class Valid(
        val export: DatabaseExport,
        val isLegacy: Boolean,
        val integrityStatus: IntegrityStatus = IntegrityStatus.Verified
    ) : BackupValidationResult()
    data class Invalid(val error: BackupResult.Error) : BackupValidationResult()
}

class ZipCorruptException(message: String) : Exception(message)
class MetadataNotFoundException(message: String) : Exception(message)

object BackupManager {
    const val CURRENT_BACKUP_FORMAT_VERSION = 1
    const val CHECKSUM_ALGORITHM_SHA256 = "SHA-256"

    val jsonFormat = Json { 
        prettyPrint = false
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun computePayloadSha256(export: DatabaseExport): String {
        // Canonical payload: identical export with algorithm = SHA-256 and checksumSha256 = null
        val canonicalPayload = export.copy(
            checksumAlgorithm = CHECKSUM_ALGORITHM_SHA256,
            checksumSha256 = null
        )
        val md = MessageDigest.getInstance("SHA-256")
        val nullOutputStream = object : OutputStream() {
            override fun write(b: Int) {}
            override fun write(b: ByteArray, off: Int, len: Int) {}
        }
        val digestOut = DigestOutputStream(nullOutputStream, md)
        jsonFormat.encodeToStream(canonicalPayload, digestOut)
        digestOut.flush()
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun buildExportData(dao: MusicDao): DatabaseExport {
        val rawExport = DatabaseExport(
            backupFormatVersion = CURRENT_BACKUP_FORMAT_VERSION,
            roomSchemaVersion = DATABASE_VERSION,
            checksumAlgorithm = CHECKSUM_ALGORITHM_SHA256,
            checksumSha256 = null,
            songs = dao.getAllSongsSync(),
            playlists = dao.getAllPlaylistsSync(),
            history = dao.getAllHistorySync(),
            playlistSongs = dao.getAllPlaylistSongsSync(),
            eqPresets = dao.getAllEqPresetsSync(),
            settings = dao.getSettingsSync(),
            lyricOffsets = dao.getAllLyricOffsets(),
            playbackQueues = dao.getAllQueuesSync(),
            queueSongs = dao.getAllQueueSongsSync(),
            trashItems = dao.getAllTrashItemsSync(),
            favorites = dao.getAllFavoritesSync()
        )
        val sha256 = computePayloadSha256(rawExport)
        return rawExport.copy(checksumSha256 = sha256)
    }

    fun validateAndParseJson(jsonString: String): BackupValidationResult {
        if (jsonString.isBlank()) {
            return BackupValidationResult.Invalid(
                BackupResult.Error.InvalidJson("El contenido del backup está vacío")
            )
        }

        val rootElement: JsonElement = try {
            jsonFormat.parseToJsonElement(jsonString)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(
                BackupResult.Error.InvalidJson(e.message ?: "Error de sintaxis JSON")
            )
        }

        val rootObject = rootElement as? JsonObject
            ?: return BackupValidationResult.Invalid(
                BackupResult.Error.IncompatibleStructure("La raíz del JSON no es un objeto")
            )

        val versionElement = rootObject["backupFormatVersion"]
        val isLegacy: Boolean
        val resolvedVersion: Int

        if (versionElement != null) {
            isLegacy = false
            val versionPrimitive = versionElement as? JsonPrimitive
            val versionInt = try {
                versionPrimitive?.content?.toIntOrNull()
            } catch (e: Exception) {
                null
            }

            if (versionInt == null) {
                return BackupValidationResult.Invalid(
                    BackupResult.Error.IncompatibleStructure("El campo 'backupFormatVersion' debe ser un número entero válido")
                )
            }

            if (versionInt > CURRENT_BACKUP_FORMAT_VERSION) {
                return BackupValidationResult.Invalid(
                    BackupResult.Error.FutureVersion(
                        foundVersion = versionInt,
                        supportedVersion = CURRENT_BACKUP_FORMAT_VERSION
                    )
                )
            }

            if (versionInt < 1) {
                return BackupValidationResult.Invalid(
                    BackupResult.Error.IncompatibleStructure("Versión de formato no soportada: $versionInt")
                )
            }

            resolvedVersion = versionInt
        } else {
            // Legacy backup without version
            // Validate minimum recognizable Avenor structure:
            // Must contain 'songs' array and at least one other standard Avenor table
            val hasSongs = rootObject.containsKey("songs") && rootObject["songs"] is JsonArray
            val hasOtherEntity = (rootObject.containsKey("playlists") && rootObject["playlists"] is JsonArray) ||
                    (rootObject.containsKey("history") && rootObject["history"] is JsonArray) ||
                    (rootObject.containsKey("playlistSongs") && rootObject["playlistSongs"] is JsonArray) ||
                    (rootObject.containsKey("settings") && (rootObject["settings"] is JsonObject || rootObject["settings"] is JsonNull)) ||
                    (rootObject.containsKey("favorites") && rootObject["favorites"] is JsonArray)

            if (!hasSongs || !hasOtherEntity) {
                return BackupValidationResult.Invalid(
                    BackupResult.Error.IncompatibleStructure("El archivo no contiene la estructura mínima requerida para un backup de Avenor")
                )
            }

            isLegacy = true
            resolvedVersion = CURRENT_BACKUP_FORMAT_VERSION
        }

        // Checksum fields extraction
        val algorithmPrimitive = (rootObject["checksumAlgorithm"] as? JsonPrimitive)?.takeUnless { it is JsonNull }
        val algorithm = algorithmPrimitive?.content
        val checksumPrimitive = (rootObject["checksumSha256"] as? JsonPrimitive)?.takeUnless { it is JsonNull }
        val expectedChecksum = checksumPrimitive?.content

        // Case D: algorithm present but unknown/unsupported
        if (algorithm != null && !algorithm.equals(CHECKSUM_ALGORITHM_SHA256, ignoreCase = true)) {
            return BackupValidationResult.Invalid(
                BackupResult.Error.UnsupportedChecksumAlgorithm(algorithm)
            )
        }

        // If algorithm is specified but hash is missing/blank
        if (algorithm != null && expectedChecksum.isNullOrBlank()) {
            return BackupValidationResult.Invalid(
                BackupResult.Error.IncompatibleStructure("Se especificó 'checksumAlgorithm' pero falta 'checksumSha256'")
            )
        }

        val exportData = try {
            jsonFormat.decodeFromJsonElement<DatabaseExport>(rootObject)
        } catch (e: Exception) {
            return BackupValidationResult.Invalid(
                BackupResult.Error.IncompatibleStructure("Error al deserializar las entidades del backup: ${e.message}")
            )
        }

        val integrityStatus: IntegrityStatus
        if (!expectedChecksum.isNullOrBlank()) {
            // Case B & C: Checksum present -> verify SHA-256
            val calculatedChecksum = computePayloadSha256(exportData)
            if (!calculatedChecksum.equals(expectedChecksum, ignoreCase = true)) {
                return BackupValidationResult.Invalid(
                    BackupResult.Error.ChecksumMismatch(
                        expected = expectedChecksum,
                        actual = calculatedChecksum
                    )
                )
            }
            integrityStatus = IntegrityStatus.Verified
        } else {
            // Case A: Legacy or unchecksummed backup
            integrityStatus = IntegrityStatus.UnverifiedLegacy("Checksum no presente en el archivo (backup legacy o v1 sin hash)")
        }

        val normalizedData = if (exportData.backupFormatVersion != resolvedVersion) {
            exportData.copy(backupFormatVersion = resolvedVersion)
        } else {
            exportData
        }

        return BackupValidationResult.Valid(normalizedData, isLegacy, integrityStatus)
    }

    suspend fun restoreValidatedBackup(dao: MusicDao, export: DatabaseExport): BackupResult = withContext(Dispatchers.IO) {
        try {
            dao.restoreDatabase(export)
            BackupResult.Success
        } catch (e: Exception) {
            BackupResult.Error.TransactionError(e.message ?: "Error desconocido en SQLite")
        }
    }

    suspend fun importBackupFromJsonString(dao: MusicDao, jsonString: String): BackupResult = withContext(Dispatchers.IO) {
        when (val validation = validateAndParseJson(jsonString)) {
            is BackupValidationResult.Invalid -> validation.error
            is BackupValidationResult.Valid -> restoreValidatedBackup(dao, validation.export)
        }
    }

    internal fun readJsonFromStream(stream: java.io.InputStream): String {
        val bufferedStream = BufferedInputStream(stream)
        bufferedStream.mark(4)
        val header = ByteArray(4)
        val bytesRead = bufferedStream.read(header, 0, 4)
        bufferedStream.reset()

        val isZip = bytesRead == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()

        if (isZip) {
            try {
                ZipInputStream(bufferedStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "metadata.json") {
                            return zis.bufferedReader(Charsets.UTF_8).readText()
                        }
                        entry = zis.nextEntry
                    }
                }
                throw MetadataNotFoundException("No se encontró 'metadata.json' en el archivo ZIP")
            } catch (e: MetadataNotFoundException) {
                throw e
            } catch (e: Exception) {
                throw ZipCorruptException("Archivo ZIP corrupto o ilegible: ${e.message}")
            }
        } else {
            return bufferedStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }

    private fun readJsonFromUri(context: Context, uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir el stream para la URI proporcionada")
        return readJsonFromStream(stream)
    }

    suspend fun importBackupDetailed(context: Context, uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val jsonString = try {
            readJsonFromUri(context, uri)
        } catch (e: ZipCorruptException) {
            return@withContext BackupResult.Error.InvalidZip(e.message ?: "ZIP corrupto")
        } catch (e: MetadataNotFoundException) {
            return@withContext BackupResult.Error.IncompatibleStructure(e.message ?: "Falta metadata.json")
        } catch (e: Exception) {
            return@withContext BackupResult.Error.UnreadableFile(e.message ?: "Error al leer el archivo")
        }

        when (val validation = validateAndParseJson(jsonString)) {
            is BackupValidationResult.Invalid -> validation.error
            is BackupValidationResult.Valid -> {
                val dao = AppDatabase.getDatabase(context).musicDao()
                restoreValidatedBackup(dao, validation.export)
            }
        }
    }

    suspend fun importBackupFromJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        importBackupDetailed(context, uri) is BackupResult.Success
    }

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun exportBackupToStream(context: Context, outStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).musicDao()
            val exportData = buildExportData(dao)
            
            // Write ZIP stream containing ONLY metadata.json (avenor_database.db removed in P1-4D)
            ZipOutputStream(outStream).use { zout ->
                zout.putNextEntry(ZipEntry("metadata.json"))
                jsonFormat.encodeToStream(exportData, zout)
                zout.closeEntry()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportBackupToJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val outStream = context.contentResolver.openOutputStream(uri) ?: return@withContext false
        outStream.use { exportBackupToStream(context, it) }
    }

    // Auto-backup to internal storage for WorkManager
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun createAutoBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).musicDao()
            val exportData = buildExportData(dao)
            val file = File(context.filesDir, "avenor_auto_backup.json")
            file.outputStream().use { os ->
                jsonFormat.encodeToStream(exportData, os)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
