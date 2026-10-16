package com.shilapi.xcertplay.web

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

internal data class MdnsTarget(val interfaceName: String, val address: String, val hostname: String, val port: Int)

/** Only the worker owns publications; creation and goodbye packets never block the UI. */
internal class MdnsPublications(
    private val open: (MdnsTarget) -> AutoCloseable,
    private val onFailure: (Exception) -> Unit,
) : AutoCloseable {
    private val active = mutableMapOf<MdnsTarget, AutoCloseable>()
    fun update(targets: Set<MdnsTarget>) {
        active.keys.filter { it !in targets }.forEach { key -> dispose(active.remove(key)!!) }
        targets.filter { it !in active }.forEach { target ->
            try { active[target] = open(target) } catch (error: Exception) { onFailure(error) }
        }
    }
    private fun dispose(value: AutoCloseable) {
        try { value.close() } catch (error: Exception) { onFailure(error) }
    }
    override fun close() { active.values.forEach(::dispose); active.clear() }
}

/** Adapted from upstream a6af3cd; opt-in, serialized I/O and Wi-Fi multicast ownership. */
internal class WebMdns(
    private val snapshot: () -> Set<MdnsTarget>,
    open: (MdnsTarget) -> AutoCloseable,
    private val onFailure: (Exception) -> Unit = { Log.w("WebMdns", "mDNS announcement failed", it) },
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val publications = MdnsPublications(open, onFailure)
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "wheelplay-web-mdns").apply { isDaemon = true }
    }
    private val periodic = worker.scheduleWithFixedDelay(::refresh, 0, 10, TimeUnit.SECONDS)

    constructor(context: Context) : this(
        snapshot = {
            val port = WebSession.httpListenerPort
            if (port == null) emptySet() else targets(LanAddresses.discover(context),
                WebListenSettings.mdnsHostname(context), port)
        },
        open = { target -> publish(context, target) },
    )

    private fun refresh() {
        if (closed.get()) return
        try { publications.update(snapshot()) } catch (error: Exception) { onFailure(error) }
    }
    fun refreshNow() {
        if (closed.get()) return
        try { worker.execute(::refresh) } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        periodic.cancel(false)
        // Queue after any in-flight creation so a late result is closed as well.
        worker.execute { publications.close() }
        worker.shutdown()
    }

    companion object {
        internal fun targets(entries: List<LanAddresses.Entry>, hostname: String, port: Int): Set<MdnsTarget> =
            entries.filter { it.kind !in setOf(LanAddresses.Kind.TESLA_HTTP, LanAddresses.Kind.VPN,
                LanAddresses.Kind.CELLULAR, LanAddresses.Kind.P2P) }
                .map { MdnsTarget(it.interfaceName, it.address, hostname, port) }.toSet()

        private fun publish(context: Context, target: MdnsTarget): AutoCloseable {
            val lock = context.getSystemService(WifiManager::class.java)
                ?.createMulticastLock("wheelplay-web-mdns")?.apply { setReferenceCounted(false) }
            var dns: JmDNS? = null
            try {
                lock?.acquire()
                dns = JmDNS.create(InetAddress.getByName(target.address), target.hostname.removeSuffix(".local"))
                dns.registerService(ServiceInfo.create("_http._tcp.local.", "WheelPlay", target.port, "path=/"))
                val registration = dns
                return AutoCloseable {
                    try { registration.close() } finally { if (lock?.isHeld == true) lock.release() }
                }
            } catch (error: Exception) {
                runCatching { dns?.close() }
                if (lock?.isHeld == true) lock.release()
                throw error
            }
        }
    }
}
