package kr.tunnelfree.app.platform.android

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kr.tunnelfree.app.R
import kr.tunnelfree.app.platform.MusicPlayer

class AndroidMusicPlayer(private val context: Context) : MusicPlayer {

    private var player: MediaPlayer? = null
    private var volume = 1f

    override val isPlaying: Boolean get() = player?.isPlaying == true

    private fun ensure(): MediaPlayer? {
        player?.let { return it }
        return try {
            MediaPlayer.create(
                context,
                R.raw.calm_pad,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                0,
            )?.also {
                it.isLooping = true
                it.setVolume(volume, volume)
                player = it
            }
        } catch (e: Throwable) {
            Log.w(TAG, "MediaPlayer create failed", e)
            null
        }
    }

    override fun play() {
        val p = ensure() ?: return
        try { if (!p.isPlaying) p.start() } catch (e: Throwable) { Log.w(TAG, "start failed", e) }
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        try { player?.setVolume(this.volume, this.volume) } catch (_: Throwable) {}
    }

    override fun pause() {
        try { player?.takeIf { it.isPlaying }?.pause() } catch (_: Throwable) {}
    }

    override fun release() {
        try { player?.release() } catch (_: Throwable) {}
        player = null
    }

    private companion object { const val TAG = "CalmMusic" }
}
