package com.shilapi.xcertplay

import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.i18n.userMessage
import com.shilapi.xcertplay.update.*
import com.shilapi.xcertplay.web.WebSession
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class NightAppearanceSettingsTest {
    @Test fun scheduleAndExistingChoicesSurvivePreferenceReload() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(NightSchedule(1110, 420), AirPlayPersistence.loadNightSchedule(context))
        AirPlayPersistence.saveNightSchedule(context, NightSchedule(1200, 390))
        assertEquals(NightSchedule(1200, 390), AirPlayPersistence.loadNightSchedule(context))
        for (mode in CarPlayAppearance.entries) {
            AirPlayPersistence.saveAppearance(context, mode)
            assertEquals(mode, AirPlayPersistence.loadAppearance(context))
        }
        WebSession.setBrowserNight(context, false)
        assertFalse(WebSession.appearanceNight(context))
        WebSession.setBrowserNight(context, true)
        assertTrue(WebSession.appearanceNight(context))
        WebSession.setBrowserNight(context, null)
    }
    @Test fun customUiAndDeferredUpdateFailuresUseTheCurrentLocale() {
        val context = RuntimeEnvironment.getApplication()
        val failure = runCatching { UpdateCatalog.checksum("invalid", "update.apk") }.exceptionOrNull()!!
        val locales = listOf("en-US" to "Check for updates", "zh-CN" to "检查更新", "zh-TW" to "檢查更新")
        for ((tag, expected) in locales) {
            val owner = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
            assertEquals(expected, owner.getString(R.string.xp_check_updates))
            assertEquals(owner.getString(R.string.xp_checksum_missing), failure.userMessage(owner))
            assertFalse(owner.getString(R.string.xp_current, "1.0.2").contains("%1"))
            assertTrue(owner.getString(R.string.xp_current, "1.0.2").contains("1.0.2"))
        }
    }
}
