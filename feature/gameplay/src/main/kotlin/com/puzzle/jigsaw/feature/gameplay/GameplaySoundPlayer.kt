package com.puzzle.jigsaw.feature.gameplay

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember

@Composable
internal fun rememberGameplaySoundPlayer(context: Context): GameplaySoundPlayer {
    val appContext = context.applicationContext
    val soundPlayer = remember(appContext) {
        SoundPoolGameplaySoundPlayer.create(appContext)
    }
    DisposableEffect(soundPlayer) {
        onDispose {
            soundPlayer.release()
        }
    }
    return soundPlayer
}

internal fun placementSoundForCompletionRatio(completionRatio: Float): PlacementSound =
    if (completionRatio >= 1f) PlacementSound.LEVEL_UP else PlacementSound.POP

internal interface GameplaySoundPlayer {
    fun play(sound: PlacementSound)
    fun release()
}

internal enum class PlacementSound {
    POP,
    LEVEL_UP,
}

private class SoundPoolGameplaySoundPlayer(
    private val soundPool: SoundPool,
    private val popSoundId: Int,
    private val levelUpSoundId: Int,
    private val loadedSoundIds: MutableSet<Int>,
) : GameplaySoundPlayer {
    override fun play(sound: PlacementSound) {
        val soundId = when (sound) {
            PlacementSound.POP -> popSoundId
            PlacementSound.LEVEL_UP -> levelUpSoundId
        }
        if (!isLoaded(soundId)) return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    override fun release() {
        soundPool.release()
    }

    private fun isLoaded(soundId: Int): Boolean = synchronized(loadedSoundIds) {
        soundId in loadedSoundIds
    }

    companion object {
        fun create(context: Context): GameplaySoundPlayer = runCatching {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val soundPool = SoundPool.Builder()
                .setMaxStreams(2)
                .setAudioAttributes(audioAttributes)
                .build()
            try {
                val loadedSoundIds = mutableSetOf<Int>()
                soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        synchronized(loadedSoundIds) {
                            loadedSoundIds += sampleId
                        }
                    }
                }
                val popSoundId = context.assets.openFd(PopSoundAssetPath).use { fileDescriptor ->
                    soundPool.load(fileDescriptor, 1)
                }
                val levelUpSoundId = context.assets.openFd(LevelUpSoundAssetPath).use { fileDescriptor ->
                    soundPool.load(fileDescriptor, 1)
                }
                SoundPoolGameplaySoundPlayer(
                    soundPool = soundPool,
                    popSoundId = popSoundId,
                    levelUpSoundId = levelUpSoundId,
                    loadedSoundIds = loadedSoundIds,
                )
            } catch (error: Throwable) {
                soundPool.release()
                throw error
            }
        }.getOrElse { NoOpGameplaySoundPlayer }
    }
}

private object NoOpGameplaySoundPlayer : GameplaySoundPlayer {
    override fun play(sound: PlacementSound) = Unit

    override fun release() = Unit
}

private const val PopSoundAssetPath = "sound/pop.mp3"
private const val LevelUpSoundAssetPath = "sound/levelup.mp3"
