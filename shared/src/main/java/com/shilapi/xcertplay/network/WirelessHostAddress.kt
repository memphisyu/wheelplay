package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal fun wirelessHostAddress(addresses: List<InetAddress>, interfaceIndex: Int): InetAddress? {
    val selected = HotspotAddressPolicy.select(addresses.filter {
        it !is Inet6Address || interfaceIndex > 0
    }) ?: return null
    if (selected is Inet6Address && interfaceIndex > 0) {
        return Inet6Address.getByAddress(null, selected.address, interfaceIndex)
    }
    return selected
}
