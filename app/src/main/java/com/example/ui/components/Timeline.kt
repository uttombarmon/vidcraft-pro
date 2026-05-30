package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.utils.ThumbnailHelper
import kotlin.math.abs

@Composable
fun Timeline(
    project: VideoProject,
    playheadMs: Long,
    zoomScale: Float,
    selectedClip: VideoClip?,
    selectedAudio: AudioClip?,
    selectedText: TextOverlay?,
    selectedSticker: StickerOverlay?,
    onPlayheadChanged: (Long) -> Unit,
    onClipSelected: (VideoClip?) -> Unit,
    onAudioSelected: (AudioClip?) -> Unit,
    onTextSelected: (TextOverlay?) -> Unit,
    onStickerSelected: (StickerOverlay?) -> Unit,
    onUpdateClipTrim: (VideoClip, Long, Long) -> Unit,
    onZoomFactorChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // Scroll state coordinates horizontal location of the tracks
    val scrollState = rememberScrollState()

    // Map units: Base duration (1 second) = 150dp at zoomFactor 1.0f
    val baseMsPerPixel = 1000f / (150f * zoomScale) // time representation
    val pixelsPerMs = 1.0f / baseMsPerPixel

    val totalDurationMs = project.totalDurationMs
    val clips = project.videoTracks.firstOrNull()?.clips ?: emptyList()
    val audioClips = project.audioTracks.firstOrNull()?.clips ?: emptyList()
    val textOverlays = project.textTracks.firstOrNull()?.textOverlays ?: emptyList()
    val stickers = project.stickerTracks.firstOrNull()?.stickers ?: emptyList()

    // Sync playhead state down to scroll offsets bidirectionally without feedback loops
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Color(0xFF0F0F0F))
            .border(BorderStroke(1.dp, Color(0xFF222222)))
    ) {
        val containerWidthPx = this.constraints.maxWidth
        val halfContainerWidthPx = containerWidthPx / 2
        val halfContainerWidthDp = with(LocalDensity.current) { halfContainerWidthPx.toDp() }

        // Whenever playhead position changes outside of normal scrolling, slide the scroll view
        LaunchedEffect(playheadMs, zoomScale) {
            val scrollGoal = (playheadMs * pixelsPerMs).toInt()
            if (abs(scrollGoal - scrollState.value) > 2) {
                isProgrammaticScroll = true
                scrollState.scrollTo(scrollGoal)
            }
        }

        // Gather scrolling changes and map back into playhead values
        LaunchedEffect(scrollState.value) {
            if (isProgrammaticScroll) {
                isProgrammaticScroll = false
            } else {
                if (pixelsPerMs > 0f) {
                    val calculatedPlayhead = (scrollState.value / pixelsPerMs).toLong()
                    if (abs(calculatedPlayhead - playheadMs) > 5) {
                        onPlayheadChanged(calculatedPlayhead)
                    }
                }
            }
        }

        // Pinch gestural detection for zoom multiplier
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            onZoomFactorChanged(zoomScale * zoom)
                        }
                    }
                }
        ) {
            // Main Tracks Container
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                // Front Spacer pads the tracks to align chronological start with the center handle ruler
                Spacer(modifier = Modifier.width(halfContainerWidthDp))

                // The Tracks stack container
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width((maxOf(totalDurationMs, 5000L) * pixelsPerMs).dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // --- TRACK 1: TEXT OVERLAYS ---
                    TextLanes(
                        textOverlays = textOverlays,
                        pixelsPerMs = pixelsPerMs,
                        selectedText = selectedText,
                        onTextSelected = onTextSelected
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // --- TRACK 1.5: STICKERS ---
                    StickerLanes(
                        stickers = stickers,
                        pixelsPerMs = pixelsPerMs,
                        selectedSticker = selectedSticker,
                        onStickerSelected = onStickerSelected
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // --- TRACK 2: VIDEO CLIPS STRIP ---
                    VideoLanes(
                        clips = clips,
                        pixelsPerMs = pixelsPerMs,
                        selectedClip = selectedClip,
                        onClipSelected = onClipSelected,
                        onUpdateClipTrim = onUpdateClipTrim
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // --- TRACK 3: AUDIO BACKGROUND TRACK ---
                    AudioLanes(
                        audioClips = audioClips,
                        pixelsPerMs = pixelsPerMs,
                        selectedAudio = selectedAudio,
                        onAudioSelected = onAudioSelected
                    )
                }

                // Behind Spacer pads end segment to enable centering last frames
                Spacer(modifier = Modifier.width(halfContainerWidthDp))
            }

            // High Precision Orange Playhead Ruler pinned at absolute screen center
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color(0xFFFF6B35))
                    .align(Alignment.Center)
            )
            
            // Floating ticks display
            TimelineRulerTicks(
                scrollState = scrollState,
                pixelsPerMs = pixelsPerMs,
                totalDurationMs = maxOf(totalDurationMs, 5000L),
                halfContainerWidthDp = halfContainerWidthDp
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoLanes(
    clips: List<VideoClip>,
    pixelsPerMs: Float,
    selectedClip: VideoClip?,
    onClipSelected: (VideoClip?) -> Unit,
    onUpdateClipTrim: (VideoClip, Long, Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF141414), RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, Color(0xFF222222)), RoundedCornerShape(6.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (clips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap Trim/Cut below or import clips",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }
        } else {
            clips.forEach { clip ->
                val isSelected = selectedClip?.id == clip.id
                val clipWidth = (clip.durationAfterSpeedMs * pixelsPerMs).dp

                Box(
                    modifier = Modifier
                        .width(clipWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFFFF6B35).copy(alpha = 0.15f) else Color(0xFF1F1F1F))
                        .border(
                            BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFF6B35) else Color(0xFF333333)
                            ),
                            RoundedCornerShape(4.dp)
                        )
                        .combinedClickable(
                            onClick = { onClipSelected(clip) },
                            onLongClick = { onClipSelected(null) }
                        )
                ) {
                    // Frame strip view
                    TimelineClipFramesStrip(clip = clip, widthLimitDp = clipWidth)

                    // Text labels indicating name & active trim scale
                    Text(
                        text = clip.name,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(Color.Black.copy(0.6f), RoundedCornerShape(bottomEnd = 4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )

                    // Overlay trim grips on left/right if selected
                    if (isSelected) {
                        // Left trim handle (trim start)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(12.dp)
                                .background(Color(0xFFFF6B35), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                                .align(Alignment.CenterStart)
                                .pointerInput(clip.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaMs = (dragAmount.x / pixelsPerMs).toLong()
                                        val maxStart = (clip.endInVideoMs - 500L).coerceAtLeast(0L)
                                        val proposedStart = (clip.startInVideoMs + deltaMs).coerceIn(0L, maxStart)
                                        onUpdateClipTrim(clip, proposedStart, clip.endInVideoMs)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(2.dp, 16.dp)
                                    .background(Color.White)
                                    .align(Alignment.Center)
                            )
                        }

                        // Right trim handle (trim end)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(12.dp)
                                .background(Color(0xFFFF6B35), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                                .align(Alignment.CenterEnd)
                                .pointerInput(clip.id) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaMs = (dragAmount.x / pixelsPerMs).toLong()
                                        val minEnd = (clip.startInVideoMs + 500L).coerceAtMost(clip.durationMs)
                                        val proposedEnd = (clip.endInVideoMs + deltaMs).coerceIn(minEnd, clip.durationMs)
                                        onUpdateClipTrim(clip, clip.startInVideoMs, proposedEnd)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(2.dp, 16.dp)
                                    .background(Color.White)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioLanes(
    audioClips: List<AudioClip>,
    pixelsPerMs: Float,
    selectedAudio: AudioClip?,
    onAudioSelected: (AudioClip?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(Color(0xFF0A1015), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, Color(0xFF1B2A3A)), RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (audioClips.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+ Add background music on Music menu below",
                    color = Color.DarkGray,
                    fontSize = 11.sp
                )
            }
        } else {
            audioClips.forEach { audio ->
                val isSelected = selectedAudio?.id == audio.id
                val audioDuration = audio.endInAudioMs - audio.startInAudioMs
                val audioWidth = (audioDuration * pixelsPerMs).dp

                Box(
                    modifier = Modifier
                        .offset(x = (audio.startInTimelineMs * pixelsPerMs).dp)
                        .width(audioWidth)
                        .fillMaxHeight()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isSelected) Color(0xFF2A5070) else Color(0xFF142B42))
                        .border(
                            BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF2E4860)
                            ),
                            RoundedCornerShape(3.dp)
                        )
                        .clickable { onAudioSelected(audio) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Audio track",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = audio.name,
                            color = Color(0xFFE0E0E0),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StickerLanes(
    stickers: List<StickerOverlay>,
    pixelsPerMs: Float,
    selectedSticker: StickerOverlay?,
    onStickerSelected: (StickerOverlay?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (stickers.isNotEmpty()) {
            stickers.forEach { stickerItem ->
                val isSelected = selectedSticker?.id == stickerItem.id
                val elementWidth = (stickerItem.durationMs * pixelsPerMs).dp

                Box(
                    modifier = Modifier
                        .offset(x = (stickerItem.startInTimelineMs * pixelsPerMs).dp)
                        .width(elementWidth)
                        .fillMaxHeight()
                        .padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFF1ADCA7).copy(alpha = 0.3f) else Color(0xFF0A5341))
                        .border(
                            BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFF00FFCC) else Color(0xFF1A8166)
                            ),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onStickerSelected(stickerItem) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.StickyNote2,
                            contentDescription = "Sticker path",
                            tint = Color(0xFF00FFCC),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sticker",
                            color = Color(0xFFAAFEEE),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TextLanes(
    textOverlays: List<TextOverlay>,
    pixelsPerMs: Float,
    selectedText: TextOverlay?,
    onTextSelected: (TextOverlay?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (textOverlays.isNotEmpty()) {
            textOverlays.forEach { textItem ->
                val isSelected = selectedText?.id == textItem.id
                val elementWidth = (textItem.durationMs * pixelsPerMs).dp

                Box(
                    modifier = Modifier
                        .offset(x = (textItem.startInTimelineMs * pixelsPerMs).dp)
                        .width(elementWidth)
                        .fillMaxHeight()
                        .padding(vertical = 1.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) Color(0xFFDCA71A).copy(alpha = 0.3f) else Color(0xFF53410A))
                        .border(
                            BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color(0xFFFFCC00) else Color(0xFF81661A)
                            ),
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { onTextSelected(textItem) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = "Text overlay path",
                            tint = Color(0xFFFFCC00),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = textItem.text,
                            color = Color(0xFFFFEEAA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineClipFramesStrip(clip: VideoClip, widthLimitDp: androidx.compose.ui.unit.Dp) {
    // Generate 4 local segment bitmaps representing the duration span
    val frames = remember(clip) { mutableStateListOf<Bitmap?>() }

    LaunchedEffect(clip) {
        frames.clear()
        val totalSpan = clip.endInVideoMs - clip.startInVideoMs
        val step = maxOf(1L, totalSpan / 4)
        for (i in 0..3) {
            val sampleTime = clip.startInVideoMs + (i * step)
            val bitmap = ThumbnailHelper.getFrameAtTime(clip.filePath, sampleTime, width = 100, height = 65)
            frames.add(bitmap)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        frames.forEach { bitmap ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color.Black)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Clip frame thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineRulerTicks(
    scrollState: ScrollState,
    pixelsPerMs: Float,
    totalDurationMs: Long,
    halfContainerWidthDp: androidx.compose.ui.unit.Dp
) {
    // Draw timeline ticks every 1 second (1000ms)
    val incrementMs = 1000L
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(Color(0xBB0A0A0A))
    ) {
        // We know scrollState is in pixels.
        // We can draw standard grid markers
        val tickCount = (totalDurationMs / incrementMs) + 1
        for (i in 0 until tickCount) {
            val timeMs = i * incrementMs
            val xPosPx = (timeMs * pixelsPerMs) + with(density) { halfContainerWidthDp.toPx() } - scrollState.value
            
            if (xPosPx in 0f..size.width) {
                val isMajor = i % 5L == 0L
                val height = if (isMajor) 12f else 6f
                val color = if (isMajor) Color.DarkGray else Color.Gray
                
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(x = xPosPx, y = 0f),
                    end = androidx.compose.ui.geometry.Offset(x = xPosPx, y = height),
                    strokeWidth = 2f
                )
            }
        }
    }
}
