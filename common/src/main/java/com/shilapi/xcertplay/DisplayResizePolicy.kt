package com.shilapi.xcertplay

/** Retain the negotiated stream only for small viewport jitter.
 * Large changes must renegotiate even when their aspect ratio is unchanged: otherwise
 * a small canvas is magnified and the phone's layout density never adapts.
 */
internal object DisplayResizePolicy {
    fun closeShape(oldWidth: Int, oldHeight: Int, width: Int, height: Int): Boolean =
        oldWidth > 0 && oldHeight > 0 && width > 0 && height > 0 &&
            kotlin.math.abs(width.toDouble() / oldWidth - 1) <= 0.03 &&
            kotlin.math.abs(height.toDouble() / oldHeight - 1) <= 0.03 &&
            kotlin.math.abs((oldWidth.toDouble() / oldHeight) / (width.toDouble() / height) - 1) <= 0.02
}
