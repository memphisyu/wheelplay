package com.shilapi.xcertplay.update

import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.i18n.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.File
import java.security.MessageDigest

internal data class UpdateVersion(val major: Int, val minor: Int, val patch: Int, val rc: Int?) : Comparable<UpdateVersion> {
    override fun compareTo(other: UpdateVersion): Int {
        val base = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (base != 0) return base
        return when { rc == null && other.rc != null -> 1; rc != null && other.rc == null -> -1; else -> compareValues(rc, other.rc) }
    }
    companion object {
        fun parse(value: String): UpdateVersion? {
            val m = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-rc\\.(\\d+))?$").matchEntire(value) ?: return null
            return runCatching { UpdateVersion(m.groupValues[1].toInt(), m.groupValues[2].toInt(),
                m.groupValues[3].ifEmpty { "0" }.toInt(), m.groupValues[4].takeIf { it.isNotEmpty() }?.toInt()) }.getOrNull()
        }
    }
}
internal enum class UpdateChannel(val label: String, val origin: String, val api: String) {
    GITEE("Gitee", "https://gitee.com", "https://gitee.com/api/v5"),
    GITHUB("GitHub", "https://github.com", "https://api.github.com");
    fun label(context: android.content.Context) = context.getString(if (this == GITEE) R.string.xp_channel_gitee else R.string.xp_channel_github)
    fun catalog(page: Int) = "$api/repos/memphisyu/wheelplay/releases?per_page=100&page=$page"
    fun assetUrl(tag: String, name: String) = "$origin/memphisyu/wheelplay/releases/download/$tag/$name"
}
internal data class UpdateRelease(val tag: String, val title: String, val notes: String, val channel: UpdateChannel,
    val apkName: String, val checksumName: String) {
    val version get() = requireNotNull(UpdateVersion.parse(tag))
    val apkUrl get() = channel.assetUrl(tag, apkName)
    val checksumUrl get() = channel.assetUrl(tag, checksumName)
    fun json() = JSONObject().put("tag", tag).put("title", title).put("notes", notes).put("channel", channel.name)
        .put("apk", apkName).put("checksum", checksumName).toString()
    companion object {
        fun restore(raw: String): UpdateRelease = JSONObject(raw).let {
            val tag = it.getString("tag"); require(UpdateVersion.parse(tag) != null)
            val apk = it.getString("apk"); val checksum = it.getString("checksum")
            require(safeName(apk) && apk.endsWith(".apk") && safeName(checksum) && checksum.endsWith(".sha256"))
            UpdateRelease(tag, it.getString("title"), it.getString("notes"), UpdateChannel.valueOf(it.getString("channel")), apk, checksum)
        }
    }
}
private fun safeName(value: String) = value.length in 1..160 && Regex("[A-Za-z0-9._-]+").matches(value) && !value.contains("..")
internal object UpdateCatalog {
    fun parse(json: String, channel: UpdateChannel, preview: Boolean): List<UpdateRelease> = buildList {
        val list = JSONArray(json)
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val tag = r.optString("tag_name"); val version = UpdateVersion.parse(tag) ?: continue
            if (!preview && (r.optBoolean("prerelease") || version.rc != null)) continue
            val assets = r.optJSONArray("assets") ?: continue
            val names = (0 until assets.length()).map { assets.getJSONObject(it).optString("name") }
            val apks = names.filter { safeName(it) && it.endsWith(".apk") }
            val sums = names.filter { safeName(it) && it == "$tag.sha256" }
            // Ambiguous APK variants are not safe to select automatically.
            if (apks.size != 1 || sums.size != 1) continue
            add(UpdateRelease(tag, r.optString("name", tag).take(200), r.optString("body").take(12000), channel, apks.single(), sums.single()))
        }
    }
    fun latest(releases: List<UpdateRelease>, current: String): UpdateRelease? {
        val installed = (UpdateVersion.parse(current) ?: errorUi(UiText(R.string.xp_version_invalid)))
        return releases.filter { it.version > installed }.maxByOrNull { it.version }
    }
    fun checksum(text: String, apkName: String): String {
        val matches = text.lineSequence().mapNotNull { line ->
            Regex("^([a-fA-F0-9]{64})[ \\t]+\\*?(.+)$").matchEntire(line.trim())
                ?.takeIf { it.groupValues[2] == apkName }?.groupValues?.get(1)?.lowercase()
        }.toList()
        requireUi(matches.size == 1) { UiText(R.string.xp_checksum_missing) }
        return matches.single()
    }
    fun stageFile(downloaded: File, directory: File, key: String, checksum: String): File {
        require(safeName(key))
        checkUi(directory.isDirectory || directory.mkdirs()) { UiText(R.string.xp_private_directory) }
        val staged = File(directory, "$key.apk")
        if (staged.exists()) { verifyFile(staged, checksum); return staged }
        val temporary = File.createTempFile("download-", ".part", directory)
        try {
            // Bound the copy as well as the hash check; only the private, checked file is shared.
            downloaded.inputStream().use { input -> temporary.outputStream().use { output ->
                val bytes = ByteArray(65536); var copied = 0L
                while (true) { val n = input.read(bytes); if (n < 0) break; copied += n; requireUi(copied <= 200L * 1024 * 1024) { UiText(R.string.xp_size_invalid) }; output.write(bytes, 0, n) }
            } }
            verifyFile(temporary, checksum)
            checkUi(temporary.renameTo(staged)) { UiText(R.string.xp_save_verified) }
            return staged
        } finally { temporary.delete() }
    }
    fun verifyFile(file: File, checksum: String) {
        requireUi(file.length() in 1..200L * 1024 * 1024) { UiText(R.string.xp_size_invalid) }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes = ByteArray(65536); while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) } }
        requireUi(digest.digest().joinToString("") { "%02x".format(it) } == checksum) { UiText(R.string.xp_checksum_failed) }
    }
}
internal class UpdateClient(private val read: (String, Int) -> String = UpdateHttp::read) {
    fun check(channel: UpdateChannel, preview: Boolean, current: String): UpdateRelease? {
        val releases = mutableListOf<UpdateRelease>()
        for (page in 1..10) {
            val raw = read(channel.catalog(page), 2 * 1024 * 1024)
            releases += UpdateCatalog.parse(raw, channel, preview)
            if (JSONArray(raw).length() < 100) return UpdateCatalog.latest(releases, current)
        }
        errorUi(UiText(R.string.xp_catalog_long))
    }
    fun checksum(release: UpdateRelease) = UpdateCatalog.checksum(read(release.checksumUrl, 65536), release.apkName)
}
internal object UpdateHttp {
    fun read(address: String, limit: Int): String {
        var target = address
        repeat(6) {
            val uri = URI(target)
            requireUi(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { UiText(R.string.xp_https_required) }
            val c = URL(target).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000; c.readTimeout = 20000; c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "WheelPlay-Xpeng-Updater")
                c.setRequestProperty("Accept", "application/json, text/plain, */*")
                when (c.responseCode) {
                    in 300..399 -> { target = uri.resolve(c.getHeaderField("Location") ?: errorUi(UiText(R.string.xp_redirect_invalid))).toString() }
                    200 -> return c.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) { val n = input.read(chunk); if (n < 0) break; requireUi(output.size() + n <= limit) { UiText(R.string.xp_response_large) }; output.write(chunk, 0, n) }
                        output.toString("UTF-8")
                    }
                    403, 429 -> errorUi(UiText(R.string.xp_rate_limited))
                    else -> errorUi(UiText(R.string.xp_http_error, listOf(c.responseCode.toString())))
                }
            } finally { c.disconnect() }
        }
        errorUi(UiText(R.string.xp_redirect_limit))
    }
}
internal data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String, val signers: Set<String>)
internal object UpdateInstallPolicy {
    fun verify(installed: ApkIdentity, downloaded: ApkIdentity, release: UpdateRelease) {
        requireUi(downloaded.packageName == installed.packageName) { UiText(R.string.xp_package_mismatch) }
        requireUi(downloaded.versionCode > installed.versionCode) { UiText(R.string.xp_version_not_new) }
        requireUi(UpdateVersion.parse(downloaded.versionName) == release.version) { UiText(R.string.xp_version_mismatch) }
        requireUi(installed.signers.isNotEmpty() && downloaded.signers == installed.signers) { UiText(R.string.xp_signature_mismatch) }
    }
}
