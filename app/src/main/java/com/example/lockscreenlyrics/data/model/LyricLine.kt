package com.example.lockscreenlyrics.data.model

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val romaji: String? = null
) : Comparable<LyricLine> {
    override fun compareTo(other: LyricLine): Int = timeMs.compareTo(other.timeMs)
}
