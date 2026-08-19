package com.example.lockscreenlyrics.data.converter

import android.content.Context
import android.util.Log
import com.example.lockscreenlyrics.data.model.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 智慧日語漢字與假名羅馬音自動補全器 (Japanese Romaji Auto-Completer)
 * 
 * 採用標準 Unicode Unihan 漢字日語訓讀/音讀對照表 + 赫本式假名映射，
 * 徹底解決「日文歌漢字被誤轉為中文漢語拼音」的問題（如：風 ➔ kaze，而非 feng）。
 */
object RomajiAutoCompleter {
    private const val TAG = "RomajiAutoCompleter"
    private val kanjiMap = HashMap<String, String>(14000)
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.assets.open("kanji_romaji.tsv").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String? = reader.readLine()
                        while (line != null) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                val idx = trimmed.indexOf('\t')
                                if (idx > 0) {
                                    val k = trimmed.substring(0, idx)
                                    val v = trimmed.substring(idx + 1)
                                    kanjiMap[k] = v
                                }
                            }
                            line = reader.readLine()
                        }
                    }
                }
                isInitialized = true
                Log.d(TAG, "已成功載入 ${kanjiMap.size} 個漢字日語讀音")
            } catch (e: Exception) {
                Log.w(TAG, "載入漢字對照表失敗: ${e.message}")
            }
        }
    }

    /**
     * 若歌詞缺少羅馬音軌，自動掃描並逐行補全日語音軌
     */
    fun completeIfMissing(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines

        // 1. 若已經有任一行具備羅馬拼音（代表平台已有官方音軌），維持原樣不做覆蓋
        val hasOfficialRomaji = lines.any { !it.romaji.isNullOrBlank() }
        if (hasOfficialRomaji) return lines

        // 2. 檢查整首歌詞是否包含日語假名或韓文字元
        val needsTransliteration = lines.any { containsEastAsianText(it.text) }
        if (!needsTransliteration) return lines

        // 3. 逐行生成平滑的日語羅馬音
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

    private fun containsEastAsianText(text: String): Boolean {
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
     * 將日文（漢字+假名）精確轉為純正的日語羅馬音（如：風のたより ➔ kaze no tayori）
     */
    fun convertToRomaji(text: String): String {
        if (text.isBlank()) return ""

        val tokens = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            // 1. 優先匹配雙假名組合 (如: きゃ, しゅ, ちょ)
            if (i + 1 < text.length) {
                val two = text.substring(i, i + 2)
                val mappedTwo = KANA_COMBO_MAP[two]
                if (mappedTwo != null) {
                    tokens.add(mappedTwo)
                    i += 2
                    continue
                }
                // 雙漢字詞組 (如: 世界, 約束, 永遠)
                val compound = kanjiMap[two]
                if (compound != null) {
                    tokens.add(compound)
                    i += 2
                    continue
                }
            }

            // 2. 匹配單個假名
            val charStr = text[i].toString()
            val mappedKana = KANA_SINGLE_MAP[charStr]
            if (mappedKana != null) {
                // 促音 っ/ッ 處理
                if (charStr == "っ" || charStr == "ッ") {
                    if (i + 1 < text.length) {
                        val nextKana = KANA_SINGLE_MAP[text[i + 1].toString()]
                        if (!nextKana.isNullOrBlank()) {
                            tokens.add(nextKana[0].toString())
                        }
                    }
                } else {
                    tokens.add(mappedKana)
                }
                i++
                continue
            }

            // 3. 匹配單個漢字 (優先日語訓讀/音讀，如: 風 ➔ kaze, 窓 ➔ mado, 花 ➔ hana)
            val mappedKanji = kanjiMap[charStr]
            if (mappedKanji != null) {
                tokens.add(mappedKanji)
                i++
                continue
            }

            // 4. 英數字與標點符號直接保留
            tokens.add(charStr)
            i++
        }

        return formatRomaji(tokens.joinToString(" "))
    }

    private fun formatRomaji(input: String): String {
        return input
            .replace(Regex("[·・~～]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja",  "ジュ" to "ju",  "ジョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo"
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
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "де" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "wo", "ン" to "n",
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",
        "ー" to "-"
    )
}
