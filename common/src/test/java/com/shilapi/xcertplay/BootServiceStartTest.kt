package com.shilapi.xcertplay
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
@RunWith(RobolectricTestRunner::class)
class BootServiceStartTest {
    @Test fun enabledBootStartsListenerServiceWithoutActivity() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        AirPlayPersistence.saveAutoStartOnBoot(context,true)
        BootReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED))
        val start=Shadows.shadowOf(context).nextStartedService
        assertEquals(DiPlaySessionService::class.java.name,start.component?.className)
        assertEquals(DiPlaySessionService.ACTION_BACKGROUND_START,start.action)
        assertNull(Shadows.shadowOf(context).nextStartedActivity)
    }
    @Test fun disabledBootStartsNothing() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        AirPlayPersistence.saveAutoStartOnBoot(context,false)
        BootReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(Shadows.shadowOf(context).nextStartedService)
    }
    @Test fun hostLaunchCommandRejectsShellInjection() {
        assertTrue(RootKeepAlive.hostLaunchCommand("moe.feng.wheelplay.xpengaudio").contains("/com.shilapi.xcertplay.CarPlayHostActivity"))
        assertThrows(IllegalArgumentException::class.java) {RootKeepAlive.hostLaunchCommand("pkg;reboot")}
    }
}
