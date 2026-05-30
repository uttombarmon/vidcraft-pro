package com.example.data

import android.net.Uri

data class VideoProject(
    val id: String,
    val name: String,
    val aspectRatio: ProjectAspectRatio = ProjectAspectRatio.WIDE,
    val videoTracks: List<VideoTrack> = listOf(VideoTrack()),
    val audioTracks: List<AudioTrack> = emptyList(),
    val textTracks: List<TextTrack> = emptyList(),
    val stickerTracks: List<StickerTrack> = emptyList()
) {
    val totalDurationMs: Long
        get() {
            val videoDuration = videoTracks.flatMap { it.clips }.maxOfOrNull { it.startInTimelineMs + it.durationAfterSpeedMs } ?: 0L
            val audioDuration = audioTracks.flatMap { it.clips }.maxOfOrNull { it.startInTimelineMs + (it.endInAudioMs - it.startInAudioMs) } ?: 0L
            val textDuration = textTracks.flatMap { it.textOverlays }.maxOfOrNull { it.startInTimelineMs + it.durationMs } ?: 0L
            val stickerDuration = stickerTracks.flatMap { it.stickers }.maxOfOrNull { it.startInTimelineMs + it.durationMs } ?: 0L
            return maxOf(videoDuration, audioDuration, textDuration, stickerDuration)
        }
}

data class VideoTrack(
    val id: String = "video_track_0",
    val clips: List<VideoClip> = emptyList()
)

data class AudioTrack(
    val id: String = "audio_track_0",
    val clips: List<AudioClip> = emptyList()
)

data class TextTrack(
    val id: String = "text_track_0",
    val textOverlays: List<TextOverlay> = emptyList()
)

data class StickerTrack(
    val id: String = "sticker_track_0",
    val stickers: List<StickerOverlay> = emptyList()
)

data class VideoClip(
    val id: String,
    val uri: Uri,
    val filePath: String,
    val name: String,
    val durationMs: Long,
    val startInVideoMs: Long = 0L,         // Trim start
    val endInVideoMs: Long = durationMs,     // Trim end
    val startInTimelineMs: Long = 0L,      // Global timeline position
    val speed: Float = 1.0f,
    val rotation: Int = 0,                 // 0, 90, 180, 270
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val brightness: Float = 0.0f,          // -1.0 to 1.0
    val contrast: Float = 1.0f,            // 0.0 to 2.0
    val saturation: Float = 1.0f,          // 0.0 to 2.0
    val transitionIn: VideoTransition = VideoTransition.NONE,
    val transitionDurationMs: Long = 500,
    val filterType: FilterType = FilterType.NONE
) {
    val durationAfterSpeedMs: Long
        get() = if (speed > 0f) ((endInVideoMs - startInVideoMs) / speed).toLong() else (endInVideoMs - startInVideoMs)
}

data class AudioClip(
    val id: String,
    val uri: Uri,
    val filePath: String,
    val name: String,
    val durationMs: Long,
    val startInAudioMs: Long = 0L,
    val endInAudioMs: Long = durationMs,
    val startInTimelineMs: Long = 0L,
    val volume: Float = 1.0f
)

data class TextOverlay(
    val id: String,
    val text: String,
    val fontName: String = "Default",
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val textSizeSp: Float = 24f,
    val startInTimelineMs: Long,
    val durationMs: Long = 3000L,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f
)

data class StickerOverlay(
    val id: String,
    val stickerResId: Int,
    val startInTimelineMs: Long,
    val durationMs: Long = 3000L,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val scale: Float = 1f,
    val rotation: Float = 0f
)

enum class ProjectAspectRatio(val ratio: Float, val label: String) {
    WIDE(16 / 9f, "16:9"),
    PORTRAIT(9 / 16f, "9:16"),
    SQUARE(1f, "1:1"),
    INSTAGRAM(4 / 5f, "4:5"),
    CINEMATIC(21 / 9f, "21:9")
}

enum class VideoTransition {
    NONE, FADE, SLIDE, WIPE
}

enum class FilterType {
    NONE, GRAYSCALE, WARM, COOL, VINTAGE, VIVID
}
