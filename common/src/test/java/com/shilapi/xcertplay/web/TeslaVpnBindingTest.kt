package com.shilapi.xcertplay.web

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN", sdk = [29], shadows = [TeslaVpnBindingTest.ConsentShadow::class])
class TeslaVpnBindingTest {
    @Implements(VpnService::class)
    class ConsentShadow : org.robolectric.shadows.ShadowService() {
        companion object {
            var granted = true
            var preparations = 0
            @JvmStatic @Implementation fun prepare(context: Context): Intent? {
                preparations++
                return if (granted) null else Intent("consent")
            }
        }
    }

    @Test fun webBindingKeepsAliasAfterCarPlayDetachAndReleasesItWhenWebStops() {
        ConsentShadow.granted = true
        ConsentShadow.preparations = 0
        val context = RuntimeEnvironment.getApplication()
        val app = shadowOf(context)
        val controller = Robolectric.buildService(TeslaVpnLifecycleTest.TestService::class.java).create()
        val service = controller.get()
        app.setComponentNameAndServiceForBindService(ComponentName(context, com.shilapi.xcertplay.network.CarPlayVpnService::class.java), service.onBind(Intent()))
        app.setBindServiceCallsOnServiceConnectedDirectly(true)
        val binding = TeslaVpnBinding()
        try {
            binding.reconcile(context, "6.6.6.6")
            assertEquals("6.6.6.6", binding.status.address)
            assertEquals(1, app.boundServiceConnections.size)
            service.detach()
            assertEquals("6.6.6.6", service.teslaVpnStatus.address)
            binding.reconcile(context, "6.6.6.6")
            assertEquals(2, service.allocations.size)
            service.onRevoke()
            repeat(3) { binding.reconcile(context, "6.6.6.6") }
            assertNull(binding.status.address)
            assertTrue(binding.status.error!!.contains("撤销"))
            assertEquals("Polling must not prepare the VPN again after revocation", 1, ConsentShadow.preparations)
            assertEquals(2, service.allocations.size)
            binding.stop()
            assertNull(service.teslaVpnStatus.address)
            assertEquals(1, app.unboundServiceConnections.size)
            service.allocations.forEach { assertTrue(runCatching { it.second.fd }.isFailure) }
        } finally {
            binding.stop(); controller.destroy(); service.peers.forEach { it.close() }
        }
    }

    @Test fun missingConsentNeverBindsOrLaunchesAnActivityFromTheBackground() {
        ConsentShadow.granted = false
        ConsentShadow.preparations = 0
        val context = RuntimeEnvironment.getApplication()
        val binding = TeslaVpnBinding()
        repeat(3) { binding.reconcile(context, "6.6.6.6") }
        assertNull(binding.status.address)
        assertTrue(binding.status.error!!.contains("授权"))
        assertTrue(shadowOf(context).boundServiceConnections.isEmpty())
        assertNull(shadowOf(context).nextStartedActivity)
        assertEquals(1, ConsentShadow.preparations)
        binding.stop()
    }

    @Test fun bindingFailuresWaitForManualRetry() {
        ConsentShadow.granted = true
        val context = RuntimeEnvironment.getApplication()
        val app = shadowOf(context)
        app.setThrowInBindService(SecurityException("blocked"))
        val binding = TeslaVpnBinding()
        binding.reconcile(context, "6.6.6.6")
        assertEquals("blocked", binding.status.error)
        app.setThrowInBindService(null)
        repeat(3) { binding.reconcile(context, "6.6.6.6") }
        assertEquals("blocked", binding.status.error)
        binding.retry()
        binding.reconcile(context, "6.6.6.6")
        assertNotEquals("blocked", binding.status.error)
        binding.stop()
    }
}
