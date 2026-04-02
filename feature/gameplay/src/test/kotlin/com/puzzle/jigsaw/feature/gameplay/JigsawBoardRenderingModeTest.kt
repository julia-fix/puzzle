package com.puzzle.jigsaw.feature.gameplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JigsawBoardRenderingModeTest {

    @Test
    fun `renders completed board seamlessly when every piece is placed and bitmap is available`() {
        assertTrue(
            shouldRenderCompletedBoardSeamlessly(
                totalPieceCount = 24,
                placedPieceCount = 24,
                hasBitmap = true,
            ),
        )
    }

    @Test
    fun `does not render seamlessly while puzzle is still in progress`() {
        assertFalse(
            shouldRenderCompletedBoardSeamlessly(
                totalPieceCount = 24,
                placedPieceCount = 23,
                hasBitmap = true,
            ),
        )
    }

    @Test
    fun `does not render seamlessly without a bitmap`() {
        assertFalse(
            shouldRenderCompletedBoardSeamlessly(
                totalPieceCount = 24,
                placedPieceCount = 24,
                hasBitmap = false,
            ),
        )
    }
}
