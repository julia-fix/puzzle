package com.puzzle.jigsaw.feature.gameplay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout
import com.puzzle.jigsaw.domain.jigsaw.JigsawSessionState

internal sealed interface TrayEntry {
    val key: Any

    data class Piece(
        val pieceId: Int,
        val collapsed: Boolean,
    ) : TrayEntry {
        override val key: Any = "piece:$pieceId"
    }

    data class Placeholder(
        val width: Dp,
    ) : TrayEntry {
        override val key: Any = "placeholder"
    }
}

internal data class TrayLayout(
    val slotHeight: Dp,
    val slotWidths: Map<Int, Dp>,
    val offsets: Map<Int, DpOffset>,
)

private data class PieceExtensions(
    val left: Dp,
    val right: Dp,
    val top: Dp,
    val bottom: Dp,
)

internal data class TrayLayoutPx(
    val slotHeight: Float,
    val slotWidths: Map<Int, Float>,
    val offsets: Map<Int, Offset>,
)

internal data class TrayRows<T>(
    val top: List<T>,
    val bottom: List<T>,
)

internal data class TrayDropTarget(
    val rowIndex: Int,
    val indexInRow: Int,
)

internal fun stableTrayRowsForDrag(
    orderedPieceIds: List<Int>,
    dragState: PieceDragState?,
    rowCount: Int,
    topRowSize: Int,
): TrayRows<Int> {
    val baseRows = splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    if (dragState?.source != PieceDragSource.TRAY || dragState.clusterPieceIds.isEmpty()) {
        return baseRows
    }

    val top = baseRows.top.toMutableList()
    val bottom = baseRows.bottom.toMutableList()
    val sourceRow = if (dragState.pieceId in baseRows.bottom) bottom else top
    sourceRow.removeAll(dragState.clusterPieceIds)
    return TrayRows(top = top, bottom = bottom)
}

internal fun splitTrayPieceIds(
    pieceIds: List<Int>,
    rowCount: Int,
    topRowSize: Int = normalizedTrayTopRowSize(0, pieceIds.size, rowCount),
): TrayRows<Int> {
    if (rowCount <= 1) return TrayRows(top = pieceIds, bottom = emptyList())
    val resolvedTopRowSize = normalizedTrayTopRowSize(topRowSize, pieceIds.size, rowCount)
    return TrayRows(
        top = pieceIds.take(resolvedTopRowSize),
        bottom = pieceIds.drop(resolvedTopRowSize),
    )
}

internal fun createRenderedTrayRows(
    orderedPieceIds: List<Int>,
    dragState: PieceDragState?,
    rowCount: Int,
    topRowSize: Int,
    pendingTrayRemovalPieceIds: Set<Int>,
): TrayRows<TrayEntry> {
    val renderOrderedPieceIds = if (pendingTrayRemovalPieceIds.isEmpty()) {
        orderedPieceIds
    } else {
        removeFromTrayRows(
            orderedPieceIds = orderedPieceIds,
            removedPieceIds = pendingTrayRemovalPieceIds,
            rowCount = rowCount,
            topRowSize = topRowSize,
        ).let { rows -> rows.top + rows.bottom }
    }
    val baseRows = splitTrayPieceIds(renderOrderedPieceIds, rowCount, topRowSize)
    val movedPieceIds = dragState?.clusterPieceIds.orEmpty()
    val collapsedPieceIds =
        if (dragState?.source == PieceDragSource.TRAY) movedPieceIds else emptySet()
    val top: MutableList<TrayEntry> = baseRows.top.mapTo(mutableListOf()) { pieceId ->
        TrayEntry.Piece(
            pieceId = pieceId,
            collapsed = pieceId in collapsedPieceIds,
        )
    }
    val bottom: MutableList<TrayEntry> = baseRows.bottom.mapTo(mutableListOf()) { pieceId ->
        TrayEntry.Piece(
            pieceId = pieceId,
            collapsed = pieceId in collapsedPieceIds,
        )
    }

    return TrayRows(top = top, bottom = bottom)
}

internal fun normalizedTrayTopRowSize(
    topRowSizeHint: Int,
    totalCount: Int,
    rowCount: Int,
): Int {
    if (rowCount <= 1) return totalCount
    val minTopRowSize = totalCount / 2
    val maxTopRowSize = (totalCount + 1) / 2
    return topRowSizeHint.coerceIn(minTopRowSize, maxTopRowSize)
}

