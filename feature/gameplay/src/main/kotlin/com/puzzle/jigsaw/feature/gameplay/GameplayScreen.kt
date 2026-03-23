package com.puzzle.jigsaw.feature.gameplay

import android.graphics.Rect as AndroidRect
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathHitTester
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
import com.puzzle.jigsaw.core.designsystem.components.PuzzleBackButton
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
import com.puzzle.jigsaw.domain.jigsaw.reorderTrayPieces
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
    val trayListState = rememberLazyListState()
    val allPieceIds = remember(pieceLayout) { pieceLayout.mapTo(linkedSetOf()) { it.pieceId } }

    var highlightedPieceIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var dragState by remember { mutableStateOf<PieceDragState?>(null) }
    var snapAnimationState by remember { mutableStateOf<PieceSnapAnimationState?>(null) }
    var snapAnimationToken by remember { mutableIntStateOf(0) }
    var pendingTrayReturnAnimation by remember { mutableStateOf<PendingTrayReturnAnimation?>(null) }
    var trayReturnAnimationState by remember { mutableStateOf<TrayReturnAnimationState?>(null) }
    var trayReturnAnimationToken by remember { mutableIntStateOf(0) }
    val snapProgress = remember { Animatable(1f) }
    val trayReturnProgress = remember { Animatable(1f) }
    val trayItemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var trayRowBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var gestureExclusionRect by remember { mutableStateOf<AndroidRect?>(null) }
    var activeTrayScrollSnapshot by remember { mutableStateOf<TrayScrollSnapshot?>(null) }
    var pendingTrayScrollRestore by remember { mutableStateOf<TrayScrollSnapshot?>(null) }
    var isResetConfirmationVisible by remember { mutableStateOf(false) }
    var didAnimateCompletion by remember(sessionState.image.id, sessionState.pieceCount.totalPieces) {
        mutableStateOf(sessionState.completionRatio >= 1f)
    }
    val latestSessionState by rememberUpdatedState(sessionState)
    val latestPiecesById by rememberUpdatedState(piecesById)
    val latestTrayRowBounds by rememberUpdatedState(trayRowBounds)
    val latestTrayItemBounds by rememberUpdatedState(trayItemBounds.toMap())
    val finishedImageProgress = remember(sessionState.image.id, sessionState.pieceCount.totalPieces) {
        Animatable(if (sessionState.completionRatio >= 1f) 1f else 0f)
    }

    LaunchedEffect(pendingTrayScrollRestore) {
        val snapshot = pendingTrayScrollRestore ?: return@LaunchedEffect
        trayListState.scrollToItem(snapshot.firstVisibleItemIndex, snapshot.firstVisibleItemScrollOffset)
        pendingTrayScrollRestore = null
    }

    LaunchedEffect(pendingTrayReturnAnimation, pendingTrayScrollRestore) {
        val request = pendingTrayReturnAnimation ?: return@LaunchedEffect
        if (pendingTrayScrollRestore != null) return@LaunchedEffect
        trayReturnAnimationState = TrayReturnAnimationState(
            pieceId = request.pieceId,
            startTopLeft = request.startTopLeft,
            endTopLeft = request.endTopLeft,
        )
        dragState = dragState?.takeUnless { current ->
            current.source == PieceDragSource.TRAY && current.pieceId == request.pieceId
        }
        pendingTrayReturnAnimation = null
        trayReturnAnimationToken += 1
    }

    LaunchedEffect(trayReturnAnimationToken) {
        val animation = trayReturnAnimationState ?: return@LaunchedEffect
        trayReturnProgress.snapTo(0f)
        trayReturnProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = TrayReturnAnimationDurationMillis),
        )
        trayReturnAnimationState = null
    }

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

    fun flashPieces(
        pieceIds: Set<Int>,
        durationMillis: Long = 420L,
    ) {
        if (pieceIds.isEmpty()) return
        highlightedPieceIds = highlightedPieceIds + pieceIds
        scope.launch {
            delay(durationMillis)
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

    LaunchedEffect(sessionState.completionRatio, snapAnimationState) {
        if (sessionState.completionRatio < 1f) {
            didAnimateCompletion = false
            finishedImageProgress.snapTo(0f)
            return@LaunchedEffect
        }
        if (didAnimateCompletion) {
            if (finishedImageProgress.value != 1f) {
                finishedImageProgress.snapTo(1f)
            }
            return@LaunchedEffect
        }
        if (snapAnimationState != null) return@LaunchedEffect

        didAnimateCompletion = true
        flashPieces(
            pieceIds = allPieceIds,
            durationMillis = CompletionHighlightDurationMillis.toLong(),
        )
        finishedImageProgress.snapTo(0f)
        delay(CompletionHighlightDurationMillis.toLong())
        finishedImageProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = CompletionRevealDurationMillis),
        )
    }

    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
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
                            PuzzleBackButton(onClick = onBack)
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = if (sessionState.completionRatio >= 1f) {
                                        "Puzzle completed"
                                    } else {
                                        "Assemble the puzzle"
                                    },
                                )
                                Text(
                                    text = "${sessionState.pieceCount.title} • ${(sessionState.completionRatio * 100).roundToInt()}% complete",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { isResetConfirmationVisible = true }) {
                            Text("Reset")
                        }
                    }
                    LinearProgressIndicator(
                        progress = { sessionState.completionRatio },
                        modifier = Modifier.fillMaxWidth(),
                    )
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot().toAndroidRect()
                        if (gestureExclusionRect != bounds) {
                            gestureExclusionRect = bounds
                        }
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
                val trayItemSpacingPx = with(density) { TrayItemSpacing.toPx() }
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
                val trayLayoutPx = remember(trayLayout, density) {
                    with(density) {
                        TrayLayoutPx(
                            slotWidth = trayLayout.slotSize.width.toPx(),
                            slotHeight = trayLayout.slotSize.height.toPx(),
                            offsets = trayLayout.offsets.mapValues { (_, offset) ->
                                Offset(offset.x.toPx(), offset.y.toPx())
                            },
                        )
                    }
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
                val returningTrayPieceId = trayReturnAnimationState?.pieceId ?: pendingTrayReturnAnimation?.pieceId
                val animatingPlacedPieceIds = snapAnimationState
                    ?.pieceIds
                    ?.intersect(sessionState.placedPieceIds)
                    .orEmpty()
                val hiddenPieceIds = ((dragState?.clusterPieceIds ?: emptySet()) - listOfNotNull(draggedPieceId).toSet()) +
                    (snapAnimationState?.pieceIds ?: emptySet())

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInRoot()
                            if (overlayOriginInRoot != position) {
                                overlayOriginInRoot = position
                            }
                        },
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
                            highlightedPieceIds = highlightedPieceIds,
                            finishedImageProgress = finishedImageProgress.value,
                            assetBitmap = assetBitmap,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(BoardInnerPadding),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(boardCardHeight)
                            .pointerInput(pieceLayout, boardCellWidthPx, boardCellHeightPx, boardContentRect) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val touchedPieceId = findTouchedBoardPieceId(
                                            pointerPosition = offset,
                                            sessionState = latestSessionState,
                                            piecesById = latestPiecesById,
                                            boardCellWidthPx = boardCellWidthPx,
                                            boardCellHeightPx = boardCellHeightPx,
                                            boardContentRect = boardContentRect,
                                        ) ?: return@detectDragGestures
                                        val piece = latestPiecesById.getValue(touchedPieceId)
                                        val topLeft = roundedOffset(
                                            boardPieceTopLeft(
                                            piece = piece,
                                            boardPosition = latestSessionState.boardPiecePositions.getValue(touchedPieceId),
                                            boardCellWidthPx = boardCellWidthPx,
                                            boardCellHeightPx = boardCellHeightPx,
                                            boardContentRect = boardContentRect,
                                        ),
                                        )
                                        val touchOffset = offset - topLeft
                                        val cluster = connectedBoardPieceIds(latestSessionState, touchedPieceId)
                                        val clusterStartPositions = cluster.associateWith { clusterPieceId ->
                                            boardPieceTopLeft(
                                                piece = latestPiecesById.getValue(clusterPieceId),
                                                boardPosition = latestSessionState.boardPiecePositions.getValue(clusterPieceId),
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
                                        val trayPreviewSizePx = piecePreviewSizePx(
                                            piece = piece,
                                            boardCellWidth = trayReferenceCellWidth,
                                            boardCellHeight = trayReferenceCellHeight,
                                            density = density,
                                        )
                                        dragState = PieceDragState(
                                            pieceId = touchedPieceId,
                                            clusterPieceIds = cluster,
                                            source = PieceDragSource.BOARD,
                                            initialPointer = offset,
                                            grabFractionX = touchOffset.x / previewSizePx.width,
                                            grabFractionY = touchOffset.y / previewSizePx.height,
                                            selectedPieceTrayCellWidth = trayReferenceCellWidth,
                                            selectedPieceTrayCellHeight = trayReferenceCellHeight,
                                            selectedPieceBoardCellWidth = boardCellWidth,
                                            selectedPieceBoardCellHeight = boardCellHeight,
                                            selectedPieceTrayPreviewSizePx = trayPreviewSizePx,
                                            selectedPieceBoardPreviewSizePx = previewSizePx,
                                            startPositions = clusterStartPositions,
                                        )
                                    },
                                    onDrag = { change, dragAmount ->
                                        val currentDrag = dragState ?: return@detectDragGestures
                                        change.consume()
                                        currentDrag.dragBy(dragAmount)
                                    },
                                    onDragEnd = {
                                        val currentDrag = dragState?.takeIf { it.source == PieceDragSource.BOARD }
                                            ?: return@detectDragGestures
                                        handleDragFinished(
                                            dragState = currentDrag,
                                            sessionState = latestSessionState,
                                            boardContentRect = boardContentRect,
                                            boardCellWidthPx = boardCellWidthPx,
                                            boardCellHeightPx = boardCellHeightPx,
                                            trayRowBounds = latestTrayRowBounds,
                                            trayItemBounds = latestTrayItemBounds,
                                            trayLayoutPx = trayLayoutPx,
                                            trayItemSpacingPx = trayItemSpacingPx,
                                            piecesById = latestPiecesById,
                                            onStartTrayReturnAnimation = { animation ->
                                                pendingTrayReturnAnimation = animation
                                            },
                                            onCommit = onSessionStateChange,
                                            onStartSnapAnimation = { animation ->
                                                snapAnimationState = animation
                                                snapAnimationToken += 1
                                            },
                                            onCancel = { dragState = null },
                                        )
                                        if (dragState == currentDrag) {
                                            dragState = null
                                        }
                                    },
                                    onDragCancel = {
                                        if (dragState?.source == PieceDragSource.BOARD) {
                                            dragState = null
                                        }
                                    },
                                )
                            },
                    )

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
                        LoosePiecesTrayRow(
                            state = trayListState,
                            remainingPieceIds = sessionState.remainingPieceIds,
                            piecesById = piecesById,
                            pieceCount = sessionState.pieceCount,
                            assetBitmap = assetBitmap,
                            dragState = dragState,
                            draggedPieceId = draggedPieceId,
                            returningTrayPieceId = returningTrayPieceId,
                            hiddenPieceIds = hiddenPieceIds,
                            highlightedPieceIds = highlightedPieceIds,
                            trayReferenceCellWidth = trayReferenceCellWidth,
                            trayReferenceCellHeight = trayReferenceCellHeight,
                            trayLayout = trayLayout,
                            trayHorizontalPadding = trayHorizontalPadding,
                            overlayOriginInRoot = overlayOriginInRoot,
                            trayRowBounds = trayRowBounds,
                            onTrayRowBoundsChanged = { bounds ->
                                if (trayRowBounds != bounds) {
                                    trayRowBounds = bounds
                                }
                            },
                            trayItemBounds = trayItemBounds,
                            onTrayItemBoundsChanged = { pieceId, bounds ->
                                if (trayItemBounds[pieceId] != bounds) {
                                    trayItemBounds[pieceId] = bounds
                                }
                            },
                            onTrayDragStarted = { pieceId: Int, piece: com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout, touchOffset: Offset ->
                                val trayBounds = trayItemBounds[pieceId] ?: return@LoosePiecesTrayRow
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
                                    initialPointer = trayBounds.topLeft + touchOffset,
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
                                activeTrayScrollSnapshot = TrayScrollSnapshot(
                                    firstVisibleItemIndex = trayListState.firstVisibleItemIndex,
                                    firstVisibleItemScrollOffset = trayListState.firstVisibleItemScrollOffset,
                                )
                            },
                            onTrayDragged = { pieceId: Int, dragAmount: Offset ->
                                dragState
                                    ?.takeIf { it.pieceId == pieceId }
                                    ?.dragBy(dragAmount)
                            },
                            onTrayDragEnded = { pieceId: Int ->
                                val currentDrag = dragState?.takeIf { it.pieceId == pieceId } ?: return@LoosePiecesTrayRow
                                val returnsToTray =
                                    trayRowBounds?.contains(currentDrag.currentPointer) == true
                                handleDragFinished(
                                    dragState = currentDrag,
                                    sessionState = sessionState,
                                    boardContentRect = boardContentRect,
                                    boardCellWidthPx = boardCellWidthPx,
                                    boardCellHeightPx = boardCellHeightPx,
                                    trayRowBounds = trayRowBounds,
                                    trayItemBounds = trayItemBounds,
                                    trayLayoutPx = trayLayoutPx,
                                    trayItemSpacingPx = trayItemSpacingPx,
                                    piecesById = piecesById,
                                    onStartTrayReturnAnimation = { animation ->
                                        pendingTrayReturnAnimation = animation
                                    },
                                    onCommit = onSessionStateChange,
                                    onStartSnapAnimation = { animation ->
                                        snapAnimationState = animation
                                        snapAnimationToken += 1
                                    },
                                    onCancel = { dragState = null },
                                )
                                if (returnsToTray) {
                                    pendingTrayScrollRestore = activeTrayScrollSnapshot
                                }
                                activeTrayScrollSnapshot = null
                                val shouldKeepTrayOverlayUntilReturnAnimation =
                                    returnsToTray && currentDrag.source == PieceDragSource.TRAY
                                if (!shouldKeepTrayOverlayUntilReturnAnimation && dragState == currentDrag) {
                                    dragState = null
                                }
                            },
                        )
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
                            onDragStarted = {},
                            onDragged = {},
                            onDragEnded = {},
                            enabled = false,
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

                    trayReturnAnimationState?.let { animation ->
                        val progress = trayReturnProgress.value
                        val currentTopLeft = lerp(animation.startTopLeft, animation.endTopLeft, progress)
                        val piece = piecesById.getValue(animation.pieceId)
                        FloatingBoardPiece(
                            pieceId = animation.pieceId,
                            pieceCount = sessionState.pieceCount,
                            piece = piece,
                            assetBitmap = assetBitmap,
                            boardCellWidth = trayReferenceCellWidth,
                            boardCellHeight = trayReferenceCellHeight,
                            topLeft = currentTopLeft,
                            ghosted = false,
                            highlight = 0f,
                            onDragStarted = {},
                            onDragged = {},
                            onDragEnded = {},
                            enabled = false,
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

    if (isResetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { isResetConfirmationVisible = false },
            title = {
                Text("Reset puzzle?")
            },
            text = {
                Text("Current progress for this puzzle size will be cleared.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isResetConfirmationVisible = false
                        onResetProgress()
                    },
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetConfirmationVisible = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun handleDragFinished(
    dragState: PieceDragState,
    sessionState: JigsawSessionState,
    boardContentRect: Rect,
    boardCellWidthPx: Float,
    boardCellHeightPx: Float,
    trayRowBounds: Rect?,
    trayItemBounds: Map<Int, Rect>,
    trayLayoutPx: TrayLayoutPx,
    trayItemSpacingPx: Float,
    piecesById: Map<Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
    onStartTrayReturnAnimation: (PendingTrayReturnAnimation) -> Unit,
    onCommit: (JigsawSessionState) -> Unit,
    onStartSnapAnimation: (PieceSnapAnimationState) -> Unit,
    onCancel: () -> Unit,
) {
    val piece = piecesById.getValue(dragState.pieceId)
    val currentTopLeft = dragState.currentSelectedPieceTopLeft(trayRowBounds)
    val boardAlignedTopLeft = dragState.currentBoardAlignedTopLeft(trayRowBounds)
    val boardAlignedCenter = dragState.currentBoardAlignedCenter(trayRowBounds)

    if (trayRowBounds?.contains(dragState.currentPointer) == true) {
        if (dragState.source == PieceDragSource.BOARD && dragState.clusterPieceIds.size > 1) {
            onCancel()
            return
        }
        val trayState = if (dragState.source == PieceDragSource.BOARD) {
            returnPieceClusterToTray(sessionState, dragState.pieceId)
        } else {
            sessionState
        }
        val resolvedTrayDropIndex = calculateTrayDropIndex(
            dragState = dragState,
            trayRowBounds = trayRowBounds,
            orderedPieceIds = trayState.remainingPieceIds,
            trayItemBounds = trayItemBounds,
        ) ?: trayState.remainingPieceIds.size
        val reorderedState = reorderTrayPieces(
            state = trayState,
            movedPieceIds = dragState.clusterPieceIds,
            targetIndex = resolvedTrayDropIndex,
        )
        if (dragState.source == PieceDragSource.TRAY) {
            resolveTrayReturnAnimation(
                pieceId = dragState.pieceId,
                movedPieceIds = dragState.clusterPieceIds,
                startTopLeft = currentTopLeft,
                insertionIndex = resolvedTrayDropIndex,
                orderedPieceIds = trayState.remainingPieceIds,
                trayItemBounds = trayItemBounds,
                trayLayoutPx = trayLayoutPx,
                trayRowBounds = trayRowBounds,
                trayItemSpacingPx = trayItemSpacingPx,
            )?.let(onStartTrayReturnAnimation)
        }
        onCommit(reorderedState)
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
    modifier: Modifier = Modifier,
    boardCellWidth: Dp,
    boardCellHeight: Dp,
    slotSize: DpSize,
    previewOffset: DpOffset,
    overlayOriginInRoot: Offset,
    collapsed: Boolean,
    ghosted: Boolean,
    highlight: Float,
    visible: Boolean,
    onPositioned: (Rect) -> Unit,
    onDragStarted: (Offset) -> Unit,
    onDragged: (Offset) -> Unit,
    onDragEnded: () -> Unit,
) {
    val density = LocalDensity.current
    val currentOnPositioned by rememberUpdatedState(onPositioned)
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val currentOnDragged by rememberUpdatedState(onDragged)
    val currentOnDragEnded by rememberUpdatedState(onDragEnded)
    val previewSize = remember(piece, boardCellWidth, boardCellHeight) {
        calculateLoosePieceSize(
            piece = piece,
            boardCellWidth = boardCellWidth,
            boardCellHeight = boardCellHeight,
        )
    }
    val previewBounds = remember(previewOffset, previewSize, density) {
        with(density) {
            Rect(
                left = previewOffset.x.toPx(),
                top = previewOffset.y.toPx(),
                right = previewOffset.x.toPx() + previewSize.width.toPx(),
                bottom = previewOffset.y.toPx() + previewSize.height.toPx(),
            )
        }
    }
    val highlightProgress by animateFloatAsState(
        targetValue = highlight,
        animationSpec = tween(durationMillis = HighlightDurationMillis),
        label = "trayPieceHighlight",
    )
    val animatedWidth by animateDpAsState(
        targetValue = if (collapsed) 0.dp else slotSize.width,
        animationSpec = tween(durationMillis = TrayReorderAnimationDurationMillis),
        label = "traySlotWidth",
    )

    Box(
        modifier = modifier
            .size(width = animatedWidth, height = slotSize.height)
            .pointerInput(pieceId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!previewBounds.contains(down.position)) {
                        return@awaitEachGesture
                    }
                    var accumulatedDrag = Offset.Zero
                    var pieceDragStarted = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (pieceDragStarted) {
                                currentOnDragEnded()
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
                            currentOnDragStarted(down.position - previewBounds.topLeft)
                            change.consume()
                            if (accumulatedDrag != Offset.Zero) {
                                currentOnDragged(accumulatedDrag)
                            }
                            continue
                        }

                        if (dragAmount != Offset.Zero) {
                            change.consume()
                            currentOnDragged(dragAmount)
                        }
                    }
                }
            }
    ) {
        if (visible || ghosted) {
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
                            currentOnPositioned(
                                coordinates
                                    .boundsInRoot()
                                    .translatedBy(Offset(-overlayOriginInRoot.x, -overlayOriginInRoot.y)),
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
            Text(
                text = "#${pieceId + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .alpha(if (ghosted) 0f else 1f)
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun TrayPlaceholderSlot(
    modifier: Modifier = Modifier,
    slotSize: DpSize,
    pieceCount: Int,
) {
    val placeholderWidth by animateDpAsState(
        targetValue = (slotSize.width * pieceCount.toFloat()) + (TrayItemSpacing * (pieceCount - 1).coerceAtLeast(0).toFloat()),
        animationSpec = tween(durationMillis = TrayReorderAnimationDurationMillis),
        label = "trayPlaceholderWidth",
    )

    Box(
        modifier = modifier
            .width(placeholderWidth)
            .height(slotSize.height),
    )
}

@Composable
private fun LoosePiecesTrayRow(
    state: androidx.compose.foundation.lazy.LazyListState,
    remainingPieceIds: List<Int>,
    piecesById: Map<Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
    pieceCount: PieceCountOption,
    assetBitmap: ImageBitmap?,
    dragState: PieceDragState?,
    draggedPieceId: Int?,
    returningTrayPieceId: Int?,
    hiddenPieceIds: Set<Int>,
    highlightedPieceIds: Set<Int>,
    trayReferenceCellWidth: Dp,
    trayReferenceCellHeight: Dp,
    trayLayout: TrayLayout,
    trayHorizontalPadding: Dp,
    overlayOriginInRoot: Offset,
    trayRowBounds: Rect?,
    onTrayRowBoundsChanged: (Rect) -> Unit,
    trayItemBounds: Map<Int, Rect>,
    onTrayItemBoundsChanged: (Int, Rect) -> Unit,
    onTrayDragStarted: (Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout, Offset) -> Unit,
    onTrayDragged: (Int, Offset) -> Unit,
    onTrayDragEnded: (Int) -> Unit,
) {
    val trayDropIndex = calculateTrayDropIndex(
        dragState = dragState,
        trayRowBounds = trayRowBounds,
        orderedPieceIds = remainingPieceIds,
        trayItemBounds = trayItemBounds,
    )
    val trayEntries = createTrayEntries(
        orderedPieceIds = remainingPieceIds,
        dragState = dragState,
        placeholderIndex = trayDropIndex,
        placeholderPieceCount = dragState?.clusterPieceIds?.size ?: 0,
    )

    LazyRow(
        state = state,
        modifier = Modifier.onGloballyPositioned { coordinates ->
            onTrayRowBoundsChanged(
                coordinates
                    .boundsInRoot()
                    .translatedBy(Offset(-overlayOriginInRoot.x, -overlayOriginInRoot.y)),
            )
        },
        contentPadding = PaddingValues(horizontal = trayHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(TrayItemSpacing),
    ) {
        itemsIndexed(
            items = trayEntries,
            key = { _, entry -> entry.key },
        ) { _, entry ->
            when (entry) {
                is TrayEntry.Piece -> {
                    val pieceId = entry.pieceId
                    val piece = piecesById.getValue(pieceId)
                    val isHidden = pieceId in hiddenPieceIds
                    TrayPieceCard(
                        pieceId = pieceId,
                        pieceCount = pieceCount,
                        piece = piece,
                        assetBitmap = assetBitmap,
                        modifier = if (pieceId == draggedPieceId || pieceId == returningTrayPieceId) {
                            Modifier
                        } else {
                            Modifier.animateItem()
                        },
                        boardCellWidth = trayReferenceCellWidth,
                        boardCellHeight = trayReferenceCellHeight,
                        slotSize = trayLayout.slotSize,
                        previewOffset = trayLayout.offsets.getValue(pieceId),
                        overlayOriginInRoot = overlayOriginInRoot,
                        collapsed = entry.collapsed,
                        ghosted = pieceId == draggedPieceId || pieceId == returningTrayPieceId,
                        highlight = if (pieceId in highlightedPieceIds) 1f else 0f,
                        visible = !isHidden,
                        onPositioned = { bounds ->
                            onTrayItemBoundsChanged(pieceId, bounds)
                        },
                        onDragStarted = { touchOffset ->
                            onTrayDragStarted(pieceId, piece, touchOffset)
                        },
                        onDragged = { dragAmount ->
                            onTrayDragged(pieceId, dragAmount)
                        },
                        onDragEnded = {
                            onTrayDragEnded(pieceId)
                        },
                    )
                }

                is TrayEntry.Placeholder -> {
                    TrayPlaceholderSlot(
                        modifier = Modifier.animateItem(),
                        slotSize = trayLayout.slotSize,
                        pieceCount = entry.pieceCount,
                    )
                }
            }
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
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)
    val currentOnDragged by rememberUpdatedState(onDragged)
    val currentOnDragEnded by rememberUpdatedState(onDragEnded)
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
            .then(
                if (enabled) {
                    Modifier.pointerInput(pieceId, topLeft) {
                        detectDragGestures(
                            onDragStart = { offset -> currentOnDragStarted(offset) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                currentOnDragged(dragAmount)
                            },
                            onDragEnd = { currentOnDragEnded() },
                            onDragCancel = { currentOnDragEnded() },
                        )
                    }
                } else {
                    Modifier
                },
            ),
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
        val previewCellWidth = if (clusterPieceId == dragState.pieceId) {
            selectedCellWidth
        } else {
            dragState.selectedPieceBoardCellWidth
        }
        val previewCellHeight = if (clusterPieceId == dragState.pieceId) {
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

@Stable
private class PieceDragState(
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
            if (source == PieceDragSource.TRAY || clusterPieceId == pieceId) {
                startPositions.getValue(clusterPieceId) + delta
            } else {
                startPositions.getValue(clusterPieceId) + delta
            }
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
        if (source != PieceDragSource.TRAY || trayRowBounds == null) return 1f
        val travel = (trayRowBounds.height * TrayPieceResizeTravelFraction).coerceAtLeast(1f)
        return ((trayRowBounds.top - currentPointer.y) / travel).coerceIn(0f, 1f)
    }

    private fun contractionProgress(trayRowBounds: Rect?): Float {
        if (source != PieceDragSource.BOARD || trayRowBounds == null) return 1f
        val travel = (trayRowBounds.height * TrayPieceResizeTravelFraction).coerceAtLeast(1f)
        return ((trayRowBounds.top - currentPointer.y) / travel).coerceIn(0f, 1f)
    }

    private fun liftProgress(trayRowBounds: Rect?): Float {
        if (source != PieceDragSource.TRAY || trayRowBounds == null) return 0f
        val travel = (trayRowBounds.height * 0.65f).coerceAtLeast(1f)
        return ((trayRowBounds.top - currentPointer.y) / travel).coerceIn(0f, 1f)
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

private sealed interface TrayEntry {
    val key: Any

    data class Piece(
        val pieceId: Int,
        val collapsed: Boolean,
    ) : TrayEntry {
        override val key: Any = "piece:$pieceId"
    }

    data class Placeholder(
        val pieceCount: Int,
    ) : TrayEntry {
        override val key: Any = "placeholder"
    }
}

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

private data class PendingTrayReturnAnimation(
    val pieceId: Int,
    val startTopLeft: Offset,
    val endTopLeft: Offset,
)

private data class TrayReturnAnimationState(
    val pieceId: Int,
    val startTopLeft: Offset,
    val endTopLeft: Offset,
)

private data class TrayScrollSnapshot(
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
)

private data class TrayLayoutPx(
    val slotWidth: Float,
    val slotHeight: Float,
    val offsets: Map<Int, Offset>,
)

private fun createTrayEntries(
    orderedPieceIds: List<Int>,
    dragState: PieceDragState?,
    placeholderIndex: Int?,
    placeholderPieceCount: Int,
): List<TrayEntry> {
    val collapsedPieceIds = if (dragState?.source == PieceDragSource.TRAY) {
        dragState.clusterPieceIds
    } else {
        emptySet()
    }
    val baseEntries = orderedPieceIds.map { pieceId ->
        TrayEntry.Piece(
            pieceId = pieceId,
            collapsed = pieceId in collapsedPieceIds,
        )
    }.toMutableList<TrayEntry>()

    if (dragState == null || placeholderIndex == null || placeholderPieceCount <= 0) {
        return baseEntries
    }

    val movablePieceIds = orderedPieceIds.filterNot { it in dragState.clusterPieceIds }
    val insertionIndex = placeholderIndex.coerceIn(0, movablePieceIds.size)
    var visiblePieceIndex = 0
    var entryInsertIndex = baseEntries.size
    baseEntries.forEachIndexed { index, entry ->
        if (entry !is TrayEntry.Piece || entry.collapsed) return@forEachIndexed
        if (visiblePieceIndex == insertionIndex) {
            entryInsertIndex = index
            return@forEachIndexed
        }
        visiblePieceIndex += 1
    }
    if (insertionIndex == 0) {
        entryInsertIndex = 0
    } else if (insertionIndex >= visiblePieceIndex) {
        entryInsertIndex = baseEntries.size
    }

    baseEntries.add(entryInsertIndex, TrayEntry.Placeholder(pieceCount = placeholderPieceCount))
    return baseEntries
}

private fun calculateTrayDropIndex(
    dragState: PieceDragState?,
    trayRowBounds: Rect?,
    orderedPieceIds: List<Int>,
    trayItemBounds: Map<Int, Rect>,
): Int? {
    if (dragState == null || trayRowBounds == null || !trayRowBounds.contains(dragState.currentPointer)) {
        return null
    }
    val stablePieceIds = orderedPieceIds.filterNot { it in dragState.clusterPieceIds }
    if (stablePieceIds.isEmpty()) return 0

    val draggedCenterX = dragState.currentSelectedPieceCenter(trayRowBounds).x
    stablePieceIds.forEachIndexed { index, pieceId ->
        val bounds = trayItemBounds[pieceId] ?: return@forEachIndexed
        if (draggedCenterX < bounds.center.x) {
            return index
        }
    }
    return stablePieceIds.size
}

private fun resolveTrayReturnAnimation(
    pieceId: Int,
    movedPieceIds: Set<Int>,
    startTopLeft: Offset,
    insertionIndex: Int,
    orderedPieceIds: List<Int>,
    trayItemBounds: Map<Int, Rect>,
    trayLayoutPx: TrayLayoutPx,
    trayRowBounds: Rect?,
    trayItemSpacingPx: Float,
): PendingTrayReturnAnimation? {
    val movedOffset = trayLayoutPx.offsets.getValue(pieceId)
    val stablePieceIds = orderedPieceIds.filterNot { it in movedPieceIds }
    val slotSpan = trayLayoutPx.slotWidth + trayItemSpacingPx

    val targetSlotTop = when {
        stablePieceIds.isNotEmpty() -> {
            val referencePieceId = stablePieceIds.first()
            val referenceBounds = trayItemBounds[referencePieceId] ?: return null
            referenceBounds.top - trayLayoutPx.offsets.getValue(referencePieceId).y
        }

        trayRowBounds != null -> trayRowBounds.top
        else -> startTopLeft.y - movedOffset.y
    }

    val targetSlotLeft = when {
        stablePieceIds.isEmpty() -> trayRowBounds?.left ?: (startTopLeft.x - movedOffset.x)
        insertionIndex < stablePieceIds.size -> {
            val anchorPieceId = stablePieceIds[insertionIndex]
            val anchorBounds = trayItemBounds[anchorPieceId] ?: return null
            anchorBounds.left - trayLayoutPx.offsets.getValue(anchorPieceId).x
        }

        else -> {
            val anchorPieceId = stablePieceIds.last()
            val anchorBounds = trayItemBounds[anchorPieceId] ?: return null
            (anchorBounds.left - trayLayoutPx.offsets.getValue(anchorPieceId).x) +
                (slotSpan * movedPieceIds.size.toFloat())
        }
    }

    return PendingTrayReturnAnimation(
        pieceId = pieceId,
        startTopLeft = startTopLeft,
        endTopLeft = Offset(
            x = targetSlotLeft + movedOffset.x,
            y = targetSlotTop + movedOffset.y,
        ),
    )
}

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

private fun findTouchedBoardPieceId(
    pointerPosition: Offset,
    sessionState: JigsawSessionState,
    piecesById: Map<Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
    boardCellWidthPx: Float,
    boardCellHeightPx: Float,
    boardContentRect: Rect,
): Int? = sessionState.boardPiecePositions.keys.toList().asReversed().firstOrNull { pieceId ->
    val piece = piecesById.getValue(pieceId)
    val topLeft = roundedOffset(
        boardPieceTopLeft(
            piece = piece,
            boardPosition = sessionState.boardPiecePositions.getValue(pieceId),
            boardCellWidthPx = boardCellWidthPx,
            boardCellHeightPx = boardCellHeightPx,
            boardContentRect = boardContentRect,
        ),
    )
    val localPoint = pointerPosition - topLeft
    val bounds = calculateLoosePieceBounds(
        piece = piece,
        cellWidth = boardCellWidthPx,
        cellHeight = boardCellHeightPx,
    )
    if (
        localPoint.x < 0f ||
        localPoint.y < 0f ||
        localPoint.x > bounds.width ||
        localPoint.y > bounds.height
    ) {
        false
    } else {
        val path = createLoosePieceLocalPath(
            piece = piece,
            pieceCount = sessionState.pieceCount,
            cellWidth = boardCellWidthPx,
            cellHeight = boardCellHeightPx,
        )
        PathHitTester(path).contains(localPoint)
    }
}

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

private fun roundedOffset(offset: Offset): Offset = Offset(
    x = offset.x.roundToInt().toFloat(),
    y = offset.y.roundToInt().toFloat(),
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
private const val CompletionHighlightDurationMillis = 650
private const val CompletionRevealDurationMillis = 320
private const val TrayReorderAnimationDurationMillis = 220
private const val TrayReturnAnimationDurationMillis = 220
private const val BoardSnapThreshold = 0.28f
private const val TrayPieceDragVerticalBias = 0.65f
private const val TrayPieceLiftOffsetPx = 150f
private const val TrayPieceResizeTravelFraction = 0.22f
