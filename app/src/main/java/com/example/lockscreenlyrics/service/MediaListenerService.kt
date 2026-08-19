package com.example.lockscreenlyrics.service

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.lockscreenlyrics.data.model.SongMetadata
import com.example.lockscreenlyrics.data.parser.LrcParser
import com.example.lockscreenlyrics.data.repository.LyricsRepository
import com.example.lockscreenlyrics.ui.lockscreen.LockscreenLyricActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MediaListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaSessionManager: MediaSessionManager? = null
    private var currentController: MediaController? = null
    private var syncJob: Job? = null
    private var lastFetchedSongKey = ""

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackInfo(state, currentController?.metadata)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updatePlaybackInfo(currentController?.playbackState, metadata)
        }

        override fun onSessionDestroyed() {
            super.onSessionDestroyed()
            findAndAttachActiveSession()
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleSessionsChanged(controllers)
    }

    // 監聽螢幕點亮與鎖屏事件
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                checkAndLaunchLockscreenLyric()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        com.example.lockscreenlyrics.data.settings.AppSettings.init(this)
        createNotificationChannel()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "NotificationListener connected")
        PlaybackStateHolder.setServiceActive(true)

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        registerReceiver(screenReceiver, filter)

        initMediaSessionManager()
        startPeriodicSync()
        updateForegroundNotification(PlaybackStateHolder.currentSong.value)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "NotificationListener disconnected")
        PlaybackStateHolder.setServiceActive(false)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        currentController?.unregisterCallback(mediaControllerCallback)
        syncJob?.cancel()
    }

    private fun initMediaSessionManager() {
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val componentName = ComponentName(this, MediaListenerService::class.java)
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            handleSessionsChanged(controllers)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for MediaSessionManager: ${e.message}")
        }
    }

    /**
     * 專注適配 Spotify：僅監聽與同步 Spotify (com.spotify.music) 的播放狀態，杜絕任何其他影音 App 干擾
     */
    private fun handleSessionsChanged(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            if (currentController != null) {
                currentController?.unregisterCallback(mediaControllerCallback)
                currentController = null
                PlaybackStateHolder.activeMediaController = null
                PlaybackStateHolder.updateSong(SongMetadata())
            }
            return
        }

        // 僅鎖定 Spotify
        val spotifyTarget = controllers.firstOrNull { it.packageName == "com.spotify.music" }

        if (spotifyTarget != null) {
            if (spotifyTarget != currentController) {
                currentController?.unregisterCallback(mediaControllerCallback)
                currentController = spotifyTarget
                currentController?.registerCallback(mediaControllerCallback)
                PlaybackStateHolder.activeMediaController = spotifyTarget
            }
            updatePlaybackInfo(spotifyTarget.playbackState, spotifyTarget.metadata)
        } else {
            // 當前沒有活躍的 Spotify 會話，重置狀態以避免其他 App 誤觸
            if (currentController != null) {
                currentController?.unregisterCallback(mediaControllerCallback)
                currentController = null
                PlaybackStateHolder.activeMediaController = null
                PlaybackStateHolder.updateSong(SongMetadata())
            }
        }
    }

    private fun findAndAttachActiveSession() {
        val componentName = ComponentName(this, MediaListenerService::class.java)
        try {
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            handleSessionsChanged(controllers)
        } catch (e: Exception) {
            Log.e(TAG, "findAndAttachActiveSession error: ${e.message}")
        }
    }

    private fun updatePlaybackInfo(state: PlaybackState?, metadata: MediaMetadata?) {
        val controller = currentController ?: return
        if (controller.packageName != "com.spotify.music") return
        if (metadata == null) return

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)

        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val position = state?.position ?: 0L
        val speed = state?.playbackSpeed ?: 1.0f
        val pkgName = currentController?.packageName ?: ""

        val song = SongMetadata(
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            albumArtBitmap = bitmap,
            albumArtUri = artUri,
            isPlaying = isPlaying,
            positionMs = position,
            lastUpdateTimestamp = SystemClock.elapsedRealtime(),
            playbackSpeed = speed,
            packageName = pkgName
        )

        PlaybackStateHolder.updateSong(song)
        updateForegroundNotification(song)

        // 若曲目改變，重新請求歌詞
        val songKey = "$title-$artist"
        if (songKey.isNotBlank() && songKey != lastFetchedSongKey) {
            lastFetchedSongKey = songKey
            fetchLyricsForSong(title, artist, (duration / 1000).toInt())
        }
    }

    private fun fetchLyricsForSong(title: String, artist: String, durationSec: Int) {
        serviceScope.launch {
            PlaybackStateHolder.setSearching(true)
            val result = LyricsRepository.getLyrics(title, artist, durationSec)
            PlaybackStateHolder.updateLyrics(result.lines, result.source)
            PlaybackStateHolder.setSearching(false)
        }
    }

    /**
     * 高精度時間同步輪詢 (每 100ms 一次，純本地時間插值，耗電極低)
     */
    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            while (isActive) {
                val song = PlaybackStateHolder.currentSong.value
                val lyrics = PlaybackStateHolder.lyrics.value

                if (song.isPlaying && lyrics.isNotEmpty()) {
                    val currentMs = song.getCurrentPositionMs()
                    val activeIndex = LrcParser.findActiveIndex(lyrics, currentMs)
                    PlaybackStateHolder.updateActiveIndex(activeIndex)
                }

                delay(50)
            }
        }
    }

    private fun checkAndLaunchLockscreenLyric() {
        if (!com.example.lockscreenlyrics.data.settings.AppSettings.isEnabled.value) return

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        val song = PlaybackStateHolder.currentSong.value
        val isSpotifyPlaying = song.isPlaying && song.packageName == "com.spotify.music"

        // 當螢幕鎖定且正在播放 Spotify 音樂時，自動喚起鎖屏歌詞 Activity
        if (isLocked && isSpotifyPlaying) {
            val intent = Intent(this, LockscreenLyricActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    /**
     * 在鎖屏顯示常駐通知，點擊直接打開歌詞介面
     */
    private fun updateForegroundNotification(song: SongMetadata) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        if (!com.example.lockscreenlyrics.data.settings.AppSettings.isEnabled.value) {
            notificationManager?.cancel(NOTIFICATION_ID)
            return
        }

        val notificationIntent = Intent(this, LockscreenLyricActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (song.title.isNotBlank()) song.title else "Lyric Flow"
        val text = if (song.artist.isNotBlank()) "${song.artist} • 點擊展開動態歌詞" else "Spotify 專屬鎖屏動態歌詞已就緒"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .build()

        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "鎖屏歌詞控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "在鎖屏與通知欄提供快速打開動態歌詞的捷徑"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MediaListenerService"
        private const val CHANNEL_ID = "lockscreen_lyrics_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
