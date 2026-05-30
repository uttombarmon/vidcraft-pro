package com.example.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThumbnailHelper {
    // Keep a maximum of 30MB of bitmaps in memory cache
    private val thumbnailCache = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount
        }
    }

    suspend fun getFrameAtTime(
        filePath: String,
        timeMs: Long,
        width: Int = 160,
        height: Int = 100
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${filePath}_${timeMs}_${width}_${height}"
        thumbnailCache.get(cacheKey)?.let { return@withContext it }

        var retriever: MediaMetadataRetriever? = null
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext null
            }
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            
            // Microseconds
            val timeUs = timeMs * 1000
            
            // OPTION_CLOSEST_SYNC is fast and suitable for rendering scrolling timeline strips
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            
            if (frame != null) {
                // Resize scale to keep smooth performance
                val scaledFrame = Bitmap.createScaledBitmap(frame, width, height, true)
                if (scaledFrame != frame) {
                    frame.recycle()
                }
                thumbnailCache.put(cacheKey, scaledFrame)
                return@withContext scaledFrame
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
        return@withContext null
    }

    fun clearCache() {
        thumbnailCache.evictAll()
    }
}
