package com.example.ui.components

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun VideoPreview(
    project: VideoProject,
    playheadMs: Long,
    isPlaying: Boolean,
    selectedClip: VideoClip?,
    selectedText: TextOverlay?,
    selectedSticker: StickerOverlay?,
    onPlayheadUpdated: (Long) -> Unit,
    onPlaybackStateChanged: (Boolean) -> Unit,
    onTextMoved: (TextOverlay, Float, Float) -> Unit = { _, _, _ -> },
    onStickerMoved: (StickerOverlay, Float, Float) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Setup physical Player instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Clean up player on dispose
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Capture the clip currently containing the playhead, or the selected clip
    val activeClip = remember(project, playheadMs, selectedClip) {
        if (selectedClip != null) {
            selectedClip
        } else {
            val clips = project.videoTracks.firstOrNull()?.clips ?: emptyList()
            clips.firstOrNull { clip ->
                playheadMs >= clip.startInTimelineMs && playheadMs < (clip.startInTimelineMs + clip.durationAfterSpeedMs)
            } ?: clips.firstOrNull()
        }
    }

    // Apply file changes, clipping configurations, and speed characteristics to ExoPlayer
    LaunchedEffect(activeClip) {
        if (activeClip != null) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            try {
                // Configure trimming/cropping boundaries
                val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(activeClip.startInVideoMs)
                    .setEndPositionMs(activeClip.endInVideoMs)
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(java.io.File(activeClip.filePath)))
                    .setClippingConfiguration(clippingConfig)
                    .build()

                exoPlayer.addMediaItem(mediaItem)
                exoPlayer.setPlaybackSpeed(activeClip.speed)
                exoPlayer.playWhenReady = isPlaying
                exoPlayer.prepare()

                // If playhead falls within this clip, seek player to relative offset
                val relativeOffset = (playheadMs - activeClip.startInTimelineMs).coerceAtLeast(0L)
                val relativeOffsetSec = (relativeOffset * activeClip.speed).toLong()
                val seekPosition = (activeClip.startInVideoMs + relativeOffsetSec).coerceIn(activeClip.startInVideoMs, activeClip.endInVideoMs)
                
                // Note ExoPlayer media is already clipped, so seek parameter starts relative to CLIIP start
                val clampedRelativeSeek = (seekPosition - activeClip.startInVideoMs).coerceAtLeast(0L)
                exoPlayer.seekTo(clampedRelativeSeek)
            } catch (e: Exception) {
                Log.e("VideoPreview", "Failed to load clip preview in media player", e)
            }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Update playhead seeker during active playback
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            exoPlayer.play()
            while (isActive) {
                if (exoPlayer.isPlaying && activeClip != null) {
                    val clipOffsetFromStart = exoPlayer.currentPosition // Relative to clipping config start
                    val timelineMs = activeClip.startInTimelineMs + (clipOffsetFromStart / activeClip.speed).toLong()
                    onPlayheadUpdated(timelineMs)
                }
                delay(50) // High frequency sampling for crisp tracker rendering
            }
        } else {
            exoPlayer.pause()
        }
    }

    // Sync external timeline scrolls to the player head
    LaunchedEffect(playheadMs) {
        if (!isPlaying && activeClip != null) {
            val relativeOffset = (playheadMs - activeClip.startInTimelineMs).coerceAtLeast(0L)
            val scaledSeek = (relativeOffset * activeClip.speed).toLong()
            exoPlayer.seekTo(scaledSeek)
        }
    }

    // Construct the overlay color filter matrix dynamically to simulate filters organically (Warm, Vintage, Vivid, Grayscale)
    val colorMatrix = remember(activeClip) {
        if (activeClip == null) return@remember null
        
        val matrix = ColorMatrix()
        
        // 1. Brightness (-1 to 1) -> we map to translation scale offset
        val bShift = activeClip.brightness * 100f // Scaling offset
        val contrast = activeClip.contrast
        val sat = activeClip.saturation

        // Compose composite matrix values
        matrix.setToSaturation(sat)
        
        // Apply Contrast to matrix values
        val scaleArray = floatArrayOf(
            contrast, 0f, 0f, 0f, bShift,
            0f, contrast, 0f, 0f, bShift,
            0f, 0f, contrast, 0f, bShift,
            0f, 0f, 0f, 1f, 0f
        )
        matrix.timesAssign(ColorMatrix(scaleArray))

        // Enhance or tint colors for filters
        when (activeClip.filterType) {
            FilterType.GRAYSCALE -> {
                val grayMatrix = ColorMatrix().apply { setToSaturation(0f) }
                matrix.timesAssign(grayMatrix)
            }
            FilterType.WARM -> {
                val warmArray = floatArrayOf(
                    1.1f, 0f, 0f, 0f, 10f,
                    0f, 1.02f, 0f, 0f, 5f,
                    0f, 0f, 0.9f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                )
                matrix.timesAssign(ColorMatrix(warmArray))
            }
            FilterType.COOL -> {
                val coolArray = floatArrayOf(
                    0.9f, 0f, 0f, 0f, -5f,
                    0f, 1.0f, 0f, 0f, 2f,
                    0f, 0f, 1.15f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
                matrix.timesAssign(ColorMatrix(coolArray))
            }
            FilterType.VINTAGE -> {
                val sepiaArray = floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 5f,
                    0.349f, 0.686f, 0.168f, 0f, 3f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
                matrix.timesAssign(ColorMatrix(sepiaArray))
            }
            FilterType.VIVID -> {
                val vividArray = floatArrayOf(
                    1.15f, 0f, 0f, 0f, 15f,
                    0f, 1.15f, 0f, 0f, 15f,
                    0f, 0f, 1.15f, 0f, 15f,
                    0f, 0f, 0f, 1f, 0f
                )
                matrix.timesAssign(ColorMatrix(vividArray))
            }
            FilterType.NONE -> {}
        }
        matrix
    }

    // Canvas Transformation state (Pinch Zoom & Pan)
    var canvasScale by remember { mutableFloatStateOf(1f) }
    var canvasOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(project.aspectRatio.ratio)
            .background(Color.Black)
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    canvasScale = (canvasScale * zoom).coerceIn(1f, 5f)
                    canvasOffset += pan
                }
            }
    ) {
        if (activeClip != null) {
            // Apply transformations (Rotation and Flips) to player visual wrapper
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = canvasScale * (if (activeClip.flipHorizontal) -1f else 1f),
                        scaleY = canvasScale * (if (activeClip.flipVertical) -1f else 1f),
                        translationX = canvasOffset.x,
                        translationY = canvasOffset.y,
                        rotationZ = activeClip.rotation.toFloat()
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        val view = android.view.LayoutInflater.from(ctx).inflate(
                            com.example.R.layout.player_view_layout,
                            null,
                            false
                        ) as PlayerView
                        view.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        view.player = exoPlayer
                        view
                    },
                    update = { view ->
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    onRelease = { view ->
                        view.player = null
                    },
                    modifier = Modifier.fillMaxSize()
                )

                val needsOverlay = activeClip.filterType != FilterType.NONE ||
                        activeClip.brightness != 0f ||
                        activeClip.contrast != 1f ||
                        activeClip.saturation != 1f
                if (needsOverlay && colorMatrix != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(
                            color = Color.White,
                            colorFilter = ColorFilter.colorMatrix(colorMatrix),
                            blendMode = BlendMode.Modulate
                        )
                    }
                }
            }

            // Text Overlays
            val textOverlays = project.textTracks.flatMap { it.textOverlays }.filter {
                playheadMs >= it.startInTimelineMs && playheadMs < (it.startInTimelineMs + it.durationMs)
            }

            textOverlays.forEach { overlay ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(overlay.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newX = (overlay.positionX + dragAmount.x / size.width).coerceIn(0f, 1f)
                                val newY = (overlay.positionY + dragAmount.y / size.height).coerceIn(0f, 1f)
                                onTextMoved(overlay, newX, newY)
                            }
                        }
                ) {
                    val isSelected = selectedText?.id == overlay.id
                    Text(
                        text = overlay.text,
                        color = Color(overlay.textColor),
                        fontSize = overlay.textSizeSp.sp,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = (overlay.positionX * 100).dp, // This is just a rough estimate for preview
                                y = (overlay.positionY * 100).dp
                            )
                            .graphicsLayer {
                                translationX = (overlay.positionX * size.width) - (size.width / 2)
                                translationY = (overlay.positionY * size.height) - (size.height / 2)
                            }
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                if (isSelected) Color(0xFFFF6B35) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Sticker Overlays
            val stickers = project.stickerTracks.flatMap { it.stickers }.filter {
                playheadMs >= it.startInTimelineMs && playheadMs < (it.startInTimelineMs + it.durationMs)
            }

            stickers.forEach { sticker ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(sticker.id) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newX = (sticker.positionX + dragAmount.x / size.width).coerceIn(0f, 1f)
                                val newY = (sticker.positionY + dragAmount.y / size.height).coerceIn(0f, 1f)
                                onStickerMoved(sticker, newX, newY)
                            }
                        }
                ) {
                    val isSelected = selectedSticker?.id == sticker.id
                    Image(
                        painter = painterResource(id = sticker.stickerResId),
                        contentDescription = "Sticker",
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                translationX = (sticker.positionX * size.width) - (size.width / 2)
                                translationY = (sticker.positionY * size.height) - (size.height / 2)
                                scaleX = sticker.scale
                                scaleY = sticker.scale
                                rotationZ = sticker.rotation
                            }
                            .border(
                                if (isSelected) 2.dp else 0.dp,
                                if (isSelected) Color(0xFFFF6B35) else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No Video Clip Selected",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "Import a video to begin drafting",
                    color = Color.DarkGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
