package com.giulioapp.videoanalysis

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.OptIn // Aggiungi questo
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi // Aggiungi questo
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters // Assicurati che ci sia questo

class VideoViewModel : ViewModel() {

    var exoPlayer: ExoPlayer? = null
        private set

    // Questo risolve l'errore "UnstableApi"
    @OptIn(UnstableApi::class)
    fun initializePlayer(context: Context) {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                // Ora SeekParameters verrà riconosciuto correttamente
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
        }
    }

    fun loadVideo(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer?.let {
            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
        }
    }

    fun seekFrame(forward: Boolean) {
        val player = exoPlayer ?: return
        val seekAmountMs = 33L
        val newPosition = if (forward) player.currentPosition + seekAmountMs else player.currentPosition - seekAmountMs
        player.seekTo(newPosition)
    }

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
                Toast.makeText(context, "Frame salvato!", Toast.LENGTH_SHORT).show()
            }
            retriever.release()
        } catch (e: Exception) {
            Toast.makeText(context, "Errore salvataggio", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Ski_${System.currentTimeMillis()}.jpg")
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
    }
}
