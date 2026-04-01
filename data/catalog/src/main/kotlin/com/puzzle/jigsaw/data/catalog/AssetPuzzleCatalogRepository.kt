package com.puzzle.jigsaw.data.catalog

import android.content.Context
import android.net.Uri
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PUZZLES_DIR = "puzzles"
private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 20_000

private val REMOTE_ASSETS_BASE_URL = BuildConfig.PUZZLE_ASSETS_BASE_URL.trimEnd('/')
private val REMOTE_MANIFEST_URL = "$REMOTE_ASSETS_BASE_URL/manifest.json"
private val REMOTE_IMAGES_BASE_URL = "$REMOTE_ASSETS_BASE_URL/images/"

class AssetPuzzleCatalogRepository(
    private val context: Context,
) {
    private val uploadedPuzzleStore = UploadedPuzzleStore(context)

    suspend fun loadImages(): List<PuzzleImage> = withContext(Dispatchers.IO) {
        combineCatalogImages(resolveCurrentCatalog().images)
    }

    suspend fun syncIfNeeded(): List<PuzzleImage>? = withContext(Dispatchers.IO) {
        val currentCatalog = resolveCurrentCatalog()
        val currentDownloadedCatalog = loadDownloadedCatalog(activeRootDir())
        val bundledCatalog = loadBundledCatalog()
        val remoteManifestJson = downloadText(REMOTE_MANIFEST_URL) ?: return@withContext null
        val remoteManifest = parseCatalogManifest(remoteManifestJson) ?: return@withContext null

        if (remoteManifest.revision == currentCatalog.revision) {
            return@withContext null
        }

        if (
            !downloadManifestToStaging(
                manifestJson = remoteManifestJson,
                remoteManifest = remoteManifest,
                currentDownloadedManifest = currentDownloadedCatalog?.manifest,
                currentDownloadedImagesRoot = currentDownloadedCatalog?.let { File(activeRootDir(), "images") },
                bundledManifest = bundledCatalog.manifest.takeIf { it.images.isNotEmpty() },
            )
        ) {
            deleteDirectory(stagingRootDir())
            return@withContext null
        }

        val stagedCatalog = loadDownloadedCatalog(stagingRootDir()) ?: run {
            deleteDirectory(stagingRootDir())
            return@withContext null
        }

        val previousCatalogBackedUp = backupActiveCatalogIfPresent()

        return@withContext runCatching {
            replaceActiveWithStaging()
            val activeCatalog = loadDownloadedCatalog(activeRootDir())
                ?: error("Downloaded catalog is invalid after swap")
            deleteDirectory(backupRootDir())
            combineCatalogImages(activeCatalog.images)
        }.getOrElse {
            restoreBackupAfterFailedSwap(previousCatalogBackedUp)
            null
        }
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

    private fun resolveCurrentCatalog(): PuzzleCatalogSnapshot {
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

    private fun loadBundledCatalog(): PuzzleCatalogSnapshot {
        val manifestJson = runCatching {
            context.assets.open("$PUZZLES_DIR/manifest.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return PuzzleCatalogSnapshot(
            revision = "bundled-missing",
            manifest = PuzzleCatalogManifest(
                revision = "bundled-missing",
                categories = emptyList(),
                images = emptyList(),
            ),
            images = emptyList(),
        )

        val manifest = parseCatalogManifest(manifestJson)
            ?: return PuzzleCatalogSnapshot(
                revision = "bundled-invalid",
                manifest = PuzzleCatalogManifest(
                    revision = "bundled-invalid",
                    categories = emptyList(),
                    images = emptyList(),
                ),
                images = emptyList(),
            )
        val locale = currentLocale()

        return PuzzleCatalogSnapshot(
            revision = manifest.revision,
            manifest = manifest,
            images = manifest.toPuzzleImages(
                locale = locale,
                storage = PuzzleImageStorage.ASSET,
                resolvePath = { relativePath -> "$PUZZLES_DIR/$relativePath" },
            ),
        )
    }

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

    private fun downloadManifestToStaging(
        manifestJson: String,
        remoteManifest: PuzzleCatalogManifest,
        currentDownloadedManifest: PuzzleCatalogManifest?,
        currentDownloadedImagesRoot: File?,
        bundledManifest: PuzzleCatalogManifest?,
    ): Boolean {
        val stagingRoot = stagingRootDir()
        deleteDirectory(stagingRoot)
        val stagingImagesRoot = File(stagingRoot, "images")
        if (!stagingImagesRoot.mkdirs() && !stagingImagesRoot.isDirectory) {
            return false
        }

        val manifestFile = File(stagingRoot, "manifest.json")
        manifestFile.writeText(manifestJson)

        val localImagesByPath = currentDownloadedManifest
            ?.images
            ?.associateBy(PuzzleCatalogManifestImage::path)
            .orEmpty()
        val bundledImagesByPath = bundledManifest
            ?.images
            ?.associateBy(PuzzleCatalogManifestImage::path)
            .orEmpty()

        val syncSucceeded = remoteManifest.images.all { remoteImage ->
            val localImage = localImagesByPath[remoteImage.path]
            if (
                localImage != null &&
                currentDownloadedImagesRoot != null &&
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
                    downloadFile(
                        url = REMOTE_IMAGES_BASE_URL + remoteImage.previewPath,
                        target = File(stagingImagesRoot, remoteImage.previewPath),
                    ) && downloadFile(
                        url = REMOTE_IMAGES_BASE_URL + remoteImage.path,
                        target = File(stagingImagesRoot, remoteImage.path),
                    )
                }
            }
        }

        return syncSucceeded
    }

    private fun backupActiveCatalogIfPresent(): Boolean {
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

    private fun replaceActiveWithStaging() {
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

    private fun restoreBackupAfterFailedSwap(previousCatalogBackedUp: Boolean) {
        deleteDirectory(activeRootDir())
        deleteDirectory(stagingRootDir())
        if (previousCatalogBackedUp) {
            moveDirectory(backupRootDir(), activeRootDir()) ||
                copyDirectory(backupRootDir(), activeRootDir())
        }
    }

    private fun downloadText(url: String): String? {
        val connection = openConnection(url) ?: return null
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadFile(url: String, target: File): Boolean {
        val connection = openConnection(url) ?: return false
        target.parentFile?.mkdirs()

        return try {
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target.isFile && target.length() > 0L
        } catch (_: IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.requestMethod = "GET"

        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            null
        } else {
            connection
        }
    }.getOrNull()

    private fun currentLocale(): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    private fun combineCatalogImages(baseImages: List<PuzzleImage>): List<PuzzleImage> =
        baseImages + uploadedPuzzleStore.loadImages()

    private fun activeRootDir(): File = File(catalogRootDir(), "active")

    private fun backupRootDir(): File = File(catalogRootDir(), "backup")

    private fun stagingRootDir(): File = File(catalogRootDir(), "staging")

    private fun catalogRootDir(): File = File(context.filesDir, "puzzle_catalog")
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
        context.assets.open("$PUZZLES_DIR/$relativePath").use { input ->
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
