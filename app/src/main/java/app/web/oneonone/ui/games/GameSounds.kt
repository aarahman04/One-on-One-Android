package app.web.oneonone.ui.games

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.web.oneonone.R

internal enum class GameSound { Move, Capture, Check, End }

/** Short game sounds (synthesized for One on One, res/raw). Media volume; silent until loaded. */
internal class GameSounds(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids = mapOf(
        GameSound.Move to pool.load(context, R.raw.chess_move, 1),
        GameSound.Capture to pool.load(context, R.raw.chess_capture, 1),
        GameSound.Check to pool.load(context, R.raw.chess_check, 1),
        GameSound.End to pool.load(context, R.raw.game_end, 1),
    )
    fun play(sound: GameSound) { pool.play(ids.getValue(sound), 1f, 1f, 1, 0, 1f) }
    fun release() = pool.release()
}

@Composable
internal fun rememberGameSounds(): GameSounds {
    val context = LocalContext.current.applicationContext
    val sounds = remember(context) { GameSounds(context) }
    DisposableEffect(sounds) { onDispose { sounds.release() } }
    return sounds
}
