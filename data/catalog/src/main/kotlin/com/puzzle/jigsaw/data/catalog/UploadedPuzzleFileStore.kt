package com.puzzle.jigsaw.data.catalog

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.io.File
import java.util.Locale

internal class UploadedPuzzleFileStore(
    private val context: Context,
) {
    fun loadImages(): List<PuzzleImage> {
        cleanupTempDirectories()
        val entries = uploadsRootDir()
            .listFiles()
            .orEmpty()
            .asSequence()
            .filter(File::isDirectory)
            .filterNot { it.name.endsWith(UploadTempSuffix) }
            .mapNotNull(::loadEntry)
            .sortedWith(
                compareByDescending<UploadedPuzzleEntry> { it.metadata.importedAtEpochMillis }
                    .thenBy { it.metadata.title.lowercase(Locale.getDefault()) },
            )
            .toList()

        return entries.mapIndexed { index, entry ->
            entry.toPuzzleImage(sortOrder = index)
        }
    }

    fun activateImport(
        metadata: UploadedPuzzleMetadata,
        importedBitmaps: ImportedUserImageBitmaps,
    ): PuzzleImage? {
        val finalDir = File(uploadsRootDir(), metadata.id)
        val stagingDir = File(uploadsRootDir(), "${metadata.id}$UploadTempSuffix")
        deleteDirectory(stagingDir)
        if (!stagingDir.mkdirs() && !stagingDir.isDirectory) {
            return null
        }

        return try {
            val fullFile = File(stagingDir, UploadFullFileName)
            val previewFile = File(stagingDir, UploadPreviewFileName)
            if (
                !writeBitmap(importedBitmaps.full, fullFile) ||
                !writeBitmap(importedBitmaps.preview, previewFile) ||
                !writeMetadata(metadata, File(stagingDir, UploadMetadataFileName))
            ) {
                return null
            }

            deleteDirectory(finalDir)
            val activated = stagingDir.renameTo(finalDir) ||
                runCatching {
                    stagingDir.copyRecursively(target = finalDir, overwrite = true)
                    deleteDirectory(stagingDir)
                    true
                }.getOrDefault(false)
            if (!activated) {
                return null
            }

            UploadedPuzzleEntry(
                metadata = metadata,
                fullFile = File(finalDir, UploadFullFileName),
                previewFile = File(finalDir, UploadPreviewFileName),
            ).toPuzzleImage(sortOrder = 0)
        } finally {
            deleteDirectory(stagingDir)
        }
    }

    private fun loadEntry(directory: File): UploadedPuzzleEntry? {
        val metadata = loadMetadata(File(directory, UploadMetadataFileName)) ?: return null
        val fullFile = File(directory, UploadFullFileName).takeIf(File::isFile) ?: return null
        val previewFile = File(directory, UploadPreviewFileName).takeIf(File::isFile) ?: return null
        return UploadedPuzzleEntry(
            metadata = metadata,
            fullFile = fullFile,
            previewFile = previewFile,
        )
    }

    private fun loadMetadata(file: File): UploadedPuzzleMetadata? = runCatching {
        val raw = org.json.JSONObject(file.readText())
        UploadedPuzzleMetadata(
            id = raw.optString("id").takeIf(String::isNotBlank) ?: return null,
            title = raw.optString("title").takeIf(String::isNotBlank) ?: UploadsCategoryName,
            importedAtEpochMillis = raw.optLong("importedAtEpochMillis").takeIf { it > 0L } ?: return null,
        )
    }.getOrNull()

    private fun writeMetadata(metadata: UploadedPuzzleMetadata, file: File): Boolean = runCatching {
        file.writeText(
            org.json.JSONObject()
                .put("id", metadata.id)
                .put("title", metadata.title)
                .put("importedAtEpochMillis", metadata.importedAtEpochMillis)
                .toString(),
        )
        file.isFile
    }.getOrDefault(false)

    private fun writeBitmap(bitmap: Bitmap, target: File): Boolean = runCatching {
        target.outputStream().use { output ->
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, UploadImageQuality, output)
        }
        target.isFile && target.length() > 0L
    }.getOrDefault(false)

    private fun uploadsRootDir(): File = File(context.filesDir, UploadsDirectoryName).apply {
        mkdirs()
    }

    private fun cleanupTempDirectories() {
        uploadsRootDir()
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && it.name.endsWith(UploadTempSuffix) }
            .forEach(::deleteDirectory)
    }
}

internal fun buildUploadedPuzzleImagesForTest(
    metadataEntries: List<UploadedPuzzleMetadata>,
): List<PuzzleImage> = metadataEntries
    .sortedWith(
        compareByDescending<UploadedPuzzleMetadata> { it.importedAtEpochMillis }
            .thenBy { it.title.lowercase(Locale.getDefault()) },
    )
    .mapIndexed { index, metadata ->
        UploadedPuzzleEntry(
            metadata = metadata,
            fullFile = File("/tmp/${metadata.id}/$UploadFullFileName"),
            previewFile = File("/tmp/${metadata.id}/$UploadPreviewFileName"),
        ).toPuzzleImage(sortOrder = index)
    }

private fun UploadedPuzzleEntry.toPuzzleImage(sortOrder: Int): PuzzleImage = PuzzleImage(
    id = metadata.id,
    title = metadata.title,
    previewPath = previewFile.path,
    fullImagePath = fullFile.path,
    storage = PuzzleImageStorage.FILE,
    categoryId = UploadsCategoryId,
    categoryName = UploadsCategoryName,
    categorySortOrder = UploadsCategorySortOrder,
    sortOrder = sortOrder,
)

private fun deleteDirectory(directory: File) {
    if (directory.exists()) {
        directory.deleteRecursively()
    }
}

private data class UploadedPuzzleEntry(
    val metadata: UploadedPuzzleMetadata,
    val fullFile: File,
    val previewFile: File,
)
