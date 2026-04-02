package com.puzzle.jigsaw.data.progress

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.puzzle.jigsaw.core.model.JigsawProgress
import com.puzzle.jigsaw.core.model.RecentPuzzleSession
import com.puzzle.jigsaw.core.model.SavedBoardPiecePosition
import com.puzzle.jigsaw.core.model.SavedPieceLink
import com.puzzle.jigsaw.domain.jigsaw.JigsawProgressStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val STORE_NAME = "jigsaw_progress"
private const val PROGRESS_PREFIX = "progress:"
private const val UPDATED_PREFIX = "updated:"
private const val ELAPSED_PREFIX = "elapsed:"
private const val GAMEPLAY_BACKGROUND_KEY = "gameplay_background"

private val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = STORE_NAME)

class DataStoreJigsawProgressStore(
    private val dataStore: DataStore<Preferences>,
) : JigsawProgressStore {
    suspend fun migrateLegacyImageIds(idMappings: Map<String, String>) {
        if (idMappings.isEmpty()) {
            return
        }

        dataStore.edit { preferences ->
            val storedRecords = preferences.asStoredProgressRecords()
            if (storedRecords.none { record -> idMappings[record.imageId]?.let { it != record.imageId } == true }) {
                return@edit
            }

            val migratedRecords = migrateStoredProgressRecords(
                records = storedRecords,
                idMappings = idMappings,
            )

            val recordsToRemove = storedRecords + migratedRecords
            recordsToRemove.forEach { record ->
                preferences.remove(progressKey(record.imageId, record.pieceCount))
                preferences.remove(updatedKey(record.imageId, record.pieceCount))
                preferences.remove(elapsedKey(record.imageId, record.pieceCount))
            }
            migratedRecords.forEach { record ->
                preferences[progressKey(record.imageId, record.pieceCount)] = record.payload
                preferences[updatedKey(record.imageId, record.pieceCount)] = record.updatedAtEpochMillis
                preferences[elapsedKey(record.imageId, record.pieceCount)] = record.elapsedPlayTimeMillis
            }
        }
    }

    override suspend fun loadProgress(imageId: String, pieceCount: Int): JigsawProgress? {
        val preferences = safePreferences().first()
        val progressPayload = decodeProgressPayload(preferences[progressKey(imageId, pieceCount)])
        val updatedAt = preferences[updatedKey(imageId, pieceCount)] ?: return null

        return JigsawProgress(
            imageId = imageId,
            pieceCount = pieceCount,
            placedPieceIds = progressPayload.placedPieceIds,
            boardPiecePositions = progressPayload.boardPiecePositions,
            pieceLinks = progressPayload.pieceLinks,
            pieceOrder = progressPayload.pieceOrder,
            elapsedPlayTimeMillis = preferences[elapsedKey(imageId, pieceCount)] ?: 0L,
            updatedAtEpochMillis = updatedAt,
        )
    }

    override suspend fun saveProgress(progress: JigsawProgress) {
        dataStore.edit { preferences ->
            preferences[progressKey(progress.imageId, progress.pieceCount)] =
                encodeProgressPayload(progress)
            preferences[updatedKey(progress.imageId, progress.pieceCount)] = progress.updatedAtEpochMillis
            preferences[elapsedKey(progress.imageId, progress.pieceCount)] = progress.elapsedPlayTimeMillis
        }
    }

    override suspend fun clearProgress(imageId: String, pieceCount: Int) {
        dataStore.edit { preferences ->
            preferences.remove(progressKey(imageId, pieceCount))
            preferences.remove(updatedKey(imageId, pieceCount))
            preferences.remove(elapsedKey(imageId, pieceCount))
        }
    }

    override suspend fun loadGameplayBackgroundId(): String? {
        val preferences = safePreferences().first()
        return preferences[gameplayBackgroundKey()]
    }

    override suspend fun saveGameplayBackgroundId(backgroundId: String) {
        dataStore.edit { preferences ->
            preferences[gameplayBackgroundKey()] = backgroundId
        }
    }

    override fun recentSessions(): Flow<List<RecentPuzzleSession>> = safePreferences().map { preferences ->
        preferences.asStoredProgressRecords().toRecentPuzzleSessions()
    }

    private fun safePreferences(): Flow<Preferences> = dataStore.data.catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    companion object {
        fun create(context: Context): DataStoreJigsawProgressStore =
            DataStoreJigsawProgressStore(context.progressDataStore)
    }
}

