package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.airplay.SafeAreaRect
import com.shilapi.xcertplay.web.WebSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w360dp-h800dp")
class CarPlayExperienceSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun row(activity: DiPlayActivity, title: String) = views(activity.window.decorView)
        .filterIsInstance<ServerUi.PreferenceRow>().single { it.contentDescription.toString().startsWith("$title，") }
    private fun openSettings(activity: DiPlayActivity) {
        views(activity.window.decorView).filterIsInstance<BottomNavigationView>().single().selectedItemId = 3
    }
    private fun choose(activity: DiPlayActivity, title: String, index: Int, save: Boolean = true) {
        row(activity, title).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.listView.performItemClick(dialog.listView.getChildAt(index), index, index.toLong())
        dialog.getButton(if (save) AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun hostBlockedByMissingAuthenticationDoesNotRequestMicrophonePermission() {
        AirPlayPersistence.saveMicrophoneSource(context, CarPlayMicrophoneSource.BROWSER)
        val controller = Robolectric.buildActivity(CarPlayHostActivity::class.java,
            android.content.Intent("android.hardware.usb.action.USB_DEVICE_ATTACHED")).setup()
        val host = controller.get()
        assertTrue(host.isFinishing)
        assertNull(shadowOf(host).lastRequestedPermission)
        controller.pause().stop().destroy()
    }

    @Test fun defaultsPreserveExistingBehaviorAndLegacyBrowserMicrophoneMigrates() {
        assertEquals(CarPlayAppearance.SYSTEM, AirPlayPersistence.loadAppearance(context))
        assertEquals(BrowserOfflinePolicy.KEEP, AirPlayPersistence.loadBrowserOfflinePolicy(context))
        assertNull(AirPlayPersistence.loadSafeMargins(context))
        assertEquals(CarPlayMicrophoneSource.ANDROID, AirPlayPersistence.loadMicrophoneSource(context))
        context.getSharedPreferences("xcertplay_airplay", 0).edit().putBoolean("browser_microphone", true).commit()
        assertEquals(CarPlayMicrophoneSource.BROWSER, AirPlayPersistence.loadMicrophoneSource(context))
        WebSession.setMicrophoneSource(context, CarPlayMicrophoneSource.DISABLED)
        WebSession.setMediaBufferMillis(context, 1000)
        WebSession.setBrowserAudioPlayback(context, true)
        assertEquals(CarPlayMicrophoneSource.DISABLED, AirPlayPersistence.loadMicrophoneSource(context))
        WebSession.audio.createRoute().use { assertFalse(it.microphoneEnabled) }
        assertFalse(AirPlayPersistence.loadBrowserMicrophone(context))
        WebSession.setMicrophoneSource(context, CarPlayMicrophoneSource.ANDROID)
    }

    @Test fun sourceAndAppearanceChoicesSaveAndCancellationDoesNotOverwriteThem() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get(); openSettings(activity)
        choose(activity, "CarPlay 昼夜模式", 2)
        assertEquals(CarPlayAppearance.NIGHT, AirPlayPersistence.loadAppearance(context))
        choose(activity, "CarPlay 昼夜模式", 1, save = false)
        assertEquals(CarPlayAppearance.NIGHT, AirPlayPersistence.loadAppearance(context))
        choose(activity, "音频输出", 1)
        assertTrue(AirPlayPersistence.loadBrowserAudioPlayback(context))
        choose(activity, "麦克风来源", 2)
        assertEquals(CarPlayMicrophoneSource.DISABLED, AirPlayPersistence.loadMicrophoneSource(context))
        choose(activity, "浏览器离线后的处理", 2)
        assertEquals(BrowserOfflinePolicy.FIVE_MINUTES, AirPlayPersistence.loadBrowserOfflinePolicy(context))
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.pause().stop().destroy()
        val next = Robolectric.buildActivity(DiPlayActivity::class.java).setup(); openSettings(next.get())
        assertEquals("CarPlay 昼夜模式，始终夜间", row(next.get(), "CarPlay 昼夜模式").contentDescription)
        assertEquals("麦克风来源，禁用麦克风", row(next.get(), "麦克风来源").contentDescription)
        next.pause().stop().destroy()
        WebSession.setMicrophoneSource(context, CarPlayMicrophoneSource.ANDROID)
    }

    @Test fun safeMarginsRetainLegacyMappingUntilEditedAndScaleAcrossResolutions() {
        val legacy = SafeAreaRect(100, 50, 1100, 650)
        AirPlayPersistence.saveSafeAreaRect(context, 1280, 720, legacy)
        assertEquals(legacy, AirPlayPersistence.loadSafeAreaRect(context, 1280, 720))
        AirPlayPersistence.saveSafeMargins(context, CarPlaySafeMargins(10, 20, 5, 15))
        assertEquals(SafeAreaRect(100, 100, 1700, 800), AirPlayPersistence.loadSafeAreaRect(context, 2000, 1000))
        assertEquals(SafeAreaRect(50, 50, 850, 400), AirPlayPersistence.loadSafeAreaRect(context, 1000, 500))
        assertNotNull(CarPlaySafeMargins(40, 40, 40, 40).rectangle(1, 1))
        assertNull(CarPlaySafeMargins.decode("50,0,0,0"))
        AirPlayPersistence.saveSafeMargins(context, CarPlaySafeMargins())
        assertNull(AirPlayPersistence.loadSafeAreaRect(context, 1280, 720))
    }

    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    @Test fun safeAreaEditorRejectsInvalidInputAndRestoresFullScreen() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get(); openSettings(activity)
        row(activity, "画面安全区域").performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val inputs = views(dialog.window!!.decorView).filterIsInstance<EditText>()
        inputs[0].setText("41")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertNull(AirPlayPersistence.loadSafeMargins(context))
        inputs[0].setText("10"); inputs[1].setText("20"); inputs[2].setText("5"); inputs[3].setText("15")
        val root = dialog.window!!.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 640)
        val bitmap = android.graphics.Bitmap.createBitmap(360, 640, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        java.io.File("build/reports/material-ui/experience").apply { mkdirs() }.resolve("safe-area.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertEquals(CarPlaySafeMargins(10, 20, 5, 15), AirPlayPersistence.loadSafeMargins(context))
        row(activity, "画面安全区域").performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(CarPlaySafeMargins(), AirPlayPersistence.loadSafeMargins(context))
        assertEquals("画面安全区域，全屏", row(activity, "画面安全区域").contentDescription)
        controller.pause().stop().destroy()
    }

    @Test fun phoneReceivesSafeAreaAndNoMicrophoneFormatsWhenDisabled() {
        AirPlayPersistence.saveMicrophoneSource(context, CarPlayMicrophoneSource.DISABLED)
        AirPlayPersistence.saveSafeMargins(context, CarPlaySafeMargins(10, 20, 5, 15))
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val type = CarPlayHostActivity::class.java
        type.getDeclaredMethod("loadPersistedSettings").apply { isAccessible = true }.invoke(host)
        type.getDeclaredField("airPlayIdentity").apply { isAccessible = true }.set(host, AirPlayPersistence.loadIdentity(context))
        val sizeType = type.declaredClasses.single { it.simpleName == "DisplaySize" }
        val size = sizeType.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(1280, 720)
        val config = type.getDeclaredMethod("createAirPlayConfig", sizeType).apply { isAccessible = true }.invoke(host, size) as AirPlayConfig
        assertFalse(config.microphone)
        assertEquals(false, config.main.safeAreaDrawOutside)
        assertTrue(config.main.safeArea!!.left > 0)
        val info = AirPlayInfoPlist.build(config)
        val formats = info["audioFormats"] as List<*>
        assertTrue(formats.all { !(it as Map<*, *>).containsKey("audioInputFormats") })
        val display = (info["displays"] as List<*>).first() as Map<*, *>
        val view = (display["viewAreas"] as List<*>).first() as Map<*, *>
        val safe = view["safeArea"] as Map<*, *>
        assertEquals(false, safe["drawUIOutsideSafeArea"])
        assertTrue((safe["widthPixels"] as Int) < config.main.widthPixels)
    }
}
