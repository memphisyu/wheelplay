package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.shilapi.xcertplay.network.TeslaHttpConfig
import com.shilapi.xcertplay.network.TeslaHttpMode
import com.shilapi.xcertplay.web.RootAccess
import com.shilapi.xcertplay.web.TeslaHttpCompatibility
import com.shilapi.xcertplay.web.WebSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w360dp-h800dp", shadows = [TeslaVpnSettingsUiTest.ConsentShadow::class])
class TeslaVpnSettingsUiTest {
    @Implements(VpnService::class)
    class ConsentShadow : org.robolectric.shadows.ShadowService() {
        companion object {
            @JvmStatic @Implementation fun prepare(context: Context): Intent = Intent("wheelplay.test.VPN_CONSENT")
        }
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun all(activity: DiPlayActivity) = views(activity.window.decorView)
    private fun openSettings(activity: DiPlayActivity) {
        all(activity).filterIsInstance<BottomNavigationView>().single().selectedItemId = 3
    }
    private fun enable(activity: DiPlayActivity) = all(activity).filterIsInstance<MaterialSwitch>()
        .single { it.contentDescription?.toString() == "启用免 Root Tesla 兼容" }

    @Test fun nonRootUserCanSelectVpnAndDeniedConsentKeepsItDisabled() {
        val original = RootAccess.authorize
        var rootRequests = 0
        RootAccess.startupChecked = false
        RootAccess.authorize = { rootRequests++; throw java.io.IOException("No root") }
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            openSettings(activity)
            assertFalse(TeslaHttpCompatibility.config(activity).enabled)
            all(activity).single { it.contentDescription?.toString()?.startsWith("连接方案，") == true }.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
            assertEquals(TeslaHttpMode.VPN, TeslaHttpCompatibility.config(activity).mode)
            assertFalse(all(activity).filterIsInstance<MaterialButton>().any { it.text.toString() == "请求 Root 权限" })
            enable(activity).isChecked = true
            val consent = shadowOf(activity).nextStartedActivity
            assertEquals("wheelplay.test.VPN_CONSENT", consent.action)
            assertFalse(TeslaHttpCompatibility.config(activity).enabled)
            shadowOf(activity).receiveResult(consent, Activity.RESULT_CANCELED, null)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(TeslaHttpCompatibility.config(activity).enabled)
            assertEquals(0, rootRequests)
        } finally {
            RootAccess.authorize = original
            controller.pause().stop().destroy(); WebSession.stop()
        }
    }

    @Test fun acceptedConsentEnablesVpnAndSwitchingModesTurnsItOff() {
        val context = RuntimeEnvironment.getApplication()
        TeslaHttpCompatibility.save(context, TeslaHttpConfig(mode = TeslaHttpMode.VPN))
        val controller = Robolectric.buildActivity(DiPlayActivity::class.java).setup()
        val activity = controller.get()
        try {
            openSettings(activity)
            enable(activity).isChecked = true
            val consent = shadowOf(activity).nextStartedActivity
            shadowOf(activity).receiveResult(consent, Activity.RESULT_OK, null)
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(TeslaHttpCompatibility.config(activity).enabled)
            all(activity).single { it.contentDescription?.toString()?.startsWith("连接方案，") == true }.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            dialog.listView.performItemClick(dialog.listView.getChildAt(0), 0, 0)
            assertEquals(TeslaHttpMode.ROOT, TeslaHttpCompatibility.config(activity).mode)
            assertFalse(TeslaHttpCompatibility.config(activity).enabled)
        } finally { controller.pause().stop().destroy(); WebSession.stop() }
    }
}
