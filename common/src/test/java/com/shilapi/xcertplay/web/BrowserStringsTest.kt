package com.shilapi.xcertplay.web

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "en-rUS")
class BrowserStringsTest {
    @Test
    fun renderedCopyIsHtmlEscapedAndLiteralBracesAndFormatArgumentsSurvive() {
        val template =
            """
            <p>{{web_pair_start_display}}</p>
            <p>{{web_https_is_disabled_enable_it_in_the_app_s}}</p>
            <p>{{web_transport_touch_connected}}</p>
            <input pattern="[0-9]{6}">
            <style>.screen { color: red; }</style>
            <script>const state = { active: true };</script>
        """
                .trimIndent()
        assertEquals(
            """
                <p>Pair &amp; start display</p>
                <p>HTTPS is disabled. Enable it in the app&#39;s Web service settings.</p>
                <p>%1${'$'}s%2${'$'}s · Touch connected</p>
                <input pattern="[0-9]{6}">
                <style>.screen { color: red; }</style>
                <script>const state = { active: true };</script>
            """
                .trimIndent(),
            BrowserStrings.render(RuntimeEnvironment.getApplication(), template),
        )
    }
}
