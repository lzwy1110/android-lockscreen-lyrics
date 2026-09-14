package com.example.lockscreenlyrics.ui.lockscreen

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.lockscreenlyrics.R
import com.example.lockscreenlyrics.data.model.LyricLine
import com.example.lockscreenlyrics.data.settings.AppSettings
import com.example.lockscreenlyrics.service.PlaybackStateHolder
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun LockscreenLyricScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song by PlaybackStateHolder.currentSong.collectAsState()
    val lyrics by PlaybackStateHolder.lyrics.collectAsState()
    val activeIndex by PlaybackStateHolder.activeLyricIndex.collectAsState()
    val isSearching by PlaybackStateHolder.isSearching.collectAsState()
    val lyricSource by PlaybackStateHolder.lyricSource.collectAsState()

    // 讀取使用者自訂偏好
    val showTranslation by AppSettings.showTranslation.collectAsState()
    val showRomaji by AppSettings.showRomaji.collectAsState()
    val showClock by AppSettings.showClock.collectAsState()
    val themeColorHex by AppSettings.themeColorHex.collectAsState()
    val originalColorHex by AppSettings.originalColorHex.collectAsState()
    val romajiColorHex by AppSettings.romajiColorHex.collectAsState()
    val translationColorHex by AppSettings.translationColorHex.collectAsState()
    val convertTraditional by AppSettings.convertTraditional.collectAsState()
    val clockSizeSp by AppSettings.clockSizeSp.collectAsState()
    val lyricSizeSp by AppSettings.lyricSizeSp.collectAsState()
    val bgDimPercent by AppSettings.bgDimPercent.collectAsState()

    val accentColor = remember(themeColorHex) {
        try {
            Color(android.graphics.Color.parseColor(themeColorHex))
        } catch (_: Exception) {
            Color(0xFF8EB5FF)
        }
    }

    val originalColor = remember(originalColorHex) {
        try { Color(android.graphics.Color.parseColor(originalColorHex)) } catch (_: Exception) { Color.White }
    }
    val romajiColor = remember(romajiColorHex) {
        try { Color(android.graphics.Color.parseColor(romajiColorHex)) } catch (_: Exception) { Color.White }
    }
    val translationColor = remember(translationColorHex) {
        try { Color(android.graphics.Color.parseColor(translationColorHex)) } catch (_: Exception) { Color.White }
    }

    var showLyricsMode by remember { mutableStateOf(true) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val listState = rememberLazyListState()

    // 歌詞自動平滑滾動至「歌詞外框正中心」（Box Center 像素級精確置中）
    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && activeIndex in lyrics.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = 0
            )
        }
    }

    val dimAlpha = (bgDimPercent / 100f).coerceIn(0.2f, 0.95f)

    Box(
        modifier = modifier
            .fillMaxSize()
            // 監聽向上滑動手勢以關閉介面 / 解鎖
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY < -150f) {
                            onDismiss()
                        }
                        dragOffsetY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount
                    }
                )
            }
    ) {
        // 1. 🌈 動態流光毛玻璃背景 (Fluid Ambient Mesh)
        AmbientFluidBackground(
            bitmap = song.albumArtBitmap,
            accentColor = accentColor,
            dimAlpha = dimAlpha
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 頂部自訂大時鐘區（依設定決定是否顯示與自訂色彩）
            if (showClock) {
                Spacer(modifier = Modifier.height(16.dp))
                TopSamsungStyleClock(clockSize = clockSizeSp, accentColor = accentColor)
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2. 中間精簡聚焦歌詞區（上下漸層遮罩，只留 1-2 行前後歌詞）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { showLyricsMode = !showLyricsMode },
                contentAlignment = Alignment.Center
            ) {
                if (showLyricsMode) {
                    if (isSearching) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("正在搜尋動態雙語歌詞…", color = Color(0xB3FFFFFF), fontSize = 14.sp)
                        }
                    } else if (lyrics.isEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = Color(0x66FFFFFF),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("未找到即時動態歌詞\n(點擊切換專輯封面)", color = Color(0x99FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center)
                        }
                    } else {
                        // 120Hz 絲滑動態歌詞捲軸（聚焦點模式，動態計算外框高度確保首尾均能置中）
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val containerHalfHeight = maxHeight / 2
                            val verticalPadding = (containerHalfHeight - 36.dp).coerceAtLeast(0.dp)

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                itemsIndexed(
                                    items = lyrics,
                                    key = { index, line -> "${line.timeMs}_$index" }
                                ) { index, line ->
                                    LyricRowItem(
                                        line = line,
                                        distanceFromActive = Math.abs(index - activeIndex),
                                        baseFontSize = lyricSizeSp,
                                        showTranslation = showTranslation,
                                        showRomaji = showRomaji,
                                        convertTraditional = convertTraditional,
                                        originalColor = originalColor,
                                        romajiColor = romajiColor,
                                        translationColor = translationColor,
                                        accentColor = accentColor,
                                        onLineClick = {
                                            PlaybackStateHolder.seekTo(line.timeMs)
                                        }
                                    )
                                }
                            }

                            // 上下邊緣淡出羽化遮罩（Fade Mask）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .align(Alignment.TopCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = dimAlpha * 0.9f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .align(Alignment.BottomCenter)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = dimAlpha * 0.9f)
                                            )
                                        )
                                    )
                            )

                            // 右下角小字標註歌詞來源（如：歌詞來源：QQ 音樂 / 網易雲音樂）
                            if (lyricSource.isNotBlank()) {
                                Text(
                                    text = "歌詞來源：$lyricSource",
                                    color = Color(0x73FFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 12.dp, bottom = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    // 大專輯封面模式
                    AlbumCoverView(bitmap = song.albumArtBitmap, artUri = song.albumArtUri)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. 底部仿 One UI 7 媒體控制器卡片
            MediaControlCard(
                song = song,
                showLyricsMode = showLyricsMode,
                accentColor = accentColor,
                onToggleLyrics = { showLyricsMode = !showLyricsMode }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 底部上滑解鎖提示條
            Text(
                text = "向上滑動關閉 / 進行解鎖",
                color = Color(0x66FFFFFF),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}

/**
 * 🌈 動態呼吸流光毛玻璃背景 (Fluid Ambient Mesh)
 */
@Composable
private fun AmbientFluidBackground(
    bitmap: Bitmap?,
    accentColor: Color,
    dimAlpha: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )

    val dynamicPrimary = remember(bitmap, accentColor) {
        extractDominantColor(bitmap) ?: accentColor
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. 全螢幕無邊界柔和流光 (使用全屏 drawRect，絕無圓形裁切硬邊)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val rad = Math.toRadians(phase1.toDouble())
            val offsetX = (Math.cos(rad) * w * 0.15f).toFloat()
            val offsetY = (Math.sin(rad) * h * 0.10f).toFloat()
            val maxDim = maxOf(w, h)

            // 全螢幕矩形填滿，漸層半徑延伸至螢幕外，平滑淡出至透明
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        dynamicPrimary.copy(alpha = 0.22f),
                        dynamicPrimary.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.5f + offsetX, h * 0.35f + offsetY),
                    radius = maxDim * 1.3f
                )
            )
        }

        // 2. 疊加暗色毛玻璃漸層遮罩 (依使用者 bgDimPercent 控制深度)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = dimAlpha * 0.35f),
                            Color.Black.copy(alpha = dimAlpha * 0.70f),
                            Color.Black.copy(alpha = dimAlpha)
                        )
                    )
                )
        )
    }
}

