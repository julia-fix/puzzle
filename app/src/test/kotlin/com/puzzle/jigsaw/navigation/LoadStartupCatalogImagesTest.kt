package com.puzzle.jigsaw.navigation

import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadStartupCatalogImagesTest {
    @Test
    fun `loadStartupCatalogImages returns synced images without loading local catalog again`() = runBlocking {
        val callLog = mutableListOf<String>()
        val syncedImages = listOf(image("synced"))

        val images = loadStartupCatalogImages(
            syncCatalogIfNeeded = {
                callLog += "sync"
                syncedImages
            },
            loadCatalogImages = {
                callLog += "load"
                listOf(image("loaded"))
            },
        )

        assertEquals(listOf("sync"), callLog)
        assertEquals(syncedImages, images)
    }

    @Test
    fun `loadStartupCatalogImages falls back to local catalog when sync does not swap content`() = runBlocking {
        val callLog = mutableListOf<String>()
        val loadedImages = listOf(image("loaded"))

        val images = loadStartupCatalogImages(
            syncCatalogIfNeeded = {
                callLog += "sync"
                null
            },
            loadCatalogImages = {
                callLog += "load"
                loadedImages
            },
        )

        assertEquals(listOf("sync", "load"), callLog)
        assertEquals(loadedImages, images)
    }

    private fun image(id: String): PuzzleImage = PuzzleImage(
        id = id,
        title = id,
        previewPath = "$id-preview.webp",
        fullImagePath = "$id.webp",
        storage = PuzzleImageStorage.FILE,
    )
}
