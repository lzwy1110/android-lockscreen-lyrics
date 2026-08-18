package com.example.lockscreenlyrics.service

import android.media.session.MediaController
import com.example.lockscreenlyrics.data.model.LyricLine
import com.example.lockscreenlyrics.data.model.SongMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackStateHolder {
    private val _currentSong = MutableStateFlow(SongMetadata())
    val currentSong: StateFlow<SongMetadata> = _currentSong.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()

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

    fun updateLyrics(lines: List<LyricLine>) {
        _lyrics.value = lines
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
}