private fun extractDominantColor(bitmap: Bitmap?): Color? {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
    return try {
        val sampleX = (bitmap.width * 0.5f).toInt().coerceIn(0, bitmap.width - 1)
        val sampleY = (bitmap.height * 0.5f).toInt().coerceIn(0, bitmap.height - 1)
        val pixel = bitmap.getPixel(sampleX, sampleY)
        Color(pixel)
    } catch (_: Exception) {
        null
    }
}

/**
 * 鎖定畫面超大時鐘與日期（支援自訂主題色彩）
 */
@Composable
private fun TopSamsungStyleClock(clockSize: Int, accentColor: Color) {
    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("M月d日, E", Locale.getDefault()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            currentTimeStr = timeFormat.format(now)
            currentDateStr = dateFormat.format(now)
            delay(1000)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // 日期文字（套用自訂色彩）
        Text(
            text = currentDateStr,
            color = accentColor,
            fontSize = (clockSize * 0.22f).coerceIn(14f, 20f).sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 超大數字時鐘（套用自訂色彩）
        Text(
            text = currentTimeStr,
            color = accentColor,
            fontSize = clockSize.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            lineHeight = (clockSize + 2).sp
        )
    }
}

@Composable
private fun LyricRowItem(
    line: LyricLine,
    distanceFromActive: Int,
    baseFontSize: Int,
    showTranslation: Boolean,
    showRomaji: Boolean,
    convertTraditional: Boolean,
    originalColor: Color,
    romajiColor: Color,
    translationColor: Color,
    accentColor: Color,
    onLineClick: () -> Unit
) {
    val isActive = (distanceFromActive == 0)

    // 1. Apple 級「距離感知階梯縮放」
    val targetScale = when (distanceFromActive) {
        0 -> 1.10f     // 當前主唱句：放大至 110%
        1 -> 0.93f     // 上下相鄰句：93%
        2 -> 0.84f     // 距離 2 行：84%
        else -> 0.76f  // 較遠句：76%
    }

    // 2. 距離感知階梯透明度（創造景深層次感）
    val targetAlpha = when (distanceFromActive) {
        0 -> 1.0f      // 100% 清晰聚焦
        1 -> 0.55f     // 55% 柔和過渡
        2 -> 0.28f     // 28% 背景弱化
        else -> 0.14f  // 14% 邊緣淡出
    }

    // 3. 物理彈簧阻尼過渡（具有自然動能與慣性質感）
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.80f,
            stiffness = 220f
        ),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 240f
        ),
        label = "alpha"
    )

    val displayText = if (convertTraditional) com.example.lockscreenlyrics.data.converter.ChineseConverter.toTraditional(line.text) else line.text
    val displayTrans = if (convertTraditional) com.example.lockscreenlyrics.data.converter.ChineseConverter.toTraditional(line.translation) else line.translation

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clickable { onLineClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 原文歌詞（✨ 主唱句自帶柔和發光質感與高光微陰影）
        Text(
            text = displayText,
            color = originalColor,
            fontSize = baseFontSize.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = (baseFontSize * 1.3f).sp,
            style = if (isActive) {
                LocalTextStyle.current.copy(
                    shadow = Shadow(
                        color = accentColor.copy(alpha = 0.45f),
                        blurRadius = 16f
                    )
                )
            } else {
                LocalTextStyle.current
            },
            modifier = Modifier.fillMaxWidth()
        )

        // 2. 羅馬拼音標註 (Romaji)
        if (showRomaji && !line.romaji.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = line.romaji,
                color = if (isActive) romajiColor else romajiColor.copy(alpha = 0.65f),
                fontSize = (baseFontSize * 0.46f).sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                lineHeight = (baseFontSize * 0.6f).sp,
                letterSpacing = 0.5.sp
            )
        }

        // 3. 雙語翻譯歌詞（支援繁體中文）
        if (showTranslation && !displayTrans.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = displayTrans,
                color = if (isActive) translationColor else translationColor.copy(alpha = 0.70f),
                fontSize = (baseFontSize * 0.58f).sp,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                lineHeight = (baseFontSize * 0.8f).sp
            )
        }
    }
}

