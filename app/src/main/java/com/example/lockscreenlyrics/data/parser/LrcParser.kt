package com.example.lockscreenlyrics.data.parser

import com.example.lockscreenlyrics.data.model.LyricLine
import java.util.regex.Pattern

object LrcParser {
    // 正則匹配 [mm:ss.xx]、[mm:ss:xx]、[mm:ss.xxx] 或 [mm:ss]
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val OFFSET_PATTERN = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE)

    /**
     * 解析標準 LRC 格式文字
     */
    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()

        val lines = lrcContent.lines()
        val result = mutableListOf<LyricLine>()
        var offsetMs = 0L

        // 1. 查找是否有 [offset:xxx] 全局時間偏移
        for (line in lines) {
            val offsetMatcher = OFFSET_PATTERN.matcher(line)
            if (offsetMatcher.find()) {
                offsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                break
            }
        }

        // 2. 逐行解析時間標籤與歌詞內容
        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") || trimmed.startsWith("[offset:")) {
                continue
            }

            val matcher = TIME_TAG_PATTERN.matcher(trimmed)
            val timeTags = mutableListOf<Long>()

            var lastMatchEnd = 0
            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = matcher.group(3)
                val ms = when {
                    fractionStr == null -> 0L
                    fractionStr.length == 1 -> fractionStr.toLong() * 100L
                    fractionStr.length == 2 -> fractionStr.toLong() * 10L
                    else -> fractionStr.take(3).toLong()
                }

                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms + offsetMs
                timeTags.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timeTags.isNotEmpty()) {
                val rawText = trimmed.substring(lastMatchEnd)
                val text = cleanLyricSpaces(rawText)
                if (text.isNotEmpty()) {
                    for (timestamp in timeTags) {
                        result.add(LyricLine(timeMs = timestamp, text = text))
                    }
                }
            }
        }

        // 3. 按照時間排序
        result.sort()
        return result
    }

    /**
     * 緊湊化歌詞文字內部的空白：將全形大空格 (\u3000) 與多重連續空格壓縮為單一窄空格
     */
    private fun cleanLyricSpaces(rawText: String): String {
        return rawText
            .replace("\u3000", " ")  // 全形空格轉為半形窄空格
            .replace("\u00A0", " ")  // 不間斷空格轉半形
            .replace(Regex("[ \\t]+"), " ") // 連續多個空格壓縮為單一空格
            .trim()
    }

    /**
     * 合併原文歌詞與翻譯歌詞（容許 1800ms 誤差範圍對齊，保留翻譯署名）
     */
    fun mergeTranslation(original: List<LyricLine>, translationLrc: String): List<LyricLine> {
        if (translationLrc.isBlank() || original.isEmpty()) return original

        val rawTrans = parse(translationLrc)
        val transLines = rawTrans.filter { line ->
            val t = line.text.trim()
            t.isNotBlank() && !t.startsWith("//")
        }
        if (transLines.isEmpty()) return original

        val merged = ArrayList<LyricLine>(original.size)
        for (orig in original) {
            val matchedTrans = transLines.minByOrNull { Math.abs(it.timeMs - orig.timeMs) }
            val transText = if (matchedTrans != null && Math.abs(matchedTrans.timeMs - orig.timeMs) <= 1800L) {
                matchedTrans.text
            } else null

            merged.add(orig.copy(translation = transText))
        }

        return merged
    }

    /**
     * 合併原文歌詞與羅馬拼音歌詞（Romaji）
     */
    fun mergeRomaji(original: List<LyricLine>, romajiLrc: String): List<LyricLine> {
        if (romajiLrc.isBlank() || original.isEmpty()) return original

        val rawRoma = parse(romajiLrc)
        val romaLines = rawRoma.filter { line ->
            val t = line.text.trim()
            t.isNotBlank() && !t.startsWith("//")
        }
        if (romaLines.isEmpty()) return original

        val merged = ArrayList<LyricLine>(original.size)
        for (orig in original) {
            val matched = romaLines.minByOrNull { Math.abs(it.timeMs - orig.timeMs) }
            val romaText = if (matched != null && Math.abs(matched.timeMs - orig.timeMs) <= 1800L) {
                matched.text
            } else null

            merged.add(orig.copy(romaji = romaText))
        }

        return merged
    }

    /**
     * 根據當前播放時間戳（毫秒）二分搜尋對應的歌詞行索引
     */
    fun findActiveIndex(lyrics: List<LyricLine>, currentMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        if (currentMs < lyrics.first().timeMs) return 0

        var low = 0
        var high = lyrics.size - 1
        var activeIndex = 0

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = lyrics[mid].timeMs

            if (midTime <= currentMs) {
                activeIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return activeIndex
    }
}
