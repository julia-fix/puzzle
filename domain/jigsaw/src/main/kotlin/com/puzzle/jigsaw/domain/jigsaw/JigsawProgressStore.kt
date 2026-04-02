package com.puzzle.jigsaw.domain.jigsaw

import com.puzzle.jigsaw.core.model.JigsawProgress
import com.puzzle.jigsaw.core.model.RecentPuzzleSession
import kotlinx.coroutines.flow.Flow

interface JigsawProgressStore {
    suspend fun loadProgress(imageId: String, pieceCount: Int): JigsawProgress?

    suspend fun saveProgress(progress: JigsawProgress)

    suspend fun clearProgress(imageId: String, pieceCount: Int)

    suspend fun loadGameplayBackgroundId(): String?

    suspend fun saveGameplayBackgroundId(backgroundId: String)

    fun recentSessions(): Flow<List<RecentPuzzleSession>>
}