@Composable
private fun AlbumCoverView(bitmap: Bitmap?, artUri: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val coverShape = RoundedCornerShape(20.dp)
        val imageModifier = Modifier
            .aspectRatio(1f)
            .shadow(elevation = 16.dp, shape = coverShape, spotColor = Color.Black)
            .clip(coverShape)

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Album Cover",
                modifier = imageModifier,
                contentScale = ContentScale.Fit
            )
        } else if (!artUri.isNullOrBlank()) {
            AsyncImage(
                model = artUri,
                contentDescription = "Album Cover",
                modifier = imageModifier,
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(coverShape)
                    .background(Color(0x26FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(80.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaControlCard(
    song: com.example.lockscreenlyrics.data.model.SongMetadata,
    showLyricsMode: Boolean,
    accentColor: Color,
    onToggleLyrics: () -> Unit
) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    var currentPosition by remember { mutableLongStateOf(0L) }

    LaunchedEffect(song.isPlaying, song.positionMs, song.lastUpdateTimestamp) {
        while (song.isPlaying) {
            currentPosition = song.getCurrentPositionMs()
            delay(400)
        }
        if (!song.isPlaying) {
            currentPosition = song.positionMs
        }
    }

    val displayPosition = if (isSeeking) {
        (seekProgress * song.durationMs).toLong()
    } else {
        currentPosition
    }

    val progressFraction = if (song.durationMs > 0) {
        (displayPosition.toFloat() / song.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 🪟 One UI 7 透光毛玻璃邊框與擬真質感
    val cardShape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0x551E1E28))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = cardShape
            )
            .padding(16.dp)
    ) {
        Column {
            // 歌名與藝人（支援繁體中文自動轉換）
            val convertTraditional by AppSettings.convertTraditional.collectAsState()
            val displayTitle = if (convertTraditional) com.example.lockscreenlyrics.data.converter.ChineseConverter.toTraditional(song.title) else song.title
            val displayArtist = if (convertTraditional) com.example.lockscreenlyrics.data.converter.ChineseConverter.toTraditional(song.artist) else song.artist

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (displayTitle.isNotBlank()) displayTitle else "未在播放音樂",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (displayArtist.isNotBlank()) displayArtist else "開啟 Spotify 播放即可自動同步",
                        color = Color(0xB3FFFFFF),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val lyricSource by PlaybackStateHolder.lyricSource.collectAsState()
                    val isSearching by PlaybackStateHolder.isSearching.collectAsState()
                    val isQQ = lyricSource.contains("QQ")

                    // 360° 彈簧旋轉切換動效
                    val iconRotation by animateFloatAsState(
                        targetValue = if (isQQ) 360f else 0f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
                        label = "iconRot"
                    )

                    // 1. 歌詞數據源切換按鈕 (網易雲 ☁️ ⇄ QQ 音樂 🐧)
                    IconButton(
                        onClick = { PlaybackStateHolder.switchLyricSource(context) },
                        modifier = Modifier
                            .size(36.dp)
                            .graphicsLayer { rotationZ = iconRotation },
                        enabled = !isSearching && song.title.isNotBlank()
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = accentColor,
                                strokeWidth = 2.dp
                            )
                        } else if (isQQ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_qq_penguin),
                                contentDescription = "目前為 QQ 音樂，點擊切換為網易雲",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Cloud,
                                contentDescription = "目前為網易雲音樂，點擊切換為 QQ 音樂",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    // 2. 歌詞 / 大封面檢視切換按鈕
                    IconButton(
                        onClick = onToggleLyrics,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (showLyricsMode) Icons.Rounded.Album else Icons.Rounded.QueueMusic,
                            contentDescription = "切換顯示",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 〰️ Android 15 靈動波浪進度條 (Squiggly Waveform Progress Bar)
            SquigglyProgressBar(
                progress = if (isSeeking) seekProgress else progressFraction,
                isPlaying = song.isPlaying,
                accentColor = accentColor,
                onSeek = { seekFraction ->
                    PlaybackStateHolder.seekTo((seekFraction * song.durationMs).toLong())
                }
            )

            // 時間標籤
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(displayPosition),
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp
                )
                Text(
                    text = formatTime(song.durationMs),
                    color = Color(0x99FFFFFF),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 控制按鈕：上一首、播放/暫停、下一首
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { PlaybackStateHolder.skipToPrevious() },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { PlaybackStateHolder.playPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (song.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "播放/暫停",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(28.dp))

                IconButton(
                    onClick = { PlaybackStateHolder.skipToNext() },
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * 〰️ Android 15 靈動波浪進度條 (Squiggly Waveform Progress Bar)
 */
@Composable
private fun SquigglyProgressBar(
    progress: Float,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val waveAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) 3.5f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "amp"
    )

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val currentProgress = if (isDragging) dragProgress else progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek(dragProgress)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val progressX = (width * currentProgress).coerceIn(0f, width)

            // 1. 已播放波浪軌道 (Squiggly Wave Track)
            if (progressX > 0f) {
                val wavePath = Path()
                wavePath.moveTo(0f, centerY)

                val wavelength = 36f
                var x = 0f
                val step = 2f
                while (x <= progressX) {
                    val angle = (x / wavelength) * (2 * PI) - phase
                    val y = centerY + (sin(angle) * waveAmplitude).toFloat()
                    wavePath.lineTo(x, y)
                    x += step
                }

                drawPath(
                    path = wavePath,
                    color = accentColor,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // 2. 未播放直線背景軌道 (Inactive Flat Track)
            if (progressX < width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.20f),
                    start = Offset(progressX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 3. 圓形進度按鈕 (Thumb)
            drawCircle(
                color = Color.White,
                radius = 5.5.dp.toPx(),
                center = Offset(progressX, centerY)
            )
            drawCircle(
                color = accentColor,
                radius = 3.5.dp.toPx(),
                center = Offset(progressX, centerY)
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%02d:%02d", min, sec)
}
