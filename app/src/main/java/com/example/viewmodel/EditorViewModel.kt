package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.data.*
import com.example.data.db.ProjectDatabase
import com.example.data.db.ProjectEntity
import com.example.utils.FFmpegHelper
import com.example.utils.ThumbnailHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ToolType {
    NONE, CANVAS, TRIM, SPEED, CROP_ROTATE, ADJUST, FILTER, TEXT, STICKER, AUDIO, TRANSITIONS
}

sealed interface ExportState {
    object Idle : ExportState
    data class Exporting(val progress: Float) : ExportState
    data class Success(val outputPath: String) : ExportState
    data class Error(val message: String) : ExportState
}

class EditorViewModel : ViewModel() {
    companion object {
        private const val TAG = "EditorViewModel"
    }

    private var projectDatabase: ProjectDatabase? = null

    // Master Video Project State
    private val _videoProject = MutableStateFlow(VideoProject(id = "project_1", name = "My Project"))
    val videoProject: StateFlow<VideoProject> = _videoProject.asStateFlow()

    // Editor Playback & Navigation State
    private val _currentPlayheadMs = MutableStateFlow(0L)
    val currentPlayheadMs: StateFlow<Long> = _currentPlayheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Selected Elements
    private val _selectedClip = MutableStateFlow<VideoClip?>(null)
    val selectedClip: StateFlow<VideoClip?> = _selectedClip.asStateFlow()

    private val _selectedAudioClip = MutableStateFlow<AudioClip?>(null)
    val selectedAudioClip: StateFlow<AudioClip?> = _selectedAudioClip.asStateFlow()

    private val _selectedTextOverlay = MutableStateFlow<TextOverlay?>(null)
    val selectedTextOverlay: StateFlow<TextOverlay?> = _selectedTextOverlay.asStateFlow()

    private val _selectedSticker = MutableStateFlow<StickerOverlay?>(null)
    val selectedSticker: StateFlow<StickerOverlay?> = _selectedSticker.asStateFlow()

    // Active Bottom Panel Options
    private val _activeTool = MutableStateFlow(ToolType.NONE)
    val activeTool: StateFlow<ToolType> = _activeTool.asStateFlow()

    // Export Process Status State
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    // Scale level of the timeline (pixels per millisecond or logical representation width)
    private val _timelineZoomScale = MutableStateFlow(1f)
    val timelineZoomScale: StateFlow<Float> = _timelineZoomScale.asStateFlow()

    init {
        // Pre-clear old thumbnails from previous session
        ThumbnailHelper.clearCache()
    }

    fun initDatabase(context: Context) {
        if (projectDatabase == null) {
            projectDatabase = ProjectDatabase.getDatabase(context)
            loadDraft()
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            val draft = projectDatabase?.projectDao()?.getProjectById("project_1")
            if (draft != null) {
                try {
                    val converters = com.example.data.db.Converters()
                    val project = converters.toVideoProject(draft.jsonContent)
                    if (project != null) {
                        _videoProject.value = project
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load draft", e)
                }
            }
        }
    }

    private fun saveDraft() {
        val project = _videoProject.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val converters = com.example.data.db.Converters()
                val json = converters.fromVideoProject(project)
                val entity = ProjectEntity(
                    id = project.id,
                    name = project.name,
                    jsonContent = json,
                    lastModified = System.currentTimeMillis()
                )
                projectDatabase?.projectDao()?.insertProject(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save draft", e)
            }
        }
    }

    private fun onProjectChanged() {
        saveDraft()
    }

    fun setZoomScale(scale: Float) {
        _timelineZoomScale.value = scale.coerceIn(0.2f, 5.0f)
    }

    fun setPlayhead(timeMs: Long) {
        val total = videoProject.value.totalDurationMs
        _currentPlayheadMs.value = timeMs.coerceIn(0L, maxOf(0L, total))
    }

    fun setIsPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setActiveTool(tool: ToolType) {
        _activeTool.value = tool
    }

    fun selectClip(clip: VideoClip?) {
        _selectedClip.value = clip
        if (clip != null) {
            _selectedAudioClip.value = null
            _selectedTextOverlay.value = null
        }
    }

    fun selectAudioClip(audio: AudioClip?) {
        _selectedAudioClip.value = audio
        if (audio != null) {
            _selectedClip.value = null
            _selectedTextOverlay.value = null
        }
    }

    fun selectTextOverlay(text: TextOverlay?) {
        _selectedTextOverlay.value = text
        if (text != null) {
            _selectedClip.value = null
            _selectedAudioClip.value = null
            _selectedSticker.value = null
        }
    }

