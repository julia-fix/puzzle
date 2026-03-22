package com.puzzle.jigsaw.domain.jigsaw

import kotlin.random.Random

enum class JigsawEdgeKind {
    FLAT,
    TAB,
    BLANK,
}

data class JigsawEdgeProfile(
    val tabSizeRatio: Float,
    val startJitterRatio: Float,
    val centerOffsetRatio: Float,
    val normalOffsetRatio: Float,
    val neckOffsetRatio: Float,
    val endJitterRatio: Float,
) {
    companion object {
        val Flat = JigsawEdgeProfile(
            tabSizeRatio = DEFAULT_TAB_SIZE_RATIO,
            startJitterRatio = 0f,
            centerOffsetRatio = 0f,
            normalOffsetRatio = 0f,
            neckOffsetRatio = 0f,
            endJitterRatio = 0f,
        )
    }
}

data class JigsawEdge(
    val kind: JigsawEdgeKind,
    val profile: JigsawEdgeProfile = JigsawEdgeProfile.Flat,
) {
    val extensionRatio: Float
        get() = if (kind == JigsawEdgeKind.TAB) {
            (profile.tabSizeRatio * 3f) + kotlin.math.abs(profile.normalOffsetRatio)
        } else {
            0f
        }
}

data class JigsawPieceLayout(
    val pieceId: Int,
    val row: Int,
    val column: Int,
    val top: JigsawEdge,
    val right: JigsawEdge,
    val bottom: JigsawEdge,
    val left: JigsawEdge,
)

fun createJigsawPieceLayout(
    rows: Int,
    columns: Int,
    tabSizePercent: Float = 20f,
    jitterPercent: Float = 4f,
): List<JigsawPieceLayout> {
    require(rows > 0) { "rows must be greater than zero." }
    require(columns > 0) { "columns must be greater than zero." }
    require(tabSizePercent > 0f) { "tabSizePercent must be greater than zero." }
    require(jitterPercent >= 0f) { "jitterPercent must be at least zero." }

    val tabSizeRatio = tabSizePercent / 200f
    val jitterRatio = jitterPercent / 100f

    val horizontalDividers = Array(rows - 1) { row ->
        createInteriorEdgeSequence(
            segmentCount = columns,
            seed = seamSeed(index = row, orientation = DividerOrientation.HORIZONTAL),
            tabSizeRatio = tabSizeRatio,
            jitterRatio = jitterRatio,
        )
    }
    val verticalDividers = Array(columns - 1) { column ->
        createInteriorEdgeSequence(
            segmentCount = rows,
            seed = seamSeed(index = column, orientation = DividerOrientation.VERTICAL),
            tabSizeRatio = tabSizeRatio,
            jitterRatio = jitterRatio,
        )
    }

    return buildList(capacity = rows * columns) {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                add(
                    JigsawPieceLayout(
                        pieceId = row * columns + column,
                        row = row,
                        column = column,
                        top = if (row == 0) {
                            JigsawEdge(kind = JigsawEdgeKind.FLAT)
                        } else {
                            horizontalDividers[row - 1][column].opposite()
                        },
                        right = if (column == columns - 1) {
                            JigsawEdge(kind = JigsawEdgeKind.FLAT)
                        } else {
                            verticalDividers[column][row]
                        },
                        bottom = if (row == rows - 1) {
                            JigsawEdge(kind = JigsawEdgeKind.FLAT)
                        } else {
                            horizontalDividers[row][column]
                        },
                        left = if (column == 0) {
                            JigsawEdge(kind = JigsawEdgeKind.FLAT)
                        } else {
                            verticalDividers[column - 1][row].opposite()
                        },
                    ),
                )
            }
        }
    }
}

private enum class DividerOrientation {
    HORIZONTAL,
    VERTICAL,
}

private fun createInteriorEdgeSequence(
    segmentCount: Int,
    seed: Int,
    tabSizeRatio: Float,
    jitterRatio: Float,
): Array<JigsawEdge> {
    val random = Random(seed)
    var previousFlip: Boolean? = null
    var previousEndJitter = random.uniform(-jitterRatio, jitterRatio)

    return Array(segmentCount) {
        val flip = random.nextBoolean()
        val profile = JigsawEdgeProfile(
            tabSizeRatio = tabSizeRatio,
            startJitterRatio = if (previousFlip != null && previousFlip == flip) {
                -previousEndJitter
            } else {
                previousEndJitter
            },
            centerOffsetRatio = random.uniform(-jitterRatio, jitterRatio),
            normalOffsetRatio = random.uniform(-jitterRatio, jitterRatio),
            neckOffsetRatio = random.uniform(-jitterRatio, jitterRatio),
            endJitterRatio = random.uniform(-jitterRatio, jitterRatio),
        )
        previousFlip = flip
        previousEndJitter = profile.endJitterRatio
        JigsawEdge(
            kind = if (flip) JigsawEdgeKind.BLANK else JigsawEdgeKind.TAB,
            profile = profile,
        )
    }
}

private fun seamSeed(
    index: Int,
    orientation: DividerOrientation,
): Int {
    val salt = when (orientation) {
        DividerOrientation.HORIZONTAL -> 19
        DividerOrientation.VERTICAL -> 47
    }
    return ((index + 1) * 9_973) + salt
}

private fun Random.uniform(
    min: Float,
    max: Float,
): Float = min + (nextFloat() * (max - min))

private fun JigsawEdge.opposite(): JigsawEdge = copy(kind = kind.opposite())

private fun JigsawEdgeKind.opposite(): JigsawEdgeKind = when (this) {
    JigsawEdgeKind.FLAT -> JigsawEdgeKind.FLAT
    JigsawEdgeKind.TAB -> JigsawEdgeKind.BLANK
    JigsawEdgeKind.BLANK -> JigsawEdgeKind.TAB
}

private const val DEFAULT_TAB_SIZE_RATIO = 0.10f
