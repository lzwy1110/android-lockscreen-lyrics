package com.example.lockscreenlyrics.data.repository

import android.util.Base64
import android.util.Log
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
import java.util.concurrent.TimeUnit

data class LyricSearchResult(
    val lines: List<LyricLine> = emptyList(),
    val source: String = ""
)

object LyricsRepository {
    private const val TAG = "LyricsRepository"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val memoryCache = mutableMapOf<String, LyricSearchResult>()

    /**
     * 智慧獲取動態歌詞（優先選擇「具有雙語翻譯」的來源：QQ 音樂 ⇄ 網易雲 雙向智慧兜底）
     */
    suspend fun getLyrics(title: String, artist: String, durationSec: Int = 0): LyricSearchResult = withContext(Dispatchers.IO) {
        val cleanTitle = cleanSongTitle(title)
        val cleanArtist = artist.trim()
        val cacheKey = "$cleanTitle-$cleanArtist".lowercase()

        // 1. 檢查記憶體快取
        memoryCache[cacheKey]?.let { return@withContext it }

        // 2. 🥇 第一優先：嘗試 QQ 音樂（使用 Lyricify 同款現代 musicu.fcg 端點）
        val qqLyrics = fetchFromQQMusic(cleanTitle, cleanArtist)
        val hasQQTranslation = qqLyrics.any { !it.translation.isNullOrBlank() }

        if (qqLyrics.isNotEmpty() && hasQQTranslation) {
            Log.d(TAG, "成功從 QQ 音樂取得雙語歌詞: $cleanTitle (${qqLyrics.size} 行，含翻譯)")
            val result = wrapResult(qqLyrics, "QQ 音樂")
            memoryCache[cacheKey] = result
            return@withContext result
        }

        // 3. 🥈 第二優先：嘗試 網易雲音樂
        val neteaseLyrics = fetchFromNetease(cleanTitle, cleanArtist)
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
        val completedLines = com.example.lockscreenlyrics.data.converter.RomajiAutoCompleter.completeIfMissing(lines)
        return LyricSearchResult(completedLines, source)
    }

    /**
     * 從 QQ 音樂抓取同步歌詞與雙語翻譯（採用 Lyricify 同款 modern musicu.fcg API）
     */
    private fun fetchFromQQMusic(title: String, artist: String): List<LyricLine> {
        try {
            val primaryArtist = artist.split(Regex("[,/&、]|feat\\.?"), 2)[0].trim()

            // 1. 搜尋 songmid
            var songMid = searchQQSongMid("$title $artist".trim())
            if (songMid.isNullOrBlank() && primaryArtist.isNotBlank()) {
                songMid = searchQQSongMid("$title $primaryArtist".trim())
            }
            if (songMid.isNullOrBlank()) {
                songMid = searchQQSongMid(title)
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
     * 搜尋 QQ 音樂 songmid
     */
    private fun searchQQSongMid(query: String): String? {
        try {
            val searchUrl = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=1&w=${URLEncoder.encode(query, "UTF-8")}&format=json"
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
                    return songList.get(0).asJsonObject.get("songmid")?.asString
                }
            }
        } catch (_: Exception) {}
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
    private fun fetchFromNetease(title: String, artist: String): List<LyricLine> {
        try {
            val primaryArtist = artist.split(Regex("[,/&、]|feat\\.?"), 2)[0].trim()
            var songId = searchNeteaseSongId("$title $artist".trim())
            if (songId == null && primaryArtist.isNotBlank()) {
                songId = searchNeteaseSongId("$title $primaryArtist".trim())
            }
            if (songId == null) {
                songId = searchNeteaseSongId(title)
            }

            if (songId != null) {
                return fetchNeteaseLyricById(songId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Netease fetch error: ${e.message}")
        }
        return emptyList()
    }

    private fun searchNeteaseSongId(query: String): Long? {
        try {
            val searchUrl = "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&total=true&limit=1"
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
                    return songs.get(0).asJsonObject.get("id")?.asLong
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
                return searchLrclib(title, artist)
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB fetch error: ${e.message}")
        }
        return emptyList()
    }

    private fun searchLrclib(title: String, artist: String): List<LyricLine> {
        try {
            val query = "$title $artist".trim()
            val url = "https://lrclib.net/api/search?q=${URLEncoder.encode(query, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return emptyList()
                val jsonArray = gson.fromJson(body, com.google.gson.JsonArray::class.java)
                if (jsonArray.size() > 0) {
                    val firstItem = jsonArray.get(0).asJsonObject
                    val syncedLyrics = firstItem.get("syncedLyrics")?.asString
                    if (!syncedLyrics.isNullOrBlank()) {
                        return LrcParser.parse(syncedLyrics)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "LRCLIB search error: ${e.message}")
        }
        return emptyList()
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
