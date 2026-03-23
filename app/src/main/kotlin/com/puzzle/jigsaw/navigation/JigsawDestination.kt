package com.puzzle.jigsaw.navigation

object JigsawDestination {
    const val imageIdArg = "imageId"
    const val pieceCountArg = "pieceCount"

    const val galleryRoute = "gallery"
    const val pieceCountRoute = "piece-count/{$imageIdArg}"
    const val gameplayRoute = "gameplay/{$imageIdArg}/{$pieceCountArg}"

    fun pieceCount(imageId: String): String = "piece-count/$imageId"

    fun gameplay(imageId: String, pieceCount: Int): String = "gameplay/$imageId/$pieceCount"
}
