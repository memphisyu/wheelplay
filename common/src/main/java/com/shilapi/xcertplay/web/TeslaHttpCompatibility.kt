package com.shilapi.xcertplay.web

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.network.TeslaHttpConfig
import com.shilapi.xcertplay.network.TeslaHttpMode
import com.shilapi.xcertplay.network.TeslaHttpStatus
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Web-service-owned routing. Only the selected VPN mode binds the shared CarPlay VpnService. */
internal object TeslaHttpCompatibility {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor { work ->
        Thread(work, "tesla-http-routing").apply { isDaemon = true }
    }
    private val routing = RootHttpRouting()
    private val vpn = TeslaVpnBinding()
    private val revision = AtomicLong()
    @Volatile private var owner: Context? = null
    @Volatile private var tethered = emptySet<String>()
    @Volatile var status = TeslaHttpStatus()
        private set

    // Broadcast action/extra adapted from VPN Hotspot v2.17.1 TetheringManager.kt.
    private const val TETHER_STATE_CHANGED = "android.net.conn.TETHER_STATE_CHANGED"
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != TETHER_STATE_CHANGED) return
            tethered = tetheredInterfaces(intent)
            refresh()
        }
    }

    init { worker.scheduleWithFixedDelay(::updateRouting, 0, 2, TimeUnit.SECONDS) }

    internal fun tetheredInterfaces(intent: Intent): Set<String> =
        (intent.getStringArrayListExtra("tetherArray") ?: intent.getStringArrayExtra("tetherArray")?.toList().orEmpty())
            .filter { Regex("[a-zA-Z0-9_.-]{1,15}").matches(it) && it != "lo" }.toSet()

    private fun preferences(context: Context) = context.getSharedPreferences("tesla_http", Context.MODE_PRIVATE)
    fun config(context: Context): TeslaHttpConfig {
        WebListenSettings.migrate(context)
        val prefs = preferences(context)
        return runCatching {
            TeslaHttpConfig(prefs.getBoolean("enabled", false),
                prefs.getString("address", TeslaHttpConfig.DEFAULT_ADDRESS)!!,
                prefs.getInt("http_mapping_port", 80), prefs.getInt("https_mapping_port", 443),
                TeslaHttpMode.valueOf(prefs.getString("mode", TeslaHttpMode.ROOT.name)!!),
                prefs.getString("vpn_address", TeslaHttpConfig.DEFAULT_VPN_ADDRESS)!!)
        }.getOrDefault(TeslaHttpConfig())
    }

    fun save(context: Context, config: TeslaHttpConfig) {
        val previous = config(context)
        preferences(context).edit().putBoolean("enabled", config.enabled)
            .putString("address", config.address).putInt("http_mapping_port", config.httpMappingPort)
            .putInt("https_mapping_port", config.httpsMappingPort).putString("mode", config.mode.name)
            .putString("vpn_address", config.vpnAddress).apply()
        refresh(retry = true)
        if (previous.accessAddress != config.accessAddress)
            WebSession.refreshGeneratedCertificate(context)
    }

    fun start(context: Context) {
        main.post {
            if (owner != null) return@post
            val application = context.applicationContext
            owner = application
            try {
                val filter = IntentFilter(TETHER_STATE_CHANGED)
                val sticky = if (Build.VERSION.SDK_INT >= 33)
                    application.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                else application.registerReceiver(receiver, filter)
                if (sticky != null) tethered = tetheredInterfaces(sticky)
            } catch (_: Exception) { tethered = emptySet() }
            refresh(retry = true)
        }
    }

    fun stop() {
        main.post {
            owner?.let { runCatching { it.unregisterReceiver(receiver) } }
            owner = null; tethered = emptySet(); status = TeslaHttpStatus()
            vpn.stop()
            refresh()
        }
    }

    fun retry() = refresh(retry = true)

    internal fun plan(config: TeslaHttpConfig, downstreams: List<HttpDownstream>, httpPort: Int?, httpsPort: Int?,
                      authorized: Boolean): RootHttpPlan? =
        if (!authorized || !config.enabled || config.mode != TeslaHttpMode.ROOT || downstreams.isEmpty() || httpPort == null) null
        else RootHttpPlan(config.address, httpPort, downstreams, httpsPort,
            config.httpMappingPort, config.httpsMappingPort.takeIf { httpsPort != null })

    private fun refresh(retry: Boolean = false) {
        revision.incrementAndGet()
        val config = owner?.let(::config)
        status = TeslaHttpStatus(error = config?.takeIf { it.enabled }?.let { "正在准备${it.mode.label}…" },
            mode = config?.mode ?: TeslaHttpMode.ROOT)
        if (retry) main.post { vpn.retry() }
        worker.execute { if (retry) routing.retry(); updateRouting() }
    }

    private fun updateRouting() {
        val version = revision.get()
        val context = owner
        val config = context?.let(::config)
        try {
            if (config?.enabled == true && config.mode == TeslaHttpMode.VPN) {
                // Do not create a competing alias until the former Root transaction is cleaned up.
                val cleanup = routing.reconcile(null)
                main.post {
                    if (revision.get() != version || owner !== context || config(context!!) != config) return@post
                    val http = WebSession.httpListenerPort
                    if (cleanup.error != null || http == null) {
                        vpn.stop()
                        status = TeslaHttpStatus(error = cleanup.error ?: "请先启动 Web 监听", mode = TeslaHttpMode.VPN)
                    } else {
                        vpn.reconcile(context, config.vpnAddress)
                        status = vpn.status.copy(httpMappingPort = http, httpsMappingPort = WebSession.httpsListenerPort)
                    }
                }
                return
            }
            main.post { if (revision.get() == version) vpn.stop() }
            val downstreams = if (config?.enabled == true && RootAccess.granted) tethered.sorted().mapNotNull { name ->
                val iface = NetworkInterface.getByName(name) ?: return@mapNotNull null
                if (!iface.isUp || iface.isLoopback) return@mapNotNull null
                // Match an actual tethered interface, never Wi-Fi/cellular name heuristics.
                val address = iface.interfaceAddresses.firstOrNull { it.address is Inet4Address && it.networkPrefixLength in 1..30 }
                    ?: return@mapNotNull null
                require(address.address.hostAddress != config.address) { "共享 IP 与热点网关相同，请选择其他地址" }
                HttpDownstream(name, iface.index, "${address.address.hostAddress}/${address.networkPrefixLength}")
            } else emptyList()
            val plan = config?.let { plan(it, downstreams, WebSession.httpListenerPort, WebSession.httpsListenerPort, RootAccess.granted) }
            val result = routing.reconcile(plan)
            // Root consent can take time. Never publish completion for a stopped/reconfigured owner.
            if (revision.get() == version && owner === context && (context == null || config(context) == config)) {
                status = if (config?.enabled == true && plan == null && result.error == null)
                    TeslaHttpStatus(error = if (!RootAccess.granted) "请先请求 Root 权限"
                        else if (WebSession.httpListenerPort == null) "请先启动 Web 监听"
                        else "请先开启系统热点；未检测到可共享的 IPv4 热点接口") else result
            } else routing.reconcile(null)
        } catch (error: Exception) {
            routing.reconcile(null)
            if (revision.get() == version && owner === context) status = TeslaHttpStatus(error = error.message ?: "Root 热点路由配置失败")
        }
    }
}
