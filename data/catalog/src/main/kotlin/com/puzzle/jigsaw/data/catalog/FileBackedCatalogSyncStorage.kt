package com.puzzle.jigsaw.data.catalog

import android.content.Context
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.io.File
import java.util.Locale

private const val PuzzlesDir = "puzzles"
private const val CatalogDirectoryName = "puzzle_catalog"

internal class FileBackedCatalogSyncStorage(
    private val context: Context,
    private val uploadedPuzzleStore: UploadedPuzzleStore,
    private val remoteDataSource: CatalogHttpRemoteDataSource,
) : CatalogSyncStorage {
    override fun resolveCurrentCatalog(): PuzzleCatalogSnapshot {
        loadDownloadedCatalog(activeRootDir())?.let { catalog ->
            deleteDirectory(stagingRootDir())
            deleteDirectory(backupRootDir())
            return catalog
        }

        restoreBackupIfPossible()?.let { restoredCatalog ->
            deleteDirectory(stagingRootDir())
            return restoredCatalog
        }

        deleteDirectory(stagingRootDir())
        return loadBundledCatalog()
    }

    override fun loadCurrentDownloadedCatalog(): PuzzleCatalogSnapshot? = loadDownloadedCatalog(activeRootDir())

    override fun loadBundledCatalog(): PuzzleCatalogSnapshot {
        val manifestJson = runCatching {
            context.assets.open("$PuzzlesDir/manifest.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return missingBundledCatalogSnapshot("bundled-missing")

        val manifest = parseCatalogManifest(manifestJson)
            ?: return missingBundledCatalogSnapshot("bundled-invalid")

        return PuzzleCatalogSnapshot(
            revision = manifest.revision,
            manifest = manifest,
            images = manifest.toPuzzleImages(
                locale = currentLocale(),
                storage = PuzzleImageStorage.ASSET,
                resolvePath = { relativePath -> "$PuzzlesDir/$relativePath" },
            ),
        )
    }

    override fun stageRemoteCatalog(request: CatalogStageRequest): Boolean {
        val stagingRoot = stagingRootDir()
        deleteDirectory(stagingRoot)
        val stagingImagesRoot = File(stagingRoot, "images")
        if (!stagingImagesRoot.mkdirs() && !stagingImagesRoot.isDirectory) {
            return false
        }

        val manifestFile = File(stagingRoot, "manifest.json")
        manifestFile.writeText(request.manifestJson)

        val localImagesByPath = request.currentDownloadedManifest
            ?.images
            ?.associateBy(PuzzleCatalogManifestImage::path)
            .orEmpty()
        val bundledImagesByPath = request.bundledManifest
            ?.images
            ?.associateBy(PuzzleCatalogManifestImage::path)
            .orEmpty()
        val currentDownloadedImagesRoot = File(activeRootDir(), "images")

        return request.remoteManifest.images.all { remoteImage ->
            val localImage = localImagesByPath[remoteImage.path]
            if (
                localImage != null &&
                canReuseDownloadedFiles(localImage, remoteImage) &&
                copyDownloadedFileIfPresent(
                    currentDownloadedImagesRoot = currentDownloadedImagesRoot,
                    stagingImagesRoot = stagingImagesRoot,
                    relativePath = remoteImage.previewPath,
                ) &&
                copyDownloadedFileIfPresent(
                    currentDownloadedImagesRoot = currentDownloadedImagesRoot,
                    stagingImagesRoot = stagingImagesRoot,
                    relativePath = remoteImage.path,
                )
            ) {
                true
            } else {
                val bundledImage = bundledImagesByPath[remoteImage.path]
                if (
                    bundledImage != null &&
                    canReuseDownloadedFiles(bundledImage, remoteImage) &&
                    copyBundledFileIfPresent(
                        context = context,
                        relativePath = remoteImage.previewPath,
                        stagingImagesRoot = stagingImagesRoot,
                    ) &&
                    copyBundledFileIfPresent(
                        context = context,
                        relativePath = remoteImage.path,
                        stagingImagesRoot = stagingImagesRoot,
                    )
                ) {
                    true
                } else {
                    remoteDataSource.downloadImage(
                        relativePath = remoteImage.previewPath,
                        target = File(stagingImagesRoot, remoteImage.previewPath),
                    ) && remoteDataSource.downloadImage(
                        relativePath = remoteImage.path,
                        target = File(stagingImagesRoot, remoteImage.path),
                    )
                }
            }
        }
    }

    override fun loadStagedCatalog(): PuzzleCatalogSnapshot? = loadDownloadedCatalog(stagingRootDir())

    override fun discardStagedCatalog() {
        deleteDirectory(stagingRootDir())
    }

    override fun backupActiveCatalogIfPresent(): Boolean {
        val activeRoot = activeRootDir()
        val backupRoot = backupRootDir()
        deleteDirectory(backupRoot)

        val activeCatalog = loadDownloadedCatalog(activeRoot)
        if (activeCatalog == null) {
            deleteDirectory(activeRoot)
            return false
        }

        return moveDirectory(activeRoot, backupRoot) ||
            copyDirectory(activeRoot, backupRoot).also { copied ->
                if (copied) {
                    deleteDirectory(activeRoot)
                }
            }
    }

    override fun activateStagedCatalog() {
        val activeRoot = activeRootDir()
        deleteDirectory(activeRoot)
        if (
            !moveDirectory(stagingRootDir(), activeRoot) &&
            !copyDirectory(stagingRootDir(), activeRoot).also { copied ->
                if (copied) {
                    deleteDirectory(stagingRootDir())
                }
            }
        ) {
            error("Unable to activate staged catalog")
        }
    }

    override fun loadActiveCatalog(): PuzzleCatalogSnapshot? = loadDownloadedCatalog(activeRootDir())

    override fun clearBackupCatalog() {
        deleteDirectory(backupRootDir())
    }

    override fun restoreBackupAfterFailedSwap(previousCatalogBackedUp: Boolean) {
        deleteDirectory(activeRootDir())
        deleteDirectory(stagingRootDir())
        if (previousCatalogBackedUp) {
            moveDirectory(backupRootDir(), activeRootDir()) ||
                copyDirectory(backupRootDir(), activeRootDir())
        }
    }

    override fun loadUploadedImages(): List<PuzzleImage> = uploadedPuzzleStore.loadImages()

    private fun loadDownloadedCatalog(rootDir: File): PuzzleCatalogSnapshot? {
        val manifestFile = File(rootDir, "manifest.json")
        val imagesRoot = File(rootDir, "images")
        val manifestJson = manifestFile.takeIf(File::isFile)?.readText() ?: return null
        val manifest = parseCatalogManifest(manifestJson) ?: return null
        if (!manifest.hasAllFiles { relativePath -> File(imagesRoot, relativePath).isFile }) {
            return null
        }

        return PuzzleCatalogSnapshot(
            revision = manifest.revision,
            manifest = manifest,
            images = manifest.toPuzzleImages(
                locale = currentLocale(),
                storage = PuzzleImageStorage.FILE,
                resolvePath = { relativePath -> File(imagesRoot, relativePath).path },
            ),
        )
    }

    private fun restoreBackupIfPossible(): PuzzleCatalogSnapshot? {
        val backupRoot = backupRootDir()
        val backupCatalog = loadDownloadedCatalog(backupRoot) ?: return null
        val activeRoot = activeRootDir()
        deleteDirectory(activeRoot)

        return if (
            moveDirectory(backupRoot, activeRoot) ||
            copyDirectory(backupRoot, activeRoot).also { copied ->
                if (copied) {
                    deleteDirectory(backupRoot)
                }
            }
        ) {
            loadDownloadedCatalog(activeRoot)
        } else {
            backupCatalog
        }
    }

    private fun missingBundledCatalogSnapshot(revision: String): PuzzleCatalogSnapshot = PuzzleCatalogSnapshot(
        revision = revision,
        manifest = PuzzleCatalogManifest(
            revision = revision,
            categories = emptyList(),
            images = emptyList(),
        ),
        images = emptyList(),
    )

    private fun currentLocale(): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    private fun activeRootDir(): File = File(catalogRootDir(), "active")

    private fun backupRootDir(): File = File(catalogRootDir(), "backup")

    private fun stagingRootDir(): File = File(catalogRootDir(), "staging")

    private fun catalogRootDir(): File = File(context.filesDir, CatalogDirectoryName)
}

private fun moveDirectory(from: File, to: File): Boolean {
    if (!from.exists()) {
        return false
    }

    deleteDirectory(to)
    to.parentFile?.mkdirs()
    return from.renameTo(to)
}

private fun copyDirectory(from: File, to: File): Boolean = runCatching {
    if (!from.exists()) {
        return false
    }

    deleteDirectory(to)
    to.parentFile?.mkdirs()
    from.copyRecursively(target = to, overwrite = true)
}.getOrDefault(false)

private fun copyDownloadedFileIfPresent(
    currentDownloadedImagesRoot: File,
    stagingImagesRoot: File,
    relativePath: String,
): Boolean {
    val source = File(currentDownloadedImagesRoot, relativePath)
    if (!source.isFile) {
        return false
    }

    val target = File(stagingImagesRoot, relativePath)
    target.parentFile?.mkdirs()
    return runCatching {
        source.copyTo(target = target, overwrite = true)
        target.isFile
    }.getOrDefault(false)
}

private fun copyBundledFileIfPresent(
    context: Context,
    relativePath: String,
    stagingImagesRoot: File,
): Boolean {
    val target = File(stagingImagesRoot, relativePath)
    target.parentFile?.mkdirs()

    return runCatching {
        context.assets.open("$PuzzlesDir/$relativePath").use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        target.isFile
    }.getOrDefault(false)
}

private fun deleteDirectory(directory: File) {
    if (directory.exists()) {
        directory.deleteRecursively()
    }
}
