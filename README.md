# 🎵 Lyric Flow
做完發現其實這個功能蠻雞肋的，如果沒有鎖屏需求推薦Lyricify，做得好很多，電腦版也很好用，推薦!!

> **專為 Spotify 打造的 Android 鎖定畫面即時動態雙語歌詞**
Real-time dynamic bilingual lock screen lyrics, built specifically for Spotify on Android.

---

## ✨ 核心特色

* 🔤 **三軌歌詞垂直並進**：
  1. 原文歌詞（Original Text）
  2. 日語/韓語自動羅馬拼音（Romaji / Pronunciation）
  3. 繁體中文雙語翻譯（Traditional Chinese Translation）
* 🎨 **三軌獨立色彩與色號自訂**：原文、羅馬拼音、翻譯與主題色彩均支援直接鍵入 16 進位色號（HEX Code）或一鍵選取色票。
* 🎧 **Spotify 專屬鎖定**：精準追蹤 Spotify 播放狀態與時間軸，徹底杜絕其他影音 App 誤觸干擾。

---

## 🛠️ 技術棧

* **語言與架構**：Kotlin / Clean Architecture / MVVM
* **UI 框架**：Jetpack Compose / Material 3
* **媒體與系統**：`MediaSessionManager` / `NotificationListenerService` / `WindowManager`
* **非同步處理**：Kotlin Coroutines & StateFlow
* **系統相容**：Android 8.0 (API 26) ~ Android 15 (API 35)

---

## 🚀 快速開始

1. 使用 **Android Studio** 開啟本專案。
2. 連接 Android 裝置並點擊 **▶ Run** 編譯安裝。
3. 首次開啟時，依引導授予：
   * **通知存取權限**（用於即時同步 Spotify 播放進度）
   * **懸浮窗 / 鎖定畫面顯示權限**
4. 打開 Spotify 播放歌曲，熄屏點亮後即可享受全自動鎖屏動態歌詞！

---

## 📄 開源授權

本專案採用 [MIT License](LICENSE) 授權開源。
