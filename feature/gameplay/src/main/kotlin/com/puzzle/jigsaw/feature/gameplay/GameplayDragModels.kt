package com.puzzle.jigsaw.feature.gameplay

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp

@Stable
internal class PieceDragState(
    val pieceId: Int,
    val clusterPieceIds: Set<Int>,
    val source: PieceDragSource,
    initialPointer: Offset,
    val grabFractionX: Float,
    val grabFractionY: Float,
    val selectedPieceTrayCellWidth: Dp,
    val selectedPieceTrayCellHeight: Dp,
    val selectedPieceBoardCellWidth: Dp,
    val selectedPieceBoardCellHeight: Dp,
    val selectedPieceTrayPreviewSizePx: SizePx,
    val selectedPieceBoardPreviewSizePx: SizePx,
    val startPositions: Map<Int, Offset>,
) {
    var currentPointer by mutableStateOf(initialPointer)
        private set

    fun dragBy(dragAmount: Offset) {
        if (dragAmount != Offset.Zero) {
            currentPointer += dragAmount
        }
    }

    fun currentCellWidth(trayRowBounds: Rect?): Dp =
        lerp(selectedPieceTrayCellWidth, selectedPieceBoardCellWidth, previewProgress(trayRowBounds))

    fun currentCellHeight(trayRowBounds: Rect?): Dp =
        lerp(selectedPieceTrayCellHeight, selectedPieceBoardCellHeight, previewProgress(trayRowBounds))

    fun currentSelectedPieceTopLeft(trayRowBounds: Rect?): Offset {
        val currentSize = currentSelectedPieceSizePx(trayRowBounds)
        val liftOffsetY = if (source == PieceDragSource.TRAY) {
            currentLiftOffsetY(trayRowBounds)
        } else {
            0f
        }
        return Offset(
            x = currentPointer.x - (currentSize.width * grabFractionX),
            y = currentPointer.y - (currentSize.height * grabFractionY) - liftOffsetY,
        )
    }

    fun currentSelectedPieceCenter(trayRowBounds: Rect?): Offset {
        val currentSize = currentSelectedPieceSizePx(trayRowBounds)
        val topLeft = currentSelectedPieceTopLeft(trayRowBounds)
        return Offset(
            x = topLeft.x + (currentSize.width / 2f),
            y = topLeft.y + (currentSize.height / 2f),
        )
    }

    fun currentBoardAlignedTopLeft(trayRowBounds: Rect?): Offset = Offset(
        x = currentPointer.x - (selectedPieceBoardPreviewSizePx.width * grabFractionX),
        y = currentPointer.y - (selectedPieceBoardPreviewSizePx.height * grabFractionY) - currentLiftOffsetY(trayRowBounds),
    )

    fun currentBoardAlignedCenter(trayRowBounds: Rect?): Offset {
        val topLeft = currentBoardAlignedTopLeft(trayRowBounds)
        return Offset(
            x = topLeft.x + (selectedPieceBoardPreviewSizePx.width / 2f),
            y = topLeft.y + (selectedPieceBoardPreviewSizePx.height / 2f),
        )
    }

    fun currentClusterTopLefts(trayRowBounds: Rect?): Map<Int, Offset> {
        val selectedTopLeft = currentSelectedPieceTopLeft(trayRowBounds)
        val selectedStartTopLeft = startPositions.getValue(pieceId)
        val delta = selectedTopLeft - selectedStartTopLeft
        return clusterPieceIds.associateWith { clusterPieceId ->
            startPositions.getValue(clusterPieceId) + delta
        }
    }

    private fun currentSelectedPieceSizePx(trayRowBounds: Rect?): SizePx {
        val progress = previewProgress(trayRowBounds)
        return SizePx(
            width = lerp(selectedPieceTrayPreviewSizePx.width, selectedPieceBoardPreviewSizePx.width, progress),
            height = lerp(selectedPieceTrayPreviewSizePx.height, selectedPieceBoardPreviewSizePx.height, progress),
        )
    }

    private fun currentLiftOffsetY(trayRowBounds: Rect?): Float =
        TrayPieceLiftOffsetPx * liftProgress(trayRowBounds)

    private fun expansionProgress(trayRowBounds: Rect?): Float {
        if (source != PieceDragSource.TRAY || !hasUsableTrayBounds(trayRowBounds)) return 1f
        val resolvedTrayRowBounds = trayRowBounds!!
        val travel = (resolvedTrayRowBounds.height * TrayPieceResizeTravelFraction).coerceAtLeast(1f)
        return ((resolvedTrayRowBounds.top - currentPointer.y) / travel).coerceIn(0f, 1f)
    }

    private fun contractionProgress(trayRowBounds: Rect?): Float {
        if (source != PieceDragSource.BOARD || !hasUsableTrayBounds(trayRowBounds)) return 1f
        val resolvedTrayRowBounds = trayRowBounds!!
        val travel = (resolvedTrayRowBounds.height * TrayPieceResizeTravelFraction).coerceAtLeast(1f)
        return ((resolvedTrayRowBounds.top - currentPointer.y) / travel).coerceIn(0f, 1f)
    }

    private fun liftProgress(trayRowBounds: Rect?): Float {
        if (source != PieceDragSource.TRAY || !hasUsableTrayBounds(trayRowBounds)) return 0f
        val resolvedTrayRowBounds = trayRowBounds!!
        val travel = (resolvedTrayRowBounds.height * 0.65f).coerceAtLeast(1f)
        return ((resolvedTrayRowBounds.top - currentPointer.y) / travel).coerceIn(0f, 1f)
    }

    private fun previewProgress(trayRowBounds: Rect?): Float {
        if (source == PieceDragSource.BOARD && clusterPieceIds.size > 1) {
            return 1f
        }
        return when (source) {
            PieceDragSource.TRAY -> expansionProgress(trayRowBounds)
            PieceDragSource.BOARD -> contractionProgress(trayRowBounds)
        }
    }
}

private fun hasUsableTrayBounds(trayRowBounds: Rect?): Boolean =
    trayRowBounds != null && trayRowBounds.height > 0f

internal enum class PieceDragSource {
    TRAY,
    BOARD,
}

internal data class SizePx(
    val width: Float,
    val height: Float,
)

internal data class TrayScrollSnapshot(
    val scrollOffset: Int,
)

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + ((end - start) * progress)

private fun lerp(start: Dp, end: Dp, progress: Float): Dp = start + ((end - start) * progress)
