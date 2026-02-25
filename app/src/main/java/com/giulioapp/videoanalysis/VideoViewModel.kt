package com.giulioapp.videoanalysis

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
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
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play() 
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun seekFrame(forward: Boolean) {
        val player = exoPlayer ?: return
        val seekAmountMs = 33L 
        val newPosition = if (forward) player.currentPosition + seekAmountMs else player.currentPosition - seekAmountMs
        player.seekTo(newPosition)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L
    fun getDuration(): Long = exoPlayer?.duration?.takeIf { it > 0 } ?: 1L

    @OptIn(UnstableApi::class)
    fun extractAndSaveHighResFrame(context: Context, videoUri: Uri) {
        val player = exoPlayer ?: return
        val currentPositionMs = player.currentPosition

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(
                currentPositionMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST
            )
            
            if (bitmap != null) {
                saveBitmapToGallery(context, bitmap)
                Toast.makeText(context, "Frame saved successfully!", Toast.LENGTH_SHORT).show()
            }
            retriever.release()
        } catch (e: Exception) {
            Toast.makeText(context, "Error saving frame.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "SkiFrame_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SkiAnalysis")
        }

        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        imageUri?.let { uri ->
            resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer?.release()
        exoPlayer = null
    }
}
