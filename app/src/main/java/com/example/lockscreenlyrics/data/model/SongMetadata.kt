package com.example.lockscreenlyrics.data.model

import android.graphics.Bitmap

/**
 * 代表當前播放歌曲的詳細資訊
 */
data class SongMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val albumArtBitmap: Bitmap? = null,
    val albumArtUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val lastUpdateTimestamp: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val packageName: String = ""
) {
    /**
     * 根據系統時鐘差即時計算當前的精確播放毫秒數
     */
    fun getCurrentPositionMs(): Long {
        if (!isPlaying || playbackSpeed == 0f) return positionMs
        val elapsed = (android.os.SystemClock.elapsedRealtime() - lastUpdateTimestamp) * playbackSpeed
        return (positionMs + elapsed.toLong()).coerceIn(0L, if (durationMs > 0) durationMs else Long.MAX_VALUE)
    }

    val isValid: Boolean
        get() = title.isNotBlank()
}
