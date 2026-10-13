package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.SafeAreaRect
import kotlin.math.roundToInt

enum class CarPlayAppearance(val label: String) {
    SYSTEM("跟随 Android"), DAY("始终日间"), NIGHT("始终夜间");
    fun night(systemNight: Boolean): Boolean = when (this) {
        SYSTEM -> systemNight
        DAY -> false
        NIGHT -> true
    }
}

enum class CarPlayMicrophoneSource(val label: String) {
    ANDROID("Android 麦克风"), BROWSER("浏览器麦克风"), DISABLED("禁用麦克风");
}

enum class CarPlayAudioOutput(val label: String) {
    ANDROID("Android 设备"), BROWSER("车机浏览器");
}

enum class BrowserOfflinePolicy(val label: String, val timeoutMillis: Long?) {
    KEEP("保持 CarPlay 连接", null), ONE_MINUTE("离线 1 分钟后断开 iPhone", 60_000),
    FIVE_MINUTES("离线 5 分钟后断开 iPhone", 300_000);
}

/** Percentages follow the negotiated display size, including adaptive browser resolutions. */
data class CarPlaySafeMargins(val top: Int = 0, val bottom: Int = 0, val left: Int = 0, val right: Int = 0) {
    init { require(listOf(top, bottom, left, right).all { it in 0..40 }) { "每边留白须为 0–40%" } }
    val isFullScreen get() = top == 0 && bottom == 0 && left == 0 && right == 0
    fun rectangle(width: Int, height: Int): SafeAreaRect? {
        require(width > 0 && height > 0)
        if (isFullScreen) return null
        val x = (width * left / 100.0).roundToInt().coerceIn(0, width - 1)
        val y = (height * top / 100.0).roundToInt().coerceIn(0, height - 1)
        return SafeAreaRect(x, y,
            (width * (100 - right) / 100.0).roundToInt().coerceIn(x + 1, width),
            (height * (100 - bottom) / 100.0).roundToInt().coerceIn(y + 1, height))
    }
    fun encode() = "$top,$bottom,$left,$right"
    companion object {
        fun decode(value: String?): CarPlaySafeMargins? = runCatching {
            val parts = value?.split(',') ?: return null
            if (parts.size != 4) return null
            CarPlaySafeMargins(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
        }.getOrNull()
    }
}
