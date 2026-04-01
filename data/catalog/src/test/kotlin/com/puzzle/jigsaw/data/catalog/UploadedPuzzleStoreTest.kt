package com.puzzle.jigsaw.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UploadedPuzzleStoreTest {

    @Test
    fun `resolveCropRectForTest converts normalized bounds to pixels`() {
        val rect = resolveCropRectForTest(
            width = 1000,
            height = 1250,
            cropLeft = 0.1f,
            cropTop = 0.2f,
            cropRight = 0.9f,
            cropBottom = 0.8f,
        )

        assertNotNull(rect)
        assertEquals(100, rect!!.left)
        assertEquals(250, rect.top)
        assertEquals(900, rect.right)
        assertEquals(1000, rect.bottom)
    }

    @Test
    fun `resolveCropRectForTest rejects empty selections`() {
        assertNull(
            resolveCropRectForTest(
                width = 800,
                height = 1000,
                cropLeft = 0.4f,
                cropTop = 0.2f,
                cropRight = 0.4f,
                cropBottom = 0.8f,
            ),
        )
    }

    @Test
    fun `buildUploadedPuzzleImagesForTest sorts newest imports first`() {
        val images = buildUploadedPuzzleImagesForTest(
            listOf(
                UploadedPuzzleMetadata(
                    id = "upload-old",
                    title = "Old",
                    importedAtEpochMillis = 10L,
                ),
                UploadedPuzzleMetadata(
                    id = "upload-new",
                    title = "New",
                    importedAtEpochMillis = 20L,
                ),
            ),
        )

        assertEquals(listOf("upload-new", "upload-old"), images.map { it.id })
        assertEquals(UploadsCategoryId, images.first().categoryId)
        assertEquals(listOf(0, 1), images.map { it.sortOrder })
    }
}
