package com.shilapi.xcertplay.update

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class AppUpdatesTest {
    private fun json(tag: String, preview: Boolean = true, apk: String = "WheelPlay-Xpeng-$tag.apk", extra: String = "") =
        """{"tag_name":"$tag","name":"$tag","prerelease":$preview,"assets":[{"name":"$apk"},{"name":"$tag.sha256"}]$extra}"""
    private fun release(tag: String = "v1.0.1-rc.7") = UpdateCatalog.parse("[${json(tag)}]", UpdateChannel.GITEE, true).single()
    @Test fun numericRcOrderingAndFinalReleaseAvoidDowngrades() {
        assertTrue(UpdateVersion.parse("1.0.1-rc.10")!! > UpdateVersion.parse("v1.0.1-rc.9")!!)
        assertTrue(UpdateVersion.parse("1.0.1")!! > UpdateVersion.parse("1.0.1-rc.99")!!)
        assertEquals(UpdateVersion.parse("1.0"), UpdateVersion.parse("v1.0.0"))
        assertNull(UpdateVersion.parse("1.0.1-rc.7/../../evil"))
        assertNull(UpdateVersion.parse("1.0.1-rc.999999999999999999999"))
    }
    @Test fun unorderedGiteeResponseSelectsHighestVersionNotFirstItem() {
        val list = UpdateCatalog.parse("[${json("v1.0.0",false)},${json("v1.0.1-rc.7")},${json("v1.0.1-rc.5")}]", UpdateChannel.GITEE, true)
        assertEquals("v1.0.1-rc.7", UpdateCatalog.latest(list, "1.0.1-rc.6")?.tag)
        assertNull(UpdateCatalog.latest(list, "1.0.1-rc.7"))
        assertNull(UpdateCatalog.latest(list, "1.0.1"))
    }
    @Test fun stableOnlyAndDraftsAreFiltered() {
        val raw = "[${json("v1.0.1-rc.7")},${json("v1.0.0",false)},${json("v2.0.0",false,extra=",\"draft\":true")}]"
        assertEquals(listOf("v1.0.0"), UpdateCatalog.parse(raw,UpdateChannel.GITHUB,false).map { it.tag })
    }
    @Test fun onlyOneSafeApkAndMatchingChecksumAreAccepted() {
        assertTrue(UpdateCatalog.parse("[${json("v1.0.1-rc.7",apk="../bad.apk")}]",UpdateChannel.GITEE,true).isEmpty())
        val raw = json("v1.0.1-rc.7").replace("\"assets\":[", "\"assets\":[{\"name\":\"other.apk\"},")
        assertTrue(UpdateCatalog.parse("[$raw]",UpdateChannel.GITEE,true).isEmpty())
        assertTrue(UpdateCatalog.parse("[${json("v1.0.1-rc.7").replace(".sha256", ".zip")}]",UpdateChannel.GITHUB,true).isEmpty())
    }
    @Test fun assetHostIsReconstructedFromSelectedChannelNotUntrustedApiUrl() {
        val r = UpdateCatalog.parse("[${json("v1.0.1-rc.7").replace("\"name\":\"WheelPlay", "\"browser_download_url\":\"https://evil.example/app.apk\",\"name\":\"WheelPlay")}]",UpdateChannel.GITEE,true).single()
        assertTrue(r.apkUrl.startsWith("https://gitee.com/memphisyu/wheelplay/releases/download/"))
        assertEquals(r,UpdateRelease.restore(r.json()))
    }
    @Test fun checksumRequiresExactUniqueApkEntryAndSupportsBinaryMarkers() {
        val hash = "a".repeat(64)
        assertEquals(hash,UpdateCatalog.checksum("$hash *app.apk\r\n${"b".repeat(64)}  source.zip", "app.apk"))
        assertThrows(IllegalArgumentException::class.java) { UpdateCatalog.checksum("$hash  other.apk", "app.apk") }
        assertThrows(IllegalArgumentException::class.java) { UpdateCatalog.checksum("$hash app.apk\n$hash app.apk", "app.apk") }
    }
    @Test fun actualFileHashDetectsCorruption() {
        val file=File.createTempFile("update", ".apk")
        try {
            file.writeText("signed APK fixture bytes")
            val hash=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            UpdateCatalog.verifyFile(file,hash)
            file.appendText("tampered")
            assertThrows(IllegalArgumentException::class.java) { UpdateCatalog.verifyFile(file,hash) }
        } finally { file.delete() }
    }
    @Test fun pagingUsesSelectedChannelAndNetworkFailureIsNotLatestVersion() {
        val urls=mutableListOf<String>()
        val client=UpdateClient { url, _ -> urls+=url; if(url.endsWith("page=1")) "[${List(100){json("v1.0.0",false)}.joinToString(",")}]" else "[${json("v1.0.1-rc.7")}]" }
        assertEquals("v1.0.1-rc.7",client.check(UpdateChannel.GITEE,true,"1.0.1-rc.6")?.tag)
        assertEquals(2,urls.size); assertTrue(urls.all { it.startsWith("https://gitee.com/api/v5/") })
        assertThrows(IllegalStateException::class.java) { UpdateClient { _,_->error("offline") }.check(UpdateChannel.GITHUB,true,"1.0.0") }
    }
    @Test fun downloadedApkMustMatchPackageVersionAndCurrentSigner() {
        val installed=ApkIdentity("moe.feng.wheelplay.xpengaudio",36,"1.0.1-rc.6",setOf("existing"))
        val next=installed.copy(versionCode=37,versionName="1.0.1-rc.7")
        UpdateInstallPolicy.verify(installed,next,release())
        for(bad in listOf(next.copy(packageName="other"),next.copy(versionCode=36),next.copy(versionName="1.0.1-rc.8"),next.copy(signers=setOf("foreign")),next.copy(signers=emptySet()))) {
            assertThrows(IllegalArgumentException::class.java) { UpdateInstallPolicy.verify(installed,bad,release()) }
        }
    }

    @Test fun liveApiFixturesFromBothChannelsSelectSameRcAndNeverOfferAnOlderBuild() {
        for(channel in UpdateChannel.entries) {
            val name=channel.name.lowercase()
            val raw=javaClass.getResourceAsStream("/updates/$name.json")!!.bufferedReader().readText()
            val client=UpdateClient { _,_->raw }
            assertEquals("v1.0.1-rc.5",client.check(channel,true,"1.0.1-rc.4")?.tag)
            assertNull(client.check(channel,true,"1.0.1-rc.6"))
        }
    }
    @Test fun privateStagingChecksCopiedBytesAndRemovesFailedTemporaryFiles() {
        val directory=java.nio.file.Files.createTempDirectory("wheelplay-update").toFile()
        val download=File(directory,"download.apk").apply { writeText("package bytes") }
        val privateDirectory=File(directory,"private")
        val hash=MessageDigest.getInstance("SHA-256").digest(download.readBytes()).joinToString("") { "%02x".format(it) }
        try {
            val staged=UpdateCatalog.stageFile(download,privateDirectory,"update",hash)
            download.writeText("external file changed")
            UpdateCatalog.verifyFile(staged,hash)
            assertThrows(IllegalArgumentException::class.java) { UpdateCatalog.stageFile(download,privateDirectory,"bad",hash) }
            assertEquals(listOf("update.apk"),privateDirectory.listFiles()!!.map { it.name })
            assertThrows(IllegalArgumentException::class.java) { UpdateCatalog.stageFile(download,privateDirectory,"../escape",hash) }
        } finally { directory.deleteRecursively() }
    }
}
