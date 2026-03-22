package com.puzzle.jigsaw.core.designsystem.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

const val PuzzleImageAspectRatio = 4f / 5f

@Composable
fun rememberPuzzleAssetBitmap(assetPath: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap = produceState<ImageBitmap?>(initialValue = null, assetPath) {
        value = runCatching {
            context.assets.open(assetPath).use(BitmapFactory::decodeStream)?.asImageBitmap()
        }.getOrNull()
    }

    return bitmap.value
}

@Composable
fun PuzzleAssetImage(
    assetPath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap = rememberPuzzleAssetBitmap(assetPath)

    if (bitmap == null) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {}
        return
    }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier.fillMaxSize(),
    )
}
