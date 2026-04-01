package com.puzzle.jigsaw.data.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import org.json.JSONObject

data class PreparedUserImageImport(
    val bitmap: Bitmap,
    val title: String,
)

internal data class UploadedPuzzleMetadata(
    val id: String,
    val title: String,
    val importedAtEpochMillis: Long,
)

internal data class PixelCropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

internal class UploadedPuzzleStore(
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
            PuzzleImage(
                id = entry.metadata.id,
                title = entry.metadata.title,
                previewPath = entry.previewFile.path,
                fullImagePath = entry.fullFile.path,
                storage = PuzzleImageStorage.FILE,
                categoryId = UploadsCategoryId,
                categoryName = UploadsCategoryName,
                categorySortOrder = UploadsCategorySortOrder,
                sortOrder = index,
            )
        }
    }

    fun prepareImport(sourceUri: Uri): PreparedUserImageImport? {
        val bitmap = decodePreparedBitmap(sourceUri) ?: return null
        return PreparedUserImageImport(
            bitmap = bitmap,
            title = resolveTitle(sourceUri),
        )
    }

    fun importPreparedImage(
        preparedImport: PreparedUserImageImport,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    ): PuzzleImage? {
        val cropRect = resolveCropRect(
            width = preparedImport.bitmap.width,
            height = preparedImport.bitmap.height,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
        ) ?: return null

        val uploadId = "upload-${UUID.randomUUID()}"
        val finalDir = File(uploadsRootDir(), uploadId)
        val stagingDir = File(uploadsRootDir(), "$uploadId$UploadTempSuffix")
        deleteDirectory(stagingDir)
        if (!stagingDir.mkdirs() && !stagingDir.isDirectory) {
            return null
        }

        val importedAt = System.currentTimeMillis()
        val metadata = UploadedPuzzleMetadata(
            id = uploadId,
            title = preparedImport.title,
            importedAtEpochMillis = importedAt,
        )
        val croppedBitmap = Bitmap.createBitmap(
            preparedImport.bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width,
            cropRect.height,
        )
        val previewBitmap = createPreviewBitmap(croppedBitmap)

        return try {
            val fullFile = File(stagingDir, UploadFullFileName)
            val previewFile = File(stagingDir, UploadPreviewFileName)
            if (
                !writeBitmap(croppedBitmap, fullFile) ||
                !writeBitmap(previewBitmap, previewFile) ||
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

            PuzzleImage(
                id = metadata.id,
                title = metadata.title,
                previewPath = File(finalDir, UploadPreviewFileName).path,
                fullImagePath = File(finalDir, UploadFullFileName).path,
                storage = PuzzleImageStorage.FILE,
                categoryId = UploadsCategoryId,
                categoryName = UploadsCategoryName,
                categorySortOrder = UploadsCategorySortOrder,
                sortOrder = 0,
            )
        } finally {
            if (previewBitmap !== croppedBitmap) {
                previewBitmap.recycle()
            }
            croppedBitmap.recycle()
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
        val raw = JSONObject(file.readText())
        UploadedPuzzleMetadata(
            id = raw.optString("id").takeIf(String::isNotBlank) ?: return null,
            title = raw.optString("title").takeIf(String::isNotBlank) ?: UploadsCategoryName,
            importedAtEpochMillis = raw.optLong("importedAtEpochMillis").takeIf { it > 0L } ?: return null,
        )
    }.getOrNull()

    private fun writeMetadata(metadata: UploadedPuzzleMetadata, file: File): Boolean = runCatching {
        file.writeText(
            JSONObject()
                .put("id", metadata.id)
                .put("title", metadata.title)
                .put("importedAtEpochMillis", metadata.importedAtEpochMillis)
                .toString(),
        )
        file.isFile
    }.getOrDefault(false)

    private fun createPreviewBitmap(croppedBitmap: Bitmap): Bitmap {
        if (croppedBitmap.width <= UploadPreviewMaxWidthPx) {
            return croppedBitmap
        }
        val previewWidth = UploadPreviewMaxWidthPx
        val previewHeight = ((previewWidth.toFloat() / croppedBitmap.width.toFloat()) * croppedBitmap.height.toFloat())
            .roundToInt()
            .coerceAtLeast(1)
        return Bitmap.createScaledBitmap(croppedBitmap, previewWidth, previewHeight, true)
    }

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

    private fun decodePreparedBitmap(sourceUri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(sourceUri)
        } else {
            decodeWithBitmapFactory(sourceUri)
        }

    private fun decodeWithImageDecoder(sourceUri: Uri): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, sourceUri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val targetSize = constrainedSize(
                width = info.size.width,
                height = info.size.height,
                maxDimension = ImportBitmapMaxDimensionPx,
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSize(targetSize.first, targetSize.second)
        }
    }.getOrNull()

    private fun decodeWithBitmapFactory(sourceUri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, ImportBitmapMaxDimensionPx)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null
        return rotateBitmapIfNeeded(sourceUri, decoded)
    }

    private fun rotateBitmapIfNeeded(sourceUri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
        }
        if (matrix.isIdentity) {
            return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { rotated ->
            if (rotated != bitmap) {
                bitmap.recycle()
            }
        }
    }

    private fun resolveTitle(sourceUri: Uri): String {
        val displayName = runCatching {
            context.contentResolver.query(
                sourceUri,
                arrayOf("_display_name"),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex("_display_name")
                if (index >= 0 && cursor.moveToFirst()) {
                    cursor.getString(index)
                } else {
                    null
                }
            }
        }.getOrNull()

        return displayName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: DefaultUploadTitle
    }

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

