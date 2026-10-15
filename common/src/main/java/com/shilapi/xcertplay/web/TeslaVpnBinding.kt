package com.shilapi.xcertplay.web

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.shilapi.xcertplay.network.CarPlayVpnService
import com.shilapi.xcertplay.network.TeslaHttpMode
import com.shilapi.xcertplay.network.TeslaHttpStatus

/** Main-thread Web owner. Its binding keeps the VPN alive independently of CarPlayController. */
internal class TeslaVpnBinding {
    private var context: Context? = null
    private var bound = false
    private var service: CarPlayVpnService? = null
    private var address: String? = null
    private var failure: String? = null
    private var retryPending = false
    var status = TeslaHttpStatus(mode = TeslaHttpMode.VPN)
        private set

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!bound) return
            service = (binder as? CarPlayVpnService.LocalBinder)?.service
            if (service == null) failure = "无法连接免 Root VPN 服务"
            updateService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            status = TeslaHttpStatus(error = "VPN 服务已断开，正在等待系统恢复；也可手动重试", mode = TeslaHttpMode.VPN)
        }
        override fun onBindingDied(name: ComponentName) {
            val requestedAddress = address
            stop()
            address = requestedAddress
            failure = "VPN 服务绑定失效，请手动重试"
            status = TeslaHttpStatus(error = failure, mode = TeslaHttpMode.VPN)
        }
        override fun onNullBinding(name: ComponentName) = onBindingDied(name)
    }

    fun retry() {
        retryPending = true
        failure = null
    }

    fun reconcile(owner: Context?, requestedAddress: String?) {
        if (owner == null || requestedAddress == null) { stop(); return }
        if (address != requestedAddress) failure = null
        address = requestedAddress
        try {
            failure?.let { status = TeslaHttpStatus(error = it, mode = TeslaHttpMode.VPN); return }
            if (!bound) {
                // prepare() can switch VPN ownership when previously authorized. Check only
                // on an initial start or explicit retry, never on every reconciliation tick.
                if (CarPlayVpnService.prepare(owner) != null) {
                    failure = "请在设置中授权 Tesla VPN 后重试"
                    status = TeslaHttpStatus(error = failure, mode = TeslaHttpMode.VPN)
                    return
                }
                context = owner.applicationContext
                bound = true
                if (!context!!.bindService(Intent(context, CarPlayVpnService::class.java), connection, Context.BIND_AUTO_CREATE)) {
                    bound = false
                    failure = "无法绑定免 Root VPN 服务，请手动重试"
                }
            }
            updateService()
        } catch (error: Exception) {
            stop()
            address = requestedAddress
            failure = error.message ?: "免 Root VPN 启动失败"
            status = TeslaHttpStatus(error = failure, mode = TeslaHttpMode.VPN)
        }
    }

    private fun updateService() {
        val current = service
        status = if (current == null) TeslaHttpStatus(error = failure ?: "正在连接免 Root VPN 服务…", mode = TeslaHttpMode.VPN)
        else current.setTeslaAddress(address, retryPending).also { retryPending = false }
    }

    fun stop() {
        service?.setTeslaAddress(null)
        service = null
        if (bound) context?.let { runCatching { it.unbindService(connection) } }
        bound = false
        context = null
        address = null
        failure = null
        retryPending = false
        status = TeslaHttpStatus(mode = TeslaHttpMode.VPN)
    }
}