private fun progressKey(imageId: String, pieceCount: Int): Preferences.Key<String> =
    stringPreferencesKey("$PROGRESS_PREFIX$imageId:$pieceCount")

private fun updatedKey(imageId: String, pieceCount: Int): Preferences.Key<Long> =
    longPreferencesKey("$UPDATED_PREFIX$imageId:$pieceCount")

private fun elapsedKey(imageId: String, pieceCount: Int): Preferences.Key<Long> =
    longPreferencesKey("$ELAPSED_PREFIX$imageId:$pieceCount")

private fun gameplayBackgroundKey(): Preferences.Key<String> =
    stringPreferencesKey(GAMEPLAY_BACKGROUND_KEY)

internal data class ProgressPayload(
    val placedPieceIds: Set<Int>,
    val boardPiecePositions: List<SavedBoardPiecePosition>,
    val pieceLinks: Set<SavedPieceLink>,
    val pieceOrder: List<Int>,
)

internal data class StoredProgressRecord(
    val imageId: String,
    val pieceCount: Int,
    val payload: String,
    val updatedAtEpochMillis: Long,
    val elapsedPlayTimeMillis: Long = 0L,
)

internal fun encodeProgressPayloadForTest(progress: JigsawProgress): String = encodeProgressPayload(progress)

internal fun decodeProgressPayloadForTest(rawValue: String?): ProgressPayload = decodeProgressPayload(rawValue)

internal fun migrateStoredProgressRecordsForTest(
    records: List<StoredProgressRecord>,
    idMappings: Map<String, String>,
): List<StoredProgressRecord> = migrateStoredProgressRecords(records, idMappings)

internal fun recentPuzzleSessionsFromRecordsForTest(
    records: List<StoredProgressRecord>,
): List<RecentPuzzleSession> = records.toRecentPuzzleSessions()

private fun encodeProgressPayload(progress: JigsawProgress): String = buildString {
    append(PROGRESS_FORMAT_VERSION)
    append('|')
    append(encodePieces(progress.placedPieceIds))
    append('|')
    append(encodeBoardPiecePositions(progress.boardPiecePositions))
    append('|')
    append(encodePieceLinks(progress.pieceLinks))
    append('|')
    append(encodePieceOrder(progress.pieceOrder))
}

private fun decodeProgressPayload(rawValue: String?): ProgressPayload {
    if (rawValue.isNullOrBlank()) {
        return ProgressPayload(
            placedPieceIds = emptySet(),
            boardPiecePositions = emptyList(),
            pieceLinks = emptySet(),
            pieceOrder = emptyList(),
        )
    }
    if (!rawValue.startsWith("$PROGRESS_FORMAT_VERSION|") && !rawValue.startsWith("$PREVIOUS_PROGRESS_FORMAT_VERSION|")) {
        return ProgressPayload(
            placedPieceIds = decodePieces(rawValue),
            boardPiecePositions = emptyList(),
            pieceLinks = emptySet(),
            pieceOrder = emptyList(),
        )
    }

    val parts = rawValue.split('|')
    return ProgressPayload(
        placedPieceIds = decodePieces(parts.getOrNull(1)),
        boardPiecePositions = decodeBoardPiecePositions(parts.getOrNull(2)),
        pieceLinks = decodePieceLinks(parts.getOrNull(3)),
        pieceOrder = if (parts.firstOrNull() == PROGRESS_FORMAT_VERSION) {
            decodePieceOrder(parts.getOrNull(4))
        } else {
            emptyList()
        },
    )
}

internal fun encodePieces(pieceIds: Set<Int>): String = pieceIds.sorted().joinToString(separator = ",")

internal fun decodePieces(rawValue: String?): Set<Int> = rawValue
    ?.split(',')
    ?.mapNotNull(String::toIntOrNull)
    ?.toSet()
    ?: emptySet()

internal fun encodeBoardPiecePositions(positions: List<SavedBoardPiecePosition>): String = positions
    .sortedBy(SavedBoardPiecePosition::pieceId)
    .joinToString(separator = ";") { position ->
        "${position.pieceId},${position.x},${position.y}"
    }

