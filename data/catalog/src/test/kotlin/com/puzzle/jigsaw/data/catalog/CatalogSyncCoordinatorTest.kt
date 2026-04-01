package com.puzzle.jigsaw.data.catalog

import com.puzzle.jigsaw.core.model.PieceCountOption
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogSyncCoordinatorTest {

    @Test
    fun `loadImages combines current catalog and uploaded images`() {
        val storage = FakeCatalogSyncStorage(
            currentCatalog = snapshot("rev-1", listOf(image("bundled"))),
            uploadedImages = listOf(image("upload")),
        )
        val coordinator = CatalogSyncCoordinator(
            storage = storage,
            remoteDataSource = FakeCatalogRemoteDataSource(),
        )

        val images = coordinator.loadImages()

        assertEquals(listOf("bundled", "upload"), images.map { it.id })
    }

    @Test
    fun `syncIfNeeded discards staging when staged catalog is invalid`() {
        val storage = FakeCatalogSyncStorage(
            currentCatalog = snapshot("rev-1", listOf(image("current"))),
            currentDownloadedCatalog = snapshot("rev-1", listOf(image("current"))),
            bundledCatalog = snapshot("bundled", listOf(image("bundled"))),
            stageRemoteCatalogResult = true,
            stagedCatalog = null,
        )
        val coordinator = CatalogSyncCoordinator(
            storage = storage,
            remoteDataSource = FakeCatalogRemoteDataSource(
                remoteManifest = remoteManifest(revision = "rev-2"),
            ),
        )

        val result = coordinator.syncIfNeeded()

        assertNull(result)
        assertTrue(storage.discardedStaging)
        assertFalse(storage.backupAttempted)
    }

    @Test
    fun `syncIfNeeded restores backup when activated catalog cannot be loaded`() {
        val storage = FakeCatalogSyncStorage(
            currentCatalog = snapshot("rev-1", listOf(image("current"))),
            currentDownloadedCatalog = snapshot("rev-1", listOf(image("current"))),
            bundledCatalog = snapshot("bundled", listOf(image("bundled"))),
            stageRemoteCatalogResult = true,
            stagedCatalog = snapshot("rev-2", listOf(image("staged"))),
            activeCatalogAfterActivation = null,
            backupActiveCatalogIfPresentResult = true,
        )
        val coordinator = CatalogSyncCoordinator(
            storage = storage,
            remoteDataSource = FakeCatalogRemoteDataSource(
                remoteManifest = remoteManifest(revision = "rev-2"),
            ),
        )

        val result = coordinator.syncIfNeeded()

        assertNull(result)
        assertTrue(storage.backupAttempted)
        assertEquals(listOf(true), storage.restoreBackupArguments)
        assertFalse(storage.clearedBackup)
    }

    @Test
    fun `syncIfNeeded clears backup and returns combined images after successful swap`() {
        val storage = FakeCatalogSyncStorage(
            currentCatalog = snapshot("rev-1", listOf(image("current"))),
            currentDownloadedCatalog = snapshot("rev-1", listOf(image("current"))),
            bundledCatalog = snapshot("bundled", listOf(image("bundled"))),
            stageRemoteCatalogResult = true,
            stagedCatalog = snapshot("rev-2", listOf(image("staged"))),
            activeCatalogAfterActivation = snapshot("rev-2", listOf(image("active"))),
            uploadedImages = listOf(image("upload")),
            backupActiveCatalogIfPresentResult = true,
        )
        val coordinator = CatalogSyncCoordinator(
            storage = storage,
            remoteDataSource = FakeCatalogRemoteDataSource(
                remoteManifest = remoteManifest(revision = "rev-2"),
            ),
        )

        val result = coordinator.syncIfNeeded()

        assertEquals(listOf("active", "upload"), result?.map { it.id })
        assertTrue(storage.clearedBackup)
        assertTrue(storage.activatedStaging)
        assertTrue(storage.restoreBackupArguments.isEmpty())
    }

    private fun snapshot(revision: String, images: List<PuzzleImage>): PuzzleCatalogSnapshot {
        val manifest = PuzzleCatalogManifest(
            revision = revision,
            categories = listOf(
                PuzzleCatalogCategory(
                    id = "featured",
                    sortOrder = 0,
                    names = mapOf("en" to "Featured"),
                ),
            ),
            images = images.map { puzzleImage ->
                PuzzleCatalogManifestImage(
                    categoryId = "featured",
                    previewPath = "${puzzleImage.id}-preview.webp",
                    path = "${puzzleImage.id}.webp",
                    updatedAt = "2026-04-01T00:00:00Z",
                    updatedAtDate = "2026-04-01",
                )
            },
        )
        return PuzzleCatalogSnapshot(
            revision = revision,
            manifest = manifest,
            images = images,
        )
    }

    private fun image(id: String): PuzzleImage = PuzzleImage(
        id = id,
        title = id,
        previewPath = "$id-preview.webp",
        fullImagePath = "$id.webp",
        storage = PuzzleImageStorage.FILE,
    )

    private fun remoteManifest(revision: String): RemoteCatalogManifest = RemoteCatalogManifest(
        manifestJson = """{"revision":"$revision"}""",
        manifest = PuzzleCatalogManifest(
            revision = revision,
            categories = listOf(
                PuzzleCatalogCategory(
                    id = "featured",
                    sortOrder = 0,
                    names = mapOf("en" to "Featured"),
                ),
            ),
            images = listOf(
                PuzzleCatalogManifestImage(
                    categoryId = "featured",
                    previewPath = "next-preview.webp",
                    path = "next.webp",
                    updatedAt = "2026-04-02T00:00:00Z",
                    updatedAtDate = "2026-04-02",
                ),
            ),
        ),
    )
}

