package com.puzzle.jigsaw.feature.gameplay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.puzzle.jigsaw.core.model.PieceCountOption
import com.puzzle.jigsaw.domain.jigsaw.JigsawEdge
import com.puzzle.jigsaw.domain.jigsaw.JigsawEdgeKind
import com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
internal fun JigsawBoard(
    pieceCount: PieceCountOption,
    pieces: List<JigsawPieceLayout>,
    placedPieceIds: Set<Int>,
    assetBitmap: ImageBitmap?,
    boardBackgroundColor: Color? = null,
    emptyFillColor: Color? = null,
    boardOutlineColor: Color? = null,
    modifier: Modifier = Modifier,
) {
    val palette = rememberJigsawPalette()
    val resolvedBoardBackground = boardBackgroundColor ?: palette.boardBackground
    val resolvedEmptyFill = emptyFillColor ?: palette.emptyFill
    val resolvedBoardOutline = boardOutlineColor ?: palette.boardOutline

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val strokeWidth = size.minDimension * 0.0075f
                val reliefOffset = strokeWidth * 0.55f
                val boardPaths = createBoardPaths(
                    pieces = pieces,
                    rows = pieceCount.rows,
                    columns = pieceCount.columns,
                    boardLeft = 0f,
                    boardTop = 0f,
                    boardWidth = size.width,
                    boardHeight = size.height,
                )
                val dstSize = IntSize(
                    width = size.width.roundToInt(),
                    height = size.height.roundToInt(),
                )

                onDrawBehind {
                    drawRect(color = resolvedBoardBackground)
                    pieces.forEach { piece ->
                        val path = boardPaths.piecePaths.getValue(piece.pieceId)
                        val isPlaced = piece.pieceId in placedPieceIds
                        if (isPlaced) {
                            drawPieceShadow(
                                path = path,
                                color = palette.pieceShadow,
                                offset = reliefOffset,
                            )
                            if (assetBitmap != null) {
                                clipPath(path) {
                                    drawImage(
                                        image = assetBitmap,
                                        dstSize = dstSize,
                                    )
                                }
                            } else {
                                drawPath(
                                    path = path,
                                    color = palette.placedFallbackFill,
                                    style = Fill,
                                )
                            }
                            drawPieceRelief(
                                path = path,
                                strokeWidth = strokeWidth,
                                shade = palette.pieceShade,
                                offset = reliefOffset,
                            )
                        } else {
                            drawPath(
                                path = path,
                                color = resolvedEmptyFill,
                                style = Fill,
                            )
                        }
                    }
                    drawPath(
                        path = boardPaths.outlinePath,
                        color = resolvedBoardOutline,
                        style = Stroke(width = strokeWidth),
                    )
                }
            },
    )
}

@Composable
internal fun JigsawLoosePiecePreview(
    pieceCount: PieceCountOption,
    piece: JigsawPieceLayout,
    assetBitmap: ImageBitmap?,
    boardCellWidth: Dp,
    boardCellHeight: Dp,
    debugHitboxStrokeColor: Color? = null,
    debugHitboxStrokeWidthPx: Float = 0f,
    debugHitboxPaddingPx: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val palette = rememberJigsawPalette()

    Box(
        modifier = modifier.drawWithCache {
            val cellWidth = boardCellWidth.toPx()
            val cellHeight = boardCellHeight.toPx()
            val bounds = calculateLoosePieceBounds(
                piece = piece,
                cellWidth = cellWidth,
                cellHeight = cellHeight,
            )
            val boardLeft = bounds.leftExtension - (piece.column.toFloat() * cellWidth)
            val boardTop = bounds.topExtension - (piece.row.toFloat() * cellHeight)
            val path = createPiecePath(
                piece = piece,
                rows = pieceCount.rows,
                columns = pieceCount.columns,
                boardLeft = boardLeft,
                boardTop = boardTop,
                boardWidth = cellWidth * pieceCount.columns,
                boardHeight = cellHeight * pieceCount.rows,
            )
            val boardMinDimension = min(
                cellWidth * pieceCount.columns,
                cellHeight * pieceCount.rows,
            )
            val strokeWidth = boardMinDimension * 0.0075f
            val reliefOffset = strokeWidth * 0.55f
            val dstOffset = IntOffset(boardLeft.roundToInt(), boardTop.roundToInt())
            val dstSize = IntSize(
                width = (cellWidth * pieceCount.columns).roundToInt(),
                height = (cellHeight * pieceCount.rows).roundToInt(),
            )

            onDrawBehind {
                drawPieceShadow(
                    path = path,
                    color = palette.pieceShadow,
                    offset = reliefOffset,
                )
                drawPath(
                    path = path,
                    color = palette.emptyFill,
                    style = Fill,
                )
                if (assetBitmap != null) {
                    clipPath(path) {
                        drawImage(
                            image = assetBitmap,
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                        )
                    }
                }
                drawPieceRelief(
                    path = path,
                    strokeWidth = strokeWidth,
                    shade = palette.pieceShade,
                    offset = reliefOffset,
                )
                if (debugHitboxStrokeColor != null && debugHitboxStrokeWidthPx > 0f) {
                    drawRect(
                        color = debugHitboxStrokeColor.copy(alpha = 0.55f),
                        topLeft = Offset(-debugHitboxPaddingPx, -debugHitboxPaddingPx),
                        size = Size(
                            width = size.width + (debugHitboxPaddingPx * 2f),
                            height = size.height + (debugHitboxPaddingPx * 2f),
                        ),
                        style = Stroke(width = debugHitboxStrokeWidthPx),
                    )
                    drawRect(
                        color = debugHitboxStrokeColor,
                        topLeft = Offset(-debugHitboxPaddingPx, -debugHitboxPaddingPx),
                        size = Size(
                            width = size.width + (debugHitboxPaddingPx * 2f),
                            height = size.height + (debugHitboxPaddingPx * 2f),
                        ),
                        style = Stroke(width = 1.25f),
                    )
                }
            }
        },
    )
}

