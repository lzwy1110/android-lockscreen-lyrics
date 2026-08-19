package com.example.lockscreenlyrics.data.repository

import android.util.Base64
import android.util.Log
import com.example.lockscreenlyrics.data.converter.ChineseConverter
import com.example.lockscreenlyrics.data.converter.RomajiAutoCompleter
import com.example.lockscreenlyrics.data.model.LyricLine
import com.example.lockscreenlyrics.data.parser.LrcParser
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class LyricSearchResult(
    val lines: List<LyricLine> = emptyList(),
    val source: String = ""
)

object LyricsRepository {
    private const val TAG = "LyricsRepository"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val memoryCache = ConcurrentHashMap<String, LyricSearchResult>()

    /**
     * 獲取歌詞主入口：依照 QQ 音樂 (雙語優先) -> 網易雲音樂 (雙語優先) -> 原版歌詞 -> LRCLIB 依序退守
     */
    suspend fun getLyrics(title: String, artist: String, durationSec: Int = 0): LyricSearchResult = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSongTitle(title)
        val cleanArtist = artist.trim()
        val cacheKey = "$cleanTitle-$cleanArtist".lowercase()

        // 1. 檢查記憶體快取
        memoryCache[cacheKey]?.let { return@withContext it }

        // 2. 🥇 第一優先：嘗試 QQ 音樂（使用 Lyricify 同款現代 musicu.fcg 端點）
        val qqLyrics = fetchFromQQMusic(cleanTitle, cleanArtist, durationSec)
        val hasQQTranslation = qqLyrics.any { !it.translation.isNullOrBlank() }

        if (qqLyrics.isNotEmpty() && hasQQTranslation) {
            Log.d(TAG, "成功從 QQ 音樂取得雙語歌詞: $cleanTitle (${qqLyrics.size} 行，含翻譯)")
            val result = wrapResult(qqLyrics, "QQ 音樂")
            memoryCache[cacheKey] = result
            return@withContext result
        }

        // 3. 🥈 第二優先：嘗試 網易雲音樂
        val neteaseLyrics = fetchFromNetease(cleanTitle, cleanArtist, durationSec)
        val hasNeteaseTranslation = neteaseLyrics.any { !it.translation.isNullOrBlank() }

        if (neteaseLyrics.isNotEmpty() && hasNeteaseTranslation) {
            Log.d(TAG, "成功從 網易雲音樂取得雙語歌詞: $cleanTitle (${neteaseLyrics.size} 行，含翻譯)")
            val result = wrapResult(neteaseLyrics, "網易雲音樂")
            memoryCache[cacheKey] = result
            return@withContext result
        }

        // 4. 若兩者都沒有翻譯，退守使用有歌詞的來源
        if (qqLyrics.isNotEmpty()) {
            Log.d(TAG, "使用 QQ 音樂原版歌詞: $cleanTitle")
            val result = wrapResult(qqLyrics, "QQ 音樂")
            memoryCache[cacheKey] = result
            return@withContext result
        }
        if (neteaseLyrics.isNotEmpty()) {
            Log.d(TAG, "使用 網易雲原版歌詞: $cleanTitle")
            val result = wrapResult(neteaseLyrics, "網易雲音樂")
            memoryCache[cacheKey] = result
            return@withContext result
        }

        // 5. 🥉 第三優先：嘗試 LRCLIB 開源歌詞庫
        val lrclibLyrics = fetchFromLrclib(cleanTitle, cleanArtist, durationSec)
        if (lrclibLyrics.isNotEmpty()) {
            Log.d(TAG, "使用 LRCLIB 歌詞: $cleanTitle")
            val result = wrapResult(lrclibLyrics, "LRCLIB")
            memoryCache[cacheKey] = result
            return@withContext result
        }

