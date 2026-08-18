package com.example.lockscreenlyrics.data.converter

import com.github.houbb.opencc4j.util.ZhConverterUtil

/**
 * 採用 OpenCC 官方詞庫標準（Lyricify 同款 OpenCC 正體詞庫）
 * 支援詞組級、語境級精確簡轉繁，徹底杜絕錯別字與同音亂碼
 */
object ChineseConverter {

    /**
     * 將簡體中文轉換為標準繁體中文（正體字）
     */
    fun toTraditional(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return try {
            ZhConverterUtil.toTraditional(text)
        } catch (e: Exception) {
            text
        }
    }
}
