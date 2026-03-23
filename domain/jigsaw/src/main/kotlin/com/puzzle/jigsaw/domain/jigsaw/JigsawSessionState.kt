package com.puzzle.jigsaw.domain.jigsaw

import com.puzzle.jigsaw.core.model.JigsawProgress
import com.puzzle.jigsaw.core.model.PieceCountOption
import com.puzzle.jigsaw.core.model.PuzzleImage
import com.puzzle.jigsaw.core.model.SavedBoardPiecePosition
import com.puzzle.jigsaw.core.model.SavedPieceLink
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

data class JigsawBoardPosition(
    val x: Float,
    val y: Float,
)

data class JigsawPieceLink(
    val firstPieceId: Int,
    val secondPieceId: Int,
) {
    init {
        require(firstPieceId != secondPieceId) { "Piece links require two distinct ids." }
        require(firstPieceId < secondPieceId) { "Piece links must be normalized." }
    }
}

data class JigsawSnapOutcome(
    val state: JigsawSessionState,
    val snappedPieceIds: Set<Int> = emptySet(),
    val lockedPieceIds: Set<Int> = emptySet(),
) {
    val didSnap: Boolean = snappedPieceIds.isNotEmpty() || lockedPieceIds.isNotEmpty()
}

data class JigsawSessionState(
    val image: PuzzleImage,
    val pieceCount: PieceCountOption,
    val placedPieceIds: Set<Int>,
    val boardPiecePositions: Map<Int, JigsawBoardPosition>,
    val pieceLinks: Set<JigsawPieceLink>,
    val pieceOrder: List<Int>,
) {
    val totalPieces: Int = pieceCount.totalPieces
    val completionRatio: Float =
        if (totalPieces == 0) 0f else placedPieceIds.size.toFloat() / totalPieces.toFloat()
    val remainingPieceIds: List<Int> =
        pieceOrder.filterNot { it in placedPieceIds || it in boardPiecePositions }

    fun toProgress(updatedAtEpochMillis: Long): JigsawProgress = JigsawProgress(
        imageId = image.id,
        pieceCount = totalPieces,
        placedPieceIds = placedPieceIds,
        boardPiecePositions = boardPiecePositions.map { (pieceId, position) ->
            SavedBoardPiecePosition(
                pieceId = pieceId,
                x = position.x,
                y = position.y,
            )
        },
        pieceLinks = pieceLinks.mapTo(mutableSetOf()) { link ->
            SavedPieceLink(
                firstPieceId = link.firstPieceId,
                secondPieceId = link.secondPieceId,
            )
        },
        pieceOrder = pieceOrder,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

fun createSessionState(
    image: PuzzleImage,
    pieceCount: PieceCountOption,
    progress: JigsawProgress?,
): JigsawSessionState {
    val allowedPieceIds = (0 until pieceCount.totalPieces).toSet()
    val placedPieces = progress
        ?.placedPieceIds
        ?.filterTo(mutableSetOf()) { it in allowedPieceIds }
        ?: emptySet()
    val boardPiecePositions = progress
        ?.boardPiecePositions
        ?.filter { saved ->
            saved.pieceId in allowedPieceIds && saved.pieceId !in placedPieces
        }
        ?.associate { saved ->
            saved.pieceId to JigsawBoardPosition(
                x = saved.x,
                y = saved.y,
            )
        }
        ?: emptyMap()
    val pieceLinks = progress
        ?.pieceLinks
        ?.mapNotNull { saved ->
            val firstPieceId = saved.firstPieceId
            val secondPieceId = saved.secondPieceId
            if (
                firstPieceId !in boardPiecePositions ||
                secondPieceId !in boardPiecePositions ||
                firstPieceId == secondPieceId
            ) {
                null
            } else {
                JigsawPieceLink(
                    firstPieceId = minOf(firstPieceId, secondPieceId),
                    secondPieceId = maxOf(firstPieceId, secondPieceId),
                )
            }
        }
        ?.toSet()
        ?: emptySet()

    val defaultPieceOrder = createPieceOrder(imageId = image.id, totalPieces = pieceCount.totalPieces)
    val pieceOrder = progress
        ?.pieceOrder
        ?.takeIf { savedOrder ->
            savedOrder.size == pieceCount.totalPieces &&
                savedOrder.toSet() == allowedPieceIds
        }
        ?: defaultPieceOrder

    return JigsawSessionState(
        image = image,
        pieceCount = pieceCount,
        placedPieceIds = placedPieces,
        boardPiecePositions = boardPiecePositions,
        pieceLinks = pieceLinks,
        pieceOrder = pieceOrder,
    )
}

fun reorderTrayPieces(
    state: JigsawSessionState,
    movedPieceIds: Set<Int>,
    targetIndex: Int,
): JigsawSessionState {
    if (movedPieceIds.isEmpty()) return state

    val remainingPieceIds = state.remainingPieceIds
    val movedBlock = remainingPieceIds.filter { it in movedPieceIds }
    if (movedBlock.isEmpty()) return state

    val stableRemaining = remainingPieceIds.filterNot { it in movedPieceIds }
    val insertionIndex = targetIndex.coerceIn(0, stableRemaining.size)
    val reorderedRemaining = buildList(remainingPieceIds.size) {
        addAll(stableRemaining.take(insertionIndex))
        addAll(movedBlock)
        addAll(stableRemaining.drop(insertionIndex))
    }
    var reorderedRemainingIndex = 0
    val reorderedPieceOrder = state.pieceOrder.map { pieceId ->
        if (pieceId in remainingPieceIds) {
            reorderedRemaining[reorderedRemainingIndex++]
        } else {
            pieceId
        }
    }

    return state.copy(pieceOrder = reorderedPieceOrder)
}

fun movePieceCluster(
    state: JigsawSessionState,
    pieceId: Int,
    targetPosition: JigsawBoardPosition,
): JigsawSessionState {
    require(pieceId in 0 until state.totalPieces) {
        "Piece id $pieceId is out of bounds for ${state.totalPieces} pieces."
    }
    require(pieceId !in state.placedPieceIds) {
        "Placed piece $pieceId cannot be moved."
    }

    val currentPosition = state.boardPiecePositions[pieceId] ?: return state.copy(
        boardPiecePositions = state.boardPiecePositions + (pieceId to targetPosition),
    )
    val clusterPieceIds = connectedBoardPieceIds(state, pieceId)
    val deltaX = targetPosition.x - currentPosition.x
    val deltaY = targetPosition.y - currentPosition.y
    val updatedPositions = state.boardPiecePositions.toMutableMap()
    clusterPieceIds.forEach { clusterPieceId ->
        val position = updatedPositions.getValue(clusterPieceId)
        updatedPositions[clusterPieceId] = position.copy(
            x = position.x + deltaX,
            y = position.y + deltaY,
        )
    }
    return state.copy(boardPiecePositions = updatedPositions)
}

fun returnPieceClusterToTray(
    state: JigsawSessionState,
    pieceId: Int,
): JigsawSessionState {
    require(pieceId in 0 until state.totalPieces) {
        "Piece id $pieceId is out of bounds for ${state.totalPieces} pieces."
    }
    if (pieceId in state.placedPieceIds || pieceId !in state.boardPiecePositions) {
        return state
    }

    val clusterPieceIds = connectedBoardPieceIds(state, pieceId)
    val updatedPositions = state.boardPiecePositions.toMutableMap().apply {
        clusterPieceIds.forEach(::remove)
    }
    val updatedLinks = state.pieceLinks.filterNot { link ->
        link.firstPieceId in clusterPieceIds || link.secondPieceId in clusterPieceIds
    }.toSet()

    return state.copy(
        boardPiecePositions = updatedPositions,
        pieceLinks = updatedLinks,
    )
}

fun snapPieceCluster(
    state: JigsawSessionState,
    pieceId: Int,
    snapThreshold: Float,
): JigsawSnapOutcome {
    require(pieceId in 0 until state.totalPieces) {
        "Piece id $pieceId is out of bounds for ${state.totalPieces} pieces."
    }
    if (pieceId in state.placedPieceIds || pieceId !in state.boardPiecePositions) {
        return JigsawSnapOutcome(state = state)
    }

    val movingCluster = connectedBoardPieceIds(state, pieceId)
    val movingPositions = state.boardPiecePositions
    val boardSnap = findBoardSnapCandidate(state, movingCluster, movingPositions, snapThreshold)
    val neighborSnap = findNeighborSnapCandidate(state, movingCluster, movingPositions, snapThreshold)
    val candidate = listOfNotNull(boardSnap, neighborSnap).minByOrNull(SnapCandidate::distance)
        ?: return JigsawSnapOutcome(state = state)

    val translatedPositions = state.boardPiecePositions.toMutableMap()
    movingCluster.forEach { clusterPieceId ->
        val position = translatedPositions.getValue(clusterPieceId)
        translatedPositions[clusterPieceId] = position.copy(
            x = position.x + candidate.deltaX,
            y = position.y + candidate.deltaY,
        )
    }

    var updatedLinks = state.pieceLinks
    candidate.link?.let { updatedLinks = updatedLinks + it }

    val postSnapState = state.copy(
        boardPiecePositions = translatedPositions,
        pieceLinks = updatedLinks,
    )
    val settledCluster = connectedBoardPieceIds(postSnapState, pieceId)
    val shouldLock = settledCluster.all { clusterPieceId ->
        val expected = correctBoardPosition(clusterPieceId, postSnapState.pieceCount)
        val current = postSnapState.boardPiecePositions.getValue(clusterPieceId)
        current.isCloseTo(expected, LOCK_EPSILON)
    }

    if (!shouldLock) {
        return JigsawSnapOutcome(
            state = postSnapState,
            snappedPieceIds = movingCluster,
        )
    }

    val updatedPositions = postSnapState.boardPiecePositions.toMutableMap().apply {
        settledCluster.forEach(::remove)
    }
    val remainingLinks = postSnapState.pieceLinks.filterNot { link ->
        link.firstPieceId in settledCluster || link.secondPieceId in settledCluster
    }.toSet()
    val lockedState = postSnapState.copy(
        placedPieceIds = postSnapState.placedPieceIds + settledCluster,
        boardPiecePositions = updatedPositions,
        pieceLinks = remainingLinks,
    )
    return JigsawSnapOutcome(
        state = lockedState,
        snappedPieceIds = movingCluster,
        lockedPieceIds = settledCluster,
    )
}

fun connectedBoardPieceIds(
    state: JigsawSessionState,
    pieceId: Int,
): Set<Int> {
    if (pieceId !in state.boardPiecePositions) return emptySet()

    val adjacency = buildMap<Int, MutableSet<Int>> {
        state.boardPiecePositions.keys.forEach { put(it, mutableSetOf()) }
        state.pieceLinks.forEach { link ->
            if (link.firstPieceId in state.boardPiecePositions && link.secondPieceId in state.boardPiecePositions) {
                getValue(link.firstPieceId).add(link.secondPieceId)
                getValue(link.secondPieceId).add(link.firstPieceId)
            }
        }
    }

    val visited = mutableSetOf<Int>()
    val queue = ArrayDeque<Int>()
    queue.add(pieceId)
    visited.add(pieceId)
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        adjacency[current].orEmpty().forEach { neighbor ->
            if (visited.add(neighbor)) queue.add(neighbor)
        }
    }
    return visited
}

private data class SnapCandidate(
    val distance: Float,
    val deltaX: Float,
    val deltaY: Float,
    val link: JigsawPieceLink? = null,
)

private fun findBoardSnapCandidate(
    state: JigsawSessionState,
    movingCluster: Set<Int>,
    positions: Map<Int, JigsawBoardPosition>,
    snapThreshold: Float,
): SnapCandidate? = movingCluster
    .map { clusterPieceId ->
        val current = positions.getValue(clusterPieceId)
        val expected = correctBoardPosition(clusterPieceId, state.pieceCount)
        val deltaX = expected.x - current.x
        val deltaY = expected.y - current.y
        SnapCandidate(
            distance = distance(deltaX, deltaY),
            deltaX = deltaX,
            deltaY = deltaY,
        )
    }
    .filter { it.distance <= snapThreshold }
    .minByOrNull(SnapCandidate::distance)

private fun findNeighborSnapCandidate(
    state: JigsawSessionState,
    movingCluster: Set<Int>,
    positions: Map<Int, JigsawBoardPosition>,
    snapThreshold: Float,
): SnapCandidate? {
    val occupiedTargets = positions.keys + state.placedPieceIds
    val candidates = mutableListOf<SnapCandidate>()
    movingCluster.forEach { movingPieceId ->
        val movingPosition = positions.getValue(movingPieceId)
        occupiedTargets
            .filter { it !in movingCluster }
            .forEach { targetPieceId ->
                if (!areAdjacentInGrid(movingPieceId, targetPieceId, state.pieceCount)) return@forEach
                val targetPosition = positions[targetPieceId] ?: correctBoardPosition(targetPieceId, state.pieceCount)
                val expectedDeltaX = correctBoardPosition(movingPieceId, state.pieceCount).x - correctBoardPosition(targetPieceId, state.pieceCount).x
                val expectedDeltaY = correctBoardPosition(movingPieceId, state.pieceCount).y - correctBoardPosition(targetPieceId, state.pieceCount).y
                val deltaX = targetPosition.x + expectedDeltaX - movingPosition.x
                val deltaY = targetPosition.y + expectedDeltaY - movingPosition.y
                val distance = distance(deltaX, deltaY)
                if (distance <= snapThreshold) {
                    candidates += SnapCandidate(
                        distance = distance,
                        deltaX = deltaX,
                        deltaY = deltaY,
                        link = JigsawPieceLink(
                            firstPieceId = minOf(movingPieceId, targetPieceId),
                            secondPieceId = maxOf(movingPieceId, targetPieceId),
                        ),
                    )
                }
            }
    }
    return candidates.minByOrNull(SnapCandidate::distance)
}

private fun correctBoardPosition(
    pieceId: Int,
    pieceCount: PieceCountOption,
): JigsawBoardPosition = JigsawBoardPosition(
    x = (pieceId % pieceCount.columns).toFloat(),
    y = (pieceId / pieceCount.columns).toFloat(),
)

private fun areAdjacentInGrid(
    firstPieceId: Int,
    secondPieceId: Int,
    pieceCount: PieceCountOption,
): Boolean {
    val first = correctBoardPosition(firstPieceId, pieceCount)
    val second = correctBoardPosition(secondPieceId, pieceCount)
    return (abs(first.x - second.x) + abs(first.y - second.y)) == 1f
}

private fun JigsawBoardPosition.isCloseTo(
    other: JigsawBoardPosition,
    epsilon: Float,
): Boolean = abs(x - other.x) <= epsilon && abs(y - other.y) <= epsilon

private fun distance(
    deltaX: Float,
    deltaY: Float,
): Float = sqrt((deltaX * deltaX) + (deltaY * deltaY))

private fun createPieceOrder(imageId: String, totalPieces: Int): List<Int> {
    val seed = imageId.fold(totalPieces.toLong()) { acc, char ->
        (acc * 31L) + char.code.toLong()
    }
    return (0 until totalPieces).toList().shuffled(Random(seed))
}

private const val LOCK_EPSILON = 0.001f
