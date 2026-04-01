package com.puzzle.jigsaw.data.catalog

import android.content.Context
import android.net.Uri
import com.puzzle.jigsaw.core.model.PuzzleImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetPuzzleCatalogRepository(
    private val context: Context,
) {
    private val uploadedPuzzleStore = UploadedPuzzleStore(context)
    private val remoteDataSource = CatalogHttpRemoteDataSource()
    private val syncCoordinator = CatalogSyncCoordinator(
        storage = FileBackedCatalogSyncStorage(
            context = context,
            uploadedPuzzleStore = uploadedPuzzleStore,
            remoteDataSource = remoteDataSource,
        ),
        remoteDataSource = remoteDataSource,
    )

    suspend fun loadImages(): List<PuzzleImage> = withContext(Dispatchers.IO) {
        syncCoordinator.loadImages()
    }

    suspend fun syncIfNeeded(): List<PuzzleImage>? = withContext(Dispatchers.IO) {
        syncCoordinator.syncIfNeeded()
    }

    suspend fun prepareUserImageImport(sourceUri: Uri): PreparedUserImageImport? = withContext(Dispatchers.IO) {
        uploadedPuzzleStore.prepareImport(sourceUri)
    }

    suspend fun importPreparedUserImage(
        preparedImport: PreparedUserImageImport,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    ): PuzzleImage? = withContext(Dispatchers.IO) {
        uploadedPuzzleStore.importPreparedImage(
            preparedImport = preparedImport,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
        )
    }
}
