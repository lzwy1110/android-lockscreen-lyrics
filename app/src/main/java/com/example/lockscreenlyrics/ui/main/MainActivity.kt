package com.example.lockscreenlyrics.ui.main

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.ui.res.painterResource
import com.example.lockscreenlyrics.R
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lockscreenlyrics.data.settings.AppSettings
import com.example.lockscreenlyrics.service.MediaListenerService
import com.example.lockscreenlyrics.service.PlaybackStateHolder
import com.example.lockscreenlyrics.ui.lockscreen.LockscreenLyricActivity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121218)
                ) {
                    MainScreen(
                        onOpenNotificationSettings = { openNotificationListenerSettings() },
                        onOpenOverlaySettings = { openOverlaySettings() },
                        onPreviewLockscreen = { previewLockscreen() }
                    )
                }
            }
        }
    }

    private fun openNotificationListenerSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun previewLockscreen() {
        val intent = Intent(this, LockscreenLyricActivity::class.java)
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenNotificationSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onPreviewLockscreen: () -> Unit
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }

    val isEnabled by AppSettings.isEnabled.collectAsState()
    val showTranslation by AppSettings.showTranslation.collectAsState()
    val clockSizeSp by AppSettings.clockSizeSp.collectAsState()
    val lyricSizeSp by AppSettings.lyricSizeSp.collectAsState()
    val bgDimPercent by AppSettings.bgDimPercent.collectAsState()

    val isServiceActive by PlaybackStateHolder.isServiceActive.collectAsState()
    val song by PlaybackStateHolder.currentSong.collectAsState()
    val lyrics by PlaybackStateHolder.lyrics.collectAsState()

    fun checkPermissions() {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val cn = ComponentName(context, MediaListenerService::class.java)
        hasNotificationPermission = flat != null && flat.contains(cn.flattenToString())
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    LaunchedEffect(Unit) {
        checkPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Lyric Flow",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF181822))
            )
        },
        containerColor = Color(0xFF121218)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 總開關卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled) Color(0xFF182E20) else Color(0xFF22222E)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (isEnabled) Color(0xFF1DB954) else Color(0x66FFFFFF),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("鎖定畫面歌詞功能", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEnabled) "已啟用 (鎖屏點亮時自動同步)" else "已關閉 (不影響原生鎖屏)",
                                color = if (isEnabled) Color(0xFFB3E5C1) else Color(0x99FFFFFF),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { AppSettings.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF1DB954)
                        )
                    )
                }
            }

            // 2. 自訂視覺與偏好設定卡片
            Text("自訂外觀與偏好", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 首選歌詞來源選擇器
                    val preferredSource by AppSettings.preferredSource.collectAsState()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Cloud, contentDescription = null, tint = Color(0xFF8EB5FF), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("首選歌詞來源優先級", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("決定播放新歌時預設第一優先檢索的平台", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 網易雲音樂選項
                            val isNeteaseSelected = preferredSource == AppSettings.SOURCE_NETEASE
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isNeteaseSelected) Color(0xFF8EB5FF) else Color(0xFF14141E))
                                    .clickable { AppSettings.setPreferredSource(AppSettings.SOURCE_NETEASE) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cloud,
                                        contentDescription = null,
                                        tint = if (isNeteaseSelected) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "網易雲音樂",
                                        color = if (isNeteaseSelected) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isNeteaseSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            // QQ 音樂選項
                            val isQQSelected = preferredSource == AppSettings.SOURCE_QQ
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isQQSelected) Color(0xFF1DB954) else Color(0xFF14141E))
                                    .clickable { AppSettings.setPreferredSource(AppSettings.SOURCE_QQ) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_qq_penguin),
                                        contentDescription = null,
                                        tint = if (isQQSelected) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "QQ 音樂",
                                        color = if (isQQSelected) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isQQSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // 雙語翻譯開關
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Translate, contentDescription = null, tint = Color(0xFF8EB5FF), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("雙語翻譯歌詞", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("自動匹配高質量雙語翻譯歌詞", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = showTranslation,
                            onCheckedChange = { AppSettings.setShowTranslation(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF8EB5FF))
                        )
                    }

                    // 繁體中文轉換開關
                    val convertTraditional by AppSettings.convertTraditional.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Palette, contentDescription = null, tint = Color(0xFF1DB954), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("自動轉為繁體中文 (正體字)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("將歌詞與翻譯即時轉換為繁體中文", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = convertTraditional,
                            onCheckedChange = { AppSettings.setConvertTraditional(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF1DB954))
                        )
                    }

                    // 羅馬拼音開關
                    val showRomaji by AppSettings.showRomaji.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Translate, contentDescription = null, tint = Color(0xFFFFB74D), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("顯示羅馬拼音 (Romaji)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("為日語 / 韓語歌曲提供拼音標註", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = showRomaji,
                            onCheckedChange = { AppSettings.setShowRomaji(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFFB74D))
                        )
                    }

                    // 頂部時鐘顯示開關
                    val showClock by AppSettings.showClock.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ScreenLockPortrait, contentDescription = null, tint = Color(0xFF8EB5FF), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("顯示頂部時鐘", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("開啟後於鎖定畫面頂部顯示質感大時鐘", color = Color(0x99FFFFFF), fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = showClock,
                            onCheckedChange = { AppSettings.setShowClock(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF8EB5FF))
                        )
                    }

                    // 獨立色彩自訂區域
                    val themeColorHex by AppSettings.themeColorHex.collectAsState()
                    val originalColorHex by AppSettings.originalColorHex.collectAsState()
                    val romajiColorHex by AppSettings.romajiColorHex.collectAsState()
                    val translationColorHex by AppSettings.translationColorHex.collectAsState()

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = Color(0xFF8EB5FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("自訂文字與介面顏色 (可直接輸入色號)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        // 1. 原文歌詞顏色
                        ColorPickerRow(
                            label = "原文歌詞顏色",
                            currentColorHex = originalColorHex,
                            onColorSelected = { AppSettings.setOriginalColorHex(it) }
                        )

                        // 2. 羅馬拼音顏色
                        ColorPickerRow(
                            label = "羅馬拼音顏色",
                            currentColorHex = romajiColorHex,
                            onColorSelected = { AppSettings.setRomajiColorHex(it) }
                        )

                        // 3. 雙語翻譯顏色
                        ColorPickerRow(
                            label = "雙語翻譯顏色",
                            currentColorHex = translationColorHex,
                            onColorSelected = { AppSettings.setTranslationColorHex(it) }
                        )

                        // 4. 時鐘與主題顏色
                        ColorPickerRow(
                            label = "時鐘與進度條主題色",
                            currentColorHex = themeColorHex,
                            onColorSelected = { AppSettings.setThemeColorHex(it) }
                        )
                    }

                    // 時鐘大小滑桿（僅在開啟時鐘時顯示）
                    if (showClock) {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("時鐘字體大小", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                                Text("${clockSizeSp} sp", color = Color(android.graphics.Color.parseColor(themeColorHex)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = clockSizeSp.toFloat(),
                                onValueChange = { AppSettings.setClockSize(it.toInt()) },
                                valueRange = 50f..95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(android.graphics.Color.parseColor(themeColorHex)),
                                    activeTrackColor = Color(android.graphics.Color.parseColor(themeColorHex))
                                )
                            )
                        }
                    }

                    // 歌詞大小滑桿
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("當前歌詞字體大小", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                            Text("${lyricSizeSp} sp", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = lyricSizeSp.toFloat(),
                            onValueChange = { AppSettings.setLyricSize(it.toInt()) },
                            valueRange = 22f..36f,
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
                        )
                    }

                    // 背景毛玻璃暗度
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("背景遮罩暗度 (毛玻璃深度)", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                            Text("${bgDimPercent}%", color = Color(0xFFFFB74D), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = bgDimPercent.toFloat(),
                            onValueChange = { AppSettings.setBgDim(it.toInt()) },
                            valueRange = 20f..90f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFFB74D), activeTrackColor = Color(0xFFFFB74D))
                        )
                    }
                }
            }

            // 3. 權限設定卡片
            Text("必要權限設定", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

            PermissionCard(
                title = "1. 音樂與通知監聽權限",
                description = "用於即時取得 Spotify 播放曲目與進度",
                isGranted = hasNotificationPermission,
                onGrantClick = onOpenNotificationSettings
            )

            PermissionCard(
                title = "2. 懸浮窗與鎖屏顯示權限",
                description = "用於在鎖定畫面上呈現動態歌詞卡片",
                isGranted = hasOverlayPermission,
                onGrantClick = onOpenOverlaySettings
            )

            // 4. 即時狀態卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = if (isServiceActive) Color(0xFF1DB954) else Color(0xFFFFB74D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServiceActive) "監聽服務已連線 (Spotify 即時同步中)" else "監聽服務待命 (請確認已授權)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "當前歌曲: ${if (song.title.isNotBlank()) "${song.title} - ${song.artist}" else "目前無播放"}",
                        color = Color(0xCCFFFFFF),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "歌詞狀態: ${if (lyrics.isNotEmpty()) "已載入 ${lyrics.size} 行雙語歌詞" else "暫無動態歌詞"}",
                        color = Color(0x99FFFFFF),
                        fontSize = 12.sp
                    )
                }
            }

            // 5. 測試與預覽按鈕
            Button(
                onClick = onPreviewLockscreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
            ) {
                Icon(imageVector = Icons.Rounded.ScreenLockPortrait, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("立即預覽鎖屏歌詞介面", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF1DB954) else Color(0xFFFFB74D),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, color = Color(0x99FFFFFF), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isGranted) {
                Text("已啟用", color = Color(0xFF1DB954), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } else {
                OutlinedButton(
                    onClick = onGrantClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("前往授權", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    currentColorHex: String,
    onColorSelected: (String) -> Unit
) {
    var hexInput by remember(currentColorHex) { mutableStateOf(currentColorHex) }
    val presetColors = listOf(
        "#FFFFFF" to "極光白",
        "#8EB5FF" to "晴空藍",
        "#1DB954" to "Spotify 綠",
        "#FF8DA1" to "櫻花粉"
    )
    val parsedColor = remember(currentColorHex) {
        try {
            Color(android.graphics.Color.parseColor(currentColorHex))
        } catch (_: Exception) {
            Color.White
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(parsedColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = Color(0xCCFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 4 色票
            for ((hex, _) in presetColors) {
                val isSelected = hex.equals(currentColorHex, ignoreCase = true)
                val pColor = Color(android.graphics.Color.parseColor(hex))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(pColor)
                        .clickable {
                            hexInput = hex
                            onColorSelected(hex)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (hex == "#FFFFFF") Color.Black else Color.White)
                        )
                    }
                }
            }

            // 直接輸入色號（Hex）
            OutlinedTextField(
                value = hexInput,
                onValueChange = { input ->
                    val formatted = if (input.startsWith("#")) input else "#$input"
                    hexInput = input
                    if (Regex("^#[0-9a-fA-F]{6}$").matches(formatted)) {
                        onColorSelected(formatted.uppercase())
                    }
                },
                modifier = Modifier.weight(1f),
                label = { Text("色號 (例如 #FFFFFF)", fontSize = 11.sp) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF14141E),
                    unfocusedContainerColor = Color(0xFF14141E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = parsedColor,
                    unfocusedLabelColor = Color(0x88FFFFFF),
                    focusedIndicatorColor = parsedColor,
                    unfocusedIndicatorColor = Color(0x33FFFFFF)
                ),
                shape = RoundedCornerShape(10.dp)
            )
        }
    }
}