private fun rebalanceTrayPieceRows(
    top: MutableList<Int>,
    bottom: MutableList<Int>,
) {
    while (top.size > bottom.size + 1) {
        bottom.add(top.removeAt(top.lastIndex))
    }
    while (bottom.size > top.size + 1) {
        top.add(bottom.removeAt(bottom.lastIndex))
    }
}

internal fun reorderTrayRows(
    orderedPieceIds: List<Int>,
    dragState: PieceDragState,
    dropTarget: TrayDropTarget,
    rowCount: Int,
    topRowSize: Int,
): TrayRows<Int> {
    val movedPieceIds = dragState.clusterPieceIds
    val movedBlock = orderedPieceIds.filter { it in movedPieceIds }
    val baseRows = splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    val top = baseRows.top.filterNot { it in movedPieceIds }.toMutableList()
    val bottom = baseRows.bottom.filterNot { it in movedPieceIds }.toMutableList()
    val targetRow = if (dropTarget.rowIndex == 0) top else bottom
    targetRow.addAll(dropTarget.indexInRow.coerceIn(0, targetRow.size), movedBlock)
    rebalanceTrayPieceRows(top, bottom)
    return TrayRows(top = top, bottom = bottom)
}

internal fun insertIntoTrayRows(
    currentTop: List<Int>,
    currentBottom: List<Int>,
    movedPieceIds: List<Int>,
    dropTarget: TrayDropTarget,
): TrayRows<Int> {
    val top = currentTop.toMutableList()
    val bottom = currentBottom.toMutableList()
    val targetRow = if (dropTarget.rowIndex == 0) top else bottom
    targetRow.addAll(dropTarget.indexInRow.coerceIn(0, targetRow.size), movedPieceIds)
    rebalanceTrayPieceRows(top, bottom)
    return TrayRows(top = top, bottom = bottom)
}

internal fun visibleTrayRowsFromBounds(
    orderedPieceIds: List<Int>,
    trayItemBounds: Map<Int, Rect>,
    trayRowBounds: Rect?,
    rowCount: Int,
    topRowSize: Int,
    traySlotHeightPx: Float,
    trayRowSpacingPx: Float,
): TrayRows<Int> {
    if (rowCount <= 1 || trayRowBounds == null) {
        return splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    }

    val visibleBounds = orderedPieceIds.mapNotNull { pieceId ->
        trayItemBounds[pieceId]?.let { bounds -> pieceId to bounds }
    }
    if (visibleBounds.size != orderedPieceIds.size) {
        return splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    }

    val rowDividerY = trayRowBounds.top + traySlotHeightPx + (trayRowSpacingPx / 2f)
    val top = visibleBounds
        .filter { (_, bounds) -> bounds.center.y < rowDividerY }
        .sortedBy { (_, bounds) -> bounds.center.x }
        .map { it.first }
    val bottom = visibleBounds
        .filter { (_, bounds) -> bounds.center.y >= rowDividerY }
        .sortedBy { (_, bounds) -> bounds.center.x }
        .map { it.first }

    return if (top.size + bottom.size == orderedPieceIds.size) {
        TrayRows(top = top, bottom = bottom)
    } else {
        splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    }
}

internal fun removeFromTrayRows(
    orderedPieceIds: List<Int>,
    removedPieceIds: Set<Int>,
    rowCount: Int,
    topRowSize: Int,
): TrayRows<Int> {
    val baseRows = splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    val top = baseRows.top.filterNot { it in removedPieceIds }.toMutableList()
    val bottom = baseRows.bottom.filterNot { it in removedPieceIds }.toMutableList()
    rebalanceTrayPieceRows(top, bottom)
    return TrayRows(top = top, bottom = bottom)
}

internal fun applyRemainingPieceOrder(
    state: JigsawSessionState,
    reorderedRemaining: List<Int>,
): JigsawSessionState {
    val remainingSet = state.remainingPieceIds.toSet()
    val hiddenPieceIdsInOrder = state.pieceOrder.filterNot { it in remainingSet }
    return state.copy(
        pieceOrder = reorderedRemaining + hiddenPieceIdsInOrder,
    )
}

