package com.puzzle.jigsaw.data.catalog

import android.content.Context
import com.puzzle.jigsaw.core.model.PuzzleImage
import org.json.JSONArray
import org.json.JSONObject

private const val PUZZLES_DIR = "puzzles"
private const val MANIFEST_PATH = "$PUZZLES_DIR/manifest.json"

class AssetPuzzleCatalogRepository(
    private val context: Context,
) {
    fun loadImages(): List<PuzzleImage> {
        val manifestImages = loadManifestImages()
        if (manifestImages.isNotEmpty()) {
            return manifestImages
        }

        return context.assets.list(PUZZLES_DIR)
            ?.filter(::isPuzzleImageFile)
            ?.sorted()
            ?.mapIndexed { index, fileName ->
                PuzzleImage(
                    id = fileName.substringBeforeLast('.'),
                    title = "Puzzle ${index + 1}",
                    subtitle = "Bundled image",
                    description = "Packaged starter puzzle image.",
                    assetPath = "$PUZZLES_DIR/$fileName",
                    categoryId = defaultCategoryIdForIndex(index),
                    categoryName = defaultCategoryNameForIndex(index),
                )
            }
            .orEmpty()
    }

    private fun loadManifestImages(): List<PuzzleImage> {
        val json = runCatching {
            context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()

        val images = runCatching {
            JSONObject(json).optJSONArray("images") ?: JSONArray()
        }.getOrElse { return emptyList() }

        return buildList {
            for (index in 0 until images.length()) {
                val item = images.optJSONObject(index) ?: continue
                val fileName = item.optString("fileName").takeIf(String::isNotBlank) ?: continue
                val id = item.optString("id").ifBlank { fileName.substringBeforeLast('.') }
                add(
                    PuzzleImage(
                        id = id,
                        title = item.optString("title").ifBlank { titleFromId(id) },
                        subtitle = item.optString("subtitle").ifBlank { "Bundled image" },
                        description = item.optString("description").ifBlank { "Packaged starter puzzle image." },
                        assetPath = "$PUZZLES_DIR/$fileName",
                        categoryId = item.optString("categoryId").ifBlank { "featured" },
                        categoryName = item.optString("categoryName").ifBlank { "Featured" },
                    ),
                )
            }
        }
    }
}

private fun isPuzzleImageFile(fileName: String): Boolean {
    val normalized = fileName.lowercase()
    return normalized.endsWith(".webp") ||
        normalized.endsWith(".jpg") ||
        normalized.endsWith(".jpeg") ||
        normalized.endsWith(".png")
}

private fun titleFromId(id: String): String = id
    .split('-', '_', ' ')
    .filter(String::isNotBlank)
    .joinToString(separator = " ") { token ->
        token.replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase() else char.toString()
        }
    }

private fun defaultCategoryIdForIndex(index: Int): String = when (index) {
    0, 3 -> "nature"
    1 -> "sunsets"
    2 -> "city"
    else -> "featured"
}

private fun defaultCategoryNameForIndex(index: Int): String = when (index) {
    0, 3 -> "Nature"
    1 -> "Sunsets"
    2 -> "City"
    else -> "Featured"
}
