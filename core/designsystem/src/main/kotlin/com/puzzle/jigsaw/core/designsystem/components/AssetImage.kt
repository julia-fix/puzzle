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
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val PuzzleImageAspectRatio = 4f / 5f

@Composable
fun rememberPuzzleBitmap(
    path: String,
    storage: PuzzleImageStorage,
): ImageBitmap? {
    val context = LocalContext.current
    val bitmap = produceState<ImageBitmap?>(initialValue = null, path, storage) {
        value = loadPuzzleImageResource(
            path = path,
            storage = storage,
            openStream = { resolvedPath, resolvedStorage ->
                when (resolvedStorage) {
                    PuzzleImageStorage.ASSET -> context.assets.open(resolvedPath)
                    PuzzleImageStorage.FILE -> File(resolvedPath).inputStream()
                }
            },
            decodeResource = { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            },
        )
    }

    return bitmap.value
}

internal suspend fun <T> loadPuzzleImageResource(
    path: String,
    storage: PuzzleImageStorage,
    decodeDispatcher: CoroutineDispatcher = Dispatchers.IO,
    openStream: (path: String, storage: PuzzleImageStorage) -> InputStream,
    decodeResource: (InputStream) -> T?,
): T? = withContext(decodeDispatcher) {
    runCatching {
        openStream(path, storage).use(decodeResource)
    }.getOrNull()
}

@Composable
fun rememberPuzzleAssetBitmap(assetPath: String): ImageBitmap? =
    rememberPuzzleBitmap(
        path = assetPath,
        storage = PuzzleImageStorage.ASSET,
    )

@Composable
fun PuzzleBitmapImage(
    path: String,
    storage: PuzzleImageStorage,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmap = rememberPuzzleBitmap(
        path = path,
        storage = storage,
    )

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

@Composable
fun PuzzleAssetImage(
    assetPath: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    PuzzleBitmapImage(
        path = assetPath,
        storage = PuzzleImageStorage.ASSET,
        modifier = modifier,
        contentScale = contentScale,
    )
}
