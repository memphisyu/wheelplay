package com.shilapi.xcertplay.web

import android.content.Context
import com.shilapi.xcertplay.network.TeslaHttpConfig
import com.shilapi.xcertplay.network.TeslaHttpMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class TeslaHttpPreferencesTest {
    @Test fun vpnModePreservesRootSettingsAndUsesASeparateSavedAddress() {
        val context = RuntimeEnvironment.getApplication()
        val root = TeslaHttpConfig(true, "100.96.0.2", 81, 444)
        TeslaHttpCompatibility.save(context, root)
        val vpn = root.copy(mode = TeslaHttpMode.VPN, vpnAddress = "6.6.6.7")
        TeslaHttpCompatibility.save(context, vpn)
        assertEquals(vpn, TeslaHttpCompatibility.config(context))
        assertEquals("6.6.6.7", vpn.accessAddress)
        assertEquals("100.96.0.2", vpn.copy(mode = TeslaHttpMode.ROOT).accessAddress)
        assertNull(TeslaHttpCompatibility.plan(vpn, listOf(HttpDownstream("ap0", 7, "192.168.43.1/24")), 8080, 8443, true))
    }

    @Test fun invalidModeOrReservedPlaceholderFailsClosed() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", true).putString("mode", "UNKNOWN").apply()
        assertFalse(TeslaHttpCompatibility.config(context).enabled)
        prefs.edit().putString("mode", "VPN").putString("vpn_address", TeslaHttpConfig.VPN_PLACEHOLDER_ADDRESS).apply()
        assertFalse(TeslaHttpCompatibility.config(context).enabled)
    }

    @Test fun modeIsOptInAndSavedAddressAndDomainSurviveReload() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(TeslaHttpConfig(), TeslaHttpCompatibility.config(context))
        val value = TeslaHttpConfig(true, "100.96.0.2", 81, 444)
        TeslaHttpCompatibility.save(context, value)
        assertEquals(value, TeslaHttpCompatibility.config(context))
        TeslaHttpCompatibility.save(context, value.copy(enabled = false))
        assertFalse(TeslaHttpCompatibility.config(context).enabled)
        assertEquals("100.96.0.2", TeslaHttpCompatibility.config(context).address)
        assertEquals(81, TeslaHttpCompatibility.config(context).httpMappingPort)
    }

    @Test fun olderPreferencesKeepDefaultPortAndInvalidPortsFailClosed() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", true).putString("address", "100.96.0.2").apply()
        assertEquals(TeslaHttpConfig(true, "100.96.0.2"), TeslaHttpCompatibility.config(context))
        prefs.edit().putInt("http_mapping_port", 0).apply()
        assertEquals(TeslaHttpConfig(), TeslaHttpCompatibility.config(context))
    }

    @Test fun invalidStoredConfigCannotStartOrAdvertiseAnArbitraryAddress() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", true).putString("address", "0.0.0.0").apply()
        assertEquals(TeslaHttpConfig(), TeslaHttpCompatibility.config(context))
        prefs.edit().putString("address", TeslaHttpConfig.DEFAULT_ADDRESS)
            .putInt("https_mapping_port", 0).apply()
        assertEquals(TeslaHttpConfig(), TeslaHttpCompatibility.config(context))
    }
}
