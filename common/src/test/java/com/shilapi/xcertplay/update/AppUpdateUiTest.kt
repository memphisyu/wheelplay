package com.shilapi.xcertplay.update

import android.widget.LinearLayout
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29])
class AppUpdateUiTest {
    @Test fun domesticDefaultAndPreviewSelectionSurviveUiRecreationWithoutAutomaticNetwork() {
        val controller=Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_Xcertplay)
        controller.setup()
        val activity=controller.get()
        activity.getSharedPreferences("app_updates",0).edit().clear().commit()
        val first=AppUpdateUi(activity);val card=LinearLayout(activity)
        first.bind(card)
        assertTrue((0 until card.childCount).map { card.getChildAt(it) }.filterIsInstance<Button>().any { it.text.toString().contains("Gitee") })
        val toggle=(0 until card.childCount).map { card.getChildAt(it) }.filterIsInstance<MaterialSwitch>().single()
        toggle.isChecked=true
        first.close()
        val second=AppUpdateUi(activity);val next=LinearLayout(activity)
        second.bind(next)
        assertTrue((0 until next.childCount).map { next.getChildAt(it) }.filterIsInstance<MaterialSwitch>().single().isChecked)
        second.close();controller.pause().stop().destroy()
    }

    @Test fun downloadTaskSurvivesUiRecreationAndCanBeClearedAfterCompletion() {
        val controller=Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.Theme_Xcertplay);controller.setup()
        val activity=controller.get();val prefs=activity.getSharedPreferences("app_updates",0)
        prefs.edit().clear().commit()
        val request=android.app.DownloadManager.Request(android.net.Uri.parse("https://gitee.com/memphisyu/wheelplay/releases/download/v99.0.0/update.apk"))
        val manager=activity.getSystemService(android.app.DownloadManager::class.java)
        val id=manager.enqueue(request)
        val state=org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowDownloadManager.ShadowRequest>(request)
        state.status=android.app.DownloadManager.STATUS_RUNNING;state.totalSize=100;state.bytesSoFar=50
        val release=UpdateRelease("v99.0.0","Update","Notes",UpdateChannel.GITEE,"update.apk","v99.0.0.sha256")
        prefs.edit().putLong("download",id).putString("release",release.json()).putString("sha256","a".repeat(64)).commit()
        val first=AppUpdateUi(activity);val firstCard=LinearLayout(activity);first.bind(firstCard)
        first.resume();org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue((0 until firstCard.childCount).map{firstCard.getChildAt(it)}.filterIsInstance<android.widget.TextView>().any{it.text.contains("50%")})
        first.close()
        assertEquals(id,prefs.getLong("download",-1))
        state.status=android.app.DownloadManager.STATUS_SUCCESSFUL
        val second=AppUpdateUi(activity);val card=LinearLayout(activity);second.bind(card);second.resume()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        val buttons=(0 until card.childCount).map{card.getChildAt(it)}.filterIsInstance<Button>()
        assertEquals(android.view.View.VISIBLE,buttons.single{it.text==activity.getString(R.string.xp_verify_install)}.visibility)
        buttons.single{it.text==activity.getString(R.string.xp_clear_download)}.performClick()
        assertEquals(-1,prefs.getLong("download",-1))
        assertEquals(0,org.robolectric.Shadows.shadowOf(manager).requestCount)
        second.close();controller.pause().stop().destroy()
    }
}