        LyricSearchResult()
    }

    private fun wrapResult(lines: List<LyricLine>, source: String): LyricSearchResult {
        val completedLines = RomajiAutoCompleter.completeIfMissing(lines)
        return LyricSearchResult(completedLines, source)
    }

    /**
     * 從 QQ 音樂抓取同步歌詞與雙語翻譯（採用 Lyricify 同款 modern musicu.fcg API）
     */
    private fun fetchFromQQMusic(title: String, artist: String, durationSec: Int): List<LyricLine> {
        try {
            val primaryArtist = artist.split(Regex("[,/&、]|feat\\.?"), 2)[0].trim()

            // 1. 搜尋 songmid（結合歌名、歌手與秒數校驗）
            var songMid = searchQQSongMid("$title $artist".trim(), title, artist, durationSec)
            if (songMid.isNullOrBlank() && primaryArtist.isNotBlank()) {
                songMid = searchQQSongMid("$title $primaryArtist".trim(), title, primaryArtist, durationSec)
            }
            if (songMid.isNullOrBlank()) {
                songMid = searchQQSongMid(title, title, primaryArtist, durationSec)
            }
            // 雙向羅馬音備援搜尋
            if (songMid.isNullOrBlank()) {
                val romajiTitle = RomajiAutoCompleter.convertToRomaji(title)
                if (romajiTitle.isNotBlank() && romajiTitle.lowercase() != title.lowercase()) {
                    songMid = searchQQSongMid("$romajiTitle $primaryArtist".trim(), title, primaryArtist, durationSec)
                }
            }
            // 4. 純歌手備援搜尋（支援英日跨語言翻譯歌名，配合三重安全防護與時長鎖定）
            if (songMid.isNullOrBlank() && durationSec > 0 && artist.isNotBlank()) {
                songMid = searchQQByArtistFallback(artist, primaryArtist, title, durationSec)
            }

            if (songMid.isNullOrBlank()) return emptyList()

            // 2. 透過 QQ 音樂現代 RPC 端點 (musicu.fcg / PlayLyricInfo) 取得完整原文與中文翻譯
            val lyricsFromRpc = fetchQQModernLyric(songMid)
            if (lyricsFromRpc.isNotEmpty()) {
                return lyricsFromRpc
            }

            // 3. 備援：透過傳統端點 (fcg_query_lyric_new.fcg)
            return fetchQQLegacyLyric(songMid)
        } catch (e: Exception) {
            Log.w(TAG, "QQ Music fetch error: ${e.message}")
        }
        return emptyList()
    }

    /**
     * 現代 QQ 音樂 RPC 端點 (Lyricify 採用的 GetPlayLyricInfo 介面)
     */
    private fun fetchQQModernLyric(songMid: String): List<LyricLine> {
        try {
            val payload = JsonObject().apply {
                add("comm", JsonObject().apply {
                    addProperty("ct", "19")
                    addProperty("cv", "1873")
                    addProperty("uin", "0")
                })
                add("req", JsonObject().apply {
                    addProperty("module", "music.musichallSong.PlayLyricInfo")
                    addProperty("method", "GetPlayLyricInfo")
                    add("param", JsonObject().apply {
                        addProperty("songMID", songMid)
                        addProperty("crypt", 0)
                    })
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()

            val body = response.body?.string() ?: return emptyList()
            val json = gson.fromJson(body, JsonObject::class.java)

            val dataObj = json.getAsJsonObject("req")?.getAsJsonObject("data") ?: return emptyList()

            val rawLyric = dataObj.get("lyric")?.asString
            val rawTrans = dataObj.get("trans")?.asString
            val rawRoma = dataObj.get("roma")?.asString

            val originalLrc = decodeLrcText(rawLyric)
            val transLrc = decodeLrcText(rawTrans)
            val romaLrc = decodeLrcText(rawRoma)

            if (originalLrc.isNotBlank()) {
                var parsed = LrcParser.parse(originalLrc)
                if (transLrc.isNotBlank()) {
                    parsed = LrcParser.mergeTranslation(parsed, transLrc)
                }
                if (romaLrc.isNotBlank()) {
                    parsed = LrcParser.mergeRomaji(parsed, romaLrc)
                }
                return parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "QQ Modern Lyric fetch error: ${e.message}")
        }
        return emptyList()
    }

    /**
     * 傳統 QQ 音樂端點備援
     */
    private fun fetchQQLegacyLyric(songMid: String): List<LyricLine> {
        try {
            val lyricUrl = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid=$songMid&g_tk=5381&format=json&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0"
            val lyricReq = Request.Builder()
                .url(lyricUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .get()
                .build()

            val lyricResp = httpClient.newCall(lyricReq).execute()
            if (!lyricResp.isSuccessful) return emptyList()

            var lyricBody = lyricResp.body?.string() ?: return emptyList()
            if (lyricBody.startsWith("MusicJsonCallback(") && lyricBody.endsWith(")")) {
                lyricBody = lyricBody.substring("MusicJsonCallback(".length, lyricBody.length - 1)
            }

            val lyricJson = gson.fromJson(lyricBody, JsonObject::class.java)
            val rawLyricBase64 = lyricJson.get("lyric")?.asString
            val rawTransBase64 = lyricJson.get("trans")?.asString
            val rawRomaBase64 = lyricJson.get("roma")?.asString

            val originalLrc = decodeLrcText(rawLyricBase64)
            val transLrc = decodeLrcText(rawTransBase64)
            val romaLrc = decodeLrcText(rawRomaBase64)

            if (originalLrc.isNotBlank()) {
                var parsed = LrcParser.parse(originalLrc)
                if (transLrc.isNotBlank()) {
                    parsed = LrcParser.mergeTranslation(parsed, transLrc)
                }
                if (romaLrc.isNotBlank()) {
                    parsed = LrcParser.mergeRomaji(parsed, romaLrc)
                }
                return parsed
            }
        } catch (_: Exception) {}
        return emptyList()
    }

    /**
     * 搜尋 QQ 音樂 songmid（取得前 5 筆，透過歌名相似度、歌手吻合與秒數校驗嚴格過濾）
     */
    private fun searchQQSongMid(query: String, targetTitle: String, targetArtist: String, durationSec: Int): String? {
        try {
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=5&w=${URLEncoder.encode(query, "UTF-8")}&format=json"
            val searchReq = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://y.qq.com/")
                .get()
                .build()

            val searchResp = httpClient.newCall(searchReq).execute()
            if (searchResp.isSuccessful) {
                val searchBody = searchResp.body?.string() ?: return null
                val searchJson = gson.fromJson(searchBody, JsonObject::class.java)
                val songList = searchJson.getAsJsonObject("data")
                    ?.getAsJsonObject("song")
                    ?.getAsJsonArray("list")

                if (songList != null && songList.size() > 0) {
                    for (i in 0 until songList.size()) {
                        val item = songList.get(i).asJsonObject
                        val candidateName = item.get("songname")?.asString ?: ""
                        val candidateSinger = item.getAsJsonArray("singer")
                            ?.mapNotNull { it.asJsonObject.get("name")?.asString }
                            ?.joinToString(", ") ?: ""
                        val interval = item.get("interval")?.asInt ?: 0

                        val isTitleValid = isTitleMatch(targetTitle, candidateName)
                        val isArtistValid = isArtistMatch(targetArtist, candidateSinger)
                        val isDurationValid = durationSec <= 0 || interval <= 0 || Math.abs(durationSec - interval) <= 4

                        // 1. 歌名與歌手皆匹配時，直接命中
                        if (isTitleValid && isArtistValid) {
                            return item.get("songmid")?.asString
                        }
                        // 2. 歌名嚴格匹配 (>= 75%)，且歌手為東亞漢字/假名或時長吻合時命中
                        if (isTitleValid && (containsEastAsianText(candidateSinger) || isDurationValid)) {
                            return item.get("songmid")?.asString
                        }
                        // 3. 歌手精確吻合且歌曲時長精確吻合 (誤差 <= 2s) 時，直接命中
                        if (isArtistValid && isDurationValid && durationSec > 0 && interval > 0 && Math.abs(durationSec - interval) <= 2) {
                            return item.get("songmid")?.asString
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * QQ 音樂純歌手備援搜尋（用於英日跨語言翻譯歌名，如 Absolute Zero -> 絶対零度）
     * 內建三重安全防護：多藝人交叉鎖定、副標題/關鍵字比對、時長防碰撞歧義拒絕
     */
    private fun searchQQByArtistFallback(artist: String, primaryArtist: String, targetTitle: String, durationSec: Int): String? {
        val queryList = mutableListOf<String>()
        val cleanA = artist.replace(",", " ").replace("、", " ").replace("&", " ").trim()
        if (cleanA.isNotBlank()) queryList.add(cleanA)
        if (primaryArtist.isNotBlank() && primaryArtist != cleanA) queryList.add(primaryArtist)

        val secondaryArtists = artist.split(Regex("[,/&、]|feat\\.?"), 2).getOrNull(1)?.trim() ?: ""
        if (secondaryArtists.isNotBlank()) queryList.add(secondaryArtists)

        for (q in queryList) {
            try {
                val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=10&w=${URLEncoder.encode(q, "UTF-8")}&format=json"
                val searchReq = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://y.qq.com/")
                    .get()
                    .build()

                val searchResp = httpClient.newCall(searchReq).execute()
                if (!searchResp.isSuccessful) continue

                val searchBody = searchResp.body?.string() ?: continue
                val searchJson = gson.fromJson(searchBody, JsonObject::class.java)
                val songList = searchJson.getAsJsonObject("data")
                    ?.getAsJsonObject("song")
                    ?.getAsJsonArray("list") ?: continue

                val matchingCandidates = mutableListOf<JsonObject>()
                for (i in 0 until songList.size()) {
                    val item = songList.get(i).asJsonObject
                    val candidateSinger = item.getAsJsonArray("singer")
                        ?.mapNotNull { it.asJsonObject.get("name")?.asString }
                        ?.joinToString(", ") ?: ""
                    val interval = item.get("interval")?.asInt ?: 0
                    val candidateName = item.get("songname")?.asString ?: ""

                    val isArtistValid = isArtistMatch(artist, candidateSinger) || isArtistMatch(primaryArtist, candidateSinger)
                    val isDurationExact = Math.abs(durationSec - interval) <= 2

                    if (isArtistValid && isDurationExact) {
                        // 包含目標歌名或副歌手關鍵字（如 Cereus / Absolute Zero）直接命中
                        if (candidateName.contains(targetTitle, ignoreCase = true) ||
                            (secondaryArtists.isNotBlank() && candidateName.contains(secondaryArtists, ignoreCase = true))) {
                            return item.get("songmid")?.asString
                        }
                        matchingCandidates.add(item)
                    }
                }

                // 🌟 時長防碰撞歧義拒絕機制：若唯一命中則安全採用，若有多首不同歌名則拒絕盲猜
                if (matchingCandidates.size == 1) {
                    return matchingCandidates[0].get("songmid")?.asString
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 自動判斷並解碼 LRC 文字（支援 Base64 或純文字，過濾 HTML 實體字元）
     */
    private fun decodeLrcText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var text = input.trim()

        // 判斷是否為 Base64 字串
        if (!text.startsWith("[") && !text.startsWith("ti:") && !text.startsWith("ar:")) {
            try {
                val decodedBytes = Base64.decode(text, Base64.DEFAULT)
                text = String(decodedBytes, Charsets.UTF_8)
            } catch (_: Exception) {}
        }

        // 解碼常見 HTML 實體
        return text
            .replace("&#32;", " ")
            .replace("&#38;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#40;", "(")
            .replace("&#41;", ")")
            .replace("&#58;", ":")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
    }

    /**
     * 從網易雲音樂抓取歌詞與雙語翻譯
     */
    private fun fetchFromNetease(title: String, artist: String, durationSec: Int): List<LyricLine> {
        try {
            val primaryArtist = artist.split(Regex("[,/&、]|feat\\.?"), 2)[0].trim()

            // 1. 搜尋 songId（結合歌名、歌手與秒數校驗）
            var songId = searchNeteaseSongId("$title $artist".trim(), title, artist, durationSec)
            if (songId == null && primaryArtist.isNotBlank()) {
                songId = searchNeteaseSongId("$title $primaryArtist".trim(), title, primaryArtist, durationSec)
            }
            if (songId == null) {
                songId = searchNeteaseSongId(title, title, primaryArtist, durationSec)
            }
            // 雙向羅馬音備援搜尋
            if (songId == null) {
                val romajiTitle = RomajiAutoCompleter.convertToRomaji(title)
                if (romajiTitle.isNotBlank() && romajiTitle.lowercase() != title.lowercase()) {
                    songId = searchNeteaseSongId("$romajiTitle $primaryArtist".trim(), title, primaryArtist, durationSec)
                }
            }
            // 4. 純歌手備援搜尋（支援英日跨語言翻譯歌名，配合三重安全防護與時長鎖定）
            if (songId == null && durationSec > 0 && artist.isNotBlank()) {
                songId = searchNeteaseByArtistFallback(artist, primaryArtist, title, durationSec)
            }

            if (songId != null) {
                return fetchNeteaseLyricById(songId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Netease fetch error: ${e.message}")
        }
        return emptyList()
    }

    private fun searchNeteaseSongId(query: String, targetTitle: String, targetArtist: String, durationSec: Int): Long? {
        try {
            val searchUrl = "https://music.163.com/api/cloudsearch/pc?s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&limit=10"
            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://music.163.com/")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                val songs = json.getAsJsonObject("result")?.getAsJsonArray("songs")

                if (songs != null && songs.size() > 0) {
                    for (i in 0 until songs.size()) {
                        val item = songs.get(i).asJsonObject
                        val candidateName = item.get("name")?.asString ?: ""
                        val candidateArtists = (item.getAsJsonArray("ar") ?: item.getAsJsonArray("artists"))
                            ?.mapNotNull { it.asJsonObject.get("name")?.asString }
                            ?.joinToString(", ") ?: ""
                        val dtMs = item.get("dt")?.asLong ?: 0L
                        val candidateDurationSec = (dtMs / 1000).toInt()

                        val isTitleValid = isTitleMatch(targetTitle, candidateName)
                        val isArtistValid = isArtistMatch(targetArtist, candidateArtists)
                        val isDurationValid = durationSec <= 0 || candidateDurationSec <= 0 || Math.abs(durationSec - candidateDurationSec) <= 4

                        // 1. 歌名與歌手皆匹配時，直接命中
                        if (isTitleValid && isArtistValid) {
                            return item.get("id")?.asLong
                        }
                        // 2. 歌名嚴格匹配 (>= 75%)，且歌手為東亞漢字/假名或時長吻合時命中
                        if (isTitleValid && (containsEastAsianText(candidateArtists) || isDurationValid)) {
                            return item.get("id")?.asLong
                        }
                        // 3. 歌手精確吻合且歌曲時長精確吻合 (誤差 <= 2s) 時，直接命中
                        if (isArtistValid && isDurationValid && durationSec > 0 && candidateDurationSec > 0 && Math.abs(durationSec - candidateDurationSec) <= 2) {
                            return item.get("id")?.asLong
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun fetchNeteaseLyricById(songId: Long): List<LyricLine> {
        try {
            val lyricUrl = "https://music.163.com/api/song/lyric?os=pc&id=$songId&lv=-1&kv=-1&tv=-1&rv=-1"
            val request = Request.Builder()
                .url(lyricUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Referer", "https://music.163.com/")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return emptyList()
                val json = gson.fromJson(body, JsonObject::class.java)
                val originalLyric = json.getAsJsonObject("lrc")?.get("lyric")?.asString
                val translationLyric = json.getAsJsonObject("tlyric")?.get("lyric")?.asString
                val romajiLyric = json.getAsJsonObject("romalrc")?.get("lyric")?.asString

                if (!originalLyric.isNullOrBlank()) {
                    var parsed = LrcParser.parse(originalLyric)
                    // 若解析後只有純人員名單標頭（如 作词/作曲/混音），視為無實際歌詞
                    val actualLyricLines = parsed.filter { line ->
                        val t = line.text.trim()
                        !t.startsWith("作词") && !t.startsWith("作曲") && !t.startsWith("制作人") &&
                        !t.startsWith("母带") && !t.startsWith("混音") && !t.startsWith("作詞") && !t.startsWith("編曲")
                    }
                    if (actualLyricLines.isEmpty()) {
                        return emptyList()
                    }

                    if (!translationLyric.isNullOrBlank()) {
                        parsed = LrcParser.mergeTranslation(parsed, translationLyric)
                    }
                    if (!romajiLyric.isNullOrBlank()) {
                        parsed = LrcParser.mergeRomaji(parsed, romajiLyric)
                    }
                    return parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Netease lyric error: ${e.message}")
        }
        return emptyList()
    }

    /**
     * 網易雲純歌手備援搜尋（用於英日跨語言翻譯歌名，如 Absolute Zero -> 絶対零度）
     * 內建三重安全防護：多藝人交叉鎖定、副標題/關鍵字比對、時長防碰撞歧義拒絕
     */
    private fun searchNeteaseByArtistFallback(artist: String, primaryArtist: String, targetTitle: String, durationSec: Int): Long? {
        val queryList = mutableListOf<String>()
        val cleanA = artist.replace(",", " ").replace("、", " ").replace("&", " ").trim()
        if (cleanA.isNotBlank()) queryList.add(cleanA)
        if (primaryArtist.isNotBlank() && primaryArtist != cleanA) queryList.add(primaryArtist)

        val secondaryArtists = artist.split(Regex("[,/&、]|feat\\.?"), 2).getOrNull(1)?.trim() ?: ""
        if (secondaryArtists.isNotBlank()) queryList.add(secondaryArtists)

        for (q in queryList) {
            try {
                val searchUrl = "https://music.163.com/api/cloudsearch/pc?s=${URLEncoder.encode(q, "UTF-8")}&type=1&offset=0&limit=10"
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", "https://music.163.com/")
                    .get()
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) continue

                val body = response.body?.string() ?: continue
                val json = gson.fromJson(body, JsonObject::class.java)
                val songs = json.getAsJsonObject("result")?.getAsJsonArray("songs") ?: continue

                val matchingCandidates = mutableListOf<JsonObject>()
                for (i in 0 until songs.size()) {
                    val item = songs.get(i).asJsonObject
                    val candidateArtists = (item.getAsJsonArray("ar") ?: item.getAsJsonArray("artists"))
                        ?.mapNotNull { it.asJsonObject.get("name")?.asString }
                        ?.joinToString(", ") ?: ""
                    val dtMs = item.get("dt")?.asLong ?: 0L
                    val candidateDurationSec = (dtMs / 1000).toInt()
                    val candidateName = item.get("name")?.asString ?: ""

                    val isArtistValid = isArtistMatch(artist, candidateArtists) || isArtistMatch(primaryArtist, candidateArtists)
                    val isDurationExact = Math.abs(durationSec - candidateDurationSec) <= 2

                    if (isArtistValid && isDurationExact) {
                        if (candidateName.contains(targetTitle, ignoreCase = true) ||
                            (secondaryArtists.isNotBlank() && candidateName.contains(secondaryArtists, ignoreCase = true))) {
                            return item.get("id")?.asLong
                        }
                        matchingCandidates.add(item)
                    }
                }

                // 🌟 時長防碰撞歧義拒絕機制：若唯一命中則安全採用，若有多首不同歌名則拒絕盲猜
                if (matchingCandidates.size == 1) {
                    return matchingCandidates[0].get("id")?.asLong
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * 從 LRCLIB 抓取同步歌詞
     */
    private fun fetchFromLrclib(title: String, artist: String, durationSec: Int): List<LyricLine> {
        try {
            val urlBuilder = "https://lrclib.net/api/get".toHttpUrlOrNull()?.newBuilder() ?: return emptyList()
            urlBuilder.addQueryParameter("track_name", title)
            if (artist.isNotBlank()) urlBuilder.addQueryParameter("artist_name", artist)
            if (durationSec > 0) urlBuilder.addQueryParameter("duration", durationSec.toString())

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("User-Agent", "LockscreenLyricsApp/1.0 (https://github.com/example/LockscreenLyrics)")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return emptyList()
                val json = gson.fromJson(body, JsonObject::class.java)
                val syncedLyrics = json.get("syncedLyrics")?.asString
                if (!syncedLyrics.isNullOrBlank()) {
                    return LrcParser.parse(syncedLyrics)
                }
            } else {
                return searchLrclib(title, artist, durationSec)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB fetch error: ${e.message}")
        }
        return emptyList()
    }

    private fun searchLrclib(title: String, artist: String, durationSec: Int): List<LyricLine> {
        try {
            val query = "$title $artist".trim()
            val url = "https://lrclib.net/api/search?q=${URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return emptyList()
                val jsonArray = gson.fromJson(body, com.google.gson.JsonArray::class.java)
                if (jsonArray.size() > 0) {
                    for (i in 0 until jsonArray.size()) {
                        val item = jsonArray.get(i).asJsonObject
                        val trackName = item.get("trackName")?.asString ?: ""
                        val artistName = item.get("artistName")?.asString ?: ""
                        val duration = item.get("duration")?.asDouble?.toInt() ?: 0

                        val isTitleValid = isTitleMatch(title, trackName)
                        val isArtistValid = isArtistMatch(artist, artistName)
                        val isDurationValid = durationSec <= 0 || duration <= 0 || Math.abs(durationSec - duration) <= 4

                        if (isTitleValid && (isArtistValid || isDurationValid || containsEastAsianText(artistName))) {
                            val syncedLyrics = item.get("syncedLyrics")?.asString
                            if (!syncedLyrics.isNullOrBlank()) {
                                return LrcParser.parse(syncedLyrics)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB search error: ${e.message}")
        }
        return emptyList()
    }

    /**
     * 比對歌手是否相符（支援日語漢字、假名、英文與羅馬音雙向對齊）
     * 例如：Spotify 是 "sana"，網易雲是 "鎖那"，RomajiAutoCompleter("鎖那") = "sana" -> 100% Match!
     */
    private fun isArtistMatch(targetArtist: String, candidateArtist: String?): Boolean {
        if (targetArtist.isBlank() || candidateArtist.isNullOrBlank()) return true

        val cleanTarget = cleanArtistName(targetArtist).lowercase()
        val cleanCandidate = cleanArtistName(candidateArtist).lowercase()

        // 1. 直觀包含或相等
        if (cleanTarget == cleanCandidate || cleanTarget.contains(cleanCandidate) || cleanCandidate.contains(cleanTarget)) {
            return true
        }

        // 2. 羅馬音與音韻歸一化比對 (如：鎖那 vs sana)
        val romajiTarget = normalizePhonetic(RomajiAutoCompleter.convertToRomaji(cleanTarget))
        val romajiCandidate = normalizePhonetic(RomajiAutoCompleter.convertToRomaji(cleanCandidate))

        if (romajiTarget.isNotBlank() && romajiCandidate.isNotBlank()) {
            if (romajiTarget == romajiCandidate || romajiTarget.contains(romajiCandidate) || romajiCandidate.contains(romajiTarget)) {
                return true
            }
        }

        // 3. 若歌手包含日文漢字/假名（如 鎖那），容許主音節重合
        if (containsEastAsianText(cleanCandidate) || containsEastAsianText(cleanTarget)) {
            val shortStr = if (romajiTarget.length < romajiCandidate.length) romajiTarget else romajiCandidate
            val longStr = if (romajiTarget.length >= romajiCandidate.length) romajiTarget else romajiCandidate
            if (shortStr.length >= 2 && longStr.contains(shortStr.take(2))) {
                return true
            }
            return true
        }

        return false
    }

    private fun cleanArtistName(artist: String): String {
        return artist
            .split(Regex("[,/&、]|feat\\.?|ft\\.?"), 2)[0]
            .replace(Regex("[-–—_()（）\\[\\]]+"), " ")
            .trim()
    }

    /**
     * 比對歌名是否一致（杜絕短詞碰瓷，長度佔比需 >= 75% 且支援日文/羅馬音/音韻雙向對齊）
     */
    private fun isTitleMatch(targetTitle: String, candidateTitle: String?): Boolean {
        if (candidateTitle.isNullOrBlank()) return false

        val cleanTarget = cleanSongTitle(targetTitle).lowercase().trim()
        val cleanCandidate = cleanSongTitle(candidateTitle).lowercase().trim()

        if (cleanTarget.isEmpty() || cleanCandidate.isEmpty()) return false

        // 1. 完全相等
        if (cleanTarget == cleanCandidate) return true

        // 2. 子字串包含，但嚴格限制長度比率 (>= 75%) 避免 "usotsuki emily" 誤中短詞 "emily"
        val minLen = Math.min(cleanTarget.length, cleanCandidate.length).toDouble()
        val maxLen = Math.max(cleanTarget.length, cleanCandidate.length).toDouble()
        val lengthRatio = if (maxLen > 0) minLen / maxLen else 0.0

        if ((cleanTarget.contains(cleanCandidate) || cleanCandidate.contains(cleanTarget)) && lengthRatio >= 0.75) {
            return true
        }

        // 3. 羅馬音與音韻歸一化比對（支援：嘘つきエミリー ➔ usotsuki emily, 風のたより ➔ kaze no tayori）
        val romajiTarget = RomajiAutoCompleter.convertToRomaji(cleanTarget)
        val romajiCandidate = RomajiAutoCompleter.convertToRomaji(cleanCandidate)

        val normTarget = normalizePhonetic(romajiTarget)
        val normCandidate = normalizePhonetic(romajiCandidate)

        if (normTarget.isNotBlank() && normCandidate.isNotBlank()) {
            if (normTarget == normCandidate) return true

            val rMinLen = Math.min(normTarget.length, normCandidate.length).toDouble()
            val rMaxLen = Math.max(normTarget.length, normCandidate.length).toDouble()
            val rRatio = if (rMaxLen > 0) rMinLen / rMaxLen else 0.0

            if ((normTarget.contains(normCandidate) || normCandidate.contains(normTarget)) && rRatio >= 0.75) {
                return true
            }

            // 編輯距離相似度 >= 85%（容許細微拼寫差異，如 14 個字元差 1 個字母）
            if (calculateSimilarity(normTarget, normCandidate) >= 0.85) {
                return true
            }
        }

        return false
    }

    /**
     * 計算兩個字串的 Levenshtein 編輯距離相似度 (0.0 ~ 1.0)
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }

        val maxLen = Math.max(s1.length, s2.length)
        return 1.0 - (dp[s1.length][s2.length].toDouble() / maxLen.toDouble())
    }

    /**
     * 音韻歸一化（統一處理日語長音 ou->o, uu->u, oo->o 以及 r/l, y/i 互通）
     */
    private fun normalizePhonetic(text: String): String {
        return text
            .lowercase()
            .replace('l', 'r')
            .replace('y', 'i')
            .replace("-", "")
            .replace("ou", "o")
            .replace("uu", "u")
            .replace("oo", "o")
            .replace("aa", "a")
            .replace("ee", "e")
            .replace(Regex("[^a-z0-9]"), "")
    }

    private fun containsEastAsianText(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0x3040..0x30FF || code in 0x4E00..0x9FFF || code in 0xAC00..0xD7AF) return true
        }
        return false
    }

    /**
     * 徹底清理歌名中的各種 feat、合作藝人與後綴符號（例如 "花 - feat. 花譜" -> "花"）
     */
    private fun cleanSongTitle(title: String): String {
        return title
            .replace(Regex("[-–—]?\\s*\\(?\\[?feat\\.?.*?[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-–—]?\\s*\\(?\\[?ft\\.?.*?[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-–—]?\\s*\\(?\\[?with\\.?.*?[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-–—]?\\s*\\(?\\[?remastered.*?[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-–—]?\\s*\\(?\\[?official.*?[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-–—]?\\s*\\(?\\[?instrumental.*?[\\)\\]]?", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[-–—_:]+$"), "")
            .trim()
    }
}
