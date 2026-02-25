package com.giulioapp.videoanalysis

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun MainScreen(viewModel: VideoViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Playback state
    val isPlaying by viewModel.isPlaying.collectAsState()
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(1L) }
    var isSeeking by remember { mutableStateOf(false) }

    // CRITICAL FIX: Ensure player is initialized precisely when needed and remembered
    val exoPlayer = remember {
        viewModel.buildPlayer(context) 
    }

    // Use DisposableEffect to tie the player to the composable lifecycle
    DisposableEffect(exoPlayer) {
        onDispose {
            // Unbind player if necessary, or rely on ViewModel to handle it
        }
    }

    // Polling logic for the Slider
    LaunchedEffect(isPlaying, isSeeking) {
        while (isPlaying && !isSeeking) {
            currentPosition = viewModel.getCurrentPosition()
            videoDuration = viewModel.getDuration()
            delay(100L) // UI update every 100ms
        }
    }
    
    LaunchedEffect(selectedVideoUri) {
        delay(500L) 
        if (!isPlaying) {
             currentPosition = viewModel.getCurrentPosition()
             videoDuration = viewModel.getDuration()
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedVideoUri = it
            viewModel.loadVideo(it)
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    val overlayButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White
    )

    // THE FULL SCREEN BOX. Think of it as a transparent window.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        
        // --- 1. THE VIDEO LAYER (Bottom of the box) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // --- 2. THE UI OVERLAY LAYERS (Floating on top) ---
        
        // Load & Save Overlay (Floating Left)
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { videoPickerLauncher.launch("video/*") },
                colors = overlayButtonColors,
                shape = RoundedCornerShape(12.dp)
            ) { Text("Load", fontWeight = FontWeight.Bold) }
            
            Button(
                onClick = { selectedVideoUri?.let { viewModel.extractAndSaveHighResFrame(context, it) } },
                colors = overlayButtonColors,
                shape = RoundedCornerShape(12.dp)
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }

        // Frame By Frame Overlay (Floating Right)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { 
                    viewModel.seekFrame(forward = false)
                    currentPosition = viewModel.getCurrentPosition()
                },
                colors = overlayButtonColors,
                shape = RoundedCornerShape(12.dp)
            ) { Text("[-]", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
            
            Button(
                onClick = { 
                    viewModel.seekFrame(forward = true) 
                    currentPosition = viewModel.getCurrentPosition()
                },
                colors = overlayButtonColors,
                shape = RoundedCornerShape(12.dp)
            ) { Text("[+]", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }
        }

        // Slider & Play/Pause Overlay (Floating Bottom)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f)) 
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = { viewModel.togglePlayPause() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.8f),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(if (isPlaying) "Pause" else "Play", fontWeight = FontWeight.Bold)
            }

            Slider(
                value = if (videoDuration > 0) currentPosition.toFloat() / videoDuration.toFloat() else 0f,
                onValueChange = { percent ->
                    isSeeking = true
                    currentPosition = (percent * videoDuration).toLong()
                },
                onValueChangeFinished = {
                    isSeeking = false
                    viewModel.seekTo(currentPosition)
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.5f)
                )
            )
        }
    }
}
