package com.puzzle.jigsaw.core.designsystem.components

import com.puzzle.jigsaw.core.model.PuzzleImageStorage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssetImageTest {
    @Test
    fun `loadPuzzleImageResource opens and decodes on the provided dispatcher`() = runBlocking {
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "puzzle-decode")
        }.asCoroutineDispatcher()
        val openThreadName = AtomicReference<String>()
        val decodeThreadName = AtomicReference<String>()
        val streamClosed = AtomicBoolean(false)

        try {
            val result = loadPuzzleImageResource(
                path = "image.webp",
                storage = PuzzleImageStorage.FILE,
                decodeDispatcher = dispatcher,
                openStream = { path, storage ->
                    openThreadName.set(Thread.currentThread().name)
                    assertEquals("image.webp", path)
                    assertEquals(PuzzleImageStorage.FILE, storage)
                    TrackingInputStream(streamClosed)
                },
                decodeResource = { inputStream ->
                    decodeThreadName.set(Thread.currentThread().name)
                    inputStream.read()
                    "decoded"
                },
            )

            assertEquals("decoded", result)
            assertTrue(openThreadName.get().startsWith("puzzle-decode"))
            assertTrue(decodeThreadName.get().startsWith("puzzle-decode"))
            assertTrue(streamClosed.get())
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun `loadPuzzleImageResource returns null when decoding fails`() = runBlocking {
        val result = loadPuzzleImageResource(
            path = "image.webp",
            storage = PuzzleImageStorage.ASSET,
            openStream = { _, _ -> ByteArrayInputStream(byteArrayOf(1, 2, 3)) },
            decodeResource = { throw IllegalStateException("decode failed") },
        )

        assertNull(result)
    }
}

private class TrackingInputStream(
    private val closed: AtomicBoolean,
) : InputStream() {
    private val delegate = ByteArrayInputStream(byteArrayOf(1, 2, 3))

    override fun read(): Int = delegate.read()

    override fun close() {
        closed.set(true)
        delegate.close()
    }
}