private class FakeCatalogRemoteDataSource(
    private val remoteManifest: RemoteCatalogManifest? = null,
) : CatalogRemoteDataSource {
    override fun fetchManifest(): RemoteCatalogManifest? = remoteManifest
}

private class FakeCatalogSyncStorage(
    private val currentCatalog: PuzzleCatalogSnapshot,
    private val currentDownloadedCatalog: PuzzleCatalogSnapshot? = null,
    private val bundledCatalog: PuzzleCatalogSnapshot = currentCatalog,
    private val uploadedImages: List<PuzzleImage> = emptyList(),
    private val stageRemoteCatalogResult: Boolean = false,
    private val stagedCatalog: PuzzleCatalogSnapshot? = null,
    private val activeCatalogAfterActivation: PuzzleCatalogSnapshot? = stagedCatalog,
    private val backupActiveCatalogIfPresentResult: Boolean = false,
) : CatalogSyncStorage {
    var discardedStaging: Boolean = false
    var backupAttempted: Boolean = false
    var activatedStaging: Boolean = false
    var clearedBackup: Boolean = false
    val restoreBackupArguments: MutableList<Boolean> = mutableListOf()

    override fun resolveCurrentCatalog(): PuzzleCatalogSnapshot = currentCatalog

    override fun loadCurrentDownloadedCatalog(): PuzzleCatalogSnapshot? = currentDownloadedCatalog

    override fun loadBundledCatalog(): PuzzleCatalogSnapshot = bundledCatalog

    override fun stageRemoteCatalog(request: CatalogStageRequest): Boolean = stageRemoteCatalogResult

    override fun loadStagedCatalog(): PuzzleCatalogSnapshot? = stagedCatalog

    override fun discardStagedCatalog() {
        discardedStaging = true
    }

    override fun backupActiveCatalogIfPresent(): Boolean {
        backupAttempted = true
        return backupActiveCatalogIfPresentResult
    }

    override fun activateStagedCatalog() {
        activatedStaging = true
    }

    override fun loadActiveCatalog(): PuzzleCatalogSnapshot? = activeCatalogAfterActivation

    override fun clearBackupCatalog() {
        clearedBackup = true
    }

    override fun restoreBackupAfterFailedSwap(previousCatalogBackedUp: Boolean) {
        restoreBackupArguments += previousCatalogBackedUp
    }

    override fun loadUploadedImages(): List<PuzzleImage> = uploadedImages
}
