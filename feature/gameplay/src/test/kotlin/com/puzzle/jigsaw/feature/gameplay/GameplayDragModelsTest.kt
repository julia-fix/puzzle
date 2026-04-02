package com.puzzle.jigsaw.feature.gameplay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayDragModelsTest {

    @Test
    fun `board drag keeps board cell size when tray bounds are collapsed`() {
        val dragState = PieceDragState(
            pieceId = 0,
            clusterPieceIds = setOf(0),
            source = PieceDragSource.BOARD,
            initialPointer = Offset(120f, 120f),
            grabFractionX = 0.5f,
            grabFractionY = 0.5f,
            selectedPieceTrayCellWidth = 20.dp,
            selectedPieceTrayCellHeight = 20.dp,
            selectedPieceBoardCellWidth = 40.dp,
            selectedPieceBoardCellHeight = 40.dp,
            selectedPieceTrayPreviewSizePx = SizePx(width = 20f, height = 20f),
            selectedPieceBoardPreviewSizePx = SizePx(width = 40f, height = 40f),
            startPositions = mapOf(0 to Offset.Zero),
        )

        val collapsedTrayBounds = Rect(left = 0f, top = 260f, right = 200f, bottom = 260f)

        assertEquals(40.dp, dragState.currentCellWidth(collapsedTrayBounds))
        assertEquals(40.dp, dragState.currentCellHeight(collapsedTrayBounds))
    }

    @Test
    fun `activeSystemGestureExclusionBounds returns no bounds when no piece is dragging`() {
        val gameplayBounds = Rect(left = 0f, top = 0f, right = 240f, bottom = 480f)
        val boardBounds = Rect(left = 16f, top = 24f, right = 224f, bottom = 264f)
        val trayBounds = Rect(left = 8f, top = 300f, right = 232f, bottom = 380f)

        val bounds = activeSystemGestureExclusionBounds(
            isPuzzleCompleted = false,
            isPieceDragging = false,
            gameplayBounds = gameplayBounds,
            boardBounds = boardBounds,
            trayBounds = trayBounds,
        )

        assertEquals(listOf(boardBounds, trayBounds), bounds)
    }

    @Test
    fun `activeSystemGestureExclusionBounds returns full gameplay bounds while dragging`() {
        val gameplayBounds = Rect(left = 0f, top = 0f, right = 240f, bottom = 480f)
        val boardBounds = Rect(left = 16f, top = 24f, right = 224f, bottom = 264f)
        val trayBounds = Rect(left = 8f, top = 300f, right = 232f, bottom = 380f)

        val bounds = activeSystemGestureExclusionBounds(
            isPuzzleCompleted = false,
            isPieceDragging = true,
            gameplayBounds = gameplayBounds,
            boardBounds = boardBounds,
            trayBounds = trayBounds,
        )

        assertEquals(listOf(gameplayBounds), bounds)
    }

    @Test
    fun `activeSystemGestureExclusionBounds skips empty tray bounds during active play`() {
        val gameplayBounds = Rect(left = 0f, top = 0f, right = 240f, bottom = 480f)
        val boardBounds = Rect(left = 16f, top = 24f, right = 224f, bottom = 264f)
        val emptyTrayBounds = Rect(left = 8f, top = 300f, right = 232f, bottom = 300f)

        val bounds = activeSystemGestureExclusionBounds(
            isPuzzleCompleted = false,
            isPieceDragging = false,
            gameplayBounds = gameplayBounds,
            boardBounds = boardBounds,
            trayBounds = emptyTrayBounds,
        )

        assertEquals(listOf(boardBounds), bounds)
    }

    @Test
    fun `activeSystemGestureExclusionBounds returns no bounds after puzzle completion`() {
        val gameplayBounds = Rect(left = 0f, top = 0f, right = 240f, bottom = 480f)
        val boardBounds = Rect(left = 16f, top = 24f, right = 224f, bottom = 264f)
        val trayBounds = Rect(left = 8f, top = 300f, right = 232f, bottom = 380f)

        val bounds = activeSystemGestureExclusionBounds(
            isPuzzleCompleted = true,
            isPieceDragging = false,
            gameplayBounds = gameplayBounds,
            boardBounds = boardBounds,
            trayBounds = trayBounds,
        )

        assertEquals(emptyList<Rect>(), bounds)
    }
}
