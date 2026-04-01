package com.puzzle.jigsaw.feature.gameplay

import android.graphics.Rect as AndroidRect
import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import com.puzzle.jigsaw.core.designsystem.components.PuzzleBackButton
import com.puzzle.jigsaw.core.designsystem.components.PuzzleImageAspectRatio
import com.puzzle.jigsaw.core.designsystem.components.rememberPuzzleBitmap
import com.puzzle.jigsaw.core.designsystem.components.rememberPuzzleAssetBitmap
import com.puzzle.jigsaw.core.model.PieceCountOption
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.domain.jigsaw.JigsawBoardPosition
import com.puzzle.jigsaw.domain.jigsaw.JigsawProgressStore
import com.puzzle.jigsaw.domain.jigsaw.JigsawSessionState
import com.puzzle.jigsaw.domain.jigsaw.connectedBoardPieceIds
import com.puzzle.jigsaw.domain.jigsaw.createJigsawPieceLayout
import com.puzzle.jigsaw.domain.jigsaw.createSessionState
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
    val assetBitmap = rememberPuzzleBitmap(
        path = sessionState.image.fullImagePath,
        storage = sessionState.image.storage,
    )
    val backgroundPatternBitmap = rememberPuzzleAssetBitmap("bg/45-degree-fabric-light.png")
    val placementSoundPlayer = rememberGameplaySoundPlayer(LocalContext.current)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val view = LocalView.current
    val trayScrollState = rememberScrollState()

    var dragState by remember { mutableStateOf<PieceDragState?>(null) }
    var pendingTrayRemovalPieceIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val trayItemBounds = remember { mutableStateMapOf<Int, Rect>() }
    var trayRowBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var gestureExclusionRect by remember { mutableStateOf<AndroidRect?>(null) }
    var activeTrayScrollSnapshot by remember { mutableStateOf<TrayScrollSnapshot?>(null) }
    var pendingTrayScrollRestore by remember { mutableStateOf<TrayScrollSnapshot?>(null) }
    var isResetConfirmationVisible by remember { mutableStateOf(false) }
    var isBackgroundPickerExpanded by remember { mutableStateOf(false) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var selectedBackgroundId by rememberSaveable(sessionState.image.id, sessionState.pieceCount.totalPieces) {
        mutableStateOf(GameplayBackgroundStyle.DARK_GREEN.id)
    }
    var trayTopRowSizeHint by remember(sessionState.image.id, sessionState.pieceCount.totalPieces) {
        mutableIntStateOf(0)
    }
    val latestSessionState by rememberUpdatedState(sessionState)
    val latestPiecesById by rememberUpdatedState(piecesById)
    val latestTrayRowBounds by rememberUpdatedState(trayRowBounds)
    val latestTrayItemBounds by rememberUpdatedState(trayItemBounds.toMap())
    val selectedBackground = remember(selectedBackgroundId) {
        GameplayBackgroundStyle.fromId(selectedBackgroundId)
    }

    LaunchedEffect(sessionState.remainingPieceIds, pendingTrayRemovalPieceIds) {
        if (pendingTrayRemovalPieceIds.isEmpty()) return@LaunchedEffect
        pendingTrayRemovalPieceIds =
            pendingTrayRemovalPieceIds.intersect(sessionState.remainingPieceIds.toSet())
    }

    LaunchedEffect(pendingTrayScrollRestore) {
        val snapshot = pendingTrayScrollRestore ?: return@LaunchedEffect
        trayScrollState.scrollTo(snapshot.scrollOffset)
        pendingTrayScrollRestore = null
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

    fun playPlacementFeedback(updatedState: JigsawSessionState) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        placementSoundPlayer.play(
            sound = placementSoundForCompletionRatio(updatedState.completionRatio),
        )
    }

    fun commitDragResult(result: GameplayDragFinishResult) {
        if (result !is GameplayDragFinishResult.Committed) return
        result.trayTopRowSize?.let { trayTopRowSizeHint = it }
        if (result.shouldPlayPlacementFeedback) {
            playPlacementFeedback(result.state)
        }
        onSessionStateChange(result.state)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .gameplayFabricBackground(
                background = selectedBackground,
                patternBitmap = backgroundPatternBitmap,
            )
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInRoot().toAndroidRect()
                if (gestureExclusionRect != bounds) {
                    gestureExclusionRect = bounds
                }
            },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            headerHeightPx = coordinates.size.height
                        },
                    color = selectedBackground.headerScrim,
                ) {
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
                                    val completionPercent = (sessionState.completionRatio * 100).roundToInt()
                                    val totalPiecesLabel = pluralStringResource(
                                        R.plurals.gameplay_piece_count,
                                        sessionState.pieceCount.totalPieces,
                                        sessionState.pieceCount.totalPieces,
                                    )
                                    Text(
                                        text = totalPiecesLabel,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.gameplay_progress_percent,
                                            completionPercent,
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BackgroundSwatchButton(
                                    background = selectedBackground,
                                    patternBitmap = backgroundPatternBitmap,
                                    onClick = {
                                        isBackgroundPickerExpanded = !isBackgroundPickerExpanded
                                    },
                                    contentDescription = stringResource(R.string.gameplay_background_picker),
                                    showCog = true,
                                )
                                TextButton(onClick = { isResetConfirmationVisible = true }) {
                                    Text(stringResource(R.string.gameplay_reset))
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { sessionState.completionRatio },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        )
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = padding.calculateTopPadding() + 12.dp,
                        bottom = padding.calculateBottomPadding() + 16.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val boardCardWidth = maxWidth - (ScreenHorizontalPadding * 2)
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
                    val boardHorizontalInsetPx = with(density) { ScreenHorizontalPadding.toPx() }
                    val trayItemSpacingPx = with(density) { TrayItemSpacing.toPx() }
                    val boardContentRect = Rect(
                        left = boardHorizontalInsetPx + boardInnerPaddingPx,
                        top = boardInnerPaddingPx,
                        right = boardHorizontalInsetPx + boardInnerPaddingPx + with(density) { boardContentWidth.toPx() },
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
                            slotHeight = trayLayout.slotHeight.toPx(),
                            slotWidths = trayLayout.slotWidths.mapValues { (_, width) ->
                                width.toPx()
                            },
                            offsets = trayLayout.offsets.mapValues { (_, offset) ->
                                Offset(offset.x.toPx(), offset.y.toPx())
                            },
                        )
                    }
                }
                val remainingPieceCount = sessionState.remainingPieceIds.size
                val trayAvailableWidth = maxWidth
                val trayAvailableHeight = (maxHeight - boardCardHeight - 16.dp).coerceAtLeast(0.dp)
                val secondRowVisibleHeight =
                    (trayAvailableHeight - trayLayout.slotHeight - TrayRowSpacing).coerceAtLeast(0.dp)
                val supportsTwoTrayRows =
                    secondRowVisibleHeight >= (trayLayout.slotHeight * TraySecondRowMinVisibleFraction)
                val trayRowCount = if (remainingPieceCount > 1 && supportsTwoTrayRows) 2 else 1
                val trayTopRowSize = normalizedTrayTopRowSize(
                    topRowSizeHint = trayTopRowSizeHint,
                    totalCount = remainingPieceCount,
                    rowCount = trayRowCount,
                )
                val trayContentWidth = if (remainingPieceCount == 0) {
                    0.dp
                } else {
                    calculateTrayContentWidth(
                        orderedPieceIds = sessionState.remainingPieceIds,
                        traySlotWidths = trayLayout.slotWidths,
                        rowCount = trayRowCount,
                        topRowSize = trayTopRowSize,
                    )
                }
                val trayHorizontalPadding = if (remainingPieceCount == 0) {
                    2.dp
                } else {
                    val extraSpace = trayAvailableWidth - trayContentWidth
                    if (extraSpace > 4.dp) extraSpace / 2f else 2.dp
                }

                val draggedPieceId = dragState?.pieceId
                val hiddenPieceIds = (dragState?.clusterPieceIds ?: emptySet()) - listOfNotNull(draggedPieceId).toSet()

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
                    Box(
                        modifier = Modifier
                            .width(boardCardWidth)
                            .align(Alignment.TopCenter)
                            .aspectRatio(PuzzleImageAspectRatio)
                            .gameplayBoardSurface(selectedBackground),
                    ) {
                        JigsawBoard(
                            pieceCount = sessionState.pieceCount,
                            pieces = pieceLayout,
                            placedPieceIds = sessionState.placedPieceIds,
                            assetBitmap = assetBitmap,
                            boardBackgroundColor = selectedBackground.boardBackground,
                            emptyFillColor = selectedBackground.emptyFill,
                            boardOutlineColor = Color.Transparent,
                            modifier = Modifier
                                .fillMaxSize(),
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
                                        commitDragResult(
                                            resolveGameplayDragFinish(
                                            dragState = currentDrag,
                                            sessionState = latestSessionState,
                                            boardContentRect = boardContentRect,
                                            boardCellWidthPx = boardCellWidthPx,
                                            boardCellHeightPx = boardCellHeightPx,
                                            trayRowBounds = latestTrayRowBounds,
                                            trayItemBounds = latestTrayItemBounds,
                                            trayLayoutPx = trayLayoutPx,
                                            trayRowCount = trayRowCount,
                                            trayTopRowSize = trayTopRowSize,
                                            trayRowSpacingPx = with(density) { TrayRowSpacing.toPx() },
                                            piecesById = latestPiecesById,
                                        ),
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
                        LoosePiecesTrayRow(
                            scrollState = trayScrollState,
                            remainingPieceIds = sessionState.remainingPieceIds,
                            piecesById = piecesById,
                            pieceCount = sessionState.pieceCount,
                            assetBitmap = assetBitmap,
                            dragState = dragState,
                            draggedPieceId = draggedPieceId,
                            hiddenPieceIds = hiddenPieceIds,
                            pendingTrayRemovalPieceIds = pendingTrayRemovalPieceIds,
                            trayReferenceCellWidth = trayReferenceCellWidth,
                            trayReferenceCellHeight = trayReferenceCellHeight,
                            trayLayout = trayLayout,
                            trayRowCount = trayRowCount,
                            trayTopRowSize = trayTopRowSize,
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
                                    scrollOffset = trayScrollState.value,
                                )
                            },
                            onTrayDragged = { pieceId: Int, dragAmount: Offset ->
                                dragState
                                    ?.takeIf { it.pieceId == pieceId }
                                    ?.dragBy(dragAmount)
                            },
                            onTrayDragEnded = { pieceId: Int ->
                                val currentDrag = dragState?.takeIf { it.pieceId == pieceId } ?: return@LoosePiecesTrayRow
                                val returnsToTray = isDragOverTray(
                                    dragState = currentDrag,
                                    trayRowBounds = trayRowBounds,
                                )
                                val dropsToBoard =
                                    !returnsToTray &&
                                        boardContentRect.contains(currentDrag.currentBoardAlignedCenter(trayRowBounds))
                                commitDragResult(
                                    resolveGameplayDragFinish(
                                    dragState = currentDrag,
                                    sessionState = sessionState,
                                    boardContentRect = boardContentRect,
                                    boardCellWidthPx = boardCellWidthPx,
                                    boardCellHeightPx = boardCellHeightPx,
                                    trayRowBounds = trayRowBounds,
                                    trayItemBounds = trayItemBounds,
                                    trayLayoutPx = trayLayoutPx,
                                    trayRowCount = trayRowCount,
                                    trayTopRowSize = trayTopRowSize,
                                    trayRowSpacingPx = with(density) { TrayRowSpacing.toPx() },
                                    piecesById = piecesById,
                                ),
                                )
                                if (returnsToTray) {
                                    pendingTrayScrollRestore = activeTrayScrollSnapshot
                                }
                                if (dropsToBoard && currentDrag.source == PieceDragSource.TRAY) {
                                    pendingTrayRemovalPieceIds =
                                        pendingTrayRemovalPieceIds + currentDrag.clusterPieceIds
                                }
                                activeTrayScrollSnapshot = null
                                val shouldKeepTrayOverlayUntilReturnAnimation = false
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
                }
            }
        }
        }
        if (isBackgroundPickerExpanded) {
                            BackgroundPickerPanel(
                                selectedBackground = selectedBackground,
                                patternBitmap = backgroundPatternBitmap,
                                onBackgroundSelected = { background ->
                                    selectedBackgroundId = background.id
                                    isBackgroundPickerExpanded = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = with(density) { headerHeightPx.toDp() }),
            )
        }
    }

    if (isResetConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { isResetConfirmationVisible = false },
            title = {
                Text(stringResource(R.string.gameplay_reset_dialog_title))
            },
            text = {
                Text(stringResource(R.string.gameplay_reset_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isResetConfirmationVisible = false
                        onResetProgress()
                    },
                ) {
                    Text(stringResource(R.string.gameplay_reset_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { isResetConfirmationVisible = false }) {
                    Text(stringResource(R.string.gameplay_cancel))
                }
            },
        )
    }
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
                            // Tray drags may start anywhere inside the slot; clamp the grab point
                            // onto the visible preview so the piece still uses a valid anchor.
                            currentOnDragStarted(
                                Offset(
                                    x = down.position.x.coerceIn(previewBounds.left, previewBounds.right) - previewBounds.left,
                                    y = down.position.y.coerceIn(previewBounds.top, previewBounds.bottom) - previewBounds.top,
                                ),
                            )
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrayPlaceholderSlot(
    modifier: Modifier = Modifier,
    width: Dp,
    height: Dp,
) {
    val placeholderWidth by animateDpAsState(
        targetValue = width,
        animationSpec = tween(durationMillis = TrayReorderAnimationDurationMillis),
        label = "trayPlaceholderWidth",
    )

    Box(
        modifier = modifier
            .width(placeholderWidth)
            .height(height),
    )
}

@Composable
private fun LoosePiecesTrayRow(
    scrollState: androidx.compose.foundation.ScrollState,
    remainingPieceIds: List<Int>,
    piecesById: Map<Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
    pieceCount: PieceCountOption,
    assetBitmap: ImageBitmap?,
    dragState: PieceDragState?,
    draggedPieceId: Int?,
    hiddenPieceIds: Set<Int>,
    pendingTrayRemovalPieceIds: Set<Int>,
    trayReferenceCellWidth: Dp,
    trayReferenceCellHeight: Dp,
    trayLayout: TrayLayout,
    trayRowCount: Int,
    trayTopRowSize: Int,
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
    val trayRows = remember(
        remainingPieceIds,
        dragState,
        trayLayout,
        trayRowCount,
        pendingTrayRemovalPieceIds,
    ) {
        createRenderedTrayRows(
            orderedPieceIds = remainingPieceIds,
            dragState = dragState,
            rowCount = trayRowCount,
            topRowSize = trayTopRowSize,
            pendingTrayRemovalPieceIds = pendingTrayRemovalPieceIds,
        )
    }
    val renderedRows = remember(trayRows, trayRowCount) {
        if (trayRowCount <= 1) listOf(trayRows.top) else listOf(trayRows.top, trayRows.bottom)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .onGloballyPositioned { coordinates ->
                onTrayRowBoundsChanged(
                    coordinates
                        .boundsInRoot()
                        .translatedBy(Offset(-overlayOriginInRoot.x, -overlayOriginInRoot.y)),
                )
            },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = trayHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(TrayRowSpacing),
        ) {
            renderedRows.forEach { rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(TrayItemSpacing)) {
                    rowEntries.forEach { entry ->
                        key(entry.key) {
                            when (entry) {
                                is TrayEntry.Piece -> {
                                    val pieceId = entry.pieceId
                                    val piece = piecesById.getValue(pieceId)
                                    val isHidden = pieceId in hiddenPieceIds
                                    val isPendingTrayRemoval = pieceId in pendingTrayRemovalPieceIds
                                    TrayPieceCard(
                                        pieceId = pieceId,
                                        pieceCount = pieceCount,
                                        piece = piece,
                                        assetBitmap = assetBitmap,
                                        modifier = Modifier,
                                        boardCellWidth = trayReferenceCellWidth,
                                        boardCellHeight = trayReferenceCellHeight,
                                        slotSize = DpSize(
                                            width = trayLayout.slotWidths.getValue(pieceId),
                                            height = trayLayout.slotHeight,
                                        ),
                                        previewOffset = trayLayout.offsets.getValue(pieceId),
                                        overlayOriginInRoot = overlayOriginInRoot,
                                        collapsed = entry.collapsed,
                                        ghosted =
                                            pieceId == draggedPieceId ||
                                                isPendingTrayRemoval,
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
                                        width = entry.width,
                                        height = trayLayout.slotHeight,
                                    )
                                }
                            }
                        }
                    }
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
    val debugHitboxStrokeWidthPx = with(LocalDensity.current) { 2.dp.toPx() }

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
            debugHitboxStrokeColor = if (ShowBoardPieceHitboxDebug) Color(0xFF00E5FF) else null,
            debugHitboxStrokeWidthPx = if (ShowBoardPieceHitboxDebug) debugHitboxStrokeWidthPx else 0f,
            debugHitboxPaddingPx = if (ShowBoardPieceHitboxDebug) BoardPieceRectHitPaddingPx else 0f,
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
    val debugHitboxStrokeWidthPx = with(LocalDensity.current) { 2.dp.toPx() }
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
                debugHitboxStrokeColor = if (ShowBoardPieceHitboxDebug) Color(0xFF00E5FF) else null,
                debugHitboxStrokeWidthPx = if (ShowBoardPieceHitboxDebug) debugHitboxStrokeWidthPx else 0f,
                debugHitboxPaddingPx = if (ShowBoardPieceHitboxDebug) BoardPieceRectHitPaddingPx else 0f,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private enum class GameplayBackgroundStyle(
    val id: String,
    val base: Color,
    val accent: Color,
    val threadLight: Color,
    val threadDark: Color,
    val headerScrim: Color,
    val boardBackground: Color,
    val emptyFill: Color,
    val boardOutline: Color,
    val boardOverlay: Color,
) {
    LIGHT_GREEN(
        id = "light_green",
        base = Color(0xFFBEEB9F),
        accent = Color(0xFFBEEB9F),
        threadLight = Color(0xFFF4FAEE),
        threadDark = Color(0xFF5A6652),
        headerScrim = Color(0xB3687461),
        boardBackground = Color(0x221F251B),
        emptyFill = Color(0x30262D21),
        boardOutline = Color(0x66FBFFF6),
        boardOverlay = Color(0x0F000000),
    ),
    LIGHT_BLUE(
        id = "light_blue",
        base = Color(0xFFACF0F2),
        accent = Color(0xFFACF0F2),
        threadLight = Color(0xFFF3F8FF),
        threadDark = Color(0xFF566474),
        headerScrim = Color(0xB3637182),
        boardBackground = Color(0x221C222A),
        emptyFill = Color(0x30232B34),
        boardOutline = Color(0x66F8FBFF),
        boardOverlay = Color(0x0F000000),
    ),
    LIGHT_YELLOW(
        id = "light_yellow",
        base = Color(0xFFFFF6A5),
        accent = Color(0xFFFFF6A5),
        threadLight = Color(0xFFFFF8EA),
        threadDark = Color(0xFF726344),
        headerScrim = Color(0xB37B6D4E),
        boardBackground = Color(0x22282218),
        emptyFill = Color(0x30312B1F),
        boardOutline = Color(0x66FFFDF6),
        boardOverlay = Color(0x0F000000),
    ),
    LIGHT_VIOLET(
        id = "light_violet",
        base = Color(0xFFB9A1DE),
        accent = Color(0xFFB9A1DE),
        threadLight = Color(0xFFF8F2FD),
        threadDark = Color(0xFF645A70),
        headerScrim = Color(0xB36D6079),
        boardBackground = Color(0x221F1C26),
        emptyFill = Color(0x30272230),
        boardOutline = Color(0x66FCF8FF),
        boardOverlay = Color(0x0F000000),
    ),
    LIGHT_GREY(
        id = "light_grey",
        base = Color(0xFFBEBDBF),
        accent = Color(0xFFBEBDBF),
        threadLight = Color(0xFFF6F6F4),
        threadDark = Color(0xFF5C5B59),
        headerScrim = Color(0xB3666664),
        boardBackground = Color(0x221F1F1E),
        emptyFill = Color(0x30272726),
        boardOutline = Color(0x66FEFEFC),
        boardOverlay = Color(0x0F000000),
    ),
    MEDIUM_GREEN(
        id = "medium_green",
        base = Color(0xFF689F38),
        accent = Color(0xFF689F38),
        threadLight = Color(0xFFE4F1D9),
        threadDark = Color(0xFF24311F),
        headerScrim = Color(0xB333472B),
        boardBackground = Color(0x2B151C12),
        emptyFill = Color(0x3B1D2618),
        boardOutline = Color(0x66F0FBE7),
        boardOverlay = Color(0x12000000),
    ),
    MEDIUM_BLUE(
        id = "medium_blue",
        base = Color(0xFF0288D1),
        accent = Color(0xFF0288D1),
        threadLight = Color(0xFFDDEBFA),
        threadDark = Color(0xFF1C2B3B),
        headerScrim = Color(0xB32A3D53),
        boardBackground = Color(0x2B101821),
        emptyFill = Color(0x3B172230),
        boardOutline = Color(0x66EEF6FF),
        boardOverlay = Color(0x12000000),
    ),
    MEDIUM_YELLOW(
        id = "medium_yellow",
        base = Color(0xFFCCB10D),
        accent = Color(0xFFCCB10D),
        threadLight = Color(0xFFFAF0CC),
        threadDark = Color(0xFF433515),
        headerScrim = Color(0xB3715922),
        boardBackground = Color(0x2B22190A),
        emptyFill = Color(0x3B30230E),
        boardOutline = Color(0x66FFF8E1),
        boardOverlay = Color(0x12000000),
    ),
    MEDIUM_VIOLET(
        id = "medium_violet",
        base = Color(0xFF673AB7),
        accent = Color(0xFF673AB7),
        threadLight = Color(0xFFEADDF6),
        threadDark = Color(0xFF30233C),
        headerScrim = Color(0xB3443153),
        boardBackground = Color(0x2B16111D),
        emptyFill = Color(0x3B20172A),
        boardOutline = Color(0x66F7EEFF),
        boardOverlay = Color(0x12000000),
    ),
    MEDIUM_GREY(
        id = "medium_grey",
        base = Color(0xFF616161),
        accent = Color(0xFF616161),
        threadLight = Color(0xFFE7E7EC),
        threadDark = Color(0xFF2A2A2E),
        headerScrim = Color(0xB33D3D42),
        boardBackground = Color(0x2B141416),
        emptyFill = Color(0x3B1C1C1F),
        boardOutline = Color(0x66F3F3F7),
        boardOverlay = Color(0x12000000),
    ),
    DARK_GREEN(
        id = "dark_green",
        base = Color(0xFF1C3014),
        accent = Color(0xFF1C3014),
        threadLight = Color(0xFFCDE0D1),
        threadDark = Color(0xFF090D0A),
        headerScrim = Color(0xB3101711),
        boardBackground = Color(0x33070A08),
        emptyFill = Color(0x44101411),
        boardOutline = Color(0x66E5F8E9),
        boardOverlay = Color(0x1B000000),
    ),
    DARK_BLUE(
        id = "dark_blue",
        base = Color(0xFF0C1630),
        accent = Color(0xFF0C1630),
        threadLight = Color(0xFFD2DEF1),
        threadDark = Color(0xFF070A10),
        headerScrim = Color(0xB30D121C),
        boardBackground = Color(0x33070A10),
        emptyFill = Color(0x44101722),
        boardOutline = Color(0x66E9F1FF),
        boardOverlay = Color(0x1B000000),
    ),
    DARK_YELLOW(
        id = "dark_yellow",
        base = Color(0xFF1D190B),
        accent = Color(0xFF1D190B),
        threadLight = Color(0xFFE8DEC0),
        threadDark = Color(0xFF0E0B07),
        headerScrim = Color(0xB31A150D),
        boardBackground = Color(0x330F0C07),
        emptyFill = Color(0x4418140D),
        boardOutline = Color(0x66FBF1D8),
        boardOverlay = Color(0x1B000000),
    ),
    DARK_VIOLET(
        id = "dark_violet",
        base = Color(0xFF1D102C),
        accent = Color(0xFF1D102C),
        threadLight = Color(0xFFE1D4ED),
        threadDark = Color(0xFF0C0810),
        headerScrim = Color(0xB3140E19),
        boardBackground = Color(0x330C0810),
        emptyFill = Color(0x44160F1C),
        boardOutline = Color(0x66F5EAFF),
        boardOverlay = Color(0x1B000000),
    ),
    DARK_GREY(
        id = "dark_grey",
        base = Color(0xFF151618),
        accent = Color(0xFF151618),
        threadLight = Color(0xFFE0E0E1),
        threadDark = Color(0xFF090909),
        headerScrim = Color(0xB3131314),
        boardBackground = Color(0x33090909),
        emptyFill = Color(0x44121213),
        boardOutline = Color(0x66F4F4F5),
        boardOverlay = Color(0x1B000000),
    );

    companion object {
        fun fromId(id: String): GameplayBackgroundStyle =
            entries.firstOrNull { it.id == id } ?: MEDIUM_GREY
    }
}

@Composable
private fun BackgroundSwatchButton(
    background: GameplayBackgroundStyle,
    patternBitmap: ImageBitmap?,
    onClick: () -> Unit,
    contentDescription: String,
    selected: Boolean = true,
    showCog: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(shape)
            .gameplayFabricBackground(
                background = background,
                patternBitmap = patternBitmap,
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = Color.White.copy(alpha = if (selected) 0.92f else 0.55f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        if (showCog) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.95f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BackgroundPickerPanel(
    selectedBackground: GameplayBackgroundStyle,
    patternBitmap: ImageBitmap?,
    onBackgroundSelected: (GameplayBackgroundStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(selectedBackground.headerScrim.copy(alpha = 0.96f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.gameplay_background_picker_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        GameplayBackgroundStyle.entries
            .chunked(5)
            .forEach { rowBackgrounds ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowBackgrounds.forEach { background ->
                        val backgroundIndex = GameplayBackgroundStyle.entries.indexOf(background)
                        BackgroundSwatchButton(
                            background = background,
                            patternBitmap = patternBitmap,
                            onClick = { onBackgroundSelected(background) },
                            contentDescription = stringResource(
                                R.string.gameplay_background_sample,
                                backgroundIndex + 1,
                            ),
                            selected = background == selectedBackground,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }
    }
}

private fun Modifier.gameplayFabricBackground(
    background: GameplayBackgroundStyle,
    patternBitmap: ImageBitmap?,
): Modifier = drawWithCache {
    val baseBrush = Brush.linearGradient(
        colors = listOf(background.base, background.accent),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val vignetteBrush = Brush.radialGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.18f),
        ),
        center = Offset(size.width * 0.55f, size.height * 0.42f),
        radius = size.maxDimension * 0.92f,
    )

    onDrawBehind {
        drawRect(brush = baseBrush)
        if (patternBitmap != null) {
            val patternWidth = patternBitmap.width.toFloat()
            val patternHeight = patternBitmap.height.toFloat()
            var y = 0f
            while (y < size.height) {
                var x = 0f
                while (x < size.width) {
                    drawImage(
                        image = patternBitmap,
                        topLeft = Offset(x, y),
                        alpha = 1f,
                    )
                    x += patternWidth
                }
                y += patternHeight
            }
        }

        drawRect(brush = vignetteBrush)
    }
}

private fun Modifier.gameplayBoardSurface(
    background: GameplayBackgroundStyle,
): Modifier = drawWithCache {
    val insetStroke = size.minDimension * 0.016f

    onDrawBehind {
        drawRect(color = background.boardOverlay.copy(alpha = background.boardOverlay.alpha * 0.45f))
        repeat(6) { layer ->
            val inset = layer * insetStroke * 0.36f
            drawRect(
                color = Color.Black.copy(alpha = 0.2f / (layer + 1f)),
                topLeft = Offset(inset, inset),
                size = Size(
                    width = size.width - (inset * 2f),
                    height = size.height - (inset * 2f),
                ),
                style = Stroke(width = insetStroke * 0.82f),
            )
        }
    }
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
        localPoint.x < -BoardPieceRectHitPaddingPx ||
        localPoint.y < -BoardPieceRectHitPaddingPx ||
        localPoint.x > bounds.width + BoardPieceRectHitPaddingPx ||
        localPoint.y > bounds.height + BoardPieceRectHitPaddingPx
    ) {
        false
    } else {
        true
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
