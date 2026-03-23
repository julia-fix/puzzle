package com.puzzle.jigsaw.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.puzzle.jigsaw.core.designsystem.components.PuzzleAssetImage
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.RecentPuzzleSession
import kotlin.math.roundToInt

@Composable
fun GalleryScreen(
    images: List<PuzzleImage>,
    recentSessions: List<RecentPuzzleSession>,
    onImageSelected: (PuzzleImage) -> Unit,
) {
    val filters = remember(images) {
        listOf(CategoryFilter(id = null, label = "All")) +
            images
                .distinctBy(PuzzleImage::categoryId)
                .map { image ->
                    CategoryFilter(
                        id = image.categoryId,
                        label = image.categoryName,
                    )
                }
    }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(filters) {
        if (filters.none { it.id == selectedCategoryId }) {
            selectedCategoryId = null
        }
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
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Choose an image",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

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
            PuzzleAssetImage(
                assetPath = image.assetPath,
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

private data class CategoryFilter(
    val id: String?,
    val label: String,
)
