package com.example.lockscreenlyrics.ui.lockscreen

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.lockscreenlyrics.data.settings.AppSettings

class LockscreenLyricActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)

        // 1. 設定在鎖定畫面上顯示與喚醒螢幕
        setupLockscreenWindow()

        // 2. 強制解除三星 One UI 鎖屏 24Hz/30Hz 限制，解鎖滿血 120Hz 高刷新率
        enforceHighRefreshRate()

        // 3. 邊緣到邊緣全透明繪製
        enableEdgeToEdge()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        // 4. Android 12+ (API 31+) 原生視窗背景高斯毛玻璃模糊
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 40
        }

        // 5. 強制顯示原生系統狀態列（電量、Wi-Fi、5G 訊號與通知圖示）
        showStatusBar()

        // 6. 設定 Compose 內容
        setContent {
            LockscreenLyricScreen(
                onDismiss = {
                    finish()
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        enforceHighRefreshRate()
        showStatusBar()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        enforceHighRefreshRate()
        showStatusBar()
    }

    /**
     * 強制向三星硬體層請求 120Hz 滿血刷新率（突破鎖定畫面 24/30Hz 節能限制）
     */
    private fun enforceHighRefreshRate() {
        try {
            val lp = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val currentDisplay = display
                val maxRefreshMode = currentDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null) {
                    lp.preferredDisplayModeId = maxRefreshMode.modeId
                    lp.preferredRefreshRate = maxRefreshMode.refreshRate
                }
                window.attributes = lp
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = windowManager.defaultDisplay
                val maxMode = display?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    lp.preferredDisplayModeId = maxMode.modeId
                    window.attributes = lp
                }
            }
        } catch (_: Exception) {}
    }

    private fun showStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    private fun setupLockscreenWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
