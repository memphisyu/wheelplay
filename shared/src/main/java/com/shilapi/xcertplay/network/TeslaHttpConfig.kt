package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.i18n.*
import com.shilapi.xcertplay.shared.R as UiR

enum class TeslaHttpMode(@param:androidx.annotation.StringRes val labelRes: Int) {
    ROOT(UiR.string.ui_root_hotspot_routing),
    VPN(UiR.string.ui_rootless_vpn_experimental),
}

/** Two opt-in ways to expose local HTTP, without changing the hotspot's DHCP subnet. */
data class TeslaHttpConfig(
    val enabled: Boolean = false,
    val address: String = DEFAULT_ADDRESS,
    val httpMappingPort: Int = 80,
    val httpsMappingPort: Int = 443,
    val mode: TeslaHttpMode = TeslaHttpMode.ROOT,
    val vpnAddress: String = DEFAULT_VPN_ADDRESS,
) {
    init {
        requireUi(isHttpAddress(address)) {
            UiText(UiR.string.ui_enter_a_unicast_ipv4_address_accessible_through_the_hotspot)
        }
        requireUi(httpMappingPort in 1..1023 && httpsMappingPort in 1..1023) {
            UiText(UiR.string.ui_forwarded_ports_must_be_between_1_and_1023)
        }
        requireUi(httpMappingPort != httpsMappingPort) {
            UiText(UiR.string.ui_http_and_https_forwarded_ports_must_be_different)
        }
        requireUi(isHttpAddress(vpnAddress) && vpnAddress != VPN_PLACEHOLDER_ADDRESS) {
            UiText(
                UiR.string.ui_enter_a_valid_ipv4_address_other_than_the_compatibility,
                listOf(VPN_PLACEHOLDER_ADDRESS),
            )
        }
    }

    val ipUrl
        get() = "http://$address:$httpMappingPort/"

    val accessAddress
        get() = if (mode == TeslaHttpMode.VPN) vpnAddress else address

    companion object {
        const val DEFAULT_ADDRESS = "100.96.0.1"
        const val DEFAULT_VPN_ADDRESS = "6.6.6.6"
        const val VPN_PLACEHOLDER_ADDRESS = "192.168.229.229"

        // Parse literals without DNS, accepting only canonical dotted decimal notation.
        fun isHttpAddress(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { !Regex("0|[1-9][0-9]{0,2}").matches(it) })
                return false
            val bytes = parts.map { it.toInt() }
            return bytes.all { it in 0..255 } &&
                bytes[0] in 1..223 &&
                bytes[0] != 127 &&
                !(bytes[0] == 169 && bytes[1] == 254)
        }

        // Ordinary interface discovery includes RFC6598 but does not list arbitrary WAN addresses.
        fun isSharedAddress(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { !Regex("0|[1-9][0-9]{0,2}").matches(it) })
                return false
            val bytes = parts.map { it.toInt() }
            return bytes.all { it in 0..255 } && bytes[0] == 100 && bytes[1] in 64..127
        }

        fun isHostname(value: String): Boolean =
            value.isEmpty() ||
                (value.length <= 253 &&
                    value.contains('.') &&
                    !value.all { it.isDigit() || it == '.' } &&
                    value.split('.').all {
                        Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?").matches(it)
                    })
    }
}

/** Configured is not a promise of hotspot reachability or Tesla browser compatibility. */
data class TeslaHttpStatus(
    val address: String? = null,
    val error: String? = null,
    val downstreams: List<String> = emptyList(),
    val httpMappingPort: Int? = null,
    val httpsMappingPort: Int? = null,
    val mode: TeslaHttpMode = TeslaHttpMode.ROOT,
)
