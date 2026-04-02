package com.puzzle.jigsaw.feature.gallery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.puzzle.jigsaw.core.designsystem.components.PuzzleBitmapImage
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.RecentPuzzleSession
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class GalleryUploadCropState(
    val bitmap: ImageBitmap,
    val title: String,
)

data class GalleryCropSelection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Composable
fun GalleryScreen(
    images: List<PuzzleImage>,
    recentSessions: List<RecentPuzzleSession>,
    onImageSelected: (PuzzleImage) -> Unit,
    onUploadClick: () -> Unit = {},
    uploadCropState: GalleryUploadCropState? = null,
    onUploadCropDismiss: () -> Unit = {},
    onUploadCropConfirm: (GalleryCropSelection) -> Unit = {},
    isUploadInProgress: Boolean = false,
    message: String? = null,
    onMessageShown: () -> Unit = {},
) {
    val allLabel = stringResource(R.string.gallery_filter_all)
    val uploadsLabel = stringResource(R.string.gallery_category_uploads)
    val snackbarHostState = remember { SnackbarHostState() }
    val filters = remember(images, allLabel, uploadsLabel) {
        listOf(CategoryFilter(id = null, label = allLabel, sortOrder = Int.MIN_VALUE)) +
            images
                .groupBy(PuzzleImage::categoryId)
                .values
                .mapNotNull { categoryImages ->
                    val representative = categoryImages.minWithOrNull(
                        compareBy<PuzzleImage>(PuzzleImage::categorySortOrder)
                            .thenBy(PuzzleImage::sortOrder),
                    ) ?: return@mapNotNull null
                    CategoryFilter(
                        id = representative.categoryId,
                        label = if (representative.categoryId == UploadsCategoryId) {
                            uploadsLabel
                        } else {
                            representative.categoryName
                        },
                        sortOrder = representative.categorySortOrder,
                    )
                }
                .sortedWith(compareBy(CategoryFilter::sortOrder).thenBy(CategoryFilter::label))
    }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(filters) {
        if (filters.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
    }
    LaunchedEffect(message) {
        if (message.isNullOrBlank()) return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    val filteredImages = remember(images, selectedCategoryId) {
        images.filter { image ->
            selectedCategoryId == null || image.categoryId == selectedCategoryId
        }
    }
    val orderedImages = remember(filteredImages, recentSessions) {
        orderCategoryImages(filteredImages, recentSessions)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.gallery_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onUploadClick,
                        enabled = !isUploadInProgress && uploadCropState == null,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        if (isUploadInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.gallery_upload_action),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = filters, key = { it.id ?: "all" }) { filter ->
                    FilterChip(
                        selected = filter.id == selectedCategoryId,
                        onClick = {
                            selectedCategoryId = filter.id
                        },
                        label = {
                            Text(filter.label)
                        },
                    )
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = orderedImages, key = { it.image.id }) { imageProgress ->
                    ImageTile(
                        image = imageProgress.image,
                        completionRatio = imageProgress.completionRatio,
                        onClick = { onImageSelected(imageProgress.image) },
                    )
                }
            }
        }
    }

    uploadCropState?.let { state ->
        UploadCropDialog(
            state = state,
            isImporting = isUploadInProgress,
            onDismiss = onUploadCropDismiss,
            onConfirm = onUploadCropConfirm,
        )
    }
}

