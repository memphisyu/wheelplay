package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.network.TeslaHttpConfig
import com.shilapi.xcertplay.network.TeslaHttpMode
import com.shilapi.xcertplay.web.TeslaHttpCompatibility
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class RootStartupPolicyTest {
    @Test fun disabledCompatibilityAndVpnDoNotRequestRoot() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(rootStartupRequested(context))
        TeslaHttpCompatibility.save(context, TeslaHttpConfig(enabled = true, mode = TeslaHttpMode.VPN))
        assertFalse(rootStartupRequested(context))
    }

    @Test fun independentXpengKeepaliveStillRequestsRootWithVpnSelected() {
        val context = RuntimeEnvironment.getApplication()
        TeslaHttpCompatibility.save(context, TeslaHttpConfig(enabled = true, mode = TeslaHttpMode.VPN))
        context.getSharedPreferences("root_keepalive", Context.MODE_PRIVATE).edit().putBoolean("enabled", true).commit()
        assertTrue(rootStartupRequested(context))
    }

    @Test fun onlyEnabledRootRoutingRequestsRootWithoutKeepalive() {
        val context = RuntimeEnvironment.getApplication()
        TeslaHttpCompatibility.save(context, TeslaHttpConfig(enabled = true))
        assertTrue(rootStartupRequested(context))
        TeslaHttpCompatibility.save(context, TeslaHttpConfig(enabled = false))
        assertFalse(rootStartupRequested(context))
    }
}
