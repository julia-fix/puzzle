package com.puzzle.jigsaw.feature.gallery

import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.RecentPuzzleSession

data class PuzzleImageProgress(
    val image: PuzzleImage,
    val completionRatio: Float,
    val updatedAtEpochMillis: Long,
)

fun orderCategoryImages(
    images: List<PuzzleImage>,
    recentSessions: List<RecentPuzzleSession>,
): List<PuzzleImageProgress> {
    val imagesById = images.associateBy(PuzzleImage::id)
    val progressByImageId = recentSessions
        .groupBy(RecentPuzzleSession::imageId)
        .mapNotNull { (imageId, sessions) ->
            val image = imagesById[imageId] ?: return@mapNotNull null
            imageId to PuzzleImageProgress(
                image = image,
                completionRatio = sessions.maxOfOrNull(RecentPuzzleSession::completionRatio) ?: 0f,
                updatedAtEpochMillis = sessions.maxOfOrNull(RecentPuzzleSession::updatedAtEpochMillis) ?: 0L,
            )
        }
        .toMap()

    return images.map { image ->
        progressByImageId[image.id] ?: PuzzleImageProgress(
            image = image,
            completionRatio = 0f,
            updatedAtEpochMillis = 0L,
        )
    }.sortedWith(
        compareBy<PuzzleImageProgress> { progressBucket(it.completionRatio) }
            .thenByDescending { if (it.completionRatio in 0f..0.99999994f) it.completionRatio else -1f }
            .thenByDescending(PuzzleImageProgress::updatedAtEpochMillis)
            .thenBy { it.image.title },
    )
}

private fun progressBucket(completionRatio: Float): Int = when {
    completionRatio > 0f && completionRatio < 1f -> 0
    completionRatio <= 0f -> 1
    else -> 2
}
