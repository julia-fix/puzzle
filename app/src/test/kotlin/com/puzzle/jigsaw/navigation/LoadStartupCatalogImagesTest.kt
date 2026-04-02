package com.puzzle.jigsaw.navigation

import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadStartupCatalogImagesTest {
    @Test
    fun `loadStartupCatalogImages loads local catalog immediately`() = runBlocking {
        val loadedImages = listOf(image("loaded"))

        val images = loadStartupCatalogImages(
            loadCatalogImages = {
                loadedImages
            },
        )

        assertEquals(loadedImages, images)
    }

    @Test
    fun `syncStartupCatalogImages updates the gallery when sync swaps content`() = runBlocking {
        val callLog = mutableListOf<String>()
        val syncedImages = listOf(image("synced"))
        var appliedImages: List<PuzzleImage>? = null

        val updated = syncStartupCatalogImages(
            syncCatalogIfNeeded = {
                callLog += "sync"
                syncedImages
            },
            onCatalogSynced = { images ->
                callLog += "apply"
                appliedImages = images
            },
        )

        assertTrue(updated)
        assertEquals(listOf("sync", "apply"), callLog)
        assertEquals(syncedImages, appliedImages)
    }

    @Test
    fun `syncStartupCatalogImages leaves the current gallery state when sync does not swap content`() = runBlocking {
        var appliedCount = 0

        val updated = syncStartupCatalogImages(
            syncCatalogIfNeeded = { null },
            onCatalogSynced = {
                appliedCount += 1
            },
        )

        assertFalse(updated)
        assertEquals(0, appliedCount)
    }

    private fun image(id: String): PuzzleImage = PuzzleImage(
        id = id,
        title = id,
        previewPath = "$id-preview.webp",
        fullImagePath = "$id.webp",
        storage = PuzzleImageStorage.FILE,
    )
}
