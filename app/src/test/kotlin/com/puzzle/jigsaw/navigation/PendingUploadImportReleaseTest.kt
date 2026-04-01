package com.puzzle.jigsaw.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingUploadImportReleaseTest {
    @Test
    fun `replacePendingUploadImport releases current value when clearing state`() {
        val current = FakePendingUploadImport()

        val next = replacePendingUploadImport(current = current, next = null)

        assertEquals(1, current.releaseCount)
        assertEquals(null, next)
    }

    @Test
    fun `replacePendingUploadImport releases replaced value and keeps next active`() {
        val current = FakePendingUploadImport()
        val nextValue = FakePendingUploadImport()

        val next = replacePendingUploadImport(current = current, next = nextValue)

        assertEquals(1, current.releaseCount)
        assertEquals(0, nextValue.releaseCount)
        assertSame(nextValue, next)
    }

    @Test
    fun `replacePendingUploadImport does not release when value instance is unchanged`() {
        val current = FakePendingUploadImport()

        val next = replacePendingUploadImport(current = current, next = current)

        assertEquals(0, current.releaseCount)
        assertTrue(next === current)
    }
}

private class FakePendingUploadImport : ReleasablePendingUploadImport {
    var releaseCount: Int = 0
        private set

    override fun release() {
        releaseCount += 1
    }
}
