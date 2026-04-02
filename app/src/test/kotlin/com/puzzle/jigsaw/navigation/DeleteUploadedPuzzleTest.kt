package com.puzzle.jigsaw.navigation

import com.puzzle.jigsaw.core.model.PieceCountOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteUploadedPuzzleTest {

    @Test
    fun `deleteUploadedPuzzle clears every piece count refreshes images and navigates after success`() = runBlocking {
        val clearedProgress = mutableListOf<Pair<String, Int>>()
        val operations = mutableListOf<String>()

        val deleted = deleteUploadedPuzzle(
            imageId = "upload-1",
            pieceCounts = listOf(
                PieceCountOption(rows = 4, columns = 6),
                PieceCountOption(rows = 10, columns = 10),
            ),
            deleteUserImage = { imageId ->
                assertEquals("upload-1", imageId)
                true
            },
            clearProgress = { imageId, pieceCount ->
                clearedProgress += imageId to pieceCount
                operations += "clear:$pieceCount"
            },
            refreshImages = {
                operations += "refresh"
            },
            onDeleted = {
                operations += "navigate"
            },
        )

        assertTrue(deleted)
        assertEquals(
            listOf("upload-1" to 24, "upload-1" to 100),
            clearedProgress,
        )
        assertEquals(
            listOf("clear:24", "clear:100", "navigate", "refresh"),
            operations,
        )
    }

    @Test
    fun `deleteUploadedPuzzle leaves progress and navigation untouched after failure`() = runBlocking {
        var clearedProgress = 0
        var refreshedImages = 0
        var deletedCallbacks = 0

        val deleted = deleteUploadedPuzzle(
            imageId = "upload-1",
            pieceCounts = listOf(PieceCountOption(rows = 4, columns = 6)),
            deleteUserImage = { false },
            clearProgress = { _, _ ->
                clearedProgress += 1
            },
            refreshImages = {
                refreshedImages += 1
            },
            onDeleted = {
                deletedCallbacks += 1
            },
        )

        assertFalse(deleted)
        assertEquals(0, clearedProgress)
        assertEquals(0, refreshedImages)
        assertEquals(0, deletedCallbacks)
    }

    @Test
    fun `deleteUploadedPuzzleAndHandleFailure invokes the failure callback when deletion fails`() = runBlocking {
        var deleteFailedCallbacks = 0

        val deleted = deleteUploadedPuzzleAndHandleFailure(
            imageId = "upload-1",
            pieceCounts = listOf(PieceCountOption(rows = 4, columns = 6)),
            deleteUserImage = { false },
            clearProgress = { _, _ -> },
            refreshImages = { },
            onDeleted = { },
            onDeleteFailed = {
                deleteFailedCallbacks += 1
            },
        )

        assertFalse(deleted)
        assertEquals(1, deleteFailedCallbacks)
    }

    @Test
    fun `deleteUploadedPuzzleAndHandleFailure skips the failure callback after success`() = runBlocking {
        var deleteFailedCallbacks = 0

        val deleted = deleteUploadedPuzzleAndHandleFailure(
            imageId = "upload-1",
            pieceCounts = listOf(PieceCountOption(rows = 4, columns = 6)),
            deleteUserImage = { true },
            clearProgress = { _, _ -> },
            refreshImages = { },
            onDeleted = { },
            onDeleteFailed = {
                deleteFailedCallbacks += 1
            },
        )

        assertTrue(deleted)
        assertEquals(0, deleteFailedCallbacks)
    }
}
