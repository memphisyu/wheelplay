package com.shilapi.xcertplay.web

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebMdnsTest {
    private val target = MdnsTarget("wlan0", "192.168.43.2", "wheelplay.local", 8080)
    @Test fun publicationsFollowIpHostnameAndPortChangesWithoutDuplicatingStableTargets() {
        val opened = mutableListOf<MdnsTarget>()
        val closed = mutableListOf<MdnsTarget>()
        val state = MdnsPublications({ opened.add(it); AutoCloseable { closed.add(it) } }, { throw AssertionError(it) })
        state.update(setOf(target)); state.update(setOf(target))
        assertEquals(listOf(target), opened)
        for (next in listOf(target.copy(address = "192.168.43.3"), target.copy(hostname = "padmini.local"), target.copy(port = 9090))) {
            state.update(setOf(next))
            assertEquals(opened.dropLast(1), closed)
        }
        state.update(emptySet())
        state.close(); state.close()
        assertEquals(opened, closed)
    }
    @Test fun failedAnnouncementRetriesAndFailedCleanupDoesNotLeakOtherPublications() {
        var attempts = 0; var closes = 0
        val errors = mutableListOf<Exception>()
        val state = MdnsPublications({
            if (++attempts == 1) throw IOException("temporary bind failure")
            AutoCloseable { closes++; if (closes == 1) throw IOException("goodbye failed") }
        }, errors::add)
        state.update(setOf(target)); state.update(setOf(target, target.copy(interfaceName = "eth0")))
        assertEquals(3, attempts)
        state.close(); state.close()
        assertEquals(2, closes); assertEquals(2, errors.size)
    }
    @Test fun onlyLanInterfacesAreAnnouncedNotCarPlayP2pOrCompatibilityVpnAliases() {
        val entries = LanAddresses.Kind.entries.map { kind -> LanAddresses.Entry(kind.name, "192.168.43.2", kind) }
        val result = WebMdns.targets(entries, "wheelplay.local", 9090)
        assertEquals(setOf("ETHERNET", "WIFI", "HOTSPOT", "USB", "OTHER"), result.map { it.interfaceName }.toSet())
        assertTrue(result.all { it.port == 9090 && it.hostname == "wheelplay.local" })
    }
    @Test fun stopReturnsWhileNetworkCreationIsBlockedAndClosesItsLateResult() {
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val disposed = CountDownLatch(1)
        val attempts = AtomicInteger()
        val mdns = WebMdns({ setOf(target) }, {
            attempts.incrementAndGet(); entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            AutoCloseable { disposed.countDown() }
        }, { throw AssertionError(it) })
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS))
            mdns.close(); mdns.close(); mdns.refreshNow()
            // Cleanup must be queued behind creation, never wait for it on the caller.
            assertEquals(1L, disposed.count)
        } finally { release.countDown(); mdns.close() }
        assertTrue(disposed.await(3, TimeUnit.SECONDS)); assertEquals(1, attempts.get())
    }
    @Test fun snapshotFailureDoesNotKillTheWorkerAndManualRefreshRecovers() {
        val failed = CountDownLatch(1); val opened = CountDownLatch(1); val closed = CountDownLatch(1)
        var samples = 0
        val mdns = WebMdns({ if (++samples == 1) throw IOException("network changed") else setOf(target) },
            { opened.countDown(); AutoCloseable { closed.countDown() } }, { failed.countDown() })
        try {
            assertTrue(failed.await(3, TimeUnit.SECONDS)); mdns.refreshNow()
            assertTrue(opened.await(3, TimeUnit.SECONDS))
        } finally { mdns.close() }
        assertTrue(closed.await(3, TimeUnit.SECONDS))
    }
}
