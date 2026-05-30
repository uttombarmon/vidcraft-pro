package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.ui.components.*
import com.example.viewmodel.EditorViewModel
import com.example.viewmodel.ExportState
import com.example.viewmodel.ToolType
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Observe States
    val project by viewModel.videoProject.collectAsState()
    val playheadMs by viewModel.currentPlayheadMs.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val selectedClip by viewModel.selectedClip.collectAsState()
    val selectedAudio by viewModel.selectedAudioClip.collectAsState()
    val selectedText by viewModel.selectedTextOverlay.collectAsState()
    val selectedSticker by viewModel.selectedSticker.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val zoomScale by viewModel.timelineZoomScale.collectAsState()

    // Screen Menu States
    var showResolutionPicker by remember { mutableStateOf(false) }
    var selectedExportResolution by remember { mutableIntStateOf(720) } // Default to 720p

    // Video Import Picker Launcher
    val videoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.addVideoClip(context, uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MovieFilter,
                            contentDescription = "VidCraft Pro Logo",
                            tint = Color(0xFFFF6B35),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "VidCraft",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
                            color = Color.White
                        )
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFF6B35),
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .background(Color(0xFFFF6B35).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                },
                actions = {
                    // Export Option Button
                    Button(
                        onClick = { showResolutionPicker = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B35),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Publish,
                            contentDescription = "Export draft",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0D0D0D),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0D0D0D),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- TOP PANEL: VIDEO PLAYER PREVIEW ---
            VideoPreview(
                project = project,
                playheadMs = playheadMs,
                isPlaying = isPlaying,
                selectedClip = selectedClip,
                selectedText = selectedText,
                selectedSticker = selectedSticker,
                onPlayheadUpdated = { viewModel.setPlayhead(it) },
                onPlaybackStateChanged = { viewModel.setIsPlaying(it) },
                onTextMoved = { text, x, y ->
                    viewModel.updateSelectedText { it.copy(positionX = x, positionY = y) }
                },
                onStickerMoved = { sticker, x, y ->
                    viewModel.updateSelectedSticker { it.copy(positionX = x, positionY = y) }
                },
                modifier = Modifier
                    .weight(1.2f)
                    .padding(horizontal = 12.dp)
            )

            // --- PLAYBACK INTERACTION BAR ---
            PlaybackControlsRow(
                currentPlayheadMs = playheadMs,
                totalDurationMs = project.totalDurationMs,
                isPlaying = isPlaying,
                onPlayPauseClicked = { viewModel.setIsPlaying(!isPlaying) },
                onSkipStartClicked = { viewModel.setPlayhead(0L) },
                onAddVideoClicked = { videoImportLauncher.launch("video/*") },
                onAddSampleClicked = { viewModel.addSampleClip(context) }
            )

            // --- SELECTION HIGHLIGHT INFO ---
            SelectionInfoBar(
                selectedClip = selectedClip,
                selectedAudio = selectedAudio,
                selectedText = selectedText,
                selectedSticker = selectedSticker,
                onClearSelection = {
                    viewModel.selectClip(null)
                    viewModel.selectAudioClip(null)
                    viewModel.selectTextOverlay(null)
                    viewModel.selectSticker(null)
                }
            )

            // --- TIMELINE SECTION GAP ---
            Timeline(
                project = project,
                playheadMs = playheadMs,
                zoomScale = zoomScale,
                selectedClip = selectedClip,
                selectedAudio = selectedAudio,
                selectedText = selectedText,
                selectedSticker = selectedSticker,
                onPlayheadChanged = { viewModel.setPlayhead(it) },
                onClipSelected = { viewModel.selectClip(it) },
                onAudioSelected = { viewModel.selectAudioClip(it) },
                onTextSelected = { viewModel.selectTextOverlay(it) },
                onStickerSelected = { viewModel.selectSticker(it) },
                onUpdateClipTrim = { _, start, end ->
                    viewModel.updateSelectedClip { it.copy(startInVideoMs = start, endInVideoMs = end) }
                },
                onZoomFactorChanged = { viewModel.setZoomScale(it) },
                modifier = Modifier.weight(1f)
            )

            // --- CONTEXTUAL PARAMETERS TOOLPANEL DECK ---
            ToolPanel(
                activeTool = activeTool,
                selectedClip = selectedClip,
                selectedAudio = selectedAudio,
                selectedText = selectedText,
                selectedSticker = selectedSticker,
                currentAspectRatio = project.aspectRatio,
                onUpdateClip = { block -> viewModel.updateSelectedClip(block) },
                onUpdateAudio = { block -> viewModel.updateSelectedAudio(block) },
                onUpdateText = { block -> viewModel.updateSelectedText(block) },
                onUpdateSticker = { block -> viewModel.updateSelectedSticker(block) },
                onSetAspectRatio = { ratio -> viewModel.setAspectRatio(ratio) },
                onImportAudio = { uri -> viewModel.addBackgroundMusic(context, uri) },
                onAddText = { txt -> viewModel.addTextOverlay(txt) },
                onRemoveText = { id -> viewModel.removeTextOverlay(id) },
                onAddSticker = { resId -> viewModel.addSticker(resId) },
                onRemoveSticker = { id -> viewModel.removeSticker(id) },
                onRemoveAudio = { viewModel.removeSelectedAudio() },
                modifier = Modifier.wrapContentHeight()
            )

            // --- PRIMARY EDITING BAR ---
            ToolbarRow(
                activeTool = activeTool,
                hasSelectedClip = selectedClip != null,
                onToolClicked = { viewModel.setActiveTool(it) },
                onInstantSplit = { viewModel.splitClipAtPlayhead() },
                onInstantDelete = { viewModel.deleteSelectedClip() }
            )

            // --- ZOOM CONTROLS ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Zoom", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.setZoomScale(zoomScale * 0.8f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", tint = Color.White)
                }
                IconButton(
                    onClick = { viewModel.setZoomScale(zoomScale * 1.2f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", tint = Color.White)
                }
            }
        }
    }

    // --- DIALOG: RESOLUTION PICKER ---
    if (showResolutionPicker) {
        Dialog(onDismissRequest = { showResolutionPicker = false }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E1E1E),
                border = BorderStroke(1.dp, Color(0xFF333333)),
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Export Compilation",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "Select output quality for final render",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val resolutions = listOf(480, 720, 1080)
                    resolutions.forEach { res ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedExportResolution = res }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedExportResolution == res,
                                onClick = { selectedExportResolution = res },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFFF6B35),
                                    unselectedColor = Color.Gray
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${res}p (HD Broadcast)",
                                color = if (selectedExportResolution == res) Color.White else Color.Gray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showResolutionPicker = false }) {
                            Text("Cancel", color = Color.Gray)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showResolutionPicker = false
                                viewModel.triggerExport(context, selectedExportResolution)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
                        ) {
                            Text("Render", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS: PROGRESS AND COMPILATION STATUS ---
    when (val state = exportState) {
        is ExportState.Exporting -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF141414),
                    border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFFF6B35),
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Rendering Compilation...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Please do not close VidCraft. FFmpeg processing: ${String.format(Locale.US, "%.1f", state.progress)}%",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            color = Color(0xFFFF6B35),
                            trackColor = Color(0xFF2A2A2A),
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                    }
                }
            }
        }

        is ExportState.Success -> {
            Dialog(onDismissRequest = { viewModel.clearExportState() }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color(0xFF00FF88)),
                    modifier = Modifier.width(325.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Export complete",
                            tint = Color(0xFF00C853),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Render Finished!",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Compilation fully consolidated in high fidelity. Press write-to-gallery below to save on your public Photos system.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                saveVideoFileToGallery(context, state.outputPath)
                                viewModel.clearExportState()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = "Gallery save")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save to Phone Gallery", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { viewModel.clearExportState() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back to Canvas", color = Color.Gray)
                        }
                    }
                }
            }
        }

        is ExportState.Error -> {
            Dialog(onDismissRequest = { viewModel.clearExportState() }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, Color(0xFFFF3B30)),
                    modifier = Modifier.width(300.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = "Export error",
                            tint = Color(0xFFFF3B30),
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Render Interrupted",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Button(
                            onClick = { viewModel.clearExportState() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Dismiss", color = Color.White)
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
fun PlaybackControlsRow(
    currentPlayheadMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    onPlayPauseClicked: () -> Unit,
    onSkipStartClicked: () -> Unit,
    onAddVideoClicked: () -> Unit,
    onAddSampleClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .background(Color(0xFF141414), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Timeline head details
        Text(
            text = formatTimecode(currentPlayheadMs) + " / " + formatTimecode(totalDurationMs),
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSkipStartClicked) {
                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Restart Timeline", tint = Color.White)
            }

            // High contrast round orange primary play/pause key
            IconButton(
                onClick = onPlayPauseClicked,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFFFF6B35), RoundedCornerShape(21.dp))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Trigger playback",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Load Sample Video Button
            Button(
                onClick = onAddSampleClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayCircleOutline, contentDescription = "Load Sample", tint = Color(0xFFFF6B35), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sample Video", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Add Video Action Button (M3 floating highlight design)
            Button(
                onClick = onAddVideoClicked,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252525)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Import video clips tag", tint = Color(0xFFFF6B35), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Clip", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SelectionInfoBar(
    selectedClip: VideoClip?,
    selectedAudio: AudioClip?,
    selectedText: TextOverlay?,
    selectedSticker: StickerOverlay?,
    onClearSelection: () -> Unit
) {
    AnimatedVisibility(
        visible = (selectedClip != null || selectedAudio != null || selectedText != null || selectedSticker != null),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .border(BorderStroke(0.5.dp, Color(0xFF333333)))
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val elementLabel = when {
                selectedClip != null -> "Selected Clip: ${selectedClip.name}"
                selectedAudio != null -> "Selected Audio: ${selectedAudio.name}"
                selectedText != null -> "Selected Subtitle: \"${selectedText.text}\""
                selectedSticker != null -> "Selected Sticker"
                else -> ""
            }
            Text(
                text = elementLabel,
                color = Color(0xFFFF6B35),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = "Close Selection",
                fontSize = 10.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xFF333333), RoundedCornerShape(3.dp))
                    .clickable { onClearSelection() }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * Save file to Android Public Movies Directory using MediaStore
 */
fun saveVideoFileToGallery(context: Context, cacheFilePath: String) {
    val srcFile = File(cacheFilePath)
    if (!srcFile.exists()) {
        Toast.makeText(context, "Rendered file missing from workspace cache.", Toast.LENGTH_SHORT).show()
        return
    }

    val resolver = context.contentResolver
    val collectionVolume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val metaValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, "VidCraft_${System.currentTimeMillis()}.mp4")
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VidCraft")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
    }

    val contentUri = resolver.insert(collectionVolume, metaValues)
    if (contentUri == null) {
        Toast.makeText(context, "Failed to instantiate gallery database row.", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        resolver.openOutputStream(contentUri).use { outStream ->
            if (outStream != null) {
                srcFile.inputStream().use { inputStrm ->
                    inputStrm.copyTo(outStream)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            metaValues.clear()
            metaValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(contentUri, metaValues, null, null)
        }
        
        Toast.makeText(context, "Saved compilation directly to phone gallery folder: Movies/VidCraft!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Log.e("EditorScreen", "Error saving file output to public Movies storage", e)
        Toast.makeText(context, "MediaStore failed to save video database copy: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Format timecode in classic video-editor format hh:mm:ss.SS
 */
fun formatTimecode(timeMs: Long): String {
    val seconds = (timeMs / 1000) % 60
    val minutes = (timeMs / (1000 * 60)) % 60
    val fraction = (timeMs % 1000) / 100 // tenths of second
    return String.format(Locale.US, "%02d:%02d.%1d", minutes, seconds, fraction)
}