internal fun calculateTrayContentWidth(
    orderedPieceIds: List<Int>,
    traySlotWidths: Map<Int, Dp>,
    rowCount: Int,
    topRowSize: Int,
): Dp {
    val rows = splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    return listOf(rows.top, rows.bottom).maxOfOrNull { rowPieceIds ->
        if (rowPieceIds.isEmpty()) {
            0.0
        } else {
            rowPieceIds.sumOf { pieceId -> traySlotWidths.getValue(pieceId).value.toDouble() } +
                (TrayItemSpacing.value.toDouble() * (rowPieceIds.size - 1).coerceAtLeast(0))
        }
    }?.dp ?: 0.dp
}

internal fun calculateTrayDropTarget(
    dragState: PieceDragState?,
    trayRowBounds: Rect?,
    orderedPieceIds: List<Int>,
    trayItemBounds: Map<Int, Rect>,
    rowCount: Int,
    topRowSize: Int,
    traySlotHeightPx: Float,
    trayRowSpacingPx: Float,
): TrayDropTarget? {
    if (dragState == null || !isDragOverTray(dragState, trayRowBounds)) {
        return null
    }
    val resolvedTrayRowBounds = trayRowBounds ?: return null
    val rows = stableTrayRowsForDrag(
        orderedPieceIds = orderedPieceIds,
        dragState = dragState,
        rowCount = rowCount,
        topRowSize = topRowSize,
    )
    if (rows.top.isEmpty() && rows.bottom.isEmpty()) return TrayDropTarget(rowIndex = 0, indexInRow = 0)

    val draggedCenter = dragState.currentSelectedPieceCenter(resolvedTrayRowBounds)
    val rowDividerY = if (rowCount <= 1 || rows.bottom.isEmpty()) {
        resolvedTrayRowBounds.center.y
    } else {
        resolvedTrayRowBounds.top + traySlotHeightPx + (trayRowSpacingPx / 2f)
    }
    val targetRowIndex = if (rowCount <= 1 || draggedCenter.y < rowDividerY) 0 else 1
    val targetRowPieceIds = if (targetRowIndex == 0) rows.top else rows.bottom
    targetRowPieceIds.forEachIndexed { rowIndex, pieceId ->
        val bounds = trayItemBounds[pieceId] ?: return@forEachIndexed
        if (draggedCenter.x < bounds.center.x) {
            return TrayDropTarget(rowIndex = targetRowIndex, indexInRow = rowIndex)
        }
    }
    return TrayDropTarget(rowIndex = targetRowIndex, indexInRow = targetRowPieceIds.size)
}

internal fun isDragOverTray(
    dragState: PieceDragState,
    trayRowBounds: Rect?,
): Boolean {
    val bounds = trayRowBounds ?: return false
    return when (dragState.source) {
        PieceDragSource.TRAY -> bounds.contains(dragState.currentPointer)
        PieceDragSource.BOARD -> bounds.contains(dragState.currentSelectedPieceCenter(trayRowBounds))
    }
}

internal fun createTrayLayout(
    pieces: List<JigsawPieceLayout>,
    cellWidth: Dp,
    cellHeight: Dp,
): TrayLayout {
    val extensionsByPiece = pieces.associate { piece ->
        piece.pieceId to PieceExtensions(
            left = cellWidth * piece.left.extensionRatio,
            right = cellWidth * piece.right.extensionRatio,
            top = cellHeight * piece.top.extensionRatio,
            bottom = cellHeight * piece.bottom.extensionRatio,
        )
    }
    val maxLeft = extensionsByPiece.values.maxOfOrNull(PieceExtensions::left) ?: 0.dp
    val maxRight = extensionsByPiece.values.maxOfOrNull(PieceExtensions::right) ?: 0.dp
    val maxTop = extensionsByPiece.values.maxOfOrNull(PieceExtensions::top) ?: 0.dp
    val maxBottom = extensionsByPiece.values.maxOfOrNull(PieceExtensions::bottom) ?: 0.dp
    val slotHeight = cellHeight + maxTop + maxBottom
    val slotWidths = extensionsByPiece.mapValues { (_, ext) ->
        val leftSlack = (maxLeft - ext.left) * TrayWidthSlackFactor
        val rightSlack = (maxRight - ext.right) * TrayWidthSlackFactor
        cellWidth + ext.left + ext.right + leftSlack + rightSlack
    }
    val offsets = extensionsByPiece.mapValues { (_, ext) ->
        val leftSlack = (maxLeft - ext.left) * TrayWidthSlackFactor
        DpOffset(
            x = leftSlack,
            y = maxTop - ext.top,
        )
    }
    return TrayLayout(
        slotHeight = slotHeight,
        slotWidths = slotWidths,
        offsets = offsets,
    )
}
