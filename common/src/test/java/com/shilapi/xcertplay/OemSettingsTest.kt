package com.shilapi.xcertplay

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.net.Uri
import android.util.AtomicFile
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayInfoPlist
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OemSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun button(activity: Activity, title: String) = views(activity.window.decorView)
        .filterIsInstance<Button>().single { it.text.toString() == title }
    private fun openSettings(activity: DiPlayActivity) {
        views(activity.window.decorView).filterIsInstance<BottomNavigationView>().single().selectedItemId = 3
    }
    private fun png(width: Int = 64, height: Int = 64): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
            it.toByteArray()
        }
    }

    @Test fun legacyDefaultMigratesOnceAndExplicitBydSurvivesReload() {
        context.getSharedPreferences("xcertplay_airplay", 0).edit().putString("oem_label", "BYD").commit()
        assertEquals("NIO", AirPlayPersistence.loadSimulatedManufacturerName(context))
        AirPlayPersistence.saveSimulatedManufacturerName(context, " BYD ")
        repeat(2) { assertEquals("BYD", AirPlayPersistence.loadSimulatedManufacturerName(context)) }
        AirPlayPersistence.saveSimulatedManufacturerName(context, "   ")
        assertEquals("NIO", AirPlayPersistence.loadSimulatedManufacturerName(context))
    }

    @Test fun interruptedIconWriteRecoversLastCompleteImageAndResetRemovesIt() {
        val original = png()
        AirPlayPersistence.saveCustomAirPlayIcon(context, original)
        val file = AirPlayPersistence.loadCustomAirPlayIconFile(context)!!
        AtomicFile(file).startWrite().use { it.write(byteArrayOf(1, 2, 3)) }
        assertArrayEquals(original, AirPlayPersistence.loadCustomAirPlayIconFile(context)!!.readBytes())
        AirPlayPersistence.clearCustomAirPlayIcon(context)
        assertNull(AirPlayPersistence.loadCustomAirPlayIconFile(context))
    }

    @Test fun phoneConfigurationAdvertisesCustomNameAndIcon() {
        val original = png()
        AirPlayPersistence.saveSimulatedManufacturerName(context, "比亚迪")
        AirPlayPersistence.saveCustomAirPlayIcon(context, original)
        // Build the real phone configuration without starting Bluetooth/USB or a session.
        val host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val type = CarPlayHostActivity::class.java
        type.getDeclaredMethod("loadPersistedSettings").apply { isAccessible = true }.invoke(host)
        type.getDeclaredField("airPlayIdentity").apply { isAccessible = true }
            .set(host, AirPlayPersistence.loadIdentity(context))
        val sizeType = type.declaredClasses.single { it.simpleName == "DisplaySize" }
        val size = sizeType.getDeclaredConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.newInstance(1280, 720)
        val config = type.getDeclaredMethod("createAirPlayConfig", sizeType).apply { isAccessible = true }
            .invoke(host, size) as AirPlayConfig
        val runtime = type.getDeclaredMethod("createRuntimeConfig").apply { isAccessible = true }
            .invoke(host) as CarPlayRuntimeConfig
        assertEquals("比亚迪", runtime.identification.manufacturer)
        val info = AirPlayInfoPlist.build(config)
        assertEquals("比亚迪", info["oemIconLabel"])
        val icon = (info["oemIcons"] as List<*>).single() as Map<*, *>
        assertEquals(64, icon["widthPixels"])
        assertEquals(64, icon["heightPixels"])
        assertArrayEquals(original, icon["imageData"] as ByteArray)
    }

    @Test fun nameEditorPersistsAndNewSettingsScreenShowsSavedName() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        openSettings(activity)
        views(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == "车厂名称" }
            .parent.let { (it as View).performClick() }
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        views(dialog.window!!.decorView).filterIsInstance<EditText>().single().setText("  比亚迪  ")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("比亚迪", AirPlayPersistence.loadSimulatedManufacturerName(context))
        controller.pause().stop().destroy()
        val next = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        openSettings(next.get())
        assertTrue(views(next.get().window.decorView).filterIsInstance<TextView>().any { it.text.toString() == "比亚迪" })
        next.pause().stop().destroy()
    }

    @Test fun previewAndIndependentIconResetKeepCustomNameAndConfirmedResetRestoresBoth() {
        AirPlayPersistence.saveSimulatedManufacturerName(context, "Custom car")
        AirPlayPersistence.saveCustomAirPlayIcon(context, png())
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        openSettings(activity)
        val preview = views(activity.window.decorView).filterIsInstance<ImageView>()
            .single { it.contentDescription == "CarPlay 车厂图标预览" }
        assertNotNull(preview.drawable)
        val body = preview.parent.parent as View
        body.measure(View.MeasureSpec.makeMeasureSpec(312, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        body.layout(0, 0, 312, body.measuredHeight)
        val screenshot = Bitmap.createBitmap(body.width, body.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screenshot).apply { drawColor(ServerUi.BG) }
        body.draw(canvas)
        File("build/reports/material-ui/oem").apply { mkdirs() }.resolve("settings.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        button(activity, "恢复默认名称与图标").performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Custom car", AirPlayPersistence.loadSimulatedManufacturerName(context))
        assertNotNull(AirPlayPersistence.loadCustomAirPlayIconFile(context))
        button(activity, "恢复默认图标").performClick()
        assertEquals("Custom car", AirPlayPersistence.loadSimulatedManufacturerName(context))
        assertNull(AirPlayPersistence.loadCustomAirPlayIconFile(context))
        assertFalse(button(activity, "恢复默认图标").isEnabled)
        AirPlayPersistence.saveCustomAirPlayIcon(context, png())
        button(activity, "恢复默认名称与图标").performClick()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("NIO", AirPlayPersistence.loadSimulatedManufacturerName(context))
        assertNull(AirPlayPersistence.loadCustomAirPlayIconFile(context))
        controller.pause().stop().destroy()
    }

    @Test fun pickerRequestsOnlyImagesAndCancellationKeepsCurrentIcon() {
        val original = png()
        AirPlayPersistence.saveCustomAirPlayIcon(context, original)
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        openSettings(activity)
        button(activity, "选择车厂图标").performClick()
        val picker = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_GET_CONTENT, picker.action)
        assertEquals("image/*", picker.type)
        shadowOf(activity).receiveResult(picker, Activity.RESULT_CANCELED, null)
        assertArrayEquals(original, AirPlayPersistence.loadCustomAirPlayIconFile(context)!!.readBytes())
        assertNull(shadowOf(activity).nextStartedActivity)
        button(activity, "选择车厂图标").performClick()
        val nextPicker = shadowOf(activity).nextStartedActivity
        val uri = Uri.parse("content://images/chosen.png")
        shadowOf(activity).receiveResult(nextPicker, Activity.RESULT_OK, Intent().setData(uri))
        val crop = shadowOf(activity).nextStartedActivity
        assertEquals(ImageCropActivity::class.java.name, crop.component!!.className)
        assertEquals(uri, crop.data)
        assertTrue(crop.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        shadowOf(activity).receiveResult(crop, Activity.RESULT_CANCELED, null)
        assertArrayEquals(original, AirPlayPersistence.loadCustomAirPlayIconFile(context)!!.readBytes())
        controller.pause().stop().destroy()
    }

    private fun waitForText(activity: Activity, text: String) {
        val deadline = System.nanoTime() + 8_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == text }) return
            Thread.sleep(10)
        }
        fail("Image loading should complete: $text")
    }

    @Test fun selectedImageIsCroppedToDurableSquarePngWithoutChangingName() {
        AirPlayPersistence.saveSimulatedManufacturerName(context, "Custom car")
        val source = File(context.cacheDir, "oem-source.png").apply { writeBytes(png(240, 120)) }
        val controller = Robolectric.buildActivity(ImageCropActivity::class.java,
            Intent().setData(Uri.fromFile(source))).setup()
        val activity = controller.get()
        val root = activity.findViewById<View>(android.R.id.content)
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 360, 800)
        waitForText(activity, "拖动调整位置，双指缩放；保存为正方形图标")
        button(activity, "保存图标").performClick()
        assertEquals(Activity.RESULT_OK, shadowOf(activity).resultCode)
        source.delete()
        val file = AirPlayPersistence.loadCustomAirPlayIconFile(context)!!
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        assertEquals("image/png", bounds.outMimeType)
        assertEquals(1024, bounds.outWidth)
        assertEquals(1024, bounds.outHeight)
        assertEquals("Custom car", AirPlayPersistence.loadSimulatedManufacturerName(context))
        controller.pause().stop().destroy()
    }

    @Test fun invalidImageOrCancelledCropDoesNotReplaceSavedIcon() {
        val original = png()
        AirPlayPersistence.saveCustomAirPlayIcon(context, original)
        val source = File(context.cacheDir, "invalid-image.png").apply { writeText("not an image") }
        val controller = Robolectric.buildActivity(ImageCropActivity::class.java,
            Intent().setData(Uri.fromFile(source))).setup()
        val activity = controller.get()
        waitForText(activity, "无法读取图片，请返回重新选择 PNG 或 JPEG 图片")
        button(activity, "保存图标").performClick()
        assertFalse(activity.isFinishing)
        button(activity, "取消").performClick()
        assertEquals(Activity.RESULT_CANCELED, shadowOf(activity).resultCode)
        assertArrayEquals(original, AirPlayPersistence.loadCustomAirPlayIconFile(context)!!.readBytes())
        controller.pause().stop().destroy()
    }
}