internal fun decodeBoardPiecePositions(rawValue: String?): List<SavedBoardPiecePosition> = rawValue
    ?.takeIf(String::isNotBlank)
    ?.split(';')
    ?.mapNotNull { token ->
        val parts = token.split(',')
        val pieceId = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val x = parts.getOrNull(1)?.toFloatOrNull() ?: return@mapNotNull null
        val y = parts.getOrNull(2)?.toFloatOrNull() ?: return@mapNotNull null
        SavedBoardPiecePosition(
            pieceId = pieceId,
            x = x,
            y = y,
        )
    }
    ?: emptyList()

internal fun encodePieceLinks(links: Set<SavedPieceLink>): String = links
    .sortedWith(compareBy(SavedPieceLink::firstPieceId, SavedPieceLink::secondPieceId))
    .joinToString(separator = ";") { link ->
        "${link.firstPieceId},${link.secondPieceId}"
    }

internal fun decodePieceLinks(rawValue: String?): Set<SavedPieceLink> = rawValue
    ?.takeIf(String::isNotBlank)
    ?.split(';')
    ?.mapNotNull { token ->
        val parts = token.split(',')
        val firstPieceId = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
        val secondPieceId = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        SavedPieceLink(
            firstPieceId = firstPieceId,
            secondPieceId = secondPieceId,
        )
    }
    ?.toSet()
    ?: emptySet()

internal fun encodePieceOrder(pieceOrder: List<Int>): String = pieceOrder.joinToString(separator = ",")

internal fun decodePieceOrder(rawValue: String?): List<Int> = rawValue
    ?.split(',')
    ?.mapNotNull(String::toIntOrNull)
    ?: emptyList()

private const val PREVIOUS_PROGRESS_FORMAT_VERSION = "v2"
private const val PROGRESS_FORMAT_VERSION = "v3"

private fun Preferences.asStoredProgressRecords(): List<StoredProgressRecord> = asMap()
    .entries
    .mapNotNull { (rawKey, value) ->
        val keyName = rawKey.name
        if (!keyName.startsWith(PROGRESS_PREFIX) || value !is String) {
            return@mapNotNull null
        }

        val sessionDescriptor = keyName.removePrefix(PROGRESS_PREFIX)
        val separatorIndex = sessionDescriptor.lastIndexOf(':')
        if (separatorIndex == -1) {
            return@mapNotNull null
        }

        val imageId = sessionDescriptor.substring(0, separatorIndex)
        val pieceCount = sessionDescriptor.substring(separatorIndex + 1).toIntOrNull()
            ?: return@mapNotNull null
        val updatedAt = this[updatedKey(imageId, pieceCount)] ?: return@mapNotNull null

        StoredProgressRecord(
            imageId = imageId,
            pieceCount = pieceCount,
            payload = value,
            updatedAtEpochMillis = updatedAt,
            elapsedPlayTimeMillis = this[elapsedKey(imageId, pieceCount)] ?: 0L,
        )
    }

private fun migrateStoredProgressRecords(
    records: List<StoredProgressRecord>,
    idMappings: Map<String, String>,
): List<StoredProgressRecord> = records
    .map { record ->
        record.copy(imageId = idMappings[record.imageId] ?: record.imageId)
    }
    .groupBy { record -> record.imageId to record.pieceCount }
    .values
    .mapNotNull { group ->
        group.maxByOrNull(StoredProgressRecord::updatedAtEpochMillis)
    }
    .sortedWith(
        compareBy<StoredProgressRecord>(StoredProgressRecord::imageId)
            .thenBy(StoredProgressRecord::pieceCount),
    )

private fun List<StoredProgressRecord>.toRecentPuzzleSessions(): List<RecentPuzzleSession> = map { record ->
    RecentPuzzleSession(
        imageId = record.imageId,
        pieceCount = record.pieceCount,
        placedPieces = decodeProgressPayload(record.payload).placedPieceIds.size,
        updatedAtEpochMillis = record.updatedAtEpochMillis,
    )
}.sortedByDescending(RecentPuzzleSession::updatedAtEpochMillis)
