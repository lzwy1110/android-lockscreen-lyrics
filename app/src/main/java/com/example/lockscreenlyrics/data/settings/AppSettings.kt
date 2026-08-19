package com.example.lockscreenlyrics.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettings {
    private const val PREFS_NAME = "lockscreen_lyrics_prefs"

    private const val KEY_IS_ENABLED = "is_enabled"
    private const val KEY_SHOW_TRANSLATION = "show_translation"
    private const val KEY_SHOW_ROMAJI = "show_romaji"
    private const val KEY_SHOW_CLOCK = "show_clock"
    private const val KEY_THEME_COLOR_HEX = "theme_color_hex"
    private const val KEY_ORIGINAL_COLOR_HEX = "original_color_hex"
    private const val KEY_ROMAJI_COLOR_HEX = "romaji_color_hex"
    private const val KEY_TRANSLATION_COLOR_HEX = "translation_color_hex"
    private const val KEY_CONVERT_TRADITIONAL = "convert_traditional"
    private const val KEY_CLOCK_SIZE = "clock_size_sp"
    private const val KEY_LYRIC_SIZE = "lyric_size_sp"
    private const val KEY_BG_DIM = "bg_dim_percent"

    private lateinit var prefs: SharedPreferences

    private val _isEnabled = MutableStateFlow(true)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _showTranslation = MutableStateFlow(true)
    val showTranslation: StateFlow<Boolean> = _showTranslation.asStateFlow()

    private val _showRomaji = MutableStateFlow(true)
    val showRomaji: StateFlow<Boolean> = _showRomaji.asStateFlow()

    private val _showClock = MutableStateFlow(true)
    val showClock: StateFlow<Boolean> = _showClock.asStateFlow()

    private val _themeColorHex = MutableStateFlow("#8EB5FF")
    val themeColorHex: StateFlow<String> = _themeColorHex.asStateFlow()

    // 原文、羅馬拼音、翻譯獨立色彩（預設皆為極光白 #FFFFFF）
    private val _originalColorHex = MutableStateFlow("#FFFFFF")
    val originalColorHex: StateFlow<String> = _originalColorHex.asStateFlow()

    private val _romajiColorHex = MutableStateFlow("#FFFFFF")
    val romajiColorHex: StateFlow<String> = _romajiColorHex.asStateFlow()

    private val _translationColorHex = MutableStateFlow("#FFFFFF")
    val translationColorHex: StateFlow<String> = _translationColorHex.asStateFlow()

    private val _convertTraditional = MutableStateFlow(true)
    val convertTraditional: StateFlow<Boolean> = _convertTraditional.asStateFlow()

    private val _clockSizeSp = MutableStateFlow(80)
    val clockSizeSp: StateFlow<Int> = _clockSizeSp.asStateFlow()

    private val _lyricSizeSp = MutableStateFlow(28)
    val lyricSizeSp: StateFlow<Int> = _lyricSizeSp.asStateFlow()

    private val _bgDimPercent = MutableStateFlow(65)
    val bgDimPercent: StateFlow<Int> = _bgDimPercent.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _isEnabled.value = prefs.getBoolean(KEY_IS_ENABLED, true)
        _showTranslation.value = prefs.getBoolean(KEY_SHOW_TRANSLATION, true)
        _showRomaji.value = prefs.getBoolean(KEY_SHOW_ROMAJI, true)
        _showClock.value = prefs.getBoolean(KEY_SHOW_CLOCK, true)
        _themeColorHex.value = prefs.getString(KEY_THEME_COLOR_HEX, "#8EB5FF") ?: "#8EB5FF"
        _originalColorHex.value = prefs.getString(KEY_ORIGINAL_COLOR_HEX, "#FFFFFF") ?: "#FFFFFF"
        _romajiColorHex.value = prefs.getString(KEY_ROMAJI_COLOR_HEX, "#FFFFFF") ?: "#FFFFFF"
        _translationColorHex.value = prefs.getString(KEY_TRANSLATION_COLOR_HEX, "#FFFFFF") ?: "#FFFFFF"
        _convertTraditional.value = prefs.getBoolean(KEY_CONVERT_TRADITIONAL, true)
        _clockSizeSp.value = prefs.getInt(KEY_CLOCK_SIZE, 80)
        _lyricSizeSp.value = prefs.getInt(KEY_LYRIC_SIZE, 28)
        _bgDimPercent.value = prefs.getInt(KEY_BG_DIM, 65)

        // 背景非阻塞預載入日語漢字音訓對照字典
        com.example.lockscreenlyrics.data.converter.RomajiAutoCompleter.init(context)
    }

    fun setThemeColorHex(hex: String) {
        _themeColorHex.value = hex
        prefs.edit().putString(KEY_THEME_COLOR_HEX, hex).apply()
    }

    fun setOriginalColorHex(hex: String) {
        _originalColorHex.value = hex
        prefs.edit().putString(KEY_ORIGINAL_COLOR_HEX, hex).apply()
    }

    fun setRomajiColorHex(hex: String) {
        _romajiColorHex.value = hex
        prefs.edit().putString(KEY_ROMAJI_COLOR_HEX, hex).apply()
    }

    fun setTranslationColorHex(hex: String) {
        _translationColorHex.value = hex
        prefs.edit().putString(KEY_TRANSLATION_COLOR_HEX, hex).apply()
    }

    fun setShowClock(show: Boolean) {
        _showClock.value = show
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply()
    }

    fun setShowRomaji(show: Boolean) {
        _showRomaji.value = show
        prefs.edit().putBoolean(KEY_SHOW_ROMAJI, show).apply()
    }

    fun setConvertTraditional(convert: Boolean) {
        _convertTraditional.value = convert
        prefs.edit().putBoolean(KEY_CONVERT_TRADITIONAL, convert).apply()
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_IS_ENABLED, enabled).apply()
    }

    fun setShowTranslation(show: Boolean) {
        _showTranslation.value = show
        prefs.edit().putBoolean(KEY_SHOW_TRANSLATION, show).apply()
    }

    fun setClockSize(sizeSp: Int) {
        _clockSizeSp.value = sizeSp
        prefs.edit().putInt(KEY_CLOCK_SIZE, sizeSp).apply()
    }

    fun setLyricSize(sizeSp: Int) {
        _lyricSizeSp.value = sizeSp
        prefs.edit().putInt(KEY_LYRIC_SIZE, sizeSp).apply()
    }

    fun setBgDim(percent: Int) {
        _bgDimPercent.value = percent
        prefs.edit().putInt(KEY_BG_DIM, percent).apply()
    }
}
