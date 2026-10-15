package com.shilapi.xcertplay.update

import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.shilapi.xcertplay.ServerUi
import com.shilapi.xcertplay.CarPlayBackgroundSession
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/** User-initiated updates. Android owns background downloads; installation is always explicit. */
internal class AppUpdateUi(private val activity: AppCompatActivity) {
    private val prefs = activity.getSharedPreferences("app_updates", 0)
    private val manager get() = activity.getSystemService(DownloadManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val client = UpdateClient()
    private val ui = ServerUi(activity)
    @Suppress("DEPRECATION") private fun installed() = activity.packageManager.getPackageInfo(activity.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    private val currentVersion get() = installed().versionName ?: "0.0.0"
    private var channel = runCatching { UpdateChannel.valueOf(prefs.getString("channel", "GITEE")!!) }.getOrDefault(UpdateChannel.GITEE)
    private var preview = prefs.getBoolean("preview", currentVersion.contains("-rc."))
    private var release = runCatching { UpdateRelease.restore(prefs.getString("release", "")!!) }.getOrNull()
    private var downloadId = prefs.getLong("download", -1)
    private var checksum = prefs.getString("sha256", "") ?: ""
    private var selected: UpdateRelease? = null
    private var closed = false
    private var resumed = false
    private var busy = false
    private var ready = false
    private var status = "当前版本 $currentVersion；检查和下载均由你手动发起。"
    private var statusView: TextView? = null
    private var channelButton: Button? = null
    private var checkButton: Button? = null
    private var actionButton: Button? = null
    private var cancelButton: Button? = null
    private var previewSwitch: MaterialSwitch? = null
    private val poll = object : Runnable {
        override fun run() {
            if (closed || !resumed) return
            if (!busy) refreshDownload()
            if (downloadId >= 0) main.postDelayed(this, 1000)
        }
    }
    init {
        if (downloadId >= 0 && (release == null || !Regex("[a-f0-9]{64}").matches(checksum) ||
                release!!.version <= requireNotNull(UpdateVersion.parse(currentVersion)))) clearDownload()
    }
    fun bind(card: LinearLayout) {
        channelButton = ui.button("更新渠道：${channel.label}") { chooseChannel() }.also { card.addView(it, ui.secondaryButtonLayout(8)) }
        previewSwitch = MaterialSwitch(activity).apply {
            text = "包含预发布版本（RC）"; isChecked = preview
            setOnCheckedChangeListener { _, checked ->
                preview = checked; prefs.edit().putBoolean("preview", checked).apply(); selected = null
                status = "版本范围已更改，请重新检查更新。"; render()
            }
        }.also { card.addView(it) }
        statusView = ui.text(status, 14).also { card.addView(it) }
        checkButton = ui.button("检查更新") { check() }.also { card.addView(it, ui.secondaryButtonLayout(12)) }
        actionButton = ui.button("下载更新", true) { if (ready) install() else offerDownload() }.also { card.addView(it, ui.secondaryButtonLayout(12)) }
        cancelButton = ui.button("取消下载 / 清除更新包") { clearDownload(); status = "已清除更新下载，可重新检查或切换渠道。"; render() }
            .also { card.addView(it, ui.secondaryButtonLayout(12)) }
        card.addView(ui.text("先下载并校验，再由系统确认安装。安装会重启应用；请在停车并结束投屏后操作。", 14))
        render()
    }
    fun resume() { resumed = true; main.removeCallbacks(poll); main.post(poll) }
    fun pause() { resumed = false; main.removeCallbacks(poll) }
    fun close() { closed = true; pause(); worker.shutdownNow() }
    private fun render() {
        if (closed) return
        statusView?.text = status
        channelButton?.text = "更新渠道：${channel.label}"
        channelButton?.isEnabled = !busy && downloadId < 0
        previewSwitch?.isEnabled = !busy && downloadId < 0
        checkButton?.isEnabled = !busy && downloadId < 0
        actionButton?.apply {
            text = if (ready) "校验并安装更新" else "下载更新"
            visibility = if (ready || selected != null && downloadId < 0) View.VISIBLE else View.GONE
            isEnabled = !busy
        }
        cancelButton?.apply { visibility = if (downloadId >= 0) View.VISIBLE else View.GONE; isEnabled = !busy }
    }
    private fun chooseChannel() {
        MaterialAlertDialogBuilder(activity).setTitle("更新渠道")
            .setSingleChoiceItems(UpdateChannel.entries.map { it.label }.toTypedArray(), channel.ordinal) { dialog, which ->
                channel = UpdateChannel.entries[which]; prefs.edit().putString("channel", channel.name).apply()
                selected = null; status = "已选择 ${channel.label}，请检查更新。"; render(); dialog.dismiss()
            }.setNegativeButton("取消", null).show()
    }
    private fun <T> work(message: String, action: () -> T, success: (T) -> Unit) {
        if (busy || closed) return
        busy = true; status = message; render()
        worker.execute {
            val result = runCatching(action)
            main.post {
                if (!closed && !activity.isFinishing && !activity.isDestroyed) {
                    busy = false
                    result.fold(success, { status = it.message?.take(240) ?: "更新操作失败，请重试或切换渠道。" })
                    render()
                }
            }
        }
    }
    private fun check() {
        val source = channel; val includeRc = preview; selected = null
        work("正在通过 ${source.label} 检查更新…", { client.check(source, includeRc, currentVersion) }) { result ->
            selected = result
            status = if (result == null) "该渠道没有比 $currentVersion 更新的可用版本。" else "发现 ${result.tag} · ${source.label}"
            if (result != null && resumed) offerDownload()
        }
    }
    private fun offerDownload() {
        val candidate = selected ?: return
        MaterialAlertDialogBuilder(activity).setTitle("更新至 ${candidate.tag}")
            .setMessage("渠道：${candidate.channel.label}\n\n${candidate.notes.take(6000)}\n\n下载不安装；完成校验后还需确认安装。")
            .setNegativeButton("稍后", null).setPositiveButton("下载更新") { _, _ -> download(candidate) }.show()
    }
    private fun download(candidate: UpdateRelease) {
        if (downloadId >= 0) { status = "已有更新下载，请先完成或取消。"; render(); return }
        work("正在读取更新包校验信息…", { client.checksum(candidate) }) { expected ->
            runCatching {
                val destination = destination(candidate); destination.parentFile?.mkdirs()
                check(!destination.exists() || destination.delete()) { "旧更新文件无法清理" }
                val request = DownloadManager.Request(Uri.parse(candidate.apkUrl))
                    .setTitle("WheelPlay ${candidate.tag}").setDescription(candidate.channel.label)
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(destination))
                val id = manager.enqueue(request)
                if (!prefs.edit().putLong("download", id).putString("release", candidate.json()).putString("sha256", expected).commit()) {
                    manager.remove(id); error("无法保存更新任务，请重试")
                }
                release = candidate; checksum = expected; downloadId = id; ready = false; selected = null
                status = "下载已开始，系统通知栏可查看进度。"
                main.removeCallbacks(poll); if (resumed) main.post(poll)
            }.onFailure { status = it.message ?: "无法启动系统下载，请检查下载管理器。" }
        }
    }
    private fun destination(r: UpdateRelease): File {
        val root = requireNotNull(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) { "下载目录不可用" }
        return File(root, "updates/${r.tag}.apk")
    }
    private fun refreshDownload() {
        if (downloadId < 0) return
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                if (!c.moveToFirst()) { clearDownload(); status = "下载任务不存在，请重新检查更新。"; return@use }
                when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> { if (!ready) status = "${release?.tag} 已下载；点击后校验并安装。"; ready = true }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        clearDownload(); status = "下载失败（$reason），请检查网络、空间，或切换渠道重试。"
                    }
                    else -> {
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        if (total > 200L * 1024 * 1024 || done > 200L * 1024 * 1024) {
                            clearDownload(); status = "更新包大小异常，下载已取消。"
                        } else status = "${release?.tag} 下载中 · ${if (total > 0) "${done * 100 / total}%" else "${done / 1024} KB"}（系统会自动重试网络中断）"
                    }
                }
            }
        }.onFailure { status = "无法查询下载状态，请检查系统下载管理器。" }
        render()
    }
    private fun clearDownload() {
        if (downloadId >= 0) runCatching { manager.remove(downloadId) }
        release?.let { runCatching { destination(it).delete() } }
        File(activity.filesDir, "updates").listFiles()?.forEach { it.delete() }
        downloadId = -1; release = null; ready = false; checksum = ""
        prefs.edit().remove("download").remove("release").remove("sha256").apply()
    }
    private fun identity(info: PackageInfo): ApkIdentity {
        val signers = info.signingInfo?.apkContentsSigners.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
        return ApkIdentity(info.packageName, info.longVersionCode, info.versionName ?: "", signers)
    }
    @Suppress("DEPRECATION")
    private fun install() {
        val candidate = release ?: return
        MaterialAlertDialogBuilder(activity).setTitle("安装更新")
            .setMessage(if (CarPlayBackgroundSession.hasSession()) "当前正在投屏，安装更新会中断连接并重启应用。请停车后继续。" else "系统将确认安装 ${candidate.tag}，应用数据会保留。")
            .setNegativeButton("取消", null).setPositiveButton("继续") { _, _ ->
                work("正在校验 APK 完整性、版本和签名…", {
                    val file = UpdateCatalog.stageFile(destination(candidate), File(activity.filesDir, "updates"),
                        "${candidate.tag}-$downloadId", checksum)
                    @Suppress("DEPRECATION")
                    val apk = activity.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                        ?: error("下载内容不是有效 APK，请清除后重新下载")
                    UpdateInstallPolicy.verify(identity(installed()), identity(apk), candidate)
                    file
                }) { file ->
                    if (!resumed) { status = "校验已通过，请返回应用后再次确认安装。"; return@work }
                    if (!activity.packageManager.canRequestPackageInstalls()) {
                        status = "请允许本应用安装更新，返回后再次点“校验并安装更新”。"
                        runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                            .onFailure { status = "无法打开安装授权，请到系统设置中允许本应用安装未知应用。" }
                    } else {
                        runCatching {
                            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
                            activity.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            status = "已交给系统安装器，请确认安装。"
                        }.onFailure { status = "无法启动安装器，请检查系统安装权限。" }
                    }
                }
            }.show()
    }
}
