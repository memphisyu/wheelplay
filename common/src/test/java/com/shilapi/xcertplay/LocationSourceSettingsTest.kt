package com.shilapi.xcertplay

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
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
class LocationSourceSettingsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun openSettings(activity: DiPlayActivity) {
        views(activity.window.decorView).filterIsInstance<BottomNavigationView>().single().selectedItemId = 3
    }
    private fun sourceRow(activity: DiPlayActivity) = views(activity.window.decorView)
        .filterIsInstance<ServerUi.PreferenceRow>().single { it.contentDescription.toString().startsWith("定位来源，") }
    private fun edit(activity: DiPlayActivity, selected: Int, save: Boolean = true) {
        sourceRow(activity).performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.listView.performItemClick(dialog.listView.getChildAt(selected), selected, selected.toLong())
        dialog.getButton(if (save) AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun defaultUsesIphoneAndDoesNotRequestLocationPermission() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        openSettings(activity)
        assertEquals("定位来源，iPhone 自身定位（默认）", sourceRow(activity).contentDescription)
        assertFalse(AirPlayPersistence.loadLocationReportingEnabled(context))
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(shadowOf(activity).lastRequestedPermission)
        controller.pause().stop().destroy()
    }

    @Test fun changingSourcesPersistsAcrossScreensAndAppliesOnNextConnection() {
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        openSettings(activity)
        edit(activity, 1)
        assertTrue(AirPlayPersistence.loadLocationReportingEnabled(context))
        assertEquals("定位来源，Android 设备定位", sourceRow(activity).contentDescription)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertNull(shadowOf(activity).lastRequestedPermission)
        controller.pause().stop().destroy()
        val next = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        openSettings(next.get())
        assertEquals("定位来源，Android 设备定位", sourceRow(next.get()).contentDescription)
        edit(next.get(), 0)
        assertFalse(AirPlayPersistence.loadLocationReportingEnabled(context))
        assertEquals("定位来源，iPhone 自身定位（默认）", sourceRow(next.get()).contentDescription)
        next.pause().stop().destroy()
    }

    @Test fun cancellingSelectionKeepsPreviousLocationSource() {
        AirPlayPersistence.saveLocationReportingEnabled(context, true)
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        openSettings(activity)
        edit(activity, 0, save = false)
        assertTrue(AirPlayPersistence.loadLocationReportingEnabled(context))
        assertEquals("定位来源，Android 设备定位", sourceRow(activity).contentDescription)
        controller.pause().stop().destroy()
    }
}
