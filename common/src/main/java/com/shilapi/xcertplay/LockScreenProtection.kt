package com.shilapi.xcertplay

import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.i18n.*
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.util.concurrent.TimeUnit

/** The Xpeng keepalive and compatibility routing independently opt into Root. */
internal fun rootStartupRequested(context: Context): Boolean = RootKeepAlive.isEnabled(context) ||
    com.shilapi.xcertplay.web.TeslaHttpCompatibility.config(context).let {
        it.enabled && it.mode == com.shilapi.xcertplay.network.TeslaHttpMode.ROOT
    }

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
            checkUi(process.waitFor(20, TimeUnit.SECONDS)) { UiText(R.string.xp_root_timeout) }
            val output = process.inputStream.bufferedReader().readText()
            checkUi(process.exitValue() == 0) { UiText(R.string.xp_root_failed) }
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
            checkUi(isListed(run("cmd deviceidle whitelist"), pkg)) { UiText(R.string.xp_root_not_accepted) }
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
        checkUi(isEnabled(context)) { UiText(R.string.xp_root_not_enabled) }
        checkUi(DiPlayPreferences.autoConnect(context)) { UiText(R.string.xp_auto_not_enabled) }
        checkUi(AirPlayPersistence.loadWirelessEnabled(context)) { UiText(R.string.xp_wireless_only) }
        val result = run(hostLaunchCommand(context.packageName))
        checkUi(!result.contains("Error:", ignoreCase = true)) { UiText(R.string.xp_background_refused) }
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
            checkUi(!isListed(run("cmd deviceidle whitelist"), context.packageName)) { UiText(R.string.xp_restore_failed) }
            check(prefs.edit().remove("added").commit())
        }
        check(prefs.edit().putBoolean("enabled", false).commit())
        record(context, "root exemption restored")
    }
    fun summary(context: Context): String {
        val pm = context.getSystemService(PowerManager::class.java)
        return context.getString(R.string.xp_exempt_summary, context.getString(if (pm.isIgnoringBatteryOptimizations(context.packageName)) R.string.xp_exempt_on else R.string.xp_exempt_off))
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