@Composable
private fun ImageTile(
    image: PuzzleImage,
    completionRatio: Float,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PuzzleBitmapImage(
                path = image.previewPath,
                storage = image.storage,
                modifier = Modifier.fillMaxSize(),
            )
            if (completionRatio > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${(completionRatio.coerceIn(0f, 1f) * 100f).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadCropDialog(
    state: GalleryUploadCropState,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (GalleryCropSelection) -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (!isImporting) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.gallery_upload_crop_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.gallery_upload_crop_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CropPreview(
                    bitmap = state.bitmap,
                    onConfirm = onConfirm,
                    isImporting = isImporting,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun CropPreview(
    bitmap: ImageBitmap,
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (GalleryCropSelection) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 320.dp, max = 560.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        val viewportWidth = constraints.maxWidth.toFloat()
        val viewportHeight = constraints.maxHeight.toFloat()
        val cropPaddingPx = with(density) { 20.dp.toPx() }
        val cropWidth = min(
            viewportWidth - (cropPaddingPx * 2f),
            (viewportHeight - (cropPaddingPx * 2f)) * CropAspectRatio,
        ).coerceAtLeast(1f)
        val cropHeight = (cropWidth / CropAspectRatio).coerceAtLeast(1f)
        val cropLeft = (viewportWidth - cropWidth) / 2f
        val cropTop = (viewportHeight - cropHeight) / 2f
        val baseScale = max(
            cropWidth / bitmap.width.toFloat(),
            cropHeight / bitmap.height.toFloat(),
        )
        var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }

        val imageWidthPx = bitmap.width.toFloat() * baseScale
        val imageHeightPx = bitmap.height.toFloat() * baseScale
        val imageLeft = ((viewportWidth - imageWidthPx) / 2f) + offset.x
        val imageTop = ((viewportHeight - imageHeightPx) / 2f) + offset.y

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, cropWidth, cropHeight) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = clampCropOffset(
                            offset = offset + dragAmount,
                            bitmapWidth = bitmap.width.toFloat(),
                            bitmapHeight = bitmap.height.toFloat(),
                            baseScale = baseScale,
                            cropWidth = cropWidth,
                            cropHeight = cropHeight,
                        )
                    }
                },
        ) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset {
                        IntOffset(
                            x = offset.x.roundToInt(),
                            y = offset.y.roundToInt(),
                        )
                    }
                    .requiredSize(
                        width = with(density) { imageWidthPx.toDp() },
                        height = with(density) { imageHeightPx.toDp() },
                    ),
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val scrimColor = Color.Black.copy(alpha = 0.5f)
                drawRect(
                    color = scrimColor,
                    size = Size(width = size.width, height = cropTop),
                )
                drawRect(
                    color = scrimColor,
                    topLeft = Offset(0f, cropTop),
                    size = Size(width = cropLeft, height = cropHeight),
                )
                drawRect(
                    color = scrimColor,
                    topLeft = Offset(cropLeft + cropWidth, cropTop),
                    size = Size(width = size.width - cropLeft - cropWidth, height = cropHeight),
                )
                drawRect(
                    color = scrimColor,
                    topLeft = Offset(0f, cropTop + cropHeight),
                    size = Size(width = size.width, height = size.height - cropTop - cropHeight),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(cropLeft, cropTop),
                    size = Size(cropWidth, cropHeight),
                    style = Stroke(width = with(density) { 3.dp.toPx() }),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(with(density) { 20.dp.toPx() }),
                )

                val gridColor = Color.White.copy(alpha = 0.32f)
                val thirdWidth = cropWidth / 3f
                val thirdHeight = cropHeight / 3f
                repeat(2) { index ->
                    val verticalX = cropLeft + (thirdWidth * (index + 1))
                    drawLine(
                        color = gridColor,
                        start = Offset(verticalX, cropTop),
                        end = Offset(verticalX, cropTop + cropHeight),
                        strokeWidth = with(density) { 1.dp.toPx() },
                    )
                    val horizontalY = cropTop + (thirdHeight * (index + 1))
                    drawLine(
                        color = gridColor,
                        start = Offset(cropLeft, horizontalY),
                        end = Offset(cropLeft + cropWidth, horizontalY),
                        strokeWidth = with(density) { 1.dp.toPx() },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(
                enabled = !isImporting,
                onClick = onDismiss,
            ) {
                Text(text = stringResource(R.string.gallery_upload_cancel))
            }
            Button(
                enabled = !isImporting,
                onClick = {
                    onConfirm(
                        GalleryCropSelection(
                            left = ((cropLeft - imageLeft) / imageWidthPx).coerceIn(0f, 1f),
                            top = ((cropTop - imageTop) / imageHeightPx).coerceIn(0f, 1f),
                            right = ((cropLeft + cropWidth - imageLeft) / imageWidthPx).coerceIn(0f, 1f),
                            bottom = ((cropTop + cropHeight - imageTop) / imageHeightPx).coerceIn(0f, 1f),
                        ),
                    )
                },
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = stringResource(R.string.gallery_upload_use_image))
                }
            }
        }
    }
}

private fun clampCropOffset(
    offset: Offset,
    bitmapWidth: Float,
    bitmapHeight: Float,
    baseScale: Float,
    cropWidth: Float,
    cropHeight: Float,
): Offset {
    val renderedWidth = bitmapWidth * baseScale
    val renderedHeight = bitmapHeight * baseScale
    val maxOffsetX = ((renderedWidth - cropWidth) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((renderedHeight - cropHeight) / 2f).coerceAtLeast(0f)
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY),
    )
}

private data class CategoryFilter(
    val id: String?,
    val label: String,
    val sortOrder: Int,
)

private const val UploadsCategoryId = "uploads"
private const val CropAspectRatio = 4f / 5f