internal fun calculateLoosePieceSize(
    piece: JigsawPieceLayout,
    boardCellWidth: Dp,
    boardCellHeight: Dp,
): DpSize {
    val bounds = calculateLoosePieceBounds(
        piece = piece,
        cellWidth = boardCellWidth.value,
        cellHeight = boardCellHeight.value,
    )
    return DpSize(width = bounds.width.dp, height = bounds.height.dp)
}

@Composable
private fun rememberJigsawPalette(): JigsawPalette = JigsawPalette(
    boardBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
    emptyFill = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
    boardOutline = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
    placedFallbackFill = MaterialTheme.colorScheme.primaryContainer,
    pieceShade = Color.Black.copy(alpha = 0.18f),
    pieceShadow = Color.Black.copy(alpha = 0.11f),
)

private data class JigsawPalette(
    val boardBackground: Color,
    val emptyFill: Color,
    val boardOutline: Color,
    val placedFallbackFill: Color,
    val pieceShade: Color,
    val pieceShadow: Color,
)

internal data class LoosePieceBounds(
    val width: Float,
    val height: Float,
    val leftExtension: Float,
    val topExtension: Float,
)

private data class JigsawBoardPaths(
    val piecePaths: Map<Int, Path>,
    val outlinePath: Path,
)

internal fun calculateLoosePieceBounds(
    piece: JigsawPieceLayout,
    cellWidth: Float,
    cellHeight: Float,
): LoosePieceBounds {
    val leftExtension = cellWidth * piece.left.extensionRatio
    val rightExtension = cellWidth * piece.right.extensionRatio
    val topExtension = cellHeight * piece.top.extensionRatio
    val bottomExtension = cellHeight * piece.bottom.extensionRatio
    return LoosePieceBounds(
        width = cellWidth + leftExtension + rightExtension,
        height = cellHeight + topExtension + bottomExtension,
        leftExtension = leftExtension,
        topExtension = topExtension,
    )
}

internal fun createLoosePieceLocalPath(
    piece: JigsawPieceLayout,
    pieceCount: PieceCountOption,
    cellWidth: Float,
    cellHeight: Float,
): Path {
    val bounds = calculateLoosePieceBounds(
        piece = piece,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
    )
    val boardLeft = bounds.leftExtension - (piece.column.toFloat() * cellWidth)
    val boardTop = bounds.topExtension - (piece.row.toFloat() * cellHeight)
    return createPiecePath(
        piece = piece,
        rows = pieceCount.rows,
        columns = pieceCount.columns,
        boardLeft = boardLeft,
        boardTop = boardTop,
        boardWidth = cellWidth * pieceCount.columns,
        boardHeight = cellHeight * pieceCount.rows,
    )
}

