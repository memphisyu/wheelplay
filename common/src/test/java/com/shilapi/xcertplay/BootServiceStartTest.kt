package com.shilapi.xcertplay
import android.content.Context
import android.content.Intent
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [29])
class BootServiceStartTest {
    @Test fun enabledBootStartsListenerServiceWithoutActivity() {
        val context = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveAutoStartOnBoot(context,true)
        BootReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED))
        val start=Shadows.shadowOf(context).nextStartedService
        assertEquals(DiPlaySessionService::class.java.name,start.component?.className)
        assertEquals(DiPlaySessionService.ACTION_BACKGROUND_START,start.action)
        assertNull(Shadows.shadowOf(context).nextStartedActivity)
    }
    @Test fun disabledBootStartsNothing() {
        val context = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveAutoStartOnBoot(context,false)
        BootReceiver().onReceive(context,Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(Shadows.shadowOf(context).nextStartedService)
    }
    @Test fun hostLaunchCommandRejectsShellInjection() {
        assertTrue(RootKeepAlive.hostLaunchCommand("moe.feng.wheelplay.xpengaudio").contains("/com.shilapi.xcertplay.CarPlayHostActivity"))
        assertThrows(IllegalArgumentException::class.java) {RootKeepAlive.hostLaunchCommand("pkg;reboot")}
    }
    @Test fun rootOptInIsDistinctFromWhitelistOwnershipAndMigratesOldChoice() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=context.getSharedPreferences("root_keepalive",Context.MODE_PRIVATE)
        prefs.edit().clear().commit(); assertFalse(RootKeepAlive.isEnabled(context))
        prefs.edit().putBoolean("added",true).commit(); assertTrue(RootKeepAlive.isEnabled(context))
        prefs.edit().putBoolean("enabled",false).commit(); assertFalse(RootKeepAlive.isEnabled(context))
        prefs.edit().clear().putBoolean("enabled",true).commit(); assertTrue(RootKeepAlive.isEnabled(context))
        assertFalse(prefs.getBoolean("added",false))
    }
}
