package com.shilapi.xcertplay

/** Similar browser shapes retain the negotiated stream; CSS fits it without a phone reconnect.
 * Inspired by DashPlay display-fit closeShape (GPL-3.0); see docs/DASHPLAY-NOTICE.md.
 */
internal object DisplayResizePolicy {
    fun closeShape(oldWidth: Int, oldHeight: Int, width: Int, height: Int): Boolean =
        oldWidth > 0 && oldHeight > 0 && width > 0 && height > 0 &&
            kotlin.math.abs((oldWidth.toDouble() / oldHeight) / (width.toDouble() / height) - 1) <= 0.2
}
