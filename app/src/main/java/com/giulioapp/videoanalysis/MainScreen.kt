package com.giulioapp.videoanalysis

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: VideoViewModel = viewModel()) {
    val context = LocalContext.current
    var selectedVideoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedVideoUri = selectedVideoUriString?.let { Uri.parse(it) }
    var hasLaunchedPicker by rememberSaveable { mutableStateOf(false) }
    
    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Playback state
    val isPlaying by viewModel.isPlaying.collectAsState()
    var currentPosition by remember { mutableLongStateOf(0L) }
    var videoDuration by remember { mutableLongStateOf(1L) }
    var isSeeking by remember { mutableStateOf(false) }
    
    // UI Visibility State (Auto-hide Option Z)
    var isUiVisible by remember { mutableStateOf(true) }

    // SlowMo Settings State (Multiplier)
    var slowMoSpeed by remember { mutableFloatStateOf(0.1f) }

    // SlowMo Active States (to block auto-hide)
    var isSlowMo30Active by remember { mutableStateOf(false) }
    var isSlowMoActive by remember { mutableStateOf(false) }

    // CRITICAL FIX: Ensure player is initialized precisely when needed and remembered
    val exoPlayer = remember {
        viewModel.buildPlayer(context) 
    }

    // Move launcher declaration up to fix unresolved reference
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedVideoUriString = it.toString()
            viewModel.loadVideo(it)
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    // State-of-the-Art: Handle Lifecycle (Pause on Background)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Pause automatically when the app goes into the background
                if (exoPlayer.isPlaying) {
                    exoPlayer.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // The player remains alive in the ViewModel to survive backgrounding.
            // It will be definitively released by ViewModel.onCleared() when the Activity finishes.
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
    
    LaunchedEffect(Unit) {
        if (!hasLaunchedPicker) {
            hasLaunchedPicker = true
            videoPickerLauncher.launch("video/*")
        }
    }

    LaunchedEffect(selectedVideoUri) {
        delay(500L) 
        if (!isPlaying) {
             currentPosition = viewModel.getCurrentPosition()
             videoDuration = viewModel.getDuration()
        }
    }
    
    // Recovery block: if navigating back to the app and the player lacks media despite a saved URI
    LaunchedEffect(exoPlayer, selectedVideoUri) {
        if (selectedVideoUri != null && exoPlayer.mediaItemCount == 0) {
            viewModel.loadVideo(selectedVideoUri)
        }
    }



    // Auto-hide Timer
    LaunchedEffect(isUiVisible, isPlaying, isSlowMo30Active, isSlowMoActive) {
        if (isUiVisible && isPlaying && !isSlowMo30Active && !isSlowMoActive) {
            delay(3000L) // Hide UI after 3 seconds of playing
            isUiVisible = false
        }
    }

    val overlayButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color.Black.copy(alpha = 0.5f),
        contentColor = Color.White
    )

    // THE FULL SCREEN BOX. Think of it as a transparent window.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap to toggle UI visibility
            .clickable(
                indication = null, 
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) {
                isUiVisible = !isUiVisible
            }
    ) {
        
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
        
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Load & Settings Overlay (Top Left)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        colors = overlayButtonColors,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(110.dp)
                    ) { Text("Load", fontWeight = FontWeight.Bold) }

                    // Menu Opzione M: Dropdown Speeds
                    var expanded by remember { mutableStateOf(false) }
                    val speeds = listOf(0.1f, 0.2f, 0.3f, 0.5f, 1.0f)

                    Box {
                        Button(
                            onClick = { 
                                expanded = true
                                isUiVisible = true
                            },
                            colors = overlayButtonColors,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(110.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("Slow: ${slowMoSpeed}x", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            speeds.forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x", fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        slowMoSpeed = speed
                                        expanded = false
                                        isUiVisible = true
                                    }
                                )
                            }
                        }
                    }
                }

                // Frame By Frame Overlay (Floating Right)
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Button +30 (Box with pointerInput release-to-pause)
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .pointerInput(slowMoSpeed) {
                                detectTapGestures(
                                    onPress = {
                                        val released = tryAwaitRelease()
                                        if (isSlowMo30Active) {
                                            viewModel.pause() // Pause immediately on finger lift
                                            isSlowMo30Active = false
                                        }
                                    },
                                    onLongPress = {
                                        isSlowMo30Active = true
                                        // Speed multiplier applied directly
                                        viewModel.playSlowMotion(slowMoSpeed)
                                        isUiVisible = true
                                    },
                                    onTap = {
                                        viewModel.seekFrames(30)
                                        currentPosition = viewModel.getCurrentPosition()
                                        isUiVisible = true // keep awake
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("+30", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium) }

                    // Button + (Box with pointerInput release-to-pause)
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .pointerInput(slowMoSpeed) {
                                detectTapGestures(
                                    onPress = {
                                        val released = tryAwaitRelease()
                                        if (isSlowMoActive) {
                                            viewModel.pause() // Pause immediately on finger lift
                                            isSlowMoActive = false
                                        }
                                    },
                                    onLongPress = {
                                        isSlowMoActive = true
                                        // Speed multiplier applied directly
                                        viewModel.playSlowMotion(slowMoSpeed)
                                        isUiVisible = true
                                    },
                                    onTap = {
                                        viewModel.seekFrame(forward = true)
                                        currentPosition = viewModel.getCurrentPosition()
                                        isUiVisible = true
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) { Text("+", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleMedium) }

                    Spacer(modifier = Modifier.height(40.dp)) // OPTION A: Add a large visual gap

                    // Button -
                    Button(
                        onClick = { 
                            viewModel.seekFrame(forward = false)
                            currentPosition = viewModel.getCurrentPosition()
                            isUiVisible = true
                        },
                        colors = overlayButtonColors,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(56.dp).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }

                    // Button -30
                    Button(
                        onClick = { 
                            viewModel.seekFrames(-30)
                            currentPosition = viewModel.getCurrentPosition()
                            isUiVisible = true
                        },
                        colors = overlayButtonColors,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(56.dp).height(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("-30", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                }

                // Slider & Play/Pause Overlay (Floating Bottom Minimal)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Minimal Play/Pause Button
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause(); isUiVisible = true },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Slider(
                        value = if (videoDuration > 0) currentPosition.toFloat() / videoDuration.toFloat() else 0f,
                        onValueChange = { percent ->
                            isSeeking = true
                            isUiVisible = true 
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
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        )
                    )
                }
            } // End of AnimatedVisibility Box
        } // End of AnimatedVisibility
    } // End of Full Screen Box
}
