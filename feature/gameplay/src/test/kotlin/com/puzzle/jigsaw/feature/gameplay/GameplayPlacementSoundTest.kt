package com.puzzle.jigsaw.feature.gameplay

import org.junit.Assert.assertEquals
import org.junit.Test

class GameplayPlacementSoundTest {

    @Test
    fun `uses pop sound before completion`() {
        assertEquals(PlacementSound.POP, placementSoundForCompletionRatio(0.75f))
    }

    @Test
    fun `uses level up sound when puzzle is complete`() {
        assertEquals(PlacementSound.LEVEL_UP, placementSoundForCompletionRatio(1f))
    }
}
