package com.shilapi.xcertplay.update

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
    GITEE("Gitee（国内）", "https://gitee.com", "https://gitee.com/api/v5"),
    GITHUB("GitHub", "https://github.com", "https://api.github.com");
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
        val installed = requireNotNull(UpdateVersion.parse(current)) { "无法识别当前版本" }
        return releases.filter { it.version > installed }.maxByOrNull { it.version }
    }
    fun checksum(text: String, apkName: String): String {
        val matches = text.lineSequence().mapNotNull { line ->
            Regex("^([a-fA-F0-9]{64})[ \\t]+\\*?(.+)$").matchEntire(line.trim())
                ?.takeIf { it.groupValues[2] == apkName }?.groupValues?.get(1)?.lowercase()
        }.toList()
        require(matches.size == 1) { "校验文件缺少唯一的 APK SHA-256" }
        return matches.single()
    }
    fun stageFile(downloaded: File, directory: File, key: String, checksum: String): File {
        require(safeName(key))
        check(directory.isDirectory || directory.mkdirs()) { "无法创建私有更新目录" }
        val staged = File(directory, "$key.apk")
        if (staged.exists()) { verifyFile(staged, checksum); return staged }
        val temporary = File.createTempFile("download-", ".part", directory)
        try {
            // Bound the copy as well as the hash check; only the private, checked file is shared.
            downloaded.inputStream().use { input -> temporary.outputStream().use { output ->
                val bytes = ByteArray(65536); var copied = 0L
                while (true) { val n = input.read(bytes); if (n < 0) break; copied += n; require(copied <= 200L * 1024 * 1024) { "更新包大小异常" }; output.write(bytes, 0, n) }
            } }
            verifyFile(temporary, checksum)
            check(temporary.renameTo(staged)) { "无法保存已校验更新包" }
            return staged
        } finally { temporary.delete() }
    }
    fun verifyFile(file: File, checksum: String) {
        require(file.length() in 1..200L * 1024 * 1024) { "更新包大小异常" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes = ByteArray(65536); while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) } }
        require(digest.digest().joinToString("") { "%02x".format(it) } == checksum) { "更新包校验失败，请重新下载" }
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
        error("发行版列表过长，请前往仓库查看")
    }
    fun checksum(release: UpdateRelease) = UpdateCatalog.checksum(read(release.checksumUrl, 65536), release.apkName)
}
internal object UpdateHttp {
    fun read(address: String, limit: Int): String {
        var target = address
        repeat(6) {
            val uri = URI(target)
            require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "更新地址必须使用 HTTPS" }
            val c = URL(target).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 15000; c.readTimeout = 20000; c.instanceFollowRedirects = false
                c.setRequestProperty("User-Agent", "WheelPlay-Xpeng-Updater")
                c.setRequestProperty("Accept", "application/json, text/plain, */*")
                when (c.responseCode) {
                    in 300..399 -> { target = uri.resolve(c.getHeaderField("Location") ?: error("更新地址跳转异常")).toString() }
                    200 -> return c.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) { val n = input.read(chunk); if (n < 0) break; require(output.size() + n <= limit) { "更新响应过大" }; output.write(chunk, 0, n) }
                        output.toString("UTF-8")
                    }
                    403, 429 -> error("更新渠道访问受限，请稍后重试或切换渠道")
                    else -> error("更新渠道返回 HTTP ${c.responseCode}，请重试或切换渠道")
                }
            } finally { c.disconnect() }
        }
        error("更新地址跳转次数过多")
    }
}
internal data class ApkIdentity(val packageName: String, val versionCode: Long, val versionName: String, val signers: Set<String>)
internal object UpdateInstallPolicy {
    fun verify(installed: ApkIdentity, downloaded: ApkIdentity, release: UpdateRelease) {
        require(downloaded.packageName == installed.packageName) { "更新包包名不匹配" }
        require(downloaded.versionCode > installed.versionCode) { "更新包版本不高于当前安装版本" }
        require(UpdateVersion.parse(downloaded.versionName) == release.version) { "更新包版本与发行版不一致" }
        require(installed.signers.isNotEmpty() && downloaded.signers == installed.signers) { "更新包签名不匹配" }
    }
}
