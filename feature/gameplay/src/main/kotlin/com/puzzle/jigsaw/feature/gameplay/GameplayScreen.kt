package com.puzzle.jigsaw.feature.gameplay

import android.content.Context
import android.graphics.Rect as AndroidRect
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.Stable
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
import com.puzzle.jigsaw.domain.jigsaw.movePieceCluster
import com.puzzle.jigsaw.domain.jigsaw.reorderTrayPieces
import com.puzzle.jigsaw.domain.jigsaw.returnPieceClusterToTray
import com.puzzle.jigsaw.domain.jigsaw.snapPieceCluster
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
    var pendingTrayReturnAnimation by remember { mutableStateOf<PendingTrayReturnAnimation?>(null) }
    var trayReturnAnimationState by remember { mutableStateOf<TrayReturnAnimationState?>(null) }
    var trayReturnAnimationToken by remember { mutableIntStateOf(0) }
    var pendingTrayRemovalPieceIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val trayReturnProgress = remember { Animatable(1f) }
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

    fun playPlacementFeedback(updatedState: JigsawSessionState) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        placementSoundPlayer.play(
            sound = placementSoundForCompletionRatio(updatedState.completionRatio),
        )
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
                val returningTrayPieceId = trayReturnAnimationState?.pieceId ?: pendingTrayReturnAnimation?.pieceId
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
                                            trayRowCount = trayRowCount,
                                            trayTopRowSize = trayTopRowSize,
                                            trayRowSpacingPx = with(density) { TrayRowSpacing.toPx() },
                                            piecesById = latestPiecesById,
                                            onTrayTopRowSizeChanged = { trayTopRowSizeHint = it },
                                            onStartTrayReturnAnimation = { animation ->
                                                pendingTrayReturnAnimation = animation
                                            },
                                            onSuccessfulPlacement = ::playPlacementFeedback,
                                            onCommit = onSessionStateChange,
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
                        LoosePiecesTrayRow(
                            scrollState = trayScrollState,
                            remainingPieceIds = sessionState.remainingPieceIds,
                            piecesById = piecesById,
                            pieceCount = sessionState.pieceCount,
                            assetBitmap = assetBitmap,
                            dragState = dragState,
                            draggedPieceId = draggedPieceId,
                            returningTrayPieceId = returningTrayPieceId,
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
                                    trayRowCount = trayRowCount,
                                    trayTopRowSize = trayTopRowSize,
                                    trayRowSpacingPx = with(density) { TrayRowSpacing.toPx() },
                                    piecesById = piecesById,
                                    onTrayTopRowSizeChanged = { trayTopRowSizeHint = it },
                                    onStartTrayReturnAnimation = { animation ->
                                        pendingTrayReturnAnimation = animation
                                    },
                                    onSuccessfulPlacement = ::playPlacementFeedback,
                                    onCommit = onSessionStateChange,
                                    onCancel = { dragState = null },
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
    trayRowCount: Int,
    trayTopRowSize: Int,
    trayRowSpacingPx: Float,
    piecesById: Map<Int, com.puzzle.jigsaw.domain.jigsaw.JigsawPieceLayout>,
    onTrayTopRowSizeChanged: (Int) -> Unit,
    onStartTrayReturnAnimation: (PendingTrayReturnAnimation) -> Unit,
    onSuccessfulPlacement: (JigsawSessionState) -> Unit,
    onCommit: (JigsawSessionState) -> Unit,
    onCancel: () -> Unit,
) {
    val piece = piecesById.getValue(dragState.pieceId)
    val currentTopLeft = dragState.currentSelectedPieceTopLeft(trayRowBounds)
    val boardAlignedTopLeft = dragState.currentBoardAlignedTopLeft(trayRowBounds)
    val boardAlignedCenter = dragState.currentBoardAlignedCenter(trayRowBounds)

    if (isDragOverTray(dragState, trayRowBounds)) {
        if (dragState.source == PieceDragSource.BOARD && dragState.clusterPieceIds.size > 1) {
            onCancel()
            return
        }
        val trayState = if (dragState.source == PieceDragSource.BOARD) {
            returnPieceClusterToTray(sessionState, dragState.pieceId)
        } else {
            sessionState
        }
        val resolvedTrayDropTarget = calculateTrayDropTarget(
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
            indexInRow = if (dragState.source == PieceDragSource.BOARD) {
                sessionState.remainingPieceIds.size
            } else {
                trayState.remainingPieceIds.size
            },
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
                dropTarget = resolvedTrayDropTarget,
            )
        } else {
            reorderTrayRows(
                orderedPieceIds = trayState.remainingPieceIds,
                dragState = dragState,
                dropTarget = resolvedTrayDropTarget,
                rowCount = trayRowCount,
                topRowSize = trayTopRowSize,
            )
        }
        val reorderedRemaining = reorderedRows.top + reorderedRows.bottom
        val reorderedState = applyRemainingPieceOrder(trayState, reorderedRemaining)
        onTrayTopRowSizeChanged(reorderedRows.top.size)
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
    val trayAwareMovedState = if (dragState.source == PieceDragSource.TRAY) {
        val removedRows = removeFromTrayRows(
            orderedPieceIds = sessionState.remainingPieceIds,
            removedPieceIds = dragState.clusterPieceIds,
            rowCount = trayRowCount,
            topRowSize = trayTopRowSize,
        )
        onTrayTopRowSizeChanged(removedRows.top.size)
        applyRemainingPieceOrder(
            state = movedState,
            reorderedRemaining = removedRows.top + removedRows.bottom,
        )
    } else {
        movedState
    }
    val snapOutcome = snapPieceCluster(
        state = trayAwareMovedState,
        pieceId = dragState.pieceId,
        snapThreshold = BoardSnapThreshold,
    )
    if (!snapOutcome.didSnap) {
        onCommit(trayAwareMovedState)
        return
    }
    onSuccessfulPlacement(snapOutcome.state)
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
    returningTrayPieceId: Int?,
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
    val trayDropTarget = calculateTrayDropTarget(
        dragState = dragState,
        trayRowBounds = trayRowBounds,
        orderedPieceIds = remainingPieceIds,
        trayItemBounds = trayItemBounds,
        rowCount = trayRowCount,
        topRowSize = trayTopRowSize,
        traySlotHeightPx = with(LocalDensity.current) { trayLayout.slotHeight.toPx() },
        trayRowSpacingPx = with(LocalDensity.current) { TrayRowSpacing.toPx() },
    )
    val trayRows = remember(
        remainingPieceIds,
        dragState,
        trayDropTarget,
        trayLayout,
        trayRowCount,
        pendingTrayRemovalPieceIds,
    ) {
        createRenderedTrayRows(
            orderedPieceIds = remainingPieceIds,
            dragState = dragState,
            dropTarget = trayDropTarget,
            traySlotWidths = trayLayout.slotWidths,
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
                                                pieceId == returningTrayPieceId ||
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

private sealed interface TrayEntry {
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

private data class TrayLayout(
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
    val scrollOffset: Int,
)

private data class TrayLayoutPx(
    val slotHeight: Float,
    val slotWidths: Map<Int, Float>,
    val offsets: Map<Int, Offset>,
)

private data class TrayRows<T>(
    val top: List<T>,
    val bottom: List<T>,
)

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

private data class TrayDropTarget(
    val rowIndex: Int,
    val indexInRow: Int,
)

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

private fun stableTrayRowsForDrag(
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

private fun splitTrayPieceIds(
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

private fun createRenderedTrayRows(
    orderedPieceIds: List<Int>,
    dragState: PieceDragState?,
    dropTarget: TrayDropTarget?,
    traySlotWidths: Map<Int, Dp>,
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
        )
            .let { rows -> rows.top + rows.bottom }
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

    return TrayRows(
        top = top,
        bottom = bottom,
    )
}

private fun normalizedTrayTopRowSize(
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

private fun reorderTrayRows(
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

private fun insertIntoTrayRows(
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

private fun visibleTrayRowsFromBounds(
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

private fun removeFromTrayRows(
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

private fun applyRemainingPieceOrder(
    state: JigsawSessionState,
    reorderedRemaining: List<Int>,
): JigsawSessionState {
    val remainingSet = state.remainingPieceIds.toSet()
    val hiddenPieceIdsInOrder = state.pieceOrder.filterNot { it in remainingSet }
    return state.copy(
        pieceOrder = reorderedRemaining + hiddenPieceIdsInOrder,
    )
}

private fun calculateTrayContentWidth(
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

private fun calculateTrayDropTarget(
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

private fun isDragOverTray(
    dragState: PieceDragState,
    trayRowBounds: Rect?,
): Boolean {
    val bounds = trayRowBounds ?: return false
    return when (dragState.source) {
        PieceDragSource.TRAY -> bounds.contains(dragState.currentPointer)
        PieceDragSource.BOARD -> bounds.contains(dragState.currentSelectedPieceCenter(trayRowBounds))
    }
}

private fun resolveTrayReturnAnimation(
    pieceId: Int,
    startTopLeft: Offset,
    orderedPieceIds: List<Int>,
    trayItemBounds: Map<Int, Rect>,
    trayLayoutPx: TrayLayoutPx,
    trayRowBounds: Rect?,
    trayItemSpacingPx: Float,
    rowCount: Int,
    topRowSize: Int,
    rowSpacingPx: Float,
): PendingTrayReturnAnimation? {
    val movedOffset = trayLayoutPx.offsets.getValue(pieceId)
    val stablePieceIds = orderedPieceIds.filterNot { it == pieceId }
    val rows = splitTrayPieceIds(orderedPieceIds, rowCount, topRowSize)
    val targetRowIndex = if (pieceId in rows.bottom) 1 else 0
    val targetRowPieceIds = if (targetRowIndex == 0) rows.top else rows.bottom
    val targetIndexInRow = targetRowPieceIds.indexOf(pieceId).coerceAtLeast(0)
    val stableRowPieceIds = targetRowPieceIds.filterNot { it == pieceId }
    val previousPieceId = stableRowPieceIds.getOrNull(targetIndexInRow - 1)
    val nextPieceId = stableRowPieceIds.getOrNull(targetIndexInRow)

    val topRowTop = rows.top
        .firstNotNullOfOrNull { rowPieceId ->
            trayItemBounds[rowPieceId]?.top?.minus(trayLayoutPx.offsets.getValue(rowPieceId).y)
        }
        ?: rows.bottom.firstNotNullOfOrNull { rowPieceId ->
            trayItemBounds[rowPieceId]
                ?.top
                ?.minus(trayLayoutPx.offsets.getValue(rowPieceId).y)
                ?.minus(trayLayoutPx.slotHeight + rowSpacingPx)
        }
        ?: trayRowBounds?.top
        ?: (startTopLeft.y - movedOffset.y)

    val targetSlotTop = topRowTop + (targetRowIndex * (trayLayoutPx.slotHeight + rowSpacingPx))

    val targetSlotLeft = when {
        previousPieceId != null -> {
            val previousBounds = trayItemBounds[previousPieceId] ?: return null
            (previousBounds.left - trayLayoutPx.offsets.getValue(previousPieceId).x) +
                trayLayoutPx.slotWidths.getValue(previousPieceId) +
                trayItemSpacingPx
        }

        nextPieceId != null -> {
            val nextBounds = trayItemBounds[nextPieceId] ?: return null
            (nextBounds.left - trayLayoutPx.offsets.getValue(nextPieceId).x) -
                trayLayoutPx.slotWidths.getValue(pieceId) -
                trayItemSpacingPx
        }

        stablePieceIds.isNotEmpty() -> {
            val referencePieceId = stablePieceIds.first()
            val referenceBounds = trayItemBounds[referencePieceId] ?: return null
            referenceBounds.left - trayLayoutPx.offsets.getValue(referencePieceId).x
        }

        trayRowBounds != null -> trayRowBounds.left
        else -> startTopLeft.x - movedOffset.x
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

@Composable
private fun rememberGameplaySoundPlayer(context: Context): GameplaySoundPlayer {
    val appContext = context.applicationContext
    val soundPlayer = remember(appContext) {
        SoundPoolGameplaySoundPlayer.create(appContext)
    }
    DisposableEffect(soundPlayer) {
        onDispose {
            soundPlayer.release()
        }
    }
    return soundPlayer
}

internal fun placementSoundForCompletionRatio(completionRatio: Float): PlacementSound =
    if (completionRatio >= 1f) PlacementSound.LEVEL_UP else PlacementSound.POP

private interface GameplaySoundPlayer {
    fun play(sound: PlacementSound)
    fun release()
}

internal enum class PlacementSound {
    POP,
    LEVEL_UP,
}

private class SoundPoolGameplaySoundPlayer(
    private val soundPool: SoundPool,
    private val popSoundId: Int,
    private val levelUpSoundId: Int,
    private val loadedSoundIds: MutableSet<Int>,
) : GameplaySoundPlayer {
    override fun play(sound: PlacementSound) {
        val soundId = when (sound) {
            PlacementSound.POP -> popSoundId
            PlacementSound.LEVEL_UP -> levelUpSoundId
        }
        if (!isLoaded(soundId)) return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    override fun release() {
        soundPool.release()
    }

    private fun isLoaded(soundId: Int): Boolean = synchronized(loadedSoundIds) {
        soundId in loadedSoundIds
    }

    companion object {
        fun create(context: Context): GameplaySoundPlayer = runCatching {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val soundPool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build()
            try {
                val loadedSoundIds = mutableSetOf<Int>()
                soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        synchronized(loadedSoundIds) {
                            loadedSoundIds += sampleId
                        }
                    }
                }
                val popSoundId = context.assets.openFd(PopSoundAssetPath).use { fileDescriptor ->
                    soundPool.load(fileDescriptor, 1)
                }
                val levelUpSoundId = context.assets.openFd(LevelUpSoundAssetPath).use { fileDescriptor ->
                    soundPool.load(fileDescriptor, 1)
                }
                SoundPoolGameplaySoundPlayer(
                    soundPool = soundPool,
                    popSoundId = popSoundId,
                    levelUpSoundId = levelUpSoundId,
                    loadedSoundIds = loadedSoundIds,
                )
            } catch (error: Throwable) {
                soundPool.release()
                throw error
            }
        }.getOrElse { NoOpGameplaySoundPlayer }
    }
}

private object NoOpGameplaySoundPlayer : GameplaySoundPlayer {
    override fun play(sound: PlacementSound) = Unit

    override fun release() = Unit
}

private val BoardInnerPadding = 0.dp
private val ScreenHorizontalPadding = 16.dp
private val TrayItemSpacing = 2.dp
private val TrayRowSpacing = 4.dp
private const val TrayWidthSlackFactor = 0.2f
private const val TrayReferenceColumns = 6
private const val TrayReferenceRows = 8
private const val TrayReorderAnimationDurationMillis = 220
private const val TrayReturnAnimationDurationMillis = 220
private const val BoardSnapThreshold = 0.28f
private const val TrayPieceDragVerticalBias = 0.65f
private const val TrayPieceLiftOffsetPx = 150f
private const val TrayPieceResizeTravelFraction = 0.22f
private const val TraySecondRowMinVisibleFraction = 2f / 3f
private const val BoardPieceRectHitPaddingPx = 20f
private const val ShowBoardPieceHitboxDebug = false
private const val PopSoundAssetPath = "sound/pop.mp3"
private const val LevelUpSoundAssetPath = "sound/levelup.mp3"