internal fun resolveCropRectForTest(
    width: Int,
    height: Int,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
): PixelCropRect? = resolveCropRect(width, height, cropLeft, cropTop, cropRight, cropBottom)

internal fun buildUploadedPuzzleImagesForTest(
    metadataEntries: List<UploadedPuzzleMetadata>,
): List<PuzzleImage> = metadataEntries
    .sortedWith(
        compareByDescending<UploadedPuzzleMetadata> { it.importedAtEpochMillis }
            .thenBy { it.title.lowercase(Locale.getDefault()) },
    )
    .mapIndexed { index, metadata ->
        PuzzleImage(
            id = metadata.id,
            title = metadata.title,
            previewPath = "/tmp/${metadata.id}/preview.webp",
            fullImagePath = "/tmp/${metadata.id}/full.webp",
            storage = PuzzleImageStorage.FILE,
            categoryId = UploadsCategoryId,
            categoryName = UploadsCategoryName,
            categorySortOrder = UploadsCategorySortOrder,
            sortOrder = index,
        )
    }

private fun resolveCropRect(
    width: Int,
    height: Int,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
): PixelCropRect? {
    if (width <= 0 || height <= 0) {
        return null
    }
    val left = cropLeft.coerceIn(0f, 1f)
    val top = cropTop.coerceIn(0f, 1f)
    val right = cropRight.coerceIn(0f, 1f)
    val bottom = cropBottom.coerceIn(0f, 1f)
    if (right <= left || bottom <= top) {
        return null
    }

    val leftPx = floor(left * width.toFloat()).toInt().coerceIn(0, width - 1)
    val topPx = floor(top * height.toFloat()).toInt().coerceIn(0, height - 1)
    val rightPx = ceil(right * width.toFloat()).toInt().coerceIn(leftPx + 1, width)
    val bottomPx = ceil(bottom * height.toFloat()).toInt().coerceIn(topPx + 1, height)
    return PixelCropRect(
        left = leftPx,
        top = topPx,
        right = rightPx,
        bottom = bottomPx,
    )
}

private fun constrainedSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    if (width <= maxDimension && height <= maxDimension) {
        return width to height
    }
    val scale = maxDimension.toFloat() / maxOf(width, height).toFloat()
    return (
        (width.toFloat() * scale).roundToInt().coerceAtLeast(1) to
            (height.toFloat() * scale).roundToInt().coerceAtLeast(1)
        )
}

private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    while ((width / sampleSize) > maxDimension || (height / sampleSize) > maxDimension) {
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

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

internal const val UploadsCategoryId = "uploads"
private const val UploadsCategoryName = "Uploads"
private const val UploadsCategorySortOrder = Int.MIN_VALUE + 1
private const val UploadsDirectoryName = "puzzle_uploads"
private const val UploadMetadataFileName = "metadata.json"
private const val UploadFullFileName = "full.webp"
private const val UploadPreviewFileName = "preview.webp"
private const val UploadTempSuffix = ".tmp"
private const val UploadPreviewMaxWidthPx = 720
private const val UploadImageQuality = 92
private const val ImportBitmapMaxDimensionPx = 2048
private const val DefaultUploadTitle = "Upload"
