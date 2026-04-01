package com.puzzle.jigsaw.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.puzzle.jigsaw.R
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.data.catalog.AssetPuzzleCatalogRepository
import com.puzzle.jigsaw.data.catalog.PreparedUserImageImport
import com.puzzle.jigsaw.data.progress.DataStoreJigsawProgressStore
import com.puzzle.jigsaw.domain.jigsaw.JigsawCatalog
import com.puzzle.jigsaw.feature.gameplay.GameplayRoute
import com.puzzle.jigsaw.feature.gallery.GalleryScreen
import com.puzzle.jigsaw.feature.gallery.GalleryUploadCropState
import com.puzzle.jigsaw.feature.gallery.R as GalleryR
import com.puzzle.jigsaw.feature.piececount.PieceCountScreen
import kotlinx.coroutines.launch

@Composable
fun JigsawNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalogRepository = remember(context.applicationContext) {
        AssetPuzzleCatalogRepository(context.applicationContext)
    }
    val progressStore = remember(context.applicationContext) {
        DataStoreJigsawProgressStore.create(context.applicationContext)
    }
    val pieceCounts = remember { JigsawCatalog.pieceCountOptions() }
    val recentSessions = progressStore.recentSessions().collectAsStateWithLifecycle(initialValue = emptyList())

    var images by remember { mutableStateOf(emptyList<PuzzleImage>()) }
    var pendingUploadImport by remember { mutableStateOf<PendingUploadImport?>(null) }
    var isUploadInProgress by remember { mutableStateOf(false) }
    var galleryMessage by remember { mutableStateOf<String?>(null) }
    var pendingImportedImageId by remember { mutableStateOf<String?>(null) }

    suspend fun refreshImages(): List<PuzzleImage> {
        val loadedImages = catalogRepository.loadImages()
        progressStore.migrateLegacyImageIds(legacyImageIdMappings(loadedImages))
        images = loadedImages
        return loadedImages
    }

    LaunchedEffect(catalogRepository, progressStore) {
        refreshImages()
        catalogRepository.syncIfNeeded()?.let { syncedImages ->
            progressStore.migrateLegacyImageIds(legacyImageIdMappings(syncedImages))
            images = syncedImages
        }
    }

    LaunchedEffect(images, pendingImportedImageId) {
        val importedImageId = pendingImportedImageId ?: return@LaunchedEffect
        if (images.none { it.id == importedImageId }) {
            return@LaunchedEffect
        }
        pendingImportedImageId = null
        navController.navigate(
            JigsawDestination.pieceCount(importedImageId),
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { selectedUri ->
        if (selectedUri == null) {
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            isUploadInProgress = true
            val preparedImport = catalogRepository.prepareUserImageImport(selectedUri)
            pendingUploadImport = preparedImport?.let { prepared ->
                PendingUploadImport(
                    preparedImport = prepared,
                    cropState = GalleryUploadCropState(
                        bitmap = prepared.bitmap.asImageBitmap(),
                        title = prepared.title,
                    ),
                )
            }
            if (preparedImport == null) {
                galleryMessage = context.getString(GalleryR.string.gallery_upload_prepare_error)
            }
            isUploadInProgress = false
        }
    }

    NavHost(
        navController = navController,
        startDestination = JigsawDestination.galleryRoute,
    ) {
        composable(JigsawDestination.galleryRoute) {
            GalleryScreen(
                images = images,
                recentSessions = recentSessions.value,
                onImageSelected = { image ->
                    navController.navigate(JigsawDestination.pieceCount(image.id))
                },
                onUploadClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                uploadCropState = pendingUploadImport?.cropState,
                onUploadCropDismiss = {
                    if (!isUploadInProgress) {
                        pendingUploadImport = null
                    }
                },
                onUploadCropConfirm = { selection ->
                    val pendingImport = pendingUploadImport ?: return@GalleryScreen
                    scope.launch {
                        isUploadInProgress = true
                        val importedImage = catalogRepository.importPreparedUserImage(
                            preparedImport = pendingImport.preparedImport,
                            cropLeft = selection.left,
                            cropTop = selection.top,
                            cropRight = selection.right,
                            cropBottom = selection.bottom,
                        )
                        pendingUploadImport = null
                        if (importedImage == null) {
                            galleryMessage = context.getString(GalleryR.string.gallery_upload_import_error)
                        } else {
                            refreshImages()
                            pendingImportedImageId = importedImage.id
                        }
                        isUploadInProgress = false
                    }
                },
                isUploadInProgress = isUploadInProgress,
                message = galleryMessage,
                onMessageShown = {
                    galleryMessage = null
                },
            )
        }
        composable(
            route = JigsawDestination.pieceCountRoute,
            arguments = listOf(navArgument(JigsawDestination.imageIdArg) { type = NavType.StringType }),
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments
                ?.getString(JigsawDestination.imageIdArg)
                ?.let(Uri::decode)
            val image = images.firstOrNull { it.id == imageId }
            if (image == null) {
                MissingArgumentState()
            } else {
                PieceCountScreen(
                    pieceCountOptions = pieceCounts,
                    recentSessions = recentSessions.value.filter { it.imageId == image.id },
                    onBack = { navController.popBackStack() },
                    onPieceCountSelected = { option ->
                        navController.navigate(JigsawDestination.gameplay(image.id, option.totalPieces))
                    },
                )
            }
        }
        composable(
            route = JigsawDestination.gameplayRoute,
            arguments = listOf(
                navArgument(JigsawDestination.imageIdArg) { type = NavType.StringType },
                navArgument(JigsawDestination.pieceCountArg) { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val imageId = backStackEntry.arguments
                ?.getString(JigsawDestination.imageIdArg)
                ?.let(Uri::decode)
            val pieceCount = backStackEntry.arguments?.getInt(JigsawDestination.pieceCountArg)
            val image = images.firstOrNull { it.id == imageId }
            val option = pieceCounts.firstOrNull { it.totalPieces == pieceCount }
            if (image == null || option == null) {
                MissingArgumentState()
            } else {
                GameplayRoute(
                    image = image,
                    pieceCount = option,
                    progressStore = progressStore,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun legacyImageIdMappings(images: List<PuzzleImage>): Map<String, String> = images
    .groupBy { image -> image.id.substringAfterLast('/').substringBeforeLast('.') }
    .mapNotNull { (legacyId, matchingImages) ->
        if (matchingImages.size != 1) {
            null
        } else {
            val image = matchingImages.single()
            if (legacyId == image.id) {
                null
            } else {
                legacyId to image.id
            }
        }
    }
    .toMap()

@Composable
private fun MissingArgumentState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.missing_argument_message),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(24.dp),
        )
    }
}

private data class PendingUploadImport(
    val preparedImport: PreparedUserImageImport,
    val cropState: GalleryUploadCropState,
)
