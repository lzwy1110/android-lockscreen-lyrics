package com.example.lockscreenlyrics.data.converter

import android.icu.text.Transliterator
import android.os.Build
import com.example.lockscreenlyrics.data.model.LyricLine

/**
 * 智慧羅馬拼音自動補全器 (Romaji Auto-Completer)
 * 
 * 優先採用平台官方校對的羅馬音軌；
 * 當偵測到日語假名或韓文字元且無官方音軌時，自動透過 Android 系統底層 ICU 引擎與赫本假名表即時補全。
 */
object RomajiAutoCompleter {

    // 系統底層 ICU 國際化文字轉換引擎（Android 7.0+ 內建，0KB 體積）
    private val icuTransliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                Transliterator.getInstance("Any-Latin; Latin-ASCII")
            } catch (_: Exception) {
                try {
                    Transliterator.getInstance("Any-Latin")
                } catch (_: Exception) {
                    null
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Transliterator.getInstance("Any-Latin")
            } catch (_: Exception) {
                null
            }
        } else null
    }

    /**
     * 若歌詞缺少羅馬音軌，自動掃描並逐行補全
     */
    fun completeIfMissing(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines

        // 1. 若已經有任一行具備羅馬拼音（代表平台已有官方音軌），維持原樣不做覆蓋
        val hasOfficialRomaji = lines.any { !it.romaji.isNullOrBlank() }
        if (hasOfficialRomaji) return lines

        // 2. 檢查整首歌詞是否包含日語假名或韓文
        val needsTransliteration = lines.any { containsKanaOrHangul(it.text) }
        if (!needsTransliteration) return lines

        // 3. 逐行生成平滑的羅馬拼音音軌
        return lines.map { line ->
            if (line.romaji.isNullOrBlank() && line.text.isNotBlank()) {
                val generated = convertToRomaji(line.text)
                if (generated.isNotBlank() && generated.lowercase() != line.text.lowercase()) {
                    line.copy(romaji = generated)
                } else {
                    line
                }
            } else {
                line
            }
        }
    }

    /**
     * 判斷字串中是否包含日語平假名、片假名或韓文字元
     */
    private fun containsKanaOrHangul(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            // 平假名 (0x3040..0x309F) 與 片假名 (0x30A0..0x30FF)
            if (code in 0x3040..0x30FF) return true
            // 韓文諺文 (0xAC00..0xD7AF, 0x1100..0x11FF)
            if (code in 0xAC00..0xD7AF || code in 0x1100..0x11FF) return true
        }
        return false
    }

    /**
     * 將日文/韓文文字轉為流暢的標準羅馬拼音
     */
    fun convertToRomaji(text: String): String {
        if (text.isBlank()) return ""

        val raw = try {
            icuTransliterator?.transliterate(text) ?: kanaFallback(text)
        } catch (_: Exception) {
            kanaFallback(text)
        }

        return formatRomaji(raw)
    }

    /**
     * 美化羅馬拼音格式（壓縮多餘空格、移除不必要符號）
     */
    private fun formatRomaji(input: String): String {
        return input
            .replace(Regex("[·・~～]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 輕量級五十音假名替換備援（當 ICU 引擎不可用時觸發）
     */
    private fun kanaFallback(text: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length) {
                val two = text.substring(i, i + 2)
                val mappedTwo = KANA_COMBO_MAP[two]
                if (mappedTwo != null) {
                    sb.append(mappedTwo).append(" ")
                    i += 2
                    continue
                }
            }
            val one = text[i].toString()
            val mappedOne = KANA_SINGLE_MAP[one]
            if (mappedOne != null) {
                sb.append(mappedOne).append(" ")
            } else {
                sb.append(text[i])
            }
            i++
        }
        return sb.toString().trim()
    }

    private val KANA_COMBO_MAP = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja",  "じゅ" to "ju",  "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo"
    )

    private val KANA_SINGLE_MAP = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "wo", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po"
    )
}
