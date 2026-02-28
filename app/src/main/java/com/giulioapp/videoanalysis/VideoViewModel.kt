package com.giulioapp.videoanalysis

import android.content.Context
import android.net.Uri

import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VideoViewModel : ViewModel() {

    var exoPlayer: ExoPlayer? = null
        private set

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    @OptIn(UnstableApi::class)
    fun buildPlayer(context: Context): ExoPlayer {
        if (exoPlayer != null) return exoPlayer!!
        
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS / 2,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS / 2,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS / 2
            )
            .build()

        val player = ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
            .apply {
                setSeekParameters(SeekParameters.EXACT)
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlayingChange: Boolean) {
                        _isPlaying.value = isPlayingChange
                    }
                })
            }
        
        exoPlayer = player
        return player
    }

    fun loadVideo(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
            it.setMediaItem(mediaItem)
            it.prepare()
            // Force ExoPlayer to render the first frame while paused
            it.seekTo(0L)
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.setPlaybackParameters(PlaybackParameters(1.0f))
                it.play()
            }
        }
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun playSlowMotion(speed: Float) {
        exoPlayer?.let {
            it.setPlaybackParameters(PlaybackParameters(speed))
            it.play()
        }
    }

    fun seekFrame(forward: Boolean) {
        seekFrames(if (forward) 1 else -1)
    }

    fun seekFrames(frameCount: Int) {
        val player = exoPlayer ?: return
        val frameDurationMs = 33L // Assuming ~30fps
        val seekAmountMs = frameCount * frameDurationMs
        val newPosition = player.currentPosition + seekAmountMs
        player.seekTo(newPosition.coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    fun getDuration(): Long = exoPlayer?.duration?.takeIf { it > 0 } ?: 1L



    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
    }
}
