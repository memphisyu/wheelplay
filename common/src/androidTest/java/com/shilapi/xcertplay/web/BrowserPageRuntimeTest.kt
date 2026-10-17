package com.shilapi.xcertplay.web

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Exercise Android's real HTTP/asset stack without requiring a paired phone or native RTC. */
@RunWith(AndroidJUnit4::class)
class BrowserPageRuntimeTest {
    @Test fun entryPagesAndMediaAssetsAreAccessibleOverHttp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (tag in listOf("en-US", "zh-CN", "zh-TW")) {
            val owner = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(tag)))
            })
            val server = LanWebServer(owner, "123456", port = 0)
            try {
                server.start(5000, true)
                fun get(path: String, mime: String): String {
                    val connection = URL("http://127.0.0.1:${server.listeningPort}$path")
                        .openConnection() as HttpURLConnection
                    try {
                        connection.connectTimeout = 3000
                        connection.readTimeout = 3000
                        assertEquals("$tag $path", 200, connection.responseCode)
                        assertTrue("$path MIME", connection.contentType.orEmpty().startsWith(mime))
                        assertEquals("no-store", connection.getHeaderField("Cache-Control"))
                        assertEquals("nosniff", connection.getHeaderField("X-Content-Type-Options"))
                        assertEquals("no-referrer", connection.getHeaderField("Referrer-Policy"))
                        assertTrue(connection.getHeaderField("Content-Security-Policy").orEmpty()
                            .contains("frame-ancestors 'none'"))
                        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } finally {
                        connection.disconnect()
                    }
                }
                val expectedHtml = owner.assets.open("web/index.html").bufferedReader().use { it.readText() }
                for (path in listOf("/", "/index.html", "/?code=123456")) {
                    val html = get(path, "text/html")
                    // XPENG currently serves a static Chinese page, independent of Android locale.
                    assertEquals("$tag $path", expectedHtml, html)
                    assertTrue(html.contains("Wheelplay 小鹏定制版"))
                    assertTrue(html.contains("pattern=\"[0-9]{6}\""))
                    assertTrue(html.contains("type=\"module\" src=\"/app.js\""))
                    assertFalse(html.contains("{{"))
                }
                for (asset in listOf("app.js", "browser-media.js", "media-probe.js", "flac-mp4.js",
                    "mse-music.js", "browser-audio.js", "audio-worklet.js", "style.css")) {
                    val expected = owner.assets.open("web/$asset").bufferedReader().use { it.readText() }
                    assertEquals("$tag $asset", expected,
                        get("/$asset", if (asset.endsWith(".css")) "text/css" else "text/javascript"))
                }
            } finally {
                server.stop()
            }
        }
    }
}
