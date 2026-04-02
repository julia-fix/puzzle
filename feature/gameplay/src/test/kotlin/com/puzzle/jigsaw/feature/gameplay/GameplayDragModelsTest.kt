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
}
