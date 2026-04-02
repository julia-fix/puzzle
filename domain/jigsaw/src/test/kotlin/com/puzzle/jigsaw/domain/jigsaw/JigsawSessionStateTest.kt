package com.puzzle.jigsaw.domain.jigsaw

import com.puzzle.jigsaw.core.model.JigsawProgress
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import com.puzzle.jigsaw.core.model.SavedBoardPiecePosition
import com.puzzle.jigsaw.core.model.SavedPieceLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JigsawSessionStateTest {
    private val image = PuzzleImage(
        id = "fixture-image",
        title = "Fixture",
        previewPath = "puzzles/fixture-preview.webp",
        fullImagePath = "puzzles/fixture.webp",
        storage = PuzzleImageStorage.ASSET,
    )
    private val option = JigsawCatalog.pieceCountOptions().first()

    @Test
    fun `createSessionState drops invalid piece ids`() {
        val progress = JigsawProgress(
            imageId = image.id,
            pieceCount = option.totalPieces,
            placedPieceIds = setOf(0, 3, 11, 99),
            updatedAtEpochMillis = 1L,
        )

        val session = createSessionState(image, option, progress)

        assertEquals(setOf(0, 3, 11), session.placedPieceIds)
    }

    @Test
    fun `createSessionState restores loose board pieces and links`() {
        val progress = JigsawProgress(
            imageId = image.id,
            pieceCount = option.totalPieces,
            placedPieceIds = setOf(0, 3),
            boardPiecePositions = listOf(
                SavedBoardPiecePosition(pieceId = 1, x = 1.2f, y = 0.7f),
                SavedBoardPiecePosition(pieceId = 2, x = 2.2f, y = 0.7f),
            ),
            pieceLinks = setOf(
                SavedPieceLink(firstPieceId = 1, secondPieceId = 2),
            ),
            updatedAtEpochMillis = 1L,
        )

        val session = createSessionState(image, option, progress)

        assertEquals(JigsawBoardPosition(x = 1.2f, y = 0.7f), session.boardPiecePositions.getValue(1))
        assertEquals(JigsawBoardPosition(x = 2.2f, y = 0.7f), session.boardPiecePositions.getValue(2))
        assertEquals(setOf(JigsawPieceLink(firstPieceId = 1, secondPieceId = 2)), session.pieceLinks)
        assertFalse(1 in session.remainingPieceIds)
        assertFalse(2 in session.remainingPieceIds)
    }

    @Test
    fun `createSessionState drops invalid restored loose board pieces and links`() {
        val progress = JigsawProgress(
            imageId = image.id,
            pieceCount = option.totalPieces,
            placedPieceIds = setOf(0),
            boardPiecePositions = listOf(
                SavedBoardPiecePosition(pieceId = 0, x = 0.1f, y = 0.2f),
                SavedBoardPiecePosition(pieceId = 1, x = 0.3f, y = 0.4f),
                SavedBoardPiecePosition(pieceId = 999, x = 0.5f, y = 0.6f),
            ),
            pieceLinks = setOf(
                SavedPieceLink(firstPieceId = 0, secondPieceId = 1),
                SavedPieceLink(firstPieceId = 1, secondPieceId = 999),
            ),
            updatedAtEpochMillis = 1L,
        )

        val session = createSessionState(image, option, progress)

        assertEquals(mapOf(1 to JigsawBoardPosition(x = 0.3f, y = 0.4f)), session.boardPiecePositions)
        assertTrue(session.pieceLinks.isEmpty())
    }

    @Test
    fun `createSessionState builds deterministic shuffled piece order`() {
        val first = createSessionState(image, option, progress = null)
        val second = createSessionState(image, option, progress = null)

        assertEquals(first.pieceOrder, second.pieceOrder)
        assertEquals((0 until option.totalPieces).toSet(), first.pieceOrder.toSet())
        assertFalse(first.pieceOrder == (0 until option.totalPieces).toList())
    }

    @Test
    fun `remainingPieceIds exclude pieces already on board`() {
        val initial = createSessionState(image, option, progress = null)
        val moved = movePieceCluster(
            state = initial,
            pieceId = 2,
            targetPosition = JigsawBoardPosition(x = 0.5f, y = 0.5f),
        )

        assertFalse(2 in moved.remainingPieceIds)
        assertTrue(2 in moved.boardPiecePositions)
    }

    @Test
    fun `movePieceCluster moves all linked pieces together`() {
        val initial = createSessionState(image, option, progress = null)
        val piece0 = movePieceCluster(initial, 0, JigsawBoardPosition(x = 0.6f, y = 0.9f))
        val piece1 = movePieceCluster(piece0, 1, JigsawBoardPosition(x = 1.55f, y = 0.92f))
        val snapped = snapPieceCluster(piece1, pieceId = 1, snapThreshold = 0.2f).state
        val movedCluster = movePieceCluster(snapped, pieceId = 0, targetPosition = JigsawBoardPosition(x = 1.2f, y = 1.4f))

        assertEquals(JigsawBoardPosition(x = 1.2f, y = 1.4f), movedCluster.boardPiecePositions.getValue(0))
        assertEquals(JigsawBoardPosition(x = 2.2f, y = 1.4f), movedCluster.boardPiecePositions.getValue(1))
    }

    @Test
    fun `snapPieceCluster locks piece when close to its correct place`() {
        val initial = createSessionState(image, option, progress = null)
        val moved = movePieceCluster(
            state = initial,
            pieceId = 0,
            targetPosition = JigsawBoardPosition(x = 0.08f, y = 0.04f),
        )

        val outcome = snapPieceCluster(moved, pieceId = 0, snapThreshold = 0.2f)

        assertTrue(outcome.didSnap)
        assertTrue(0 in outcome.lockedPieceIds)
        assertTrue(0 in outcome.state.placedPieceIds)
        assertFalse(0 in outcome.state.boardPiecePositions)
    }

    @Test
    fun `snapPieceCluster links to neighboring movable piece when close enough`() {
        val initial = createSessionState(image, option, progress = null)
        val first = movePieceCluster(initial, 0, JigsawBoardPosition(x = 1.1f, y = 1.1f))
        val second = movePieceCluster(first, 1, JigsawBoardPosition(x = 2.06f, y = 1.08f))

        val outcome = snapPieceCluster(second, pieceId = 1, snapThreshold = 0.15f)

        assertTrue(outcome.didSnap)
        assertTrue(JigsawPieceLink(firstPieceId = 0, secondPieceId = 1) in outcome.state.pieceLinks)
        assertEquals(JigsawBoardPosition(x = 2.1f, y = 1.1f), outcome.state.boardPiecePositions.getValue(1))
        assertFalse(1 in outcome.lockedPieceIds)
    }

    @Test
    fun `returnPieceClusterToTray clears linked board pieces`() {
        val initial = createSessionState(image, option, progress = null)
        val piece0 = movePieceCluster(initial, 0, JigsawBoardPosition(x = 1.1f, y = 1.1f))
        val piece1 = movePieceCluster(piece0, 1, JigsawBoardPosition(x = 2.06f, y = 1.08f))
        val snapped = snapPieceCluster(piece1, pieceId = 1, snapThreshold = 0.15f).state

        val returned = returnPieceClusterToTray(snapped, pieceId = 0)

        assertFalse(0 in returned.boardPiecePositions)
        assertFalse(1 in returned.boardPiecePositions)
        assertTrue(returned.pieceLinks.isEmpty())
        assertTrue(0 in returned.remainingPieceIds)
        assertTrue(1 in returned.remainingPieceIds)
    }

    @Test
    fun `toProgress includes loose board positions and links`() {
        val state = JigsawSessionState(
            image = image,
            pieceCount = option,
            placedPieceIds = setOf(0),
            boardPiecePositions = mapOf(
                1 to JigsawBoardPosition(x = 1.25f, y = 0.5f),
                2 to JigsawBoardPosition(x = 2.25f, y = 0.5f),
            ),
            pieceLinks = setOf(JigsawPieceLink(firstPieceId = 1, secondPieceId = 2)),
            pieceOrder = (0 until option.totalPieces).toList(),
        )

        val progress = state.toProgress(updatedAtEpochMillis = 55L)

        assertEquals(setOf(0), progress.placedPieceIds)
        assertEquals(
            listOf(
                SavedBoardPiecePosition(pieceId = 1, x = 1.25f, y = 0.5f),
                SavedBoardPiecePosition(pieceId = 2, x = 2.25f, y = 0.5f),
            ),
            progress.boardPiecePositions,
        )
        assertEquals(setOf(SavedPieceLink(firstPieceId = 1, secondPieceId = 2)), progress.pieceLinks)
    }

    @Test
    fun `toProgress includes current piece order`() {
        val pieceOrder = listOf(2, 1, 0) + (3 until option.totalPieces)
        val state = JigsawSessionState(
            image = image,
            pieceCount = option,
            placedPieceIds = emptySet(),
            boardPiecePositions = emptyMap(),
            pieceLinks = emptySet(),
            pieceOrder = pieceOrder,
            elapsedPlayTimeMillis = 45_000L,
        )

        val progress = state.toProgress(updatedAtEpochMillis = 55L)

        assertEquals(pieceOrder, progress.pieceOrder)
        assertEquals(45_000L, progress.elapsedPlayTimeMillis)
    }

    @Test
    fun `createSessionState restores saved piece order when valid`() {
        val pieceOrder = listOf(2, 1, 0) + (3 until option.totalPieces)
        val progress = JigsawProgress(
            imageId = image.id,
            pieceCount = option.totalPieces,
            placedPieceIds = emptySet(),
            pieceOrder = pieceOrder,
            elapsedPlayTimeMillis = 77_000L,
            updatedAtEpochMillis = 1L,
        )

        val session = createSessionState(image, option, progress)

        assertEquals(pieceOrder, session.pieceOrder)
        assertEquals(77_000L, session.elapsedPlayTimeMillis)
    }

    @Test
    fun `reorderTrayPieces moves a tray piece to the requested remaining index`() {
        val initial = createSessionState(image, option, progress = null)
        val ordered = initial.copy(pieceOrder = (0 until option.totalPieces).toList())

        val reordered = reorderTrayPieces(
            state = ordered,
            movedPieceIds = setOf(1),
            targetIndex = 3,
        )

        assertEquals(listOf(0, 2, 3, 1), reordered.remainingPieceIds.take(4))
    }
}
