package com.puzzle.jigsaw.data.catalog

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.puzzle.jigsaw.core.model.PuzzleImage
import java.util.UUID

data class PreparedUserImageImport(
    val bitmap: Bitmap,
    val title: String,
) {
    fun recycle() {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

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
    private val bitmapProcessor = UploadedPuzzleBitmapProcessor(context)
    private val fileStore = UploadedPuzzleFileStore(context)

    fun loadImages(): List<PuzzleImage> = fileStore.loadImages()

    fun prepareImport(sourceUri: Uri): PreparedUserImageImport? = bitmapProcessor.prepareImport(sourceUri)

    fun importPreparedImage(
        preparedImport: PreparedUserImageImport,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float,
    ): PuzzleImage? {
        val importedBitmaps = bitmapProcessor.createImportedBitmaps(
            preparedImport = preparedImport,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropRight = cropRight,
            cropBottom = cropBottom,
        ) ?: return null

        val importedAt = System.currentTimeMillis()
        val metadata = UploadedPuzzleMetadata(
            id = "upload-${UUID.randomUUID()}",
            title = preparedImport.title,
            importedAtEpochMillis = importedAt,
        )

        return try {
            fileStore.activateImport(
                metadata = metadata,
                importedBitmaps = importedBitmaps,
            )
        } finally {
            importedBitmaps.recycle()
        }
    }
}
