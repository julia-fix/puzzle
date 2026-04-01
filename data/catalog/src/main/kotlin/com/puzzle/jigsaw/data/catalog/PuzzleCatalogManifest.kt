package com.puzzle.jigsaw.data.catalog

import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class PuzzleCatalogManifest(
    val revision: String,
    val categories: List<PuzzleCatalogCategory>,
    val images: List<PuzzleCatalogManifestImage>,
)

internal data class PuzzleCatalogCategory(
    val id: String,
    val sortOrder: Int,
    val names: Map<String, String>,
)

internal data class PuzzleCatalogManifestImage(
    val categoryId: String,
    val previewPath: String,
    val path: String,
    val updatedAt: String,
    val updatedAtDate: String,
)

internal data class PuzzleCatalogSnapshot(
    val revision: String,
    val manifest: PuzzleCatalogManifest,
    val images: List<PuzzleImage>,
)

internal fun validateCatalogManifestForTest(manifest: PuzzleCatalogManifest): Boolean =
    manifest.isStructurallyValid()

internal fun normalizeRelativePathForTest(rawValue: String): String? = normalizeRelativePath(rawValue)

internal fun legacyImageIdFromPathForTest(path: String): String = legacyImageIdFromPath(path)

internal fun canReuseDownloadedFiles(
    localImage: PuzzleCatalogManifestImage,
    remoteImage: PuzzleCatalogManifestImage,
): Boolean = canReuseDownloadedFilesImpl(localImage, remoteImage)

internal fun parseCatalogManifest(json: String): PuzzleCatalogManifest? {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    val revision = root.optString("revision").trim()
    if (revision.isEmpty()) {
        return null
    }

    val manifest = PuzzleCatalogManifest(
        revision = revision,
        categories = parseCategories(root.optJSONArray("categories") ?: JSONArray()),
        images = parseImages(root.optJSONArray("images") ?: JSONArray()),
    )

    return manifest.takeIf(PuzzleCatalogManifest::isStructurallyValid)
}

internal fun PuzzleCatalogManifest.toPuzzleImages(
    locale: Locale,
    storage: PuzzleImageStorage,
    resolvePath: (String) -> String,
): List<PuzzleImage> {
    val categoriesById = categories.associateBy(PuzzleCatalogCategory::id)

    return images
        .sortedWith(
            compareByDescending<PuzzleCatalogManifestImage> { it.updatedAtDate }
                .thenBy(::fileNameFromPath)
                .thenBy(PuzzleCatalogManifestImage::path),
        )
        .mapIndexed { index, image ->
        val category = categoriesById.getValue(image.categoryId)
        val id = imageIdFromPath(image.path)
        PuzzleImage(
            id = id,
            title = legacyImageIdFromPath(image.path),
            previewPath = resolvePath(image.previewPath),
            fullImagePath = resolvePath(image.path),
            storage = storage,
            categoryId = category.id,
            categoryName = category.localizedName(locale),
            categorySortOrder = category.sortOrder,
            sortOrder = index,
        )
        }
}

internal fun PuzzleCatalogManifest.hasAllFiles(relativePathExists: (String) -> Boolean): Boolean =
    images.all { image ->
        relativePathExists(image.previewPath) && relativePathExists(image.path)
    }

private fun parseCategories(rawCategories: JSONArray): List<PuzzleCatalogCategory> = buildList {
    for (index in 0 until rawCategories.length()) {
        val item = rawCategories.optJSONObject(index) ?: continue
        val id = item.optString("id").trim()
        if (id.isEmpty()) {
            continue
        }
        val names = item.optJSONObject("names").toStringMap()
        add(
            PuzzleCatalogCategory(
                id = id,
                sortOrder = item.optInt("sortOrder", index),
                names = names,
            ),
        )
    }
}

private fun parseImages(rawImages: JSONArray): List<PuzzleCatalogManifestImage> = buildList {
    for (index in 0 until rawImages.length()) {
        val item = rawImages.optJSONObject(index) ?: continue
        val categoryId = item.optString("categoryId").trim()
        if (categoryId.isEmpty()) {
            continue
        }
        val previewPath = normalizeRelativePath(item.optString("previewPath")) ?: continue
        val path = normalizeRelativePath(item.optString("path")) ?: continue
        val updatedAt = item.optString("updatedAt").trim().takeIf(String::isNotEmpty) ?: continue
        val updatedAtDate = item.optString("updatedAtDate").trim()
            .takeIf(String::isNotEmpty)
            ?: updatedAt.substringBefore('T').takeIf(String::isNotEmpty)
            ?: continue
        add(
            PuzzleCatalogManifestImage(
                categoryId = categoryId,
                previewPath = previewPath,
                path = path,
                updatedAt = updatedAt,
                updatedAtDate = updatedAtDate,
            ),
        )
    }
}

private fun PuzzleCatalogManifest.isStructurallyValid(): Boolean {
    if (categories.isEmpty() || images.isEmpty()) {
        return false
    }

    val categoryIds = categories.map(PuzzleCatalogCategory::id)
    if (categoryIds.size != categoryIds.toSet().size) {
        return false
    }

    val imageIds = images.map { image -> imageIdFromPath(image.path) }
    if (imageIds.any(String::isBlank) || imageIds.size != imageIds.toSet().size) {
        return false
    }

    return images.all { image -> image.categoryId in categoryIds }
}

private fun PuzzleCatalogCategory.localizedName(locale: Locale): String {
    val language = locale.language.lowercase(Locale.ROOT)
    return names[language]
        ?.takeIf(String::isNotBlank)
        ?: names["en"]?.takeIf(String::isNotBlank)
        ?: names.values.firstOrNull(String::isNotBlank)
        ?: id
}

private fun JSONObject?.toStringMap(): Map<String, String> {
    if (this == null) {
        return emptyMap()
    }

    val names = linkedMapOf<String, String>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        val value = optString(key).trim()
        if (value.isNotEmpty()) {
            names[key] = value
        }
    }
    return names
}

private fun normalizeRelativePath(rawValue: String): String? {
    val trimmed = rawValue.trim().replace('\\', '/')
    if (trimmed.isEmpty()) {
        return null
    }

    val segments = trimmed.removePrefix("/")
        .split('/')
        .filter(String::isNotEmpty)

    if (segments.isEmpty() || segments.any { segment -> segment == "." || segment == ".." }) {
        return null
    }

    return segments.joinToString(separator = "/")
}

private fun imageIdFromPath(path: String): String = path

private fun legacyImageIdFromPath(path: String): String = path.substringAfterLast('/').substringBeforeLast('.')

private fun fileNameFromPath(image: PuzzleCatalogManifestImage): String = image.path.substringAfterLast('/')

private fun canReuseDownloadedFilesImpl(
    localImage: PuzzleCatalogManifestImage,
    remoteImage: PuzzleCatalogManifestImage,
): Boolean = localImage.path == remoteImage.path &&
    localImage.previewPath == remoteImage.previewPath &&
    localImage.updatedAt == remoteImage.updatedAt
