package com.puzzle.jigsaw.feature.gameplay

internal fun currentElapsedPlayTimeMillis(
    baseElapsedPlayTimeMillis: Long,
    activeStartedAtEpochMillis: Long?,
    nowEpochMillis: Long,
): Long {
    val activeElapsedMillis = activeStartedAtEpochMillis?.let { startedAtEpochMillis ->
        (nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)
    } ?: 0L
    return (baseElapsedPlayTimeMillis + activeElapsedMillis).coerceAtLeast(0L)
}

internal fun formatElapsedPlayTime(elapsedPlayTimeMillis: Long): String {
    val totalSeconds = (elapsedPlayTimeMillis.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
