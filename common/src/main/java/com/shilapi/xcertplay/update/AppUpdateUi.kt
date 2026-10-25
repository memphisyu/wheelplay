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
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.i18n.*
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
    private var status = activity.getString(R.string.xp_current, currentVersion)
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
        channelButton = ui.button(activity.getString(R.string.xp_channel, channel.label(activity))) { chooseChannel() }.also { card.addView(it, ui.secondaryButtonLayout(8)) }
        previewSwitch = MaterialSwitch(activity).apply {
            text = activity.getString(R.string.xp_preview); isChecked = preview
            setOnCheckedChangeListener { _, checked ->
                preview = checked; prefs.edit().putBoolean("preview", checked).apply(); selected = null
                status = activity.getString(R.string.xp_range_changed); render()
            }
        }.also { card.addView(it) }
        statusView = ui.text(status, 14).also { card.addView(it) }
        checkButton = ui.button(activity.getString(R.string.xp_check_updates)) { check() }.also { card.addView(it, ui.secondaryButtonLayout(12)) }
        actionButton = ui.button(activity.getString(R.string.xp_download_update), true) { if (ready) install() else offerDownload() }.also { card.addView(it, ui.secondaryButtonLayout(12)) }
        cancelButton = ui.button(activity.getString(R.string.xp_clear_download)) { clearDownload(); status = activity.getString(R.string.xp_cleared); render() }
            .also { card.addView(it, ui.secondaryButtonLayout(12)) }
        card.addView(ui.text(activity.getString(R.string.xp_install_help), 14))
        render()
    }
    fun resume() { resumed = true; main.removeCallbacks(poll); main.post(poll) }
    fun pause() { resumed = false; main.removeCallbacks(poll) }
    fun close() { closed = true; pause(); worker.shutdownNow() }
    private fun render() {
        if (closed) return
        statusView?.text = status
        channelButton?.text = activity.getString(R.string.xp_channel, channel.label(activity))
        channelButton?.isEnabled = !busy && downloadId < 0
        previewSwitch?.isEnabled = !busy && downloadId < 0
        checkButton?.isEnabled = !busy && downloadId < 0
        actionButton?.apply {
            text = if (ready) activity.getString(R.string.xp_verify_install) else activity.getString(R.string.xp_download_update)
            visibility = if (ready || selected != null && downloadId < 0) View.VISIBLE else View.GONE
            isEnabled = !busy
        }
        cancelButton?.apply { visibility = if (downloadId >= 0) View.VISIBLE else View.GONE; isEnabled = !busy }
    }
    private fun chooseChannel() {
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.xp_update_channel))
            .setSingleChoiceItems(UpdateChannel.entries.map { it.label(activity) }.toTypedArray(), channel.ordinal) { dialog, which ->
                channel = UpdateChannel.entries[which]; prefs.edit().putString("channel", channel.name).apply()
                selected = null; status = activity.getString(R.string.xp_selected, channel.label(activity)); render(); dialog.dismiss()
            }.setNegativeButton(activity.getString(R.string.xp_cancel), null).show()
    }
    private fun <T> work(message: String, action: () -> T, success: (T) -> Unit) {
        if (busy || closed) return
        busy = true; status = message; render()
        worker.execute {
            val result = runCatching(action)
            main.post {
                if (!closed && !activity.isFinishing && !activity.isDestroyed) {
                    busy = false
                    result.fold(success, { status = it.userMessage(activity).take(240).ifEmpty { activity.getString(R.string.xp_update_failed) } })
                    render()
                }
            }
        }
    }
    private fun check() {
        val source = channel; val includeRc = preview; selected = null
        work(activity.getString(R.string.xp_checking, source.label(activity)), { client.check(source, includeRc, currentVersion) }) { result ->
            selected = result
            status = if (result == null) activity.getString(R.string.xp_no_update, currentVersion) else activity.getString(R.string.xp_found, result.tag, source.label(activity))
            if (result != null && resumed) offerDownload()
        }
    }
    private fun offerDownload() {
        val candidate = selected ?: return
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.xp_update_to, candidate.tag))
            .setMessage(activity.getString(R.string.xp_offer, candidate.channel.label(activity), candidate.notes.take(6000)))
            .setNegativeButton(activity.getString(R.string.xp_later), null).setPositiveButton(activity.getString(R.string.xp_download_update)) { _, _ -> download(candidate) }.show()
    }
    private fun download(candidate: UpdateRelease) {
        if (downloadId >= 0) { status = activity.getString(R.string.xp_already_downloading); render(); return }
        work(activity.getString(R.string.xp_reading_checksum), { client.checksum(candidate) }) { expected ->
            runCatching {
                val destination = destination(candidate); destination.parentFile?.mkdirs()
                check(!destination.exists() || destination.delete()) { activity.getString(R.string.xp_old_file) }
                val request = DownloadManager.Request(Uri.parse(candidate.apkUrl))
                    .setTitle("WheelPlay ${candidate.tag}").setDescription(candidate.channel.label(activity))
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(destination))
                val id = manager.enqueue(request)
                if (!prefs.edit().putLong("download", id).putString("release", candidate.json()).putString("sha256", expected).commit()) {
                    manager.remove(id); error(activity.getString(R.string.xp_save_task))
                }
                release = candidate; checksum = expected; downloadId = id; ready = false; selected = null
                status = activity.getString(R.string.xp_download_started)
                main.removeCallbacks(poll); if (resumed) main.post(poll)
            }.onFailure { status = it.userMessage(activity).ifEmpty { activity.getString(R.string.xp_download_start_failed) } }
        }
    }
    private fun destination(r: UpdateRelease): File {
        val root = requireNotNull(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)) { activity.getString(R.string.xp_directory_unavailable) }
        return File(root, "updates/${r.tag}.apk")
    }
    private fun refreshDownload() {
        if (downloadId < 0) return
        runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                if (!c.moveToFirst()) { clearDownload(); status = activity.getString(R.string.xp_missing_task); return@use }
                when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> { if (!ready) status = activity.getString(R.string.xp_downloaded, release?.tag.toString()); ready = true }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        clearDownload(); status = activity.getString(R.string.xp_download_failed, reason.toString())
                    }
                    else -> {
                        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        if (total > 200L * 1024 * 1024 || done > 200L * 1024 * 1024) {
                            clearDownload(); status = activity.getString(R.string.xp_size_cancelled)
                        } else status = activity.getString(R.string.xp_progress, release?.tag.toString(), if (total > 0) "${done * 100 / total}%" else "${done / 1024} KB")
                    }
                }
            }
        }.onFailure { status = activity.getString(R.string.xp_query_failed) }
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
        MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.xp_install_update))
            .setMessage(if (CarPlayBackgroundSession.hasSession()) activity.getString(R.string.xp_projection_warning) else activity.getString(R.string.xp_install_confirm, candidate.tag))
            .setNegativeButton(activity.getString(R.string.xp_cancel), null).setPositiveButton(activity.getString(R.string.xp_continue)) { _, _ ->
                work(activity.getString(R.string.xp_verifying), {
                    val file = UpdateCatalog.stageFile(destination(candidate), File(activity.filesDir, "updates"),
                        "${candidate.tag}-$downloadId", checksum)
                    @Suppress("DEPRECATION")
                    val apk = activity.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                        ?: error(activity.getString(R.string.xp_invalid_apk))
                    UpdateInstallPolicy.verify(identity(installed()), identity(apk), candidate)
                    file
                }) { file ->
                    if (!resumed) { status = activity.getString(R.string.xp_return_install); return@work }
                    if (!activity.packageManager.canRequestPackageInstalls()) {
                        status = activity.getString(R.string.xp_allow_install)
                        runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                            .onFailure { status = activity.getString(R.string.xp_permission_failed) }
                    } else {
                        runCatching {
                            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
                            activity.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            status = activity.getString(R.string.xp_installer_started)
                        }.onFailure { status = activity.getString(R.string.xp_installer_failed) }
                    }
                }
            }.show()
    }
}
