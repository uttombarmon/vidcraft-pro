package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.viewmodel.ToolType
import java.io.File
import java.io.FileOutputStream

@Composable
fun ToolPanel(
    activeTool: ToolType,
    selectedClip: VideoClip?,
    selectedAudio: AudioClip?,
    selectedText: TextOverlay?,
    selectedSticker: StickerOverlay?,
    currentAspectRatio: ProjectAspectRatio,
    onUpdateClip: ((VideoClip) -> VideoClip) -> Unit,
    onUpdateAudio: ((AudioClip) -> AudioClip) -> Unit,
    onUpdateText: ((TextOverlay) -> TextOverlay) -> Unit,
    onUpdateSticker: ((StickerOverlay) -> StickerOverlay) -> Unit,
    onSetAspectRatio: (ProjectAspectRatio) -> Unit,
    onImportAudio: (Uri) -> Unit,
    onAddText: (String) -> Unit,
    onRemoveText: (String) -> Unit,
    onAddSticker: (Int) -> Unit,
    onRemoveSticker: (String) -> Unit,
    onRemoveAudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(Color(0xFF141414))
            .border(BorderStroke(1.dp, Color(0xFF222222)))
            .padding(12.dp)
    ) {
        if (selectedClip == null && 
            activeTool != ToolType.AUDIO && 
            activeTool != ToolType.TEXT && 
            activeTool != ToolType.STICKER && 
            activeTool != ToolType.CANVAS) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a video clip in the timeline to make adjustments",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            when (activeTool) {
                ToolType.CANVAS -> CanvasPanel(currentAspectRatio = currentAspectRatio, onSetAspectRatio = onSetAspectRatio)
                ToolType.SPEED -> selectedClip?.let { SpeedPanel(selectedClip = it, onUpdateClip = onUpdateClip) }
                ToolType.CROP_ROTATE -> selectedClip?.let { TransformPanel(selectedClip = it, onUpdateClip = onUpdateClip) }
                ToolType.ADJUST -> selectedClip?.let { AdjustPanel(selectedClip = it, onUpdateClip = onUpdateClip) }
                ToolType.FILTER -> selectedClip?.let { FilterPanel(selectedClip = it, onUpdateClip = onUpdateClip) }
                ToolType.TEXT -> TextPanel(selectedText = selectedText, onAddText = onAddText, onUpdateText = onUpdateText, onRemoveText = onRemoveText)
                ToolType.STICKER -> StickerPanel(selectedSticker = selectedSticker, onAddSticker = onAddSticker, onUpdateSticker = onUpdateSticker, onRemoveSticker = onRemoveSticker)
                ToolType.AUDIO -> AudioPanel(selectedAudio = selectedAudio, onImportAudio = onImportAudio, onRemoveAudio = onRemoveAudio)
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "VidCraft Pro: Select an editing deck below",
                            color = Color(0xFFFF6B35),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- CANVAS ASPECT RATIO PANEL ---
@Composable
fun CanvasPanel(
    currentAspectRatio: ProjectAspectRatio,
    onSetAspectRatio: (ProjectAspectRatio) -> Unit
) {
    val ratios = ProjectAspectRatio.values()
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Project Aspect Ratio", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ratios) { ratio ->
                val isActive = currentAspectRatio == ratio
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(48.dp)
                        .background(if (isActive) Color(0xFFFF6B35).copy(0.2f) else Color(0xFF262626), RoundedCornerShape(8.dp))
                        .border(BorderStroke(if (isActive) 1.5.dp else 1.dp, if (isActive) Color(0xFFFF6B35) else Color(0xFF3A3A3A)), RoundedCornerShape(8.dp))
                        .clickable { onSetAspectRatio(ratio) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(ratio.label, color = if (isActive) Color(0xFFFF6B35) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// --- SPEED EDITOR PANEL ---
@Composable
fun SpeedPanel(
    selectedClip: VideoClip,
    onUpdateClip: ((VideoClip) -> VideoClip) -> Unit
) {
    val options = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            text = "Clip Velocity Duration Scale: ${selectedClip.speed}x",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            options.forEach { speed ->
                val isActive = selectedClip.speed == speed
                Button(
                    onClick = { onUpdateClip { it.copy(speed = speed) } },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isActive) Color(0xFFFF6B35) else Color(0xFF2A2A2A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                ) {
                    Text(text = "${speed}x", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- TRANSFORM / ROTATE EDITOR PANEL ---
@Composable
fun TransformPanel(
    selectedClip: VideoClip,
    onUpdateClip: ((VideoClip) -> VideoClip) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = {
                    val nextRot = (selectedClip.rotation + 90) % 360
                    onUpdateClip { it.copy(rotation = nextRot) }
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF2E2E2E), RoundedCornerShape(8.dp))
            ) {
                Icon(imageVector = Icons.Default.RotateRight, contentDescription = "Rotate Clockwise", tint = Color.White)
            }
            Text("Rotate 90°", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { onUpdateClip { it.copy(flipHorizontal = !it.flipHorizontal) } },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (selectedClip.flipHorizontal) Color(0xFFFF6B35).copy(0.2f) else Color(0xFF2E2E2E),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        if (selectedClip.flipHorizontal) BorderStroke(1.dp, Color(0xFFFF6B35)) else BorderStroke(0.dp, Color.Transparent),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(imageVector = Icons.Default.Flip, contentDescription = "Flip Horizontal", tint = Color.White)
            }
            Text("Mirror Horiz", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(
                onClick = { onUpdateClip { it.copy(flipVertical = !it.flipVertical) } },
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (selectedClip.flipVertical) Color(0xFFFF6B35).copy(0.2f) else Color(0xFF2E2E2E),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        if (selectedClip.flipVertical) BorderStroke(1.dp, Color(0xFFFF6B35)) else BorderStroke(0.dp, Color.Transparent),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(imageVector = Icons.Default.FlipCameraAndroid, contentDescription = "Flip Vertical", tint = Color.White)
            }
            Text("Mirror Vert", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// --- COLOR ADJUSTMENT SLIDERS ---
@Composable
fun AdjustPanel(
    selectedClip: VideoClip,
    onUpdateClip: ((VideoClip) -> VideoClip) -> Unit
) {
    var activeAdjustTab by remember { mutableStateOf("Brightness") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Brightness", "Contrast", "Saturation").forEach { tab ->
                val isActive = activeAdjustTab == tab
                Text(
                    text = tab,
                    color = if (isActive) Color(0xFFFF6B35) else Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clickable { activeAdjustTab = tab }
                        .background(if (isActive) Color(0xFFFF6B35).copy(alpha = 0.1f) else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        when (activeAdjustTab) {
            "Brightness" -> {
                Text("Level: ${(selectedClip.brightness * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp)
                Slider(
                    value = selectedClip.brightness,
                    onValueChange = { onUpdateClip { clip -> clip.copy(brightness = it) } },
                    valueRange = -0.5f..0.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6B35),
                        activeTrackColor = Color(0xFFFF6B35)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
            "Contrast" -> {
                Text("Level: ${(selectedClip.contrast * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp)
                Slider(
                    value = selectedClip.contrast,
                    onValueChange = { onUpdateClip { clip -> clip.copy(contrast = it) } },
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6B35),
                        activeTrackColor = Color(0xFFFF6B35)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
            "Saturation" -> {
                Text("Level: ${(selectedClip.saturation * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp)
                Slider(
                    value = selectedClip.saturation,
                    onValueChange = { onUpdateClip { clip -> clip.copy(saturation = it) } },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF6B35),
                        activeTrackColor = Color(0xFFFF6B35)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }
        }
    }
}

// --- PHOTO FILTER PRESET REELS ---
@Composable
fun FilterPanel(
    selectedClip: VideoClip,
    onUpdateClip: ((VideoClip) -> VideoClip) -> Unit
) {
    val filters = FilterType.values()
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Cinematic Filter Presets", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(filters) { filter ->
                val isActive = selectedClip.filterType == filter
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .fillMaxHeight()
                        .background(if (isActive) Color(0xFFFF6B35).copy(0.2f) else Color(0xFF262626), RoundedCornerShape(6.dp))
                        .border(
                            BorderStroke(
                                width = if (isActive) 1.5.dp else 1.dp,
                                color = if (isActive) Color(0xFFFF6B35) else Color(0xFF3A3A3A)
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onUpdateClip { it.copy(filterType = filter) } }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.name,
                        color = if (isActive) Color(0xFFFF6B35) else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- TEXT OVERLAY INSERTION DECK ---
@Composable
fun TextPanel(
    selectedText: TextOverlay?,
    onAddText: (String) -> Unit,
    onUpdateText: ((TextOverlay) -> TextOverlay) -> Unit,
    onRemoveText: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (selectedText != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Edit Overlay Text: ",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onRemoveText(selectedText.id) }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Text", tint = Color.Red, modifier = Modifier.size(18.dp))
                }
            }
            OutlinedTextField(
                value = selectedText.text,
                onValueChange = { next -> onUpdateText { it.copy(text = next) } },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFFFF6B35),
                    unfocusedBorderColor = Color.DarkGray
                ),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Add Dynamic Text Overlay", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("type content...", fontSize = 12.sp, color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (textInput.isNotBlank()) {
                            onAddText(textInput)
                            textInput = ""
                            keyboardController?.hide()
                        }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFF6B35),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    maxLines = 1,
                    modifier = Modifier.weight(1f).height(46.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onAddText(textInput)
                            textInput = ""
                            keyboardController?.hide()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Text")
                }
            }
        }
    }
}

// --- STICKER SELECTOR PANEL ---
@Composable
fun StickerPanel(
    selectedSticker: StickerOverlay?,
    onAddSticker: (Int) -> Unit,
    onUpdateSticker: ((StickerOverlay) -> StickerOverlay) -> Unit,
    onRemoveSticker: (String) -> Unit
) {
    // Demo stickers using built-in Icons as stickers (represented by resource IDs or similar)
    // For this example we'll use a few drawable resources if they exist, or just use Icons.
    // Since we need resource IDs for StickerOverlay, let's assume we have some.
    // We will use standard android resources for demo.
    val demoStickers = listOf(
        android.R.drawable.ic_menu_edit,
        android.R.drawable.ic_menu_camera,
        android.R.drawable.ic_menu_gallery,
        android.R.drawable.ic_menu_share,
        android.R.drawable.ic_delete
    )

    if (selectedSticker != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Edit Sticker", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemoveSticker(selectedSticker.id) }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scale: ${(selectedSticker.scale * 100).toInt()}%", color = Color.Gray, fontSize = 10.sp)
                    Slider(
                        value = selectedSticker.scale,
                        onValueChange = { s -> onUpdateSticker { it.copy(scale = s) } },
                        valueRange = 0.5f..3.0f,
                        modifier = Modifier.height(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rotate: ${selectedSticker.rotation.toInt()}°", color = Color.Gray, fontSize = 10.sp)
                    Slider(
                        value = selectedSticker.rotation,
                        onValueChange = { r -> onUpdateSticker { it.copy(rotation = r) } },
                        valueRange = 0f..360f,
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Add Sticker", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(demoStickers) { resId ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .background(Color(0xFF262626), RoundedCornerShape(8.dp))
                            .clickable { onAddSticker(resId) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = resId),
                            contentDescription = "Sticker Option",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

// --- MUSIC AUDIO DECK ---
@Composable
fun AudioPanel(
    selectedAudio: AudioClip?,
    onImportAudio: (Uri) -> Unit,
    onRemoveAudio: () -> Unit
) {
    val context = LocalContext.current
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onImportAudio(uri)
        }
    }

    if (selectedAudio != null) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text("Active Ambient Loop:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = "Music", tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(selectedAudio.name, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = onRemoveAudio) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Remove music icon", tint = Color.Red)
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(
                onClick = { audioPicker.launch("audio/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E4860)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.AudioFile, contentDescription = "Pick storage loop", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Device Music", fontSize = 11.sp, color = Color.White)
            }

            Button(
                onClick = {
                    val defaultAmbientPath = generateDemoAudioInCache(context)
                    if (defaultAmbientPath != null) {
                        onImportAudio(Uri.fromFile(File(defaultAmbientPath)))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B2B2B)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.MusicVideo, contentDescription = "Inject default synth sound", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("VidCraft Loop", fontSize = 11.sp, color = Color.White)
            }
        }
    }
}

/**
 * High-performance virtual beat file generation: creates a real minimal WAV synth audio cache file
 * so exporting runs successfully even if the user has no music files downloaded!
 */
private fun generateDemoAudioInCache(context: Context): String? {
    try {
        val audioFile = File(context.cacheDir, "vidcraft_wav_theme.wav")
        if (audioFile.exists() && audioFile.length() > 100) {
            return audioFile.absolutePath
        }

        val sampleRate = 44100
        val durationSeconds = 15
        val numChannels = 1
        val bitsPerSample = 16
        val dataSize = sampleRate * durationSeconds * numChannels * (bitsPerSample / 8)
        val audioLength = dataSize + 36

        val outputStream = FileOutputStream(audioFile)
        val header = ByteArray(44)
        
        // RIFF header
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        
        // Size
        val totalSize = audioLength
        header[4] = (totalSize and 0xff).toByte()
        header[5] = ((totalSize shr 8) and 0xff).toByte()
        header[6] = ((totalSize shr 16) and 0xff).toByte()
        header[7] = ((totalSize shr 24) and 0xff).toByte()
        
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        
        // fmt chunk
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        
        header[16] = 16 // Subchunk1Size
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        header[20] = 1 // Format = PCM
        header[21] = 0
        
        header[22] = numChannels.toByte() // Channels
        header[23] = 0
        
        // Sample rate
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        
        // Byte rate
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        
        header[32] = (numChannels * (bitsPerSample / 8)).toByte() // Block align
        header[33] = 0
        
        header[34] = bitsPerSample.toByte() // Bits per sample
        header[35] = 0
        
        // data chunk
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        
        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()
        
        outputStream.write(header)
        
        // Synthesizes 440Hz standard continuous audio frequency
        val buffer = ByteArray(4096)
        var bytesWritten = 0
        val hz = 440.0
        val scale = 32767.0
        
        var sampleIndex = 0
        while (bytesWritten < dataSize) {
            val chunkLength = minOf(buffer.size, dataSize - bytesWritten)
            for (j in 0 until chunkLength step 2) {
                val angle = 2.0 * Math.PI * hz * sampleIndex / sampleRate
                val sampleVal = (Math.sin(angle) * scale).toInt().coerceIn(-32768, 32767)
                
                buffer[j] = (sampleVal and 0xff).toByte()
                if (j + 1 < chunkLength) {
                    buffer[j + 1] = ((sampleVal shr 8) and 0xff).toByte()
                }
                sampleIndex++
            }
            outputStream.write(buffer, 0, chunkLength)
            bytesWritten += chunkLength
        }
        
        outputStream.flush()
        outputStream.close()
        return audioFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}
