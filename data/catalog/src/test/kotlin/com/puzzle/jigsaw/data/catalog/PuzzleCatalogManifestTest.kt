package com.puzzle.jigsaw.data.catalog

import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleCatalogManifestTest {

    @Test
    fun `validateCatalogManifestForTest accepts distinct image ids`() {
        val manifest = manifest(
            images = listOf(
                image(
                    previewPath = "animals/thumbnails/cat_thumb.webp",
                    path = "animals/fullsize/cat.webp",
                    updatedAt = "2026-03-18T14:10:00Z",
                    updatedAtDate = "2026-03-18",
                ),
            ),
        )

        assertTrue(validateCatalogManifestForTest(manifest))
    }

    @Test
    fun `toPuzzleImages sorts by updatedAtDate then filename`() {
        val manifest = manifest(
            images = listOf(
                image(
                    previewPath = "animals/thumbnails/zebra_thumb.webp",
                    path = "animals/fullsize/zebra.webp",
                    updatedAt = "2026-03-18T14:10:00Z",
                    updatedAtDate = "2026-03-18",
                ),
                image(
                    previewPath = "animals/thumbnails/antelope_thumb.webp",
                    path = "animals/fullsize/antelope.webp",
                    updatedAt = "2026-03-20T11:40:00Z",
                    updatedAtDate = "2026-03-20",
                ),
                image(
                    previewPath = "animals/thumbnails/bear_thumb.webp",
                    path = "animals/fullsize/bear.webp",
                    updatedAt = "2026-03-20T09:00:00Z",
                    updatedAtDate = "2026-03-20",
                ),
            ),
        )

        val images = manifest.toPuzzleImages(
            locale = Locale.forLanguageTag("ru"),
            storage = PuzzleImageStorage.ASSET,
            resolvePath = { relativePath -> "puzzles/$relativePath" },
        )

        assertEquals(
            listOf(
                "animals/fullsize/antelope.webp",
                "animals/fullsize/bear.webp",
                "animals/fullsize/zebra.webp",
            ),
            images.map { it.id },
        )
        assertEquals(listOf(0, 1, 2), images.map { it.sortOrder })
        assertEquals("Животные", images.first().categoryName)
        assertEquals("antelope", images.first().title)
        assertEquals("puzzles/animals/thumbnails/antelope_thumb.webp", images.first().previewPath)
    }

    @Test
    fun `validateCatalogManifestForTest rejects duplicate paths`() {
        val manifest = manifest(
            images = listOf(
                image(
                    previewPath = "animals/thumbnails/cat_thumb.webp",
                    path = "animals/fullsize/cat.webp",
                    updatedAt = "2026-03-18T14:10:00Z",
                    updatedAtDate = "2026-03-18",
                ),
                image(
                    previewPath = "animals/thumbnails/cat_alt_thumb.webp",
                    path = "animals/fullsize/cat.webp",
                    updatedAt = "2026-03-20T11:40:00Z",
                    updatedAtDate = "2026-03-20",
                ),
            ),
        )

        assertFalse(validateCatalogManifestForTest(manifest))
    }

    @Test
    fun `validateCatalogManifestForTest allows same filename in different folders`() {
        val manifest = PuzzleCatalogManifest(
            revision = "rev-1",
            categories = listOf(
                PuzzleCatalogCategory(
                    id = "animals",
                    sortOrder = 10,
                    names = mapOf("en" to "Animals"),
                ),
                PuzzleCatalogCategory(
                    id = "nature",
                    sortOrder = 20,
                    names = mapOf("en" to "Nature"),
                ),
            ),
            images = listOf(
                PuzzleCatalogManifestImage(
                    categoryId = "animals",
                    previewPath = "animals/thumbnails/shared_thumb.webp",
                    path = "animals/fullsize/shared.webp",
                    updatedAt = "2026-03-18T14:10:00Z",
                    updatedAtDate = "2026-03-18",
                ),
                PuzzleCatalogManifestImage(
                    categoryId = "nature",
                    previewPath = "nature/thumbnails/shared_thumb.webp",
                    path = "nature/fullsize/shared.webp",
                    updatedAt = "2026-03-18T14:10:00Z",
                    updatedAtDate = "2026-03-18",
                ),
            ),
        )

        assertTrue(validateCatalogManifestForTest(manifest))
    }

    @Test
    fun `hasAllFiles returns false when preview is missing`() {
        val manifest = manifest(
            images = listOf(
                image(
                    previewPath = "animals/thumbnails/cat_thumb.webp",
                    path = "animals/fullsize/cat.webp",
                    updatedAt = "2026-03-18T14:10:00Z",
                    updatedAtDate = "2026-03-18",
                ),
            ),
        )

        assertFalse(
            manifest.hasAllFiles { relativePath ->
                relativePath == "animals/fullsize/cat.webp"
            },
        )
    }

    @Test
    fun `normalizeRelativePathForTest rejects parent traversal`() {
        assertEquals(null, normalizeRelativePathForTest("../animals/fullsize/cat.webp"))
        assertEquals("animals/fullsize/cat.webp", normalizeRelativePathForTest("/animals/fullsize/cat.webp"))
    }

    @Test
    fun `legacyImageIdFromPathForTest keeps filename without extension`() {
        assertEquals("cat", legacyImageIdFromPathForTest("animals/fullsize/cat.webp"))
    }

    @Test
    fun `canReuseDownloadedFiles requires same preview path and updatedAt`() {
        val local = image(
            previewPath = "animals/thumbnails/cat_thumb.webp",
            path = "animals/fullsize/cat.webp",
            updatedAt = "2026-03-18T14:10:00Z",
            updatedAtDate = "2026-03-18",
        )
        val same = image(
            previewPath = "animals/thumbnails/cat_thumb.webp",
            path = "animals/fullsize/cat.webp",
            updatedAt = "2026-03-18T14:10:00Z",
            updatedAtDate = "2026-03-18",
        )
        val changedTimestamp = image(
            previewPath = "animals/thumbnails/cat_thumb.webp",
            path = "animals/fullsize/cat.webp",
            updatedAt = "2026-03-20T11:40:00Z",
            updatedAtDate = "2026-03-20",
        )
        val changedPreview = image(
            previewPath = "animals/thumbnails/cat_alt_thumb.webp",
            path = "animals/fullsize/cat.webp",
            updatedAt = "2026-03-18T14:10:00Z",
            updatedAtDate = "2026-03-18",
        )

        assertTrue(canReuseDownloadedFiles(local, same))
        assertFalse(canReuseDownloadedFiles(local, changedTimestamp))
        assertFalse(canReuseDownloadedFiles(local, changedPreview))
    }

    private fun manifest(
        images: List<PuzzleCatalogManifestImage>,
    ): PuzzleCatalogManifest = PuzzleCatalogManifest(
        revision = "rev-1",
        categories = listOf(
            PuzzleCatalogCategory(
                id = "animals",
                sortOrder = 10,
                names = mapOf(
                    "en" to "Animals",
                    "ru" to "Животные",
                ),
            ),
        ),
        images = images,
    )

    private fun image(
        previewPath: String,
        path: String,
        updatedAt: String,
        updatedAtDate: String,
    ): PuzzleCatalogManifestImage = PuzzleCatalogManifestImage(
        categoryId = "animals",
        previewPath = previewPath,
        path = path,
        updatedAt = updatedAt,
        updatedAtDate = updatedAtDate,
    )
}