    fun selectSticker(sticker: StickerOverlay?) {
        _selectedSticker.value = sticker
        if (sticker != null) {
            _selectedClip.value = null
            _selectedAudioClip.value = null
            _selectedTextOverlay.value = null
        }
    }

    fun setAspectRatio(ratio: ProjectAspectRatio) {
        _videoProject.value = _videoProject.value.copy(aspectRatio = ratio)
        onProjectChanged()
    }

    // --- TIMELINE MANAGEMENT ---

    fun recalculateTimeline(clips: List<VideoClip>) {
        var currentOffset = 0L
        val updatedClips = clips.map { clip ->
            val updated = clip.copy(startInTimelineMs = currentOffset)
            currentOffset += updated.durationAfterSpeedMs
            updated
        }
        _videoProject.value = _videoProject.value.copy(
            videoTracks = listOf(VideoTrack(id = "video_track_0", clips = updatedClips))
        )
        onProjectChanged()
        
        // Keep selection updated
        _selectedClip.value?.let { currentSec ->
            val updatedSelection = updatedClips.firstOrNull { it.id == currentSec.id }
            _selectedClip.value = updatedSelection
        }
    }

    fun addVideoClip(context: Context, uri: Uri) {
        viewModelScope.launch {
            val details = resolveUriDetails(context, uri) ?: return@launch
            val newClip = VideoClip(
                id = "clip_${System.currentTimeMillis()}",
                uri = uri,
                filePath = details.filePath,
                name = details.name,
                durationMs = details.durationMs
            )
            val currentClips = _videoProject.value.videoTracks.firstOrNull()?.clips ?: emptyList()
            val updatedClips = currentClips + newClip
            recalculateTimeline(updatedClips)
            selectClip(newClip)
        }
    }

