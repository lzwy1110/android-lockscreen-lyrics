package com.example.lockscreenlyrics.service

import android.media.session.MediaController
import com.example.lockscreenlyrics.data.model.LyricLine
import com.example.lockscreenlyrics.data.model.SongMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object PlaybackStateHolder {
    private val _currentSong = MutableStateFlow(SongMetadata())
    val currentSong: StateFlow<SongMetadata> = _currentSong.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

    private val _lyricSource = MutableStateFlow("")
    val lyricSource: StateFlow<String> = _lyricSource.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(0)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    var activeMediaController: MediaController? = null

    fun updateSong(song: SongMetadata) {
        _currentSong.value = song
    }

    fun updateLyrics(lines: List<LyricLine>, source: String = "") {
        _lyrics.value = lines
        _lyricSource.value = source
    }

    fun updateActiveIndex(index: Int) {
        if (_activeLyricIndex.value != index) {
            _activeLyricIndex.value = index
        }
    }

    fun setSearching(searching: Boolean) {
        _isSearching.value = searching
    }

    fun setServiceActive(active: Boolean) {
        _isServiceActive.value = active
    }

    // 控制音樂播放
    fun playPause() {
        val controller = activeMediaController ?: return
        val pbState = controller.playbackState ?: return
        val state = pbState.state
        if (state == android.media.session.PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipToNext() {
        activeMediaController?.transportControls?.skipToNext()
    }

    fun skipToPrevious() {
        activeMediaController?.transportControls?.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        activeMediaController?.transportControls?.seekTo(positionMs)
    }

    /**
     * 一鍵切換歌詞數據源（在 網易雲音樂 ☁️ 與 QQ 音樂 🐧 之間即時切換）
     */
    fun switchLyricSource() {
        val song = _currentSong.value
        if (song.title.isBlank()) return

        val currentSource = _lyricSource.value
        val targetSource = if (currentSource.contains("網易")) {
            com.example.lockscreenlyrics.data.settings.AppSettings.SOURCE_QQ
        } else {
            com.example.lockscreenlyrics.data.settings.AppSettings.SOURCE_NETEASE
        }

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            _isSearching.value = true
            val durationSec = (song.durationMs / 1000).toInt()
            val result = com.example.lockscreenlyrics.data.repository.LyricsRepository.fetchSpecificSource(
                targetSource,
                song.title,
                song.artist,
                durationSec
            )

            if (result.lines.isNotEmpty()) {
                updateLyrics(result.lines, result.source)
            }
            _isSearching.value = false
        }
    }
}
