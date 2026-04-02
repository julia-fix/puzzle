package com.puzzle.jigsaw.feature.gameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayTimerTest {
    @Test
    fun `currentElapsedPlayTimeMillis adds active session delta`() {
        assertEquals(
            8_250L,
            currentElapsedPlayTimeMillis(
                baseElapsedPlayTimeMillis = 5_000L,
                activeStartedAtEpochMillis = 10_000L,
                nowEpochMillis = 13_250L,
            ),
        )
    }

    @Test
    fun `formatElapsedPlayTime uses minutes and seconds before one hour`() {
        assertEquals("01:01", formatElapsedPlayTime(61_000L))
    }

    @Test
    fun `formatElapsedPlayTime includes hours after one hour`() {
        assertEquals("1:01:01", formatElapsedPlayTime(3_661_000L))
    }
}
