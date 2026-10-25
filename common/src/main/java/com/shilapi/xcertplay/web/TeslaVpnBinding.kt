package com.shilapi.xcertplay.web

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.shilapi.xcertplay.i18n.*
import com.shilapi.xcertplay.network.CarPlayVpnService
import com.shilapi.xcertplay.network.TeslaHttpMode
import com.shilapi.xcertplay.network.TeslaHttpStatus
import com.shilapi.xcertplay.shared.R as UiR

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

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (!bound) return
                service = (binder as? CarPlayVpnService.LocalBinder)?.service
                if (service == null)
                    failure =
                        (context?.getString(
                            UiR.string.ui_could_not_connect_to_the_rootless_vpn_service
                        ) ?: "")
                updateService()
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                status =
                    TeslaHttpStatus(
                        error =
                            (context?.getString(
                                UiR.string
                                    .ui_vpn_service_disconnected_waiting_for_system_recovery_you_can
                            ) ?: ""),
                        mode = TeslaHttpMode.VPN,
                    )
            }

            override fun onBindingDied(name: ComponentName) {
                val requestedAddress = address
                val owner = context
                stop()
                address = requestedAddress
                failure =
                    (owner?.getString(UiR.string.ui_vpn_service_binding_lost_retry_manually) ?: "")
                status = TeslaHttpStatus(error = failure, mode = TeslaHttpMode.VPN)
            }

            override fun onNullBinding(name: ComponentName) = onBindingDied(name)
        }

    fun retry() {
        retryPending = true
        failure = null
    }

    fun reconcile(owner: Context?, requestedAddress: String?) {
        if (owner == null || requestedAddress == null) {
            stop()
            return
        }
        context = owner.applicationContext
        if (address != requestedAddress) failure = null
        address = requestedAddress
        try {
            failure?.let {
                status = TeslaHttpStatus(error = it, mode = TeslaHttpMode.VPN)
                return
            }
            if (!bound) {
                // prepare() can switch VPN ownership when previously authorized. Check only
                // on an initial start or explicit retry, never on every reconciliation tick.
                if (CarPlayVpnService.prepare(owner) != null) {
                    failure =
                        (context?.getString(
                            UiR.string.ui_authorize_tesla_vpn_in_settings_and_try_again
                        ) ?: "")
                    status = TeslaHttpStatus(error = failure, mode = TeslaHttpMode.VPN)
                    return
                }
                context = owner.applicationContext
                bound = true
                if (
                    !context!!.bindService(
                        Intent(context, CarPlayVpnService::class.java),
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                ) {
                    bound = false
                    failure =
                        (context?.getString(
                            UiR.string.ui_could_not_bind_the_rootless_vpn_service_retry_manually
                        ) ?: "")
                }
            }
            updateService()
        } catch (error: Exception) {
            stop()
            address = requestedAddress
            failure = error.userMessage(owner)
            status = TeslaHttpStatus(error = failure, mode = TeslaHttpMode.VPN)
        }
    }

    private fun updateService() {
        val current = service
        status =
            if (current == null)
                TeslaHttpStatus(
                    error =
                        failure
                            ?: (context?.getString(UiR.string.ui_connecting_to_rootless_vpn_service)
                                ?: ""),
                    mode = TeslaHttpMode.VPN,
                )
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
