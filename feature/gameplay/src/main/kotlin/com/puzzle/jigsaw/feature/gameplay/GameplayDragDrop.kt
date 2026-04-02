package com.puzzle.jigsaw.feature.gameplay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.puzzle.jigsaw.domain.jigsaw.JigsawBoardPosition
import com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout
import com.puzzle.jigsaw.domain.jigsaw.JigsawSessionState
import com.puzzle.jigsaw.domain.jigsaw.movePieceCluster
import com.puzzle.jigsaw.domain.jigsaw.returnPieceClusterToTray
import com.puzzle.jigsaw.domain.jigsaw.snapPieceCluster

internal sealed interface GameplayDragFinishResult {
    data object Cancelled : GameplayDragFinishResult

    data class Committed(
        val state: JigsawSessionState,
        val trayTopRowSize: Int? = null,
        val shouldPlayPlacementFeedback: Boolean = false,
    ) : GameplayDragFinishResult
}

internal fun resolveGameplayDragFinish(
    dragState: PieceDragState,
    sessionState: JigsawSessionState,
    boardContentRect: Rect,
    boardCellWidthPx: Float,
    boardCellHeightPx: Float,
    trayRowBounds: Rect?,
    trayItemBounds: Map<Int, Rect>,
    trayLayoutPx: TrayLayoutPx,
    trayRowCount: Int,
    trayTopRowSize: Int,
    trayRowSpacingPx: Float,
    piecesById: Map<Int, JigsawPieceLayout>,
): GameplayDragFinishResult {
    val piece = piecesById.getValue(dragState.pieceId)
    val boardAlignedTopLeft = dragState.currentBoardAlignedTopLeft(trayRowBounds)
    val boardAlignedCenter = dragState.currentBoardAlignedCenter(trayRowBounds)

    if (isDragOverTray(dragState, trayRowBounds)) {
        if (dragState.source == PieceDragSource.BOARD && dragState.clusterPieceIds.size > 1) {
            return GameplayDragFinishResult.Cancelled
        }
        val trayState = if (dragState.source == PieceDragSource.BOARD) {
            returnPieceClusterToTray(sessionState, dragState.pieceId)
        } else {
            sessionState
        }
        val resolvedDropTarget = calculateTrayDropTarget(
            dragState = dragState,
            trayRowBounds = trayRowBounds,
            orderedPieceIds = if (dragState.source == PieceDragSource.BOARD) {
                sessionState.remainingPieceIds
            } else {
                trayState.remainingPieceIds
            },
            trayItemBounds = trayItemBounds,
            rowCount = trayRowCount,
            topRowSize = trayTopRowSize,
            traySlotHeightPx = trayLayoutPx.slotHeight,
            trayRowSpacingPx = trayRowSpacingPx,
        ) ?: TrayDropTarget(
            rowIndex = 0,
            indexInRow = trayState.remainingPieceIds.size,
        )
        val reorderedRows = if (dragState.source == PieceDragSource.BOARD) {
            val currentRows = visibleTrayRowsFromBounds(
                orderedPieceIds = sessionState.remainingPieceIds,
                trayItemBounds = trayItemBounds,
                trayRowBounds = trayRowBounds,
                rowCount = trayRowCount,
                topRowSize = trayTopRowSize,
                traySlotHeightPx = trayLayoutPx.slotHeight,
                trayRowSpacingPx = trayRowSpacingPx,
            )
            insertIntoTrayRows(
                currentTop = currentRows.top,
                currentBottom = currentRows.bottom,
                movedPieceIds = sessionState.pieceOrder.filter { it in dragState.clusterPieceIds },
                dropTarget = resolvedDropTarget,
                rowCount = trayRowCount,
            )
        } else {
            reorderTrayRows(
                orderedPieceIds = trayState.remainingPieceIds,
                dragState = dragState,
                dropTarget = resolvedDropTarget,
                rowCount = trayRowCount,
                topRowSize = trayTopRowSize,
            )
        }
        val reorderedState = applyRemainingPieceOrder(trayState, reorderedRows.top + reorderedRows.bottom)
        return GameplayDragFinishResult.Committed(
            state = reorderedState,
            trayTopRowSize = reorderedRows.top.size,
        )
    }

    if (!boardContentRect.contains(boardAlignedCenter)) {
        return GameplayDragFinishResult.Cancelled
    }

    val movedState = movePieceCluster(
        state = sessionState,
        pieceId = dragState.pieceId,
        targetPosition = topLeftToBoardPosition(
            piece = piece,
            topLeft = boardAlignedTopLeft,
            boardCellWidthPx = boardCellWidthPx,
            boardCellHeightPx = boardCellHeightPx,
            boardContentRect = boardContentRect,
        ),
    )
    val trayAwareMove = if (dragState.source == PieceDragSource.TRAY) {
        val removedRows = removeFromTrayRows(
            orderedPieceIds = sessionState.remainingPieceIds,
            removedPieceIds = dragState.clusterPieceIds,
            rowCount = trayRowCount,
            topRowSize = trayTopRowSize,
        )
        val updatedState = applyRemainingPieceOrder(
            state = movedState,
            reorderedRemaining = removedRows.top + removedRows.bottom,
        )
        TrayAwareMove(state = updatedState, trayTopRowSize = removedRows.top.size)
    } else {
        TrayAwareMove(state = movedState, trayTopRowSize = null)
    }
    val snapOutcome = snapPieceCluster(
        state = trayAwareMove.state,
        pieceId = dragState.pieceId,
        snapThreshold = BoardSnapThreshold,
    )
    if (!snapOutcome.didSnap) {
        return GameplayDragFinishResult.Committed(
            state = trayAwareMove.state,
            trayTopRowSize = trayAwareMove.trayTopRowSize,
        )
    }
    return GameplayDragFinishResult.Committed(
        state = snapOutcome.state,
        trayTopRowSize = trayAwareMove.trayTopRowSize,
        shouldPlayPlacementFeedback = true,
    )
}

private data class TrayAwareMove(
    val state: JigsawSessionState,
    val trayTopRowSize: Int?,
)

private fun topLeftToBoardPosition(
    piece: JigsawPieceLayout,
    topLeft: Offset,
    boardCellWidthPx: Float,
    boardCellHeightPx: Float,
    boardContentRect: Rect,
): JigsawBoardPosition = JigsawBoardPosition(
    x = (topLeft.x + (boardCellWidthPx * piece.left.extensionRatio) - boardContentRect.left) / boardCellWidthPx,
    y = (topLeft.y + (boardCellHeightPx * piece.top.extensionRatio) - boardContentRect.top) / boardCellHeightPx,
)