private fun createBoardPaths(
    pieces: List<JigsawPieceLayout>,
    rows: Int,
    columns: Int,
    boardLeft: Float,
    boardTop: Float,
    boardWidth: Float,
    boardHeight: Float,
): JigsawBoardPaths {
    val cellWidth = boardWidth / columns.toFloat()
    val cellHeight = boardHeight / rows.toFloat()
    val piecesByPosition = pieces.associateBy { it.row to it.column }
    val piecePaths = pieces.associate { piece ->
        piece.pieceId to createPiecePath(
            piece = piece,
            rows = rows,
            columns = columns,
            boardLeft = boardLeft,
            boardTop = boardTop,
            boardWidth = boardWidth,
            boardHeight = boardHeight,
        )
    }
    val outlinePath = Path().apply {
        moveTo(boardLeft, boardTop)
        lineTo(boardLeft + boardWidth, boardTop)
        lineTo(boardLeft + boardWidth, boardTop + boardHeight)
        lineTo(boardLeft, boardTop + boardHeight)
        close()
    }

    return JigsawBoardPaths(
        piecePaths = piecePaths,
        outlinePath = outlinePath,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPieceShadow(
    path: Path,
    color: Color,
    offset: Float,
) {
    translate(left = offset * 0.9f, top = offset * 1.15f) {
        drawPath(
            path = path,
            color = color.copy(alpha = color.alpha * 0.82f),
            style = Fill,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPieceRelief(
    path: Path,
    strokeWidth: Float,
    shade: Color,
    offset: Float,
) {
    val bounds = path.getBounds()
    val shadeBrush = Brush.linearGradient(
        colors = listOf(
            shade.copy(alpha = 0f),
            shade.copy(alpha = shade.alpha * 0.55f),
            shade.copy(alpha = shade.alpha),
        ),
        start = bounds.topLeft,
        end = bounds.bottomRight,
    )

    drawPath(
        path = path,
        brush = shadeBrush,
        style = Stroke(width = strokeWidth * 0.68f),
    )
    translate(left = offset * 0.22f, top = offset * 0.34f) {
        drawPath(
            path = path,
            brush = shadeBrush,
            style = Stroke(width = strokeWidth * 0.36f),
        )
    }
}

private fun createPiecePath(
    piece: JigsawPieceLayout,
    rows: Int,
    columns: Int,
    boardLeft: Float,
    boardTop: Float,
    boardWidth: Float,
    boardHeight: Float,
): Path {
    val cellWidth = boardWidth / columns.toFloat()
    val cellHeight = boardHeight / rows.toFloat()
    val left = boardLeft + (piece.column * cellWidth)
    val top = boardTop + (piece.row * cellHeight)
    val right = left + cellWidth
    val bottom = top + cellHeight

    return Path().apply {
        moveTo(left, top)
        appendHorizontalEdge(
            startX = left,
            endX = right,
            baselineY = top,
            edge = piece.top,
            outwardSign = -1f,
            unit = cellHeight,
        )
        appendVerticalEdge(
            baselineX = right,
            startY = top,
            endY = bottom,
            edge = piece.right,
            outwardSign = 1f,
            unit = cellWidth,
        )
        appendHorizontalEdge(
            startX = right,
            endX = left,
            baselineY = bottom,
            edge = piece.bottom,
            outwardSign = 1f,
            unit = cellHeight,
        )
        appendVerticalEdge(
            baselineX = left,
            startY = bottom,
            endY = top,
            edge = piece.left,
            outwardSign = -1f,
            unit = cellWidth,
        )
        close()
    }
}

private fun Path.appendHorizontalEdge(
    startX: Float,
    endX: Float,
    baselineY: Float,
    edge: JigsawEdge,
    outwardSign: Float,
    unit: Float,
) {
    if (edge.kind == JigsawEdgeKind.FLAT) {
        lineTo(endX, baselineY)
        return
    }

    val dir = if (endX >= startX) 1f else -1f
    val length = abs(endX - startX)
    val profile = edge.profile.orientedForTraversal(isReversed = dir < 0f)
    val bumpSign = edge.kind.bumpSign(outwardSign)
    val tab = profile.tabSizeRatio

    val p1x = startX + (dir * length * 0.2f)
    val p1y = baselineY + (bumpSign * unit * profile.startJitterRatio)
    val p2x = startX + (dir * length * (0.5f + profile.centerOffsetRatio + profile.neckOffsetRatio))
    val p2y = baselineY + (bumpSign * unit * (-tab + profile.normalOffsetRatio))
    val p3x = startX + (dir * length * (0.5f - tab + profile.centerOffsetRatio))
    val p3y = baselineY + (bumpSign * unit * (tab + profile.normalOffsetRatio))
    val p4x = startX + (dir * length * (0.5f - (2f * tab) + profile.centerOffsetRatio - profile.neckOffsetRatio))
    val p4y = baselineY + (bumpSign * unit * ((3f * tab) + profile.normalOffsetRatio))
    val p5x = startX + (dir * length * (0.5f + (2f * tab) + profile.centerOffsetRatio - profile.neckOffsetRatio))
    val p5y = p4y
    val p6x = startX + (dir * length * (0.5f + tab + profile.centerOffsetRatio))
    val p6y = p3y
    val p7x = p2x
    val p7y = p2y
    val p8x = startX + (dir * length * 0.8f)
    val p8y = baselineY + (bumpSign * unit * profile.endJitterRatio)

    cubicTo(
        x1 = p1x,
        y1 = p1y,
        x2 = p2x,
        y2 = p2y,
        x3 = p3x,
        y3 = p3y,
    )
    cubicTo(
        x1 = p4x,
        y1 = p4y,
        x2 = p5x,
        y2 = p5y,
        x3 = p6x,
        y3 = p6y,
    )
    cubicTo(
        x1 = p7x,
        y1 = p7y,
        x2 = p8x,
        y2 = p8y,
        x3 = endX,
        y3 = baselineY,
    )
}

private fun Path.appendVerticalEdge(
    baselineX: Float,
    startY: Float,
    endY: Float,
    edge: JigsawEdge,
    outwardSign: Float,
    unit: Float,
) {
    if (edge.kind == JigsawEdgeKind.FLAT) {
        lineTo(baselineX, endY)
        return
    }

    val dir = if (endY >= startY) 1f else -1f
    val length = abs(endY - startY)
    val profile = edge.profile.orientedForTraversal(isReversed = dir < 0f)
    val bumpSign = edge.kind.bumpSign(outwardSign)
    val tab = profile.tabSizeRatio

    val p1x = baselineX + (bumpSign * unit * profile.startJitterRatio)
    val p1y = startY + (dir * length * 0.2f)
    val p2x = baselineX + (bumpSign * unit * (-tab + profile.normalOffsetRatio))
    val p2y = startY + (dir * length * (0.5f + profile.centerOffsetRatio + profile.neckOffsetRatio))
    val p3x = baselineX + (bumpSign * unit * (tab + profile.normalOffsetRatio))
    val p3y = startY + (dir * length * (0.5f - tab + profile.centerOffsetRatio))
    val p4x = baselineX + (bumpSign * unit * ((3f * tab) + profile.normalOffsetRatio))
    val p4y = startY + (dir * length * (0.5f - (2f * tab) + profile.centerOffsetRatio - profile.neckOffsetRatio))
    val p5x = p4x
    val p5y = startY + (dir * length * (0.5f + (2f * tab) + profile.centerOffsetRatio - profile.neckOffsetRatio))
    val p6x = p3x
    val p6y = startY + (dir * length * (0.5f + tab + profile.centerOffsetRatio))
    val p7x = p2x
    val p7y = p2y
    val p8x = baselineX + (bumpSign * unit * profile.endJitterRatio)
    val p8y = startY + (dir * length * 0.8f)

    cubicTo(
        x1 = p1x,
        y1 = p1y,
        x2 = p2x,
        y2 = p2y,
        x3 = p3x,
        y3 = p3y,
    )
    cubicTo(
        x1 = p4x,
        y1 = p4y,
        x2 = p5x,
        y2 = p5y,
        x3 = p6x,
        y3 = p6y,
    )
    cubicTo(
        x1 = p7x,
        y1 = p7y,
        x2 = p8x,
        y2 = p8y,
        x3 = baselineX,
        y3 = endY,
    )
}

private fun JigsawEdgeKind.bumpSign(outwardSign: Float): Float = when (this) {
    JigsawEdgeKind.FLAT -> 0f
    JigsawEdgeKind.TAB -> outwardSign
    JigsawEdgeKind.BLANK -> -outwardSign
}

private fun com.puzzle.jigsaw.domain.jigsaw.JigsawEdgeProfile.orientedForTraversal(
    isReversed: Boolean,
): com.puzzle.jigsaw.domain.jigsaw.JigsawEdgeProfile {
    if (!isReversed) return this
    return copy(
        startJitterRatio = endJitterRatio,
        centerOffsetRatio = -centerOffsetRatio,
        neckOffsetRatio = -neckOffsetRatio,
        endJitterRatio = startJitterRatio,
    )
}
