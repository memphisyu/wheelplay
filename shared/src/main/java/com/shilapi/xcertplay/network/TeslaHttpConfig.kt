package com.shilapi.xcertplay.network

enum class TeslaHttpMode(val label: String) {
    ROOT("Root 热点路由"), VPN("免 Root VPN（实验）")
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
        require(isHttpAddress(address)) { "请输入可供热点访问的 IPv4 单播地址" }
        require(httpMappingPort in 1..1023 && httpsMappingPort in 1..1023) { "映射端口必须为 1–1023" }
        require(httpMappingPort != httpsMappingPort) { "HTTP 与 HTTPS 映射端口不能相同" }
        require(isHttpAddress(vpnAddress) && vpnAddress != VPN_PLACEHOLDER_ADDRESS) {
            "请输入有效 IPv4 地址，且不能使用兼容接口地址 $VPN_PLACEHOLDER_ADDRESS"
        }
    }

    val ipUrl get() = "http://$address:$httpMappingPort/"
    val accessAddress get() = if (mode == TeslaHttpMode.VPN) vpnAddress else address

    companion object {
        const val DEFAULT_ADDRESS = "100.96.0.1"
        const val DEFAULT_VPN_ADDRESS = "6.6.6.6"
        const val VPN_PLACEHOLDER_ADDRESS = "192.168.229.229"

        // Parse literals without DNS, accepting only canonical dotted decimal notation.
        fun isHttpAddress(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { !Regex("0|[1-9][0-9]{0,2}").matches(it) }) return false
            val bytes = parts.map { it.toInt() }
            return bytes.all { it in 0..255 } && bytes[0] in 1..223 && bytes[0] != 127 &&
                !(bytes[0] == 169 && bytes[1] == 254)
        }

        // Ordinary interface discovery includes RFC6598 but does not list arbitrary WAN addresses.
        fun isSharedAddress(value: String): Boolean {
            val parts = value.split('.')
            if (parts.size != 4 || parts.any { !Regex("0|[1-9][0-9]{0,2}").matches(it) }) return false
            val bytes = parts.map { it.toInt() }
            return bytes.all { it in 0..255 } && bytes[0] == 100 && bytes[1] in 64..127
        }

        fun isHostname(value: String): Boolean = value.isEmpty() ||
            (value.length <= 253 && value.contains('.') &&
                !value.all { it.isDigit() || it == '.' } &&
                value.split('.').all { Regex("[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?").matches(it) })
    }
}

/** Configured is not a promise of hotspot reachability or Tesla browser compatibility. */
data class TeslaHttpStatus(val address: String? = null, val error: String? = null, val downstreams: List<String> = emptyList(),
                           val httpMappingPort: Int? = null, val httpsMappingPort: Int? = null,
                           val mode: TeslaHttpMode = TeslaHttpMode.ROOT)
