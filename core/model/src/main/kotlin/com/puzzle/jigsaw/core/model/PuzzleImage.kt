package com.puzzle.jigsaw.core.model

enum class PuzzleImageStorage {
    ASSET,
    FILE,
}

data class PuzzleImage(
    val id: String,
    val title: String,
    val previewPath: String,
    val fullImagePath: String,
    val storage: PuzzleImageStorage,
    val categoryId: String = "featured",
    val categoryName: String = "Featured",
    val categorySortOrder: Int = Int.MAX_VALUE,
    val sortOrder: Int = Int.MAX_VALUE,
)
