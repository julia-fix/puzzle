package com.puzzle.jigsaw.domain.jigsaw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JigsawPieceLayoutTest {
    @Test
    fun `createJigsawPieceLayout keeps border edges flat`() {
        val layout = createJigsawPieceLayout(rows = 4, columns = 6)

        layout.forEach { piece ->
            if (piece.row == 0) assertEquals(JigsawEdgeKind.FLAT, piece.top.kind)
            if (piece.row == 3) assertEquals(JigsawEdgeKind.FLAT, piece.bottom.kind)
            if (piece.column == 0) assertEquals(JigsawEdgeKind.FLAT, piece.left.kind)
            if (piece.column == 5) assertEquals(JigsawEdgeKind.FLAT, piece.right.kind)
        }
    }

    @Test
    fun `createJigsawPieceLayout makes adjacent edges complementary`() {
        val rows = 4
        val columns = 6
        val layoutById = createJigsawPieceLayout(rows = rows, columns = columns)
            .associateBy(JigsawPieceLayout::pieceId)

        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val pieceId = row * columns + column
                val piece = layoutById.getValue(pieceId)

                if (column < columns - 1) {
                    val rightNeighbor = layoutById.getValue(pieceId + 1)
                    assertEquals(piece.right.kind, rightNeighbor.left.kind.opposite())
                    assertEquals(piece.right.profile, rightNeighbor.left.profile)
                }
                if (row < rows - 1) {
                    val bottomNeighbor = layoutById.getValue(pieceId + columns)
                    assertEquals(piece.bottom.kind, bottomNeighbor.top.kind.opposite())
                    assertEquals(piece.bottom.profile, bottomNeighbor.top.profile)
                }
            }
        }
    }

    @Test
    fun `createJigsawPieceLayout is deterministic`() {
        val first = createJigsawPieceLayout(rows = 6, columns = 8)
        val second = createJigsawPieceLayout(rows = 6, columns = 8)

        assertEquals(first, second)
        assertTrue(first.any { it.right.kind == JigsawEdgeKind.TAB || it.bottom.kind == JigsawEdgeKind.TAB })
        assertTrue(first.any { it.right.kind == JigsawEdgeKind.BLANK || it.bottom.kind == JigsawEdgeKind.BLANK })
        assertTrue(first.any { it.right.profile != JigsawEdgeProfile.Flat || it.bottom.profile != JigsawEdgeProfile.Flat })
    }

    @Test
    fun `createJigsawPieceLayout keeps generated profiles inside tuned ranges`() {
        val layout = createJigsawPieceLayout(rows = 5, columns = 4)
        val generatedProfiles = layout
            .flatMap { listOf(it.top, it.right, it.bottom, it.left) }
            .filter { it.kind != JigsawEdgeKind.FLAT }
            .map(JigsawEdge::profile)

        assertTrue(generatedProfiles.isNotEmpty())
        generatedProfiles.forEach { profile ->
            assertEquals(0.10f, profile.tabSizeRatio)
            assertTrue(profile.startJitterRatio in -0.04f..0.04f)
            assertTrue(profile.centerOffsetRatio in -0.04f..0.04f)
            assertTrue(profile.normalOffsetRatio in -0.04f..0.04f)
            assertTrue(profile.neckOffsetRatio in -0.04f..0.04f)
            assertTrue(profile.endJitterRatio in -0.04f..0.04f)
        }
    }
}

private fun JigsawEdgeKind.opposite(): JigsawEdgeKind = when (this) {
    JigsawEdgeKind.FLAT -> JigsawEdgeKind.FLAT
    JigsawEdgeKind.TAB -> JigsawEdgeKind.BLANK
    JigsawEdgeKind.BLANK -> JigsawEdgeKind.TAB
}
