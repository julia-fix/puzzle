package com.puzzle.jigsaw.feature.gallery

import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import com.puzzle.jigsaw.core.model.RecentPuzzleSession
import org.junit.Assert.assertEquals
import org.junit.Test

class PuzzleImageCategoryTest {

    @Test
    fun orderCategoryImages_placesPartialThenUntouchedThenCompleted() {
        val images = listOf(
            image(id = "zero", categoryId = "nature", categoryName = "Nature"),
            image(id = "partial", categoryId = "nature", categoryName = "Nature"),
            image(id = "done", categoryId = "nature", categoryName = "Nature"),
        )
        val sessions = listOf(
            RecentPuzzleSession(
                imageId = "partial",
                pieceCount = 24,
                placedPieces = 12,
                updatedAtEpochMillis = 2L,
            ),
            RecentPuzzleSession(
                imageId = "done",
                pieceCount = 24,
                placedPieces = 24,
                updatedAtEpochMillis = 3L,
            ),
        )

        val ordered = orderCategoryImages(images, sessions)

        assertEquals(listOf("partial", "zero", "done"), ordered.map { it.image.id })
    }

    @Test
    fun orderCategoryImages_ignoresSessionsForImagesOutsideFilteredCategory() {
        val images = listOf(
            image(id = "nature-1", categoryId = "nature", categoryName = "Nature"),
        )
        val sessions = listOf(
            RecentPuzzleSession(
                imageId = "city-1",
                pieceCount = 24,
                placedPieces = 8,
                updatedAtEpochMillis = 1L,
            ),
        )

        val ordered = orderCategoryImages(images, sessions)

        assertEquals(listOf("nature-1"), ordered.map { it.image.id })
        assertEquals(0f, ordered.single().completionRatio)
    }

    private fun image(
        id: String,
        categoryId: String,
        categoryName: String,
    ): PuzzleImage = PuzzleImage(
        id = id,
        title = id,
        previewPath = "puzzles/$id-preview.webp",
        fullImagePath = "puzzles/$id.webp",
        storage = PuzzleImageStorage.ASSET,
        categoryId = categoryId,
        categoryName = categoryName,
    )
}
