package com.shilapi.xcertplay

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.util.concurrent.TimeUnit

/** Per-package exemption only: never disables device-wide power saving. */
internal object RootKeepAlive {
    private const val PREFS = "root_keepalive"
    internal fun isListed(output: String, pkg: String): Boolean = output.lineSequence().any {
        val fields = it.trim().split(',')
        fields.size >= 3 && fields[1] == pkg
    }
    internal fun command(pkg: String, add: Boolean): String {
        require(pkg.matches(Regex("[A-Za-z0-9_.]+")))
        return "cmd deviceidle whitelist ${if (add) "+" else "-"}$pkg"
    }
    private fun run(script: String): String {
        val process = ProcessBuilder("su", "-c", script).redirectErrorStream(true).start()
        try {
            check(process.waitFor(20, TimeUnit.SECONDS)) { "Root 操作超时" }
            val output = process.inputStream.bufferedReader().readText()
            check(process.exitValue() == 0) { "Root 操作失败：${output.take(240)}" }
            return output
        } finally { process.destroy() }
    }
    @Synchronized fun apply(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pkg = context.packageName
        if (!isListed(run("cmd deviceidle whitelist"), pkg)) {
            // Save ownership before mutation so a crash cannot lose the rollback record.
            check(prefs.edit().putBoolean("added", true).commit())
            run(command(pkg, true))
            check(isListed(run("cmd deviceidle whitelist"), pkg)) { "系统未接受省电豁免" }
        }
        check(prefs.edit().putBoolean("enabled", true).commit())
        record(context, "root exemption applied")
    }
    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getBoolean("enabled", prefs.getBoolean("added", false))
    }
    fun hostLaunchCommand(pkg: String): String {
        require(pkg.matches(Regex("[A-Za-z0-9_.]+")))
        return "am start --user current -f 0x10000000 -n $pkg/com.shilapi.xcertplay.CarPlayHostActivity"
    }
    fun launchHost(context: Context) {
        check(isEnabled(context)) { "未启用应用管理的 Root 保活" }
        check(DiPlayPreferences.autoConnect(context)) { "未启用自动连接" }
        check(AirPlayPersistence.loadWirelessEnabled(context)) { "后台准备仅用于已配置的无线连接" }
        val result = run(hostLaunchCommand(context.packageName))
        check(!result.contains("Error:", ignoreCase = true)) { "系统拒绝后台准备连接" }
        record(context, "root requested wireless host initialization; interactive=${context.getSystemService(PowerManager::class.java).isInteractive}")
    }
    @Synchronized fun reapplyIfOwned(context: Context) {
        if (isEnabled(context)) apply(context)
        else record(context, "root reapply skipped: no app-owned exemption")
    }
    @Synchronized fun restore(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("added", false)) {
            run(command(context.packageName, false))
            check(!isListed(run("cmd deviceidle whitelist"), context.packageName)) { "恢复失败，请重试" }
            check(prefs.edit().remove("added").commit())
        }
        check(prefs.edit().putBoolean("enabled", false).commit())
        record(context, "root exemption restored")
    }
    fun summary(context: Context): String {
        val pm = context.getSystemService(PowerManager::class.java)
        return "系统省电豁免：${if (pm.isIgnoringBatteryOptimizations(context.packageName)) "已启用" else "未启用"}"
    }
    @Synchronized fun record(context: Context, event: String) {
        runCatching {
            val pm = context.getSystemService(PowerManager::class.java)
            val file = File(context.filesDir, "logs/keepalive.log")
            file.parentFile?.mkdirs()
            if (file.length() > 64 * 1024) file.writeText(file.readText().takeLast(32 * 1024).substringAfter('\n'))
            file.appendText("${System.currentTimeMillis()} elapsed=${SystemClock.elapsedRealtime()} $event interactive=${pm.isInteractive} idle=${pm.isDeviceIdleMode} exempt=${pm.isIgnoringBatteryOptimizations(context.packageName)} session=${CarPlayBackgroundSession.hasSession()}\n")
        }
    }
}

/** Best-effort radio lock; Wi-Fi Direct/OEM policies still require device testing. */
internal class LockScreenProtection(private val context: Context) {
    private var wifi: WifiManager.WifiLock? = null
    @Suppress("DEPRECATION") fun acquire() {
        if (wifi != null) return
        runCatching {
            val manager = context.applicationContext.getSystemService(WifiManager::class.java)
            wifi = manager.createWifiLock(WifiManager.WIFI_MODE_FULL, "WheelPlay:connection").apply {
                setReferenceCounted(false); acquire()
            }
        }.onFailure { RootKeepAlive.record(context, "wifi lock failed ${it.javaClass.simpleName}") }
        RootKeepAlive.record(context, "locks acquired wifi=${wifi?.isHeld == true}")
    }
    fun release() {
        runCatching { wifi?.let { if (it.isHeld) it.release() } }; wifi = null
        RootKeepAlive.record(context, "wifi lock released")
    }
}
