package com.shilapi.xcertplay.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.shilapi.xcertplay.network.TeslaHttpConfig
import com.shilapi.xcertplay.network.TeslaHttpMode
import com.shilapi.xcertplay.shared.R as UiR
import java.net.Inet4Address
import java.net.NetworkInterface

/** Keep interface provenance; a private address alone does not imply LAN reachability. */
internal object LanAddresses {
    enum class Kind(@param:androidx.annotation.StringRes val labelRes: Int, val priority: Int) {
        TESLA_HTTP(UiR.string.ui_tesla_compatibility_experimental, 5),
        ETHERNET(UiR.string.ui_ethernet, 10),
        WIFI(UiR.string.host_wi_fi, 10),
        HOTSPOT(UiR.string.ui_hotspot, 20),
        USB(UiR.string.ui_usb_network, 30),
        OTHER(UiR.string.ui_other_network, 50),
        P2P(UiR.string.host_wi_fi_direct, 60),
        VPN(UiR.string.ui_vpn_tunnel, 90),
        CELLULAR(UiR.string.ui_cellular, 100),
    }

    data class Entry(
        val interfaceName: String,
        val address: String,
        val kind: Kind,
        val active: Boolean = false,
        val hostname: String = "",
        val port: Int = WebListenSettings.DEFAULT_HTTP_PORT,
        val teslaMode: TeslaHttpMode = TeslaHttpMode.ROOT,
    ) {
        val url
            get() = "http://${hostname.ifEmpty { address }}:$port"

        val ipUrl
            get() = "http://$address:$port"

        fun description(context: Context) =
            "${context.getString(kind.labelRes)} · $interfaceName" +
                when (kind) {
                    Kind.TESLA_HTTP ->
                        context.getString(
                            UiR.string.address_tesla_test_hint,
                            context.getString(teslaMode.labelRes),
                        )
                    Kind.P2P ->
                        context.getString(UiR.string.ui_usually_used_for_the_iphone_connection)
                    Kind.VPN,
                    Kind.CELLULAR ->
                        context.getString(UiR.string.ui_usually_inaccessible_from_the_local_network)
                    else -> ""
                }
    }

    fun classify(name: String, transport: Kind? = null): Kind {
        val n = name.lowercase()
        return when {
            transport == Kind.VPN ||
                n.startsWith("tun") ||
                n.startsWith("tap") ||
                n.startsWith("wg") ||
                n.startsWith("ipsec") -> Kind.VPN
            n.contains("p2p") -> Kind.P2P
            transport != null -> transport
            n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp") -> Kind.CELLULAR
            n.startsWith("ap") || n.startsWith("softap") || n.startsWith("swlan") -> Kind.HOTSPOT
            n.startsWith("rndis") || n.startsWith("usb") || n.startsWith("ncm") -> Kind.USB
            n.startsWith("wlan") || n.startsWith("wifi") -> Kind.WIFI
            n.startsWith("eth") || n.matches(Regex("en[0-9]+")) -> Kind.ETHERNET
            else -> Kind.OTHER
        }
    }

    fun ranked(entries: List<Entry>): List<Entry> =
        entries
            .distinctBy { it.interfaceName to it.address }
            .sortedWith(
                compareBy<Entry> { it.kind.priority }
                    .thenByDescending { it.active }
                    .thenBy { it.interfaceName }
                    .thenBy { it.address }
            )

    fun isDiscoverable(address: Inet4Address): Boolean =
        address.isSiteLocalAddress || TeslaHttpConfig.isSharedAddress(address.hostAddress ?: "")

    fun sharedEntry(
        address: String,
        hostname: String,
        port: Int = WebListenSettings.DEFAULT_HTTP_PORT,
        mode: TeslaHttpMode = TeslaHttpMode.ROOT,
    ): Entry =
        Entry(
            mode.name,
            address,
            Kind.TESLA_HTTP,
            hostname = hostname,
            port = port,
            teslaMode = mode,
        )

    fun discover(
        context: Context,
        teslaAddress: String? = null,
        hostname: String = "",
        port: Int = WebListenSettings.DEFAULT_HTTP_PORT,
        mappedPort: Int? = null,
        mode: TeslaHttpMode = TeslaHttpMode.ROOT,
    ): List<Entry> {
        val transports = mutableMapOf<String, Kind>()
        var activeInterface: String? = null
        // Framework metadata is authoritative; interface-name heuristics cover tethering/P2P.
        runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            activeInterface =
                manager.activeNetwork?.let { manager.getLinkProperties(it)?.interfaceName }
            for (network in manager.allNetworks) {
                val name = manager.getLinkProperties(network)?.interfaceName ?: continue
                val caps = manager.getNetworkCapabilities(network) ?: continue
                val kind =
                    when {
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> Kind.VPN
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Kind.CELLULAR
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Kind.ETHERNET
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Kind.WIFI
                        else -> null
                    }
                if (kind != null) transports[name] = kind
            }
        }
        val entries =
            runCatching {
                    NetworkInterface.getNetworkInterfaces().toList().flatMap { iface ->
                        runCatching {
                                if (!iface.isUp || iface.isLoopback) emptyList()
                                else
                                    iface.inetAddresses
                                        .toList()
                                        .filterIsInstance<Inet4Address>()
                                        .filter(::isDiscoverable)
                                        .map {
                                            Entry(
                                                iface.name,
                                                it.hostAddress!!,
                                                classify(iface.name, transports[iface.name]),
                                                iface.name == activeInterface,
                                                port = port,
                                            )
                                        }
                            }
                            .getOrDefault(emptyList())
                    }
                }
                .getOrDefault(emptyList())
        // Aliases on loopback or a retained DOWN TUN are excluded from ordinary discovery.
        // Only the owning routing/VPN manager may publish them; reachability still needs testing.
        val shared =
            teslaAddress
                ?.let { listOf(sharedEntry(it, hostname, mappedPort ?: port, mode)) }
                .orEmpty()
        val ranked = ranked(shared + entries)
        return if (shared.isEmpty() && hostname.isNotEmpty())
            ranked.mapIndexed { index, entry ->
                if (index == 0) entry.copy(hostname = hostname) else entry
            }
        else ranked
    }
}
