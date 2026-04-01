package com.puzzle.jigsaw.navigation

import android.net.Uri

object JigsawDestination {
    const val imageIdArg = "imageId"
    const val pieceCountArg = "pieceCount"

    const val galleryRoute = "gallery"
    const val pieceCountRoute = "piece-count/{$imageIdArg}"
    const val gameplayRoute = "gameplay/{$imageIdArg}/{$pieceCountArg}"

    fun pieceCount(imageId: String): String = "piece-count/${Uri.encode(imageId)}"

    fun gameplay(imageId: String, pieceCount: Int): String = "gameplay/${Uri.encode(imageId)}/$pieceCount"
}
