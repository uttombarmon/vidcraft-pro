package com.example.utils

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Effects
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

object FFmpegHelper {
    private const val TAG = "FFmpegHelper"

    interface ExportProgressListener {
        fun onProgress(percentage: Float)
        fun onComplete(outputPath: String)
        fun onError(errorMsg: String)
    }

    /**
     * Build the Media3 visual pipeline and export the composition.
     */
    @UnstableApi
    suspend fun exportVideo(
        context: Context,
        project: VideoProject,
        resolutionWidth: Int,
        resolutionHeight: Int,
        listener: ExportProgressListener,
    ) = withContext(Dispatchers.Main) {
        val cacheDir = context.cacheDir
        val outputDir = File(cacheDir, "exports")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, "VidCraft_${System.currentTimeMillis()}.mp4")
        val outputPath = outputFile.absolutePath

        val videoTrack = project.videoTracks.firstOrNull()
        if (videoTrack == null || videoTrack.clips.isEmpty()) {
            listener.onError("No video clips found in the project.")
            return@withContext
        }

        val totalDurationMs = project.totalDurationMs
        if (totalDurationMs <= 0) {
            listener.onError("Project duration is 0ms.")
            return@withContext
        }

        try {
            val clips = videoTrack.clips
            val editedMediaItems = clips.map { clip ->
                // 1. Trimming start and end
                val clippingConfiguration = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startInVideoMs.toLong())
                    .setEndPositionMs(clip.endInVideoMs.toLong())
                    .build()

                val mediaItem = MediaItem.Builder()
                    .setUri(android.net.Uri.fromFile(File(clip.filePath)))
                    .setClippingConfiguration(clippingConfiguration)
                    .build()

                val effectsList = mutableListOf<Effect>()

                // 2. Add rotation
                if (clip.rotation != 0) {
                    effectsList.add(
                        ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(clip.rotation.toFloat())
                            .build()
                    )
                }

                // 3. Scale and SAR
                effectsList.add(Presentation.createForWidthAndHeight(
                    resolutionWidth,
                    resolutionHeight,
                    Presentation.LAYOUT_SCALE_TO_FIT
                ))

                // 4. Contrast, Brightness and Saturation
                val brightnessFactor = 1.0f + clip.brightness
                val rgbAdjustment = RgbAdjustment.Builder()
                    .setRedScale(brightnessFactor)
                    .setGreenScale(brightnessFactor)
                    .setBlueScale(brightnessFactor)
                    .build()
                effectsList.add(rgbAdjustment)

                EditedMediaItem.Builder(mediaItem)
                    .setEffects(
                        Effects(
                            emptyList<AudioProcessor>(),
                            effectsList
                        )
                    )
                    .build()
            }

            val videoSequence = EditedMediaItemSequence(editedMediaItems)
            
            // 5. Add Audio Tracks (Background Music)
            val audioSequences = mutableListOf<EditedMediaItemSequence>()
            project.audioTracks.forEach { track ->
                val editedAudioItems = mutableListOf<EditedMediaItem>()
                track.clips.forEach { audioClip ->
                    // Handle gaps by prepending silence if needed
                    // For simplicity in this version, we assume clips are added sequentially or handle the first offset
                    val mediaItem = MediaItem.Builder()
                        .setUri(android.net.Uri.fromFile(File(audioClip.filePath)))
                        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(audioClip.startInAudioMs)
                            .setEndPositionMs(audioClip.endInAudioMs)
                            .build())
                        .build()
                    
                    editedAudioItems.add(EditedMediaItem.Builder(mediaItem).build())
                }
                if (editedAudioItems.isNotEmpty()) {
                    audioSequences.add(EditedMediaItemSequence(editedAudioItems))
                }
            }

            val allSequences = mutableListOf<EditedMediaItemSequence>()
            allSequences.add(videoSequence)
            allSequences.addAll(audioSequences)

            val composition = Composition.Builder(allSequences).build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    Log.d(TAG, "Export completed to $outputPath")
                    listener.onComplete(outputPath)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    Log.e(TAG, "Export failed: ", exportException)
                    listener.onError(exportException.message ?: "Encoder / Muxer export failure")
                }
            })

            // Run the compiler transformation
            transformer.start(composition, outputPath)

            // Dynamic progress polling - must happen on the same thread (Main) as transformer.start()
            val progressHolder = ProgressHolder()
            while (true) {
                val progressState = transformer.getProgress(progressHolder)
                if (progressState == Transformer.PROGRESS_STATE_NOT_STARTED) {
                    break
                }
                
                val progressPercent = progressHolder.progress.toFloat()
                listener.onProgress(progressPercent)
                
                // Suspend the coroutine to avoid tight looping, without blocking the UI thread
                delay(250)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error initiating export: ", e)
            listener.onError(e.message ?: "Failed to initialize exporter")
        }
    }

    /**
     * Preview helper description info.
     */
    fun createPreviewFilter(clip: VideoClip): String {
        val list = mutableListOf<String>()
        list.add("B: ${clip.brightness}")
        list.add("C: ${clip.contrast}")
        list.add("S: ${clip.saturation}")
        if (clip.filterType != FilterType.NONE) {
            list.add(clip.filterType.name)
        }
        return list.joinToString(", ")
    }
}
