package com.puzzle.jigsaw.data.catalog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal data class ImportedUserImageBitmaps(
    val full: Bitmap,
    val preview: Bitmap,
) {
    fun recycle() {
        if (preview !== full) {
            preview.recycle()
        }
        full.recycle()
    }
}

internal class UploadedPuzzleBitmapProcessor(
    private val context: Context,
) {
    fun prepareImport(sourceUri: Uri): PreparedUserImageImport? {
        val bitmap = decodePreparedBitmap(sourceUri) ?: return null
        return PreparedUserImageImport(
            bitmap = bitmap,
            title = resolveTitle(sourceUri),
        )
    }

    fun createImportedBitmaps(
        preparedImport: PreparedUserImageImport,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    ): ImportedUserImageBitmaps? {
        val cropRect = resolveCropRect(
            width = preparedImport.bitmap.width,
            height = preparedImport.bitmap.height,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
        ) ?: return null

        val croppedBitmap = Bitmap.createBitmap(
            preparedImport.bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width,
            cropRect.height,
        )
        return ImportedUserImageBitmaps(
            full = croppedBitmap,
            preview = createPreviewBitmap(croppedBitmap),
        )
    }

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

    private fun decodePreparedBitmap(sourceUri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(sourceUri)
        } else {
            decodeWithBitmapFactory(sourceUri)
        }

    @SuppressLint("NewApi")
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
}

internal fun resolveCropRectForTest(
    width: Int,
    height: Int,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
): PixelCropRect? = resolveCropRect(width, height, cropLeft, cropTop, cropRight, cropBottom)

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