    fun addSampleClip(context: Context) {
        viewModelScope.launch {
            _exportState.value = ExportState.Exporting(10f)
            val sampleFile = File(context.cacheDir, "sample_clip_escapes.mp4")
            try {
                withContext(Dispatchers.IO) {
                    if (!sampleFile.exists() || (sampleFile.length() < 1000)) {
                        _exportState.value = ExportState.Exporting(30f)
                        val url = java.net.URL("https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
                        url.openStream().use { input ->
                            sampleFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
                _exportState.value = ExportState.Exporting(80f)
                val details = resolveUriDetails(context, Uri.fromFile(sampleFile))
                if (details != null) {
                    val newClip = VideoClip(
                        id = "clip_sample_${System.currentTimeMillis()}",
                        uri = Uri.fromFile(sampleFile),
                        filePath = details.filePath,
                        name = "Escapes Sample",
                        durationMs = details.durationMs
                    )
                    val currentClips = _videoProject.value.videoTracks.firstOrNull()?.clips ?: emptyList()
                    val updatedClips = currentClips + newClip
                    recalculateTimeline(updatedClips)
                    selectClip(newClip)
                }
                _exportState.value = ExportState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download sample video", e)
                _exportState.value = ExportState.Error("Could not retrieve sample video: ${e.message}")
            }
        }
    }

    fun addBackgroundMusic(context: Context, uri: Uri) {
        viewModelScope.launch {
            val details = resolveUriDetails(context, uri) ?: return@launch
            val newAudio = AudioClip(
                id = "audio_${System.currentTimeMillis()}",
                uri = uri,
                filePath = details.filePath,
                name = details.name,
                durationMs = details.durationMs,
                startInTimelineMs = 0L
            )
            _videoProject.value = _videoProject.value.copy(
                audioTracks = listOf(AudioTrack(id = "audio_track_0", clips = listOf(newAudio)))
            )
            onProjectChanged()
            selectAudioClip(newAudio)
        }
    }

    fun addTextOverlay(text: String) {
        val textId = "text_${System.currentTimeMillis()}"
        val currentPlayhead = _currentPlayheadMs.value
        val newText = TextOverlay(
            id = textId,
            text = text,
            startInTimelineMs = currentPlayhead,
            durationMs = 3000L
        )
        val currentTracks = _videoProject.value.textTracks.firstOrNull()?.textOverlays ?: emptyList()
        _videoProject.value = _videoProject.value.copy(
            textTracks = listOf(TextTrack(id = "text_track_0", textOverlays = currentTracks + newText))
        )
        onProjectChanged()
        selectTextOverlay(newText)
    }

    fun removeTextOverlay(id: String) {
        val currentTracks = _videoProject.value.textTracks.firstOrNull()?.textOverlays ?: emptyList()
        val filtered = currentTracks.filter { it.id != id }
        _videoProject.value = _videoProject.value.copy(
            textTracks = listOf(TextTrack(textOverlays = filtered))
        )
        onProjectChanged()
        if (_selectedTextOverlay.value?.id == id) {
            _selectedTextOverlay.value = null
        }
    }

    fun addSticker(stickerResId: Int) {
        val stickerId = "sticker_${System.currentTimeMillis()}"
        val currentPlayhead = _currentPlayheadMs.value
        val newSticker = StickerOverlay(
            id = stickerId,
            stickerResId = stickerResId,
            startInTimelineMs = currentPlayhead,
            durationMs = 3000L
        )
        val currentTracks = _videoProject.value.stickerTracks.firstOrNull()?.stickers ?: emptyList()
        _videoProject.value = _videoProject.value.copy(
            stickerTracks = listOf(StickerTrack(id = "sticker_track_0", stickers = currentTracks + newSticker))
        )
        onProjectChanged()
        selectSticker(newSticker)
    }

    fun removeSticker(id: String) {
        val currentTracks = _videoProject.value.stickerTracks.firstOrNull()?.stickers ?: emptyList()
        val filtered = currentTracks.filter { it.id != id }
        _videoProject.value = _videoProject.value.copy(
            stickerTracks = listOf(StickerTrack(stickers = filtered))
        )
        onProjectChanged()
        if (_selectedSticker.value?.id == id) {
            _selectedSticker.value = null
        }
    }

    // --- LOGICAL CUT / SPLIT AT PLAYHEAD ---

    fun splitClipAtPlayhead() {
        val playhead = _currentPlayheadMs.value
        val project = _videoProject.value
        val videoTrack = project.videoTracks.firstOrNull() ?: return
        val clips = videoTrack.clips

        var clipToSplit: VideoClip? = null
        var clipIndex = -1
        for (i in clips.indices) {
            val c = clips[i]
            val cStart = c.startInTimelineMs
            val cEnd = cStart + c.durationAfterSpeedMs
            if (playhead in (cStart + 1) until cEnd) {
                clipToSplit = c
                clipIndex = i
                break
            }
        }

        if (clipToSplit != null) {
            val timelineOffset = playhead - clipToSplit.startInTimelineMs
            val videoOffsetMs = (timelineOffset * clipToSplit.speed).toLong()
            val splitPointInVideo = clipToSplit.startInVideoMs + videoOffsetMs

            val clipA = clipToSplit.copy(
                id = "${clipToSplit.id}_split_a",
                endInVideoMs = splitPointInVideo
            )
            val clipB = clipToSplit.copy(
                id = "${clipToSplit.id}_split_b_${System.currentTimeMillis()}",
                startInVideoMs = splitPointInVideo,
                startInTimelineMs = playhead
            )

            val newClips = clips.toMutableList()
            newClips.removeAt(clipIndex)
            newClips.add(clipIndex, clipA)
            newClips.add(clipIndex + 1, clipB)

            recalculateTimeline(newClips)
            selectClip(clipB)
        }
    }

    fun deleteSelectedClip() {
        val clip = _selectedClip.value ?: return
        val clips = _videoProject.value.videoTracks.firstOrNull()?.clips ?: return
        val updated = clips.filter { it.id != clip.id }
        recalculateTimeline(updated)
        selectClip(null)
    }

    fun removeSelectedAudio() {
        _videoProject.value = _videoProject.value.copy(audioTracks = emptyList())
        onProjectChanged()
        _selectedAudioClip.value = null
    }

    // --- CLIP PROPERTY MODIFICATIONS ---

    fun updateSelectedClip(updateBlock: (VideoClip) -> VideoClip) {
        val clip = _selectedClip.value ?: return
        val clips = _videoProject.value.videoTracks.firstOrNull()?.clips ?: return
        val updatedClips = clips.map {
            if (it.id == clip.id) {
                val next = updateBlock(it)
                _selectedClip.value = next
                next
            } else it
        }
        recalculateTimeline(updatedClips)
    }

    fun updateSelectedAudio(updateBlock: (AudioClip) -> AudioClip) {
        val audio = _selectedAudioClip.value ?: return
        val currentTracks = _videoProject.value.audioTracks.firstOrNull()?.clips ?: return
        val updated = currentTracks.map {
            if (it.id == audio.id) {
                val next = updateBlock(it)
                _selectedAudioClip.value = next
                next
            } else it
        }
        _videoProject.value = _videoProject.value.copy(
            audioTracks = listOf(AudioTrack(clips = updated))
        )
        onProjectChanged()
    }

    fun updateSelectedText(updateBlock: (TextOverlay) -> TextOverlay) {
        val text = _selectedTextOverlay.value ?: return
        val currentTracks = _videoProject.value.textTracks.firstOrNull()?.textOverlays ?: return
        val updated = currentTracks.map {
            if (it.id == text.id) {
                val next = updateBlock(it)
                _selectedTextOverlay.value = next
                next
            } else it
        }
        _videoProject.value = _videoProject.value.copy(
            textTracks = listOf(TextTrack(textOverlays = updated))
        )
        onProjectChanged()
    }

    fun updateSelectedSticker(updateBlock: (StickerOverlay) -> StickerOverlay) {
        val sticker = _selectedSticker.value ?: return
        val currentTracks = _videoProject.value.stickerTracks.firstOrNull()?.stickers ?: return
        val updated = currentTracks.map {
            if (it.id == sticker.id) {
                val next = updateBlock(it)
                _selectedSticker.value = next
                next
            } else it
        }
        _videoProject.value = _videoProject.value.copy(
            stickerTracks = listOf(StickerTrack(stickers = updated))
        )
        onProjectChanged()
    }

    // --- SCOPED URI TO FILE PATH COPIER ---

    private suspend fun resolveUriDetails(context: Context, uri: Uri): UriFileDetails? = withContext(Dispatchers.IO) {
        try {
            var name = "video_${System.currentTimeMillis()}.mp4"
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        name = cursor.getString(nameIndex)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving Display Name from ContentResolver: ${e.message}")
            }

            val importDir = File(context.cacheDir, "imports")
            if (!importDir.exists()) {
                importDir.mkdirs()
            }

            val destFile = File(importDir, "import_${System.currentTimeMillis()}_$name")
            
            var isCopied = false
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                isCopied = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed Copying via openInputStream: ${e.message}")
            }

            if (!isCopied && uri.scheme == "file") {
                try {
                    val sourceFile = File(uri.path ?: "")
                    if (sourceFile.exists()) {
                        sourceFile.inputStream().use { inputStream ->
                            destFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                                isCopied = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed Direct File Copy Fallback: ${e.message}")
                }
            }

            var durationMs = 0L
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(destFile.absolutePath)
                val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                durationMs = durationStr?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                Log.e(TAG, "Failed to retrieve duration via MediaMetadataRetriever", e)
            } finally {
                try {
                    retriever.release()
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // Fallback duration search
            if (durationMs <= 0L) {
                try {
                    context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Video.Media.DURATION), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val durationCol = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DURATION)
                            if (durationCol != -1) {
                                val found = cursor.getLong(durationCol)
                                if (found > 0) durationMs = found
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fallback duration cursor query: ${e.message}")
                }
                
                if (durationMs <= 0L) {
                    durationMs = 5000L
                }
            }

            return@withContext UriFileDetails(
                filePath = destFile.absolutePath,
                name = name,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error on resolveUriDetails", e)
            return@withContext null
        }
    }

    private data class UriFileDetails(
        val filePath: String,
        val name: String,
        val durationMs: Long
    )

    // --- FFMPEG EXPORT ENGINES ---

    @OptIn(UnstableApi::class)
    fun triggerExport(context: Context, resolutionHeight: Int) {
        val ratio = _videoProject.value.aspectRatio.ratio
        val width = (resolutionHeight * ratio).toInt()
        
        Log.d(TAG, "Triggering video export at scale: ${width}x$resolutionHeight (ratio: $ratio)")
        _exportState.value = ExportState.Exporting(0f)

        viewModelScope.launch {
            FFmpegHelper.exportVideo(
                context = context,
                project = _videoProject.value,
                resolutionWidth = width,
                resolutionHeight = resolutionHeight,
                listener = object : FFmpegHelper.ExportProgressListener {
                    override fun onProgress(percentage: Float) {
                        _exportState.value = ExportState.Exporting(percentage)
                    }

                    override fun onComplete(outputPath: String) {
                        _exportState.value = ExportState.Success(outputPath)
                    }

                    override fun onError(errorMsg: String) {
                        _exportState.value = ExportState.Error(errorMsg)
                    }
                }
            )
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        ThumbnailHelper.clearCache()
    }
}
