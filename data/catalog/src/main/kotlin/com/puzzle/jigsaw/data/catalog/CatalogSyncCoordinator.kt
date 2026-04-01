package com.puzzle.jigsaw.data.catalog

import com.puzzle.jigsaw.core.model.PuzzleImage

internal data class RemoteCatalogManifest(
    val manifestJson: String,
    val manifest: PuzzleCatalogManifest,
)

internal data class CatalogStageRequest(
    val manifestJson: String,
    val remoteManifest: PuzzleCatalogManifest,
    val currentDownloadedManifest: PuzzleCatalogManifest?,
    val bundledManifest: PuzzleCatalogManifest?,
)

internal interface CatalogRemoteDataSource {
    fun fetchManifest(): RemoteCatalogManifest?
}

internal interface CatalogSyncStorage {
    fun resolveCurrentCatalog(): PuzzleCatalogSnapshot
    fun loadCurrentDownloadedCatalog(): PuzzleCatalogSnapshot?
    fun loadBundledCatalog(): PuzzleCatalogSnapshot
    fun stageRemoteCatalog(request: CatalogStageRequest): Boolean
    fun loadStagedCatalog(): PuzzleCatalogSnapshot?
    fun discardStagedCatalog()
    fun backupActiveCatalogIfPresent(): Boolean
    fun activateStagedCatalog()
    fun loadActiveCatalog(): PuzzleCatalogSnapshot?
    fun clearBackupCatalog()
    fun restoreBackupAfterFailedSwap(previousCatalogBackedUp: Boolean)
    fun loadUploadedImages(): List<PuzzleImage>
}

internal class CatalogSyncCoordinator(
    private val storage: CatalogSyncStorage,
    private val remoteDataSource: CatalogRemoteDataSource,
) {
    fun loadImages(): List<PuzzleImage> = combineCatalogImages(storage.resolveCurrentCatalog().images)

    fun syncIfNeeded(): List<PuzzleImage>? {
        val currentCatalog = storage.resolveCurrentCatalog()
        val currentDownloadedCatalog = storage.loadCurrentDownloadedCatalog()
        val bundledCatalog = storage.loadBundledCatalog()
        val remoteManifest = remoteDataSource.fetchManifest() ?: return null

        if (remoteManifest.manifest.revision == currentCatalog.revision) {
            return null
        }

        if (
            !storage.stageRemoteCatalog(
                CatalogStageRequest(
                    manifestJson = remoteManifest.manifestJson,
                    remoteManifest = remoteManifest.manifest,
                    currentDownloadedManifest = currentDownloadedCatalog?.manifest,
                    bundledManifest = bundledCatalog.manifest.takeIf { it.images.isNotEmpty() },
                ),
            )
        ) {
            storage.discardStagedCatalog()
            return null
        }

        if (storage.loadStagedCatalog() == null) {
            storage.discardStagedCatalog()
            return null
        }

        val previousCatalogBackedUp = storage.backupActiveCatalogIfPresent()

        return runCatching {
            storage.activateStagedCatalog()
            val activeCatalog = storage.loadActiveCatalog()
                ?: error("Downloaded catalog is invalid after swap")
            storage.clearBackupCatalog()
            combineCatalogImages(activeCatalog.images)
        }.getOrElse {
            storage.restoreBackupAfterFailedSwap(previousCatalogBackedUp)
            null
        }
    }

    private fun combineCatalogImages(baseImages: List<PuzzleImage>): List<PuzzleImage> =
        baseImages + storage.loadUploadedImages()
}
