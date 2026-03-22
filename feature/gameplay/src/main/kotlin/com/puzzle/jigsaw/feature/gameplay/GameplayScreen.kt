package com.puzzle.jigsaw.feature.gameplay

import android.graphics.Rect as AndroidRect
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.puzzle.jigsaw.core.designsystem.components.PuzzleImageAspectRatio
import com.puzzle.jigsaw.core.designsystem.components.rememberPuzzleAssetBitmap
import com.puzzle.jigsaw.core.model.PieceCountOption
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.domain.jigsaw.JigsawBoardPosition
import com.puzzle.jigsaw.domain.jigsaw.JigsawProgressStore
import com.puzzle.jigsaw.domain.jigsaw.JigsawSessionState
import com.puzzle.jigsaw.domain.jigsaw.connectedBoardPieceIds
import com.puzzle.jigsaw.domain.jigsaw.createJigsawPieceLayout
import com.puzzle.jigsaw.domain.jigsaw.createSessionState
import com.puzzle.jigsaw.domain.jigsaw.movePieceCluster
import com.puzzle.jigsaw.domain.jigsaw.returnPieceClusterToTray
import com.puzzle.jigsaw.domain.jigsaw.snapPieceCluster
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun GameplayRoute(
    image: PuzzleImage,
    pieceCount: PieceCountOption,
    progressStore: JigsawProgressStore,
    onBack: () -> Unit,
) {
    var sessionState by remember(image.id, pieceCount.totalPieces) {
        mutableStateOf<JigsawSessionState?>(null)
    }
    val scope = rememberCoroutineScope()

    fun commitSession(updated: JigsawSessionState) {
        sessionState = updated
        scope.launch {
            progressStore.saveProgress(updated.toProgress(System.currentTimeMillis()))
        }
    }

    LaunchedEffect(image.id, pieceCount.totalPieces, progressStore) {
        val progress = progressStore.loadProgress(image.id, pieceCount.totalPieces)
        sessionState = createSessionState(image, pieceCount, progress)
    }

    if (sessionState == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    GameplayScreen(
        sessionState = sessionState!!,
        onBack = onBack,
        onSessionStateChange = ::commitSession,
        onResetProgress = {
            val reset = createSessionState(image, pieceCount, progress = null)
            sessionState = reset
            scope.launch {
                progressStore.clearProgress(image.id, pieceCount.totalPieces)
            }
        },
    )
}

@Composable
fun GameplayScreen(
    sessionState: JigsawSessionState,
    onBack: () -> Unit,
    onSessionStateChange: (JigsawSessionState) -> Unit,
    onResetProgress: () -> Unit,
) {
    val pieceLayout = remember(sessionState.pieceCount.rows, sessionState.pieceCount.columns) {
        createJigsawPieceLayout(
            rows = sessionState.pieceCount.rows,
            columns = sessionState.pieceCount.columns,
        )
    }
    val piecesById = remember(pieceLayout) {
        pieceLayout.associateBy { it.pieceId }
    }
    val assetBitmap = rememberPuzzleAssetBitmap(sessionState.image.assetPath)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val view = LocalView.current

    var highlightedPieceIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var dragState by remember { mutableStateOf<PieceDragState?>(null) }
    var snapAnimationState by remember { mutableStateOf<PieceSnapAnimationState?>(null) }
    var snapAnimationToken by remember { mutableIntStateOf(0) }
    val snapProgress = remember { Animatable(1f) }
    val trayItemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var trayRowBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var gestureExclusionRect by remember { mutableStateOf<AndroidRect?>(null) }

    DisposableEffect(view, gestureExclusionRect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            view.systemGestureExclusionRects = gestureExclusionRect?.let(::listOf).orEmpty()
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                view.systemGestureExclusionRects = emptyList()
            }
        }
    }

    fun flashPieces(pieceIds: Set<Int>) {
        if (pieceIds.isEmpty()) return
        highlightedPieceIds = highlightedPieceIds + pieceIds
        scope.launch {
            delay(420)
            highlightedPieceIds = highlightedPieceIds - pieceIds
        }
    }

    LaunchedEffect(snapAnimationToken) {
        val animation = snapAnimationState ?: return@LaunchedEffect
        snapProgress.snapTo(0f)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        snapProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SnapAnimationDurationMillis),
        )
        flashPieces(animation.highlightPieceIds)
        snapAnimationState = null
    }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top,
                    ) {
                        IconButton(onClick = onBack) {
                            Text("←")
                        }
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(sessionState.image.title)
                            Text(
                                text = "${sessionState.pieceCount.title} • ${(sessionState.completionRatio * 100).roundToInt()}% complete",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = onResetProgress) {
                        Text("Reset")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { sessionState.completionRatio },
                modifier = Modifier.fillMaxWidth(),
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        gestureExclusionRect = coordinates.boundsInRoot().toAndroidRect()
                    },
            ) {
                val boardCardWidth = maxWidth
                val boardCardHeight = boardCardWidth / PuzzleImageAspectRatio
                val boardContentWidth = boardCardWidth - (BoardInnerPadding * 2)
                val boardContentHeight = boardCardHeight - (BoardInnerPadding * 2)
                val boardCellWidth = boardContentWidth / sessionState.pieceCount.columns
                val boardCellHeight = boardContentHeight / sessionState.pieceCount.rows
                val trayReferenceCellWidth = boardContentWidth / TrayReferenceColumns
                val trayReferenceCellHeight = boardContentHeight / TrayReferenceRows
                val boardCellWidthPx = with(density) { boardCellWidth.toPx() }
                val boardCellHeightPx = with(density) { boardCellHeight.toPx() }
                val boardInnerPaddingPx = with(density) { BoardInnerPadding.toPx() }
                val boardContentRect = Rect(
                    left = boardInnerPaddingPx,
                    top = boardInnerPaddingPx,
                    right = boardInnerPaddingPx + with(density) { boardContentWidth.toPx() },
                    bottom = boardInnerPaddingPx + with(density) { boardContentHeight.toPx() },
                )

                val trayLayout = remember(pieceLayout, trayReferenceCellWidth, trayReferenceCellHeight) {
                    createTrayLayout(
                        pieces = pieceLayout,
                        cellWidth = trayReferenceCellWidth,
                        cellHeight = trayReferenceCellHeight,
                    )
                }
                val remainingPieceCount = sessionState.remainingPieceIds.size
                val trayContentWidth = if (remainingPieceCount == 0) {
                    0.dp
                } else {
                    (trayLayout.slotSize.width * remainingPieceCount.toFloat()) +
                        (TrayItemSpacing * (remainingPieceCount - 1).toFloat())
                }
                val trayHorizontalPadding = if (remainingPieceCount == 0) {
                    2.dp
                } else {
                    val extraSpace = maxWidth - trayContentWidth
                    if (extraSpace > 4.dp) extraSpace / 2f else 2.dp
                }

                val draggedPieceId = dragState?.pieceId
                val animatingPlacedPieceIds = snapAnimationState
                    ?.pieceIds
                    ?.intersect(sessionState.placedPieceIds)
                    .orEmpty()
                val hiddenPieceIds = ((dragState?.clusterPieceIds ?: emptySet()) - listOfNotNull(draggedPieceId).toSet()) +
                    (snapAnimationState?.pieceIds ?: emptySet())

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { overlayOriginInRoot = it.positionInRoot() },
                ) {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(PuzzleImageAspectRatio),
                    ) {
                        JigsawBoard(
                            pieceCount = sessionState.pieceCount,
                            pieces = pieceLayout,
                            placedPieceIds = sessionState.placedPieceIds - animatingPlacedPieceIds,
                            assetBitmap = assetBitmap,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(BoardInnerPadding),
                        )
                    }

                    Column(
                        modifier = Modifier.padding(top = boardCardHeight + 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Loose pieces",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${sessionState.remainingPieceIds.size} left",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LazyRow(
                            modifier = Modifier.onGloballyPositioned { coordinates ->
                                trayRowBounds = coordinates
                                    .boundsInRoot()
                                    .translatedBy(Offset(-overlayOriginInRoot.x, -overlayOriginInRoot.y))
                            },
                            contentPadding = PaddingValues(horizontal = trayHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(TrayItemSpacing),
                        ) {
                            items(sessionState.remainingPieceIds, key = { it }) { pieceId ->
                                val piece = piecesById.getValue(pieceId)
                                val isHidden = pieceId in hiddenPieceIds
                                TrayPieceCard(
                                    pieceId = pieceId,
                                    pieceCount = sessionState.pieceCount,
                                    piece = piece,
                                    assetBitmap = assetBitmap,
                                    boardCellWidth = trayReferenceCellWidth,
                                    boardCellHeight = trayReferenceCellHeight,
                                    slotSize = trayLayout.slotSize,
                                    previewOffset = trayLayout.offsets.getValue(pieceId),
                                    overlayOriginInRoot = overlayOriginInRoot,
                                    ghosted = pieceId == draggedPieceId,
                                    highlight = if (pieceId in highlightedPieceIds) 1f else 0f,
                                    visible = !isHidden,
                                    onPositioned = { bounds ->
                                        trayItemBounds[pieceId] = bounds
                                    },
                                    onDragStarted = { touchOffset ->
                                        val trayBounds = trayItemBounds[pieceId] ?: return@TrayPieceCard
                                        val trayPreviewSizePx = piecePreviewSizePx(
                                            piece = piece,
                                            boardCellWidth = trayReferenceCellWidth,
                                            boardCellHeight = trayReferenceCellHeight,
                                            density = density,
                                        )
                                        val boardPreviewSizePx = piecePreviewSizePx(
                                            piece = piece,
                                            boardCellWidth = boardCellWidth,
                                            boardCellHeight = boardCellHeight,
                                            density = density,
                                        )
                                        dragState = PieceDragState(
                                            pieceId = pieceId,
                                            clusterPieceIds = setOf(pieceId),
                                            source = PieceDragSource.TRAY,
                                            currentPointer = trayBounds.topLeft + touchOffset,
                                            grabFractionX = touchOffset.x / trayBounds.width,
                                            grabFractionY = touchOffset.y / trayBounds.height,
                                            selectedPieceTrayCellWidth = trayReferenceCellWidth,
                                            selectedPieceTrayCellHeight = trayReferenceCellHeight,
                                            selectedPieceBoardCellWidth = boardCellWidth,
                                            selectedPieceBoardCellHeight = boardCellHeight,
                                            selectedPieceTrayPreviewSizePx = trayPreviewSizePx,
                                            selectedPieceBoardPreviewSizePx = boardPreviewSizePx,
                                            startPositions = mapOf(pieceId to trayBounds.topLeft),
                                        )
                                    },
                                    onDragged = { dragAmount ->
                                        dragState = dragState
                                            ?.takeIf { it.pieceId == pieceId }
                                            ?.let { state ->
                                                state.copy(currentPointer = state.currentPointer + dragAmount)
                                            }
                                    },
                                    onDragEnded = {
                                        val currentDrag = dragState?.takeIf { it.pieceId == pieceId } ?: return@TrayPieceCard
                                        handleDragFinished(
                                            dragState = currentDrag,
                                            sessionState = sessionState,
                                            boardContentRect = boardContentRect,
                                            boardCellWidthPx = boardCellWidthPx,
                                            boardCellHeightPx = boardCellHeightPx,
                                            trayRowBounds = trayRowBounds,
                                            piecesById = piecesById,
                                            onCommit = onSessionStateChange,
                                            onStartSnapAnimation = { animation ->
                                                snapAnimationState = animation
                                                snapAnimationToken += 1
                                            },
                                            onCancel = { dragState = null },
                                        )
                                        if (dragState == currentDrag) dragState = null
                                    },
                                )
                            }
                        }
                    }

                    sessionState.boardPiecePositions.forEach { (pieceId, boardPosition) ->
                        if (pieceId in hiddenPieceIds) return@forEach

                        val piece = piecesById.getValue(pieceId)
                        val topLeft = boardPieceTopLeft(
                            piece = piece,
                            boardPosition = boardPosition,
                            boardCellWidthPx = boardCellWidthPx,
                            boardCellHeightPx = boardCellHeightPx,
                            boardContentRect = boardContentRect,
                        )
                        FloatingBoardPiece(
                            pieceId = pieceId,
                            pieceCount = sessionState.pieceCount,
                            piece = piece,
                            assetBitmap = assetBitmap,
                            boardCellWidth = boardCellWidth,
                            boardCellHeight = boardCellHeight,
                            topLeft = topLeft,
                            ghosted = pieceId == draggedPieceId,
                            highlight = if (pieceId in highlightedPieceIds) 1f else 0f,
                            onDragStarted = { touchOffset ->
                                val cluster = connectedBoardPieceIds(sessionState, pieceId)
                                val clusterStartPositions = cluster.associateWith { clusterPieceId ->
                                    boardPieceTopLeft(
                                        piece = piecesById.getValue(clusterPieceId),
                                        boardPosition = sessionState.boardPiecePositions.getValue(clusterPieceId),
                                        boardCellWidthPx = boardCellWidthPx,
                                        boardCellHeightPx = boardCellHeightPx,
                                        boardContentRect = boardContentRect,
                                    )
                                }
                                val previewSizePx = piecePreviewSizePx(
                                    piece = piece,
                                    boardCellWidth = boardCellWidth,
                                    boardCellHeight = boardCellHeight,
                                    density = density,
                                )
                                dragState = PieceDragState(
                                    pieceId = pieceId,
                                    clusterPieceIds = cluster,
                                    source = PieceDragSource.BOARD,
                                    currentPointer = topLeft + touchOffset,
                                    grabFractionX = touchOffset.x / previewSizePx.width,
                                    grabFractionY = touchOffset.y / previewSizePx.height,
                                    selectedPieceTrayCellWidth = boardCellWidth,
                                    selectedPieceTrayCellHeight = boardCellHeight,
                                    selectedPieceBoardCellWidth = boardCellWidth,
                                    selectedPieceBoardCellHeight = boardCellHeight,
                                    selectedPieceTrayPreviewSizePx = previewSizePx,
                                    selectedPieceBoardPreviewSizePx = previewSizePx,
                                    startPositions = clusterStartPositions,
                                )
                            },
                            onDragged = { dragAmount ->
                                dragState = dragState
                                    ?.takeIf { it.pieceId == pieceId }
                                    ?.let { state ->
                                        state.copy(currentPointer = state.currentPointer + dragAmount)
                                    }
                            },
                            onDragEnded = {
                                val currentDrag = dragState?.takeIf { it.pieceId == pieceId } ?: return@FloatingBoardPiece
                                handleDragFinished(
                                    dragState = currentDrag,
                                    sessionState = sessionState,
                                    boardContentRect = boardContentRect,
                                    boardCellWidthPx = boardCellWidthPx,
                                    boardCellHeightPx = boardCellHeightPx,
                                    trayRowBounds = trayRowBounds,
                                    piecesById = piecesById,
                                    onCommit = onSessionStateChange,
                                    onStartSnapAnimation = { animation ->
                                        snapAnimationState = animation
                                        snapAnimationToken += 1
                                    },
                                    onCancel = { dragState = null },
                                )
                                if (dragState == currentDrag) dragState = null
                            },
                        )
                    }

                    dragState?.let { currentDrag ->
                        FloatingDragCluster(
                            dragState = currentDrag,
                            piecesById = piecesById,
                            pieceCount = sessionState.pieceCount,
                            assetBitmap = assetBitmap,
                            trayRowBounds = trayRowBounds,
                        )
                    }

                    snapAnimationState?.let { animation ->
                        val progress = snapProgress.value
                        animation.pieceIds.forEach { pieceId ->
                            val piece = piecesById.getValue(pieceId)
                            val startTopLeft = animation.startPositions.getValue(pieceId)
                            val endTopLeft = animation.endPositions.getValue(pieceId)
                            val currentTopLeft = lerp(startTopLeft, endTopLeft, progress)
                            FloatingBoardPiece(
                                pieceId = pieceId,
                                pieceCount = sessionState.pieceCount,
                                piece = piece,
                                assetBitmap = assetBitmap,
                                boardCellWidth = boardCellWidth,
                                boardCellHeight = boardCellHeight,
                                topLeft = currentTopLeft,
                                ghosted = false,
                                highlight = 1f - (progress * 0.25f),
                                onDragStarted = {},
                                onDragged = {},
                                onDragEnded = {},
                                enabled = false,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun handleDragFinished(
    dragState: PieceDragState,
    sessionState: JigsawSessionState,
    boardContentRect: Rect,
    boardCellWidthPx: Float,
    boardCellHeightPx: Float,
    trayRowBounds: Rect?,
    piecesById: Map<Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
    onCommit: (JigsawSessionState) -> Unit,
    onStartSnapAnimation: (PieceSnapAnimationState) -> Unit,
    onCancel: () -> Unit,
) {
    val piece = piecesById.getValue(dragState.pieceId)
    val currentTopLeft = dragState.currentSelectedPieceTopLeft(trayRowBounds)
    val currentCenter = dragState.currentSelectedPieceCenter(trayRowBounds)
    val boardAlignedTopLeft = dragState.currentBoardAlignedTopLeft()
    val boardAlignedCenter = dragState.currentBoardAlignedCenter()

    if (trayRowBounds?.contains(currentCenter) == true && dragState.source == PieceDragSource.BOARD) {
        onCommit(returnPieceClusterToTray(sessionState, dragState.pieceId))
        return
    }

    if (!boardContentRect.contains(boardAlignedCenter)) {
        onCancel()
        return
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
    val snapOutcome = snapPieceCluster(
        state = movedState,
        pieceId = dragState.pieceId,
        snapThreshold = BoardSnapThreshold,
    )
    if (!snapOutcome.didSnap) {
        onCommit(movedState)
        return
    }

    val animationPieceIds = dragState.clusterPieceIds
    val animationEndPositions = animationPieceIds.associateWith { clusterPieceId ->
        val targetPiece = piecesById.getValue(clusterPieceId)
        if (clusterPieceId in snapOutcome.state.boardPiecePositions) {
            boardPieceTopLeft(
                piece = targetPiece,
                boardPosition = snapOutcome.state.boardPiecePositions.getValue(clusterPieceId),
                boardCellWidthPx = boardCellWidthPx,
                boardCellHeightPx = boardCellHeightPx,
                boardContentRect = boardContentRect,
            )
        } else {
            boardPieceTopLeft(
                piece = targetPiece,
                boardPosition = JigsawBoardPosition(
                    x = (clusterPieceId % sessionState.pieceCount.columns).toFloat(),
                    y = (clusterPieceId / sessionState.pieceCount.columns).toFloat(),
                ),
                boardCellWidthPx = boardCellWidthPx,
                boardCellHeightPx = boardCellHeightPx,
                boardContentRect = boardContentRect,
            )
        }
    }
    onStartSnapAnimation(
        PieceSnapAnimationState(
            pieceIds = animationPieceIds,
            startPositions = dragState.currentClusterTopLefts(trayRowBounds),
            endPositions = animationEndPositions,
            targetState = snapOutcome.state,
            highlightPieceIds = snapOutcome.lockedPieceIds.ifEmpty { snapOutcome.snappedPieceIds },
        ),
    )
    onCommit(snapOutcome.state)
}

@Composable
private fun TrayPieceCard(
    pieceId: Int,
    pieceCount: PieceCountOption,
    piece: com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout,
    assetBitmap: ImageBitmap?,
    boardCellWidth: Dp,
    boardCellHeight: Dp,
    slotSize: DpSize,
    previewOffset: DpOffset,
    overlayOriginInRoot: Offset,
    ghosted: Boolean,
    highlight: Float,
    visible: Boolean,
    onPositioned: (Rect) -> Unit,
    onDragStarted: (Offset) -> Unit,
    onDragged: (Offset) -> Unit,
    onDragEnded: () -> Unit,
) {
    val previewSize = remember(piece, boardCellWidth, boardCellHeight) {
        calculateLoosePieceSize(
            piece = piece,
            boardCellWidth = boardCellWidth,
            boardCellHeight = boardCellHeight,
        )
    }
    val highlightProgress by animateFloatAsState(
        targetValue = highlight,
        animationSpec = tween(durationMillis = HighlightDurationMillis),
        label = "trayPieceHighlight",
    )

    Box(
        modifier = Modifier
            .size(width = slotSize.width, height = slotSize.height)
    ) {
        if (visible) {
            Box(
                modifier = Modifier
                    .size(width = previewSize.width, height = previewSize.height)
                    .align(Alignment.TopStart)
                    .offset(x = previewOffset.x, y = previewOffset.y),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (ghosted) 0f else 1f)
                        .onGloballyPositioned { coordinates ->
                            onPositioned(
                                coordinates
                                    .boundsInRoot()
                                    .translatedBy(Offset(-overlayOriginInRoot.x, -overlayOriginInRoot.y)),
                            )
                        }
                        .pointerInput(pieceId) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var accumulatedDrag = Offset.Zero
                                var pieceDragStarted = false

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                    if (!change.pressed) {
                                        if (pieceDragStarted) {
                                            onDragEnded()
                                        }
                                        break
                                    }

                                    val dragAmount = change.position - change.previousPosition
                                    accumulatedDrag += dragAmount

                                    if (!pieceDragStarted) {
                                        val dragDistance = hypot(
                                            accumulatedDrag.x.toDouble(),
                                            accumulatedDrag.y.toDouble(),
                                        ).toFloat()
                                        if (dragDistance < viewConfiguration.touchSlop) {
                                            continue
                                        }

                                        val absX = abs(accumulatedDrag.x)
                                        val absY = abs(accumulatedDrag.y)
                                        val shouldStartPieceDrag =
                                            accumulatedDrag.y < 0f &&
                                                absY >= (absX * TrayPieceDragVerticalBias)

                                        if (!shouldStartPieceDrag) {
                                            break
                                        }

                                        pieceDragStarted = true
                                        onDragStarted(down.position)
                                        change.consume()
                                        if (accumulatedDrag != Offset.Zero) {
                                            onDragged(accumulatedDrag)
                                        }
                                        continue
                                    }

                                    if (dragAmount != Offset.Zero) {
                                        change.consume()
                                        onDragged(dragAmount)
                                    }
                                }
                            }
                        },
                ) {
                    JigsawLoosePiecePreview(
                        pieceCount = pieceCount,
                        piece = piece,
                        assetBitmap = assetBitmap,
                        boardCellWidth = boardCellWidth,
                        boardCellHeight = boardCellHeight,
                        highlightProgress = highlightProgress,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = "#${pieceId + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun FloatingBoardPiece(
    pieceId: Int,
    pieceCount: PieceCountOption,
    piece: com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout,
    assetBitmap: ImageBitmap?,
    boardCellWidth: Dp,
    boardCellHeight: Dp,
    topLeft: Offset,
    ghosted: Boolean,
    highlight: Float,
    onDragStarted: (Offset) -> Unit,
    onDragged: (Offset) -> Unit,
    onDragEnded: () -> Unit,
    enabled: Boolean = true,
) {
    val previewSize = remember(piece, boardCellWidth, boardCellHeight) {
        calculateLoosePieceSize(
            piece = piece,
            boardCellWidth = boardCellWidth,
            boardCellHeight = boardCellHeight,
        )
    }
    val highlightProgress by animateFloatAsState(
        targetValue = highlight,
        animationSpec = tween(durationMillis = HighlightDurationMillis),
        label = "boardPieceHighlight",
    )

    Box(
        modifier = Modifier
            .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
            .size(width = previewSize.width, height = previewSize.height)
            .alpha(if (ghosted) 0f else 1f)
            .pointerInput(pieceId, enabled, topLeft) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = onDragStarted,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragged(dragAmount)
                    },
                    onDragEnd = onDragEnded,
                    onDragCancel = onDragEnded,
                )
            },
    ) {
        JigsawLoosePiecePreview(
            pieceCount = pieceCount,
            piece = piece,
            assetBitmap = assetBitmap,
            boardCellWidth = boardCellWidth,
            boardCellHeight = boardCellHeight,
            highlightProgress = highlightProgress,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FloatingDragCluster(
    dragState: PieceDragState,
    piecesById: Map<Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
    pieceCount: PieceCountOption,
    assetBitmap: ImageBitmap?,
    trayRowBounds: Rect?,
) {
    val selectedPiece = piecesById.getValue(dragState.pieceId)
    val selectedCellWidth = dragState.currentCellWidth(trayRowBounds)
    val selectedCellHeight = dragState.currentCellHeight(trayRowBounds)
    val selectedPreviewSize = calculateLoosePieceSize(
        piece = selectedPiece,
        boardCellWidth = selectedCellWidth,
        boardCellHeight = selectedCellHeight,
    )
    val selectedTopLeft = dragState.currentSelectedPieceTopLeft(trayRowBounds)
    val selectedStartTopLeft = dragState.startPositions.getValue(dragState.pieceId)
    val delta = selectedTopLeft - selectedStartTopLeft

    dragState.clusterPieceIds.forEach { clusterPieceId ->
        val piece = piecesById.getValue(clusterPieceId)
        val startTopLeft = dragState.startPositions.getValue(clusterPieceId)
        val previewTopLeft = if (clusterPieceId == dragState.pieceId || dragState.source == PieceDragSource.BOARD) {
            startTopLeft + delta
        } else {
            selectedTopLeft
        }
        val previewCellWidth = if (dragState.source == PieceDragSource.TRAY && clusterPieceId == dragState.pieceId) {
            selectedCellWidth
        } else {
            dragState.selectedPieceBoardCellWidth
        }
        val previewCellHeight = if (dragState.source == PieceDragSource.TRAY && clusterPieceId == dragState.pieceId) {
            selectedCellHeight
        } else {
            dragState.selectedPieceBoardCellHeight
        }
        val previewSize = if (clusterPieceId == dragState.pieceId) {
            selectedPreviewSize
        } else {
            calculateLoosePieceSize(
                piece = piece,
                boardCellWidth = previewCellWidth,
                boardCellHeight = previewCellHeight,
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(previewTopLeft.x.roundToInt(), previewTopLeft.y.roundToInt()) }
                .size(width = previewSize.width, height = previewSize.height),
        ) {
            JigsawLoosePiecePreview(
                pieceCount = pieceCount,
                piece = piece,
                assetBitmap = assetBitmap,
                boardCellWidth = previewCellWidth,
                boardCellHeight = previewCellHeight,
                highlightProgress = 0.35f,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private data class PieceDragState(
    val pieceId: Int,
    val clusterPieceIds: Set<Int>,
    val source: PieceDragSource,
    val currentPointer: Offset,
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
    fun currentCellWidth(trayRowBounds: Rect?): Dp = if (source == PieceDragSource.TRAY) {
        lerp(selectedPieceTrayCellWidth, selectedPieceBoardCellWidth, expansionProgress(trayRowBounds))
    } else {
        selectedPieceBoardCellWidth
    }

    fun currentCellHeight(trayRowBounds: Rect?): Dp = if (source == PieceDragSource.TRAY) {
        lerp(selectedPieceTrayCellHeight, selectedPieceBoardCellHeight, expansionProgress(trayRowBounds))
    } else {
        selectedPieceBoardCellHeight
    }

    fun currentSelectedPieceTopLeft(trayRowBounds: Rect?): Offset {
        val currentSize = currentSelectedPieceSizePx(trayRowBounds)
        return Offset(
            x = currentPointer.x - (currentSize.width * grabFractionX),
            y = currentPointer.y - (currentSize.height * grabFractionY),
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

    fun currentBoardAlignedTopLeft(): Offset = Offset(
        x = currentPointer.x - (selectedPieceBoardPreviewSizePx.width * grabFractionX),
        y = currentPointer.y - (selectedPieceBoardPreviewSizePx.height * grabFractionY),
    )

    fun currentBoardAlignedCenter(): Offset {
        val topLeft = currentBoardAlignedTopLeft()
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
            if (source == PieceDragSource.TRAY || clusterPieceId == pieceId) {
                startPositions.getValue(clusterPieceId) + delta
            } else {
                startPositions.getValue(clusterPieceId) + delta
            }
        }
    }

    private fun currentSelectedPieceSizePx(trayRowBounds: Rect?): SizePx {
        val progress = expansionProgress(trayRowBounds)
        return SizePx(
            width = lerp(selectedPieceTrayPreviewSizePx.width, selectedPieceBoardPreviewSizePx.width, progress),
            height = lerp(selectedPieceTrayPreviewSizePx.height, selectedPieceBoardPreviewSizePx.height, progress),
        )
    }

    private fun expansionProgress(trayRowBounds: Rect?): Float {
        if (source != PieceDragSource.TRAY || trayRowBounds == null) return 1f
        val travel = (trayRowBounds.height * 0.85f).coerceAtLeast(1f)
        return ((trayRowBounds.top - currentPointer.y) / travel).coerceIn(0f, 1f)
    }
}

private enum class PieceDragSource {
    TRAY,
    BOARD,
}

private data class PieceSnapAnimationState(
    val pieceIds: Set<Int>,
    val startPositions: Map<Int, Offset>,
    val endPositions: Map<Int, Offset>,
    val targetState: JigsawSessionState,
    val highlightPieceIds: Set<Int>,
)

private data class TrayLayout(
    val slotSize: DpSize,
    val offsets: Map<Int, DpOffset>,
)

private data class PieceExtensions(
    val left: Dp,
    val right: Dp,
    val top: Dp,
    val bottom: Dp,
)

private data class SizePx(
    val width: Float,
    val height: Float,
)

private fun createTrayLayout(
    pieces: List<com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
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
    val slotSize = DpSize(
        width = cellWidth + maxLeft + maxRight,
        height = cellHeight + maxTop + maxBottom,
    )
    val offsets = extensionsByPiece.mapValues { (_, ext) ->
        DpOffset(
            x = maxLeft - ext.left,
            y = maxTop - ext.top,
        )
    }
    return TrayLayout(
        slotSize = slotSize,
        offsets = offsets,
    )
}

private fun boardPieceTopLeft(
    piece: com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout,
    boardPosition: JigsawBoardPosition,
    boardCellWidthPx: Float,
    boardCellHeightPx: Float,
    boardContentRect: Rect,
): Offset = Offset(
    x = boardContentRect.left + (boardPosition.x * boardCellWidthPx) - (boardCellWidthPx * piece.left.extensionRatio),
    y = boardContentRect.top + (boardPosition.y * boardCellHeightPx) - (boardCellHeightPx * piece.top.extensionRatio),
)

private fun topLeftToBoardPosition(
    piece: com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout,
    topLeft: Offset,
    boardCellWidthPx: Float,
    boardCellHeightPx: Float,
    boardContentRect: Rect,
): JigsawBoardPosition = JigsawBoardPosition(
    x = (topLeft.x + (boardCellWidthPx * piece.left.extensionRatio) - boardContentRect.left) / boardCellWidthPx,
    y = (topLeft.y + (boardCellHeightPx * piece.top.extensionRatio) - boardContentRect.top) / boardCellHeightPx,
)

private fun piecePreviewSizePx(
    piece: com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout,
    boardCellWidth: Dp,
    boardCellHeight: Dp,
    density: androidx.compose.ui.unit.Density,
): SizePx {
    val previewSize = calculateLoosePieceSize(piece, boardCellWidth, boardCellHeight)
    return with(density) {
        SizePx(width = previewSize.width.toPx(), height = previewSize.height.toPx())
    }
}

private fun lerp(start: Offset, end: Offset, progress: Float): Offset = Offset(
    x = start.x + ((end.x - start.x) * progress),
    y = start.y + ((end.y - start.y) * progress),
)

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + ((end - start) * progress)

private fun lerp(start: Dp, end: Dp, progress: Float): Dp = start + ((end - start) * progress)

private fun Rect.translatedBy(offset: Offset): Rect = Rect(
    left = left + offset.x,
    top = top + offset.y,
    right = right + offset.x,
    bottom = bottom + offset.y,
)

private fun Rect.toAndroidRect(): AndroidRect = AndroidRect(
    left.roundToInt(),
    top.roundToInt(),
    right.roundToInt(),
    bottom.roundToInt(),
)

private val BoardInnerPadding = 12.dp
private val TrayItemSpacing = 8.dp
private const val TrayReferenceColumns = 6
private const val TrayReferenceRows = 8
private const val HighlightDurationMillis = 260
private const val SnapAnimationDurationMillis = 220
private const val BoardSnapThreshold = 0.28f
private const val TrayPieceDragVerticalBias = 0.65f
