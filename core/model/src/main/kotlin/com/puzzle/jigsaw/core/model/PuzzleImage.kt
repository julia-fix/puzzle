package com.puzzle.jigsaw.core.model

data class PuzzleImage(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val assetPath: String,
    val categoryId: String = "featured",
    val categoryName: String = "Featured",
)
