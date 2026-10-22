package com.shilapi.xcertplay.web

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Negotiated with media=2. Raw JPEG remains the default for existing browsers. */
internal object VideoPacket {
    const val MAGIC = 0x32565057 // WPV2, distinct from browser audio WPA1
    const val HEADER_BYTES = 36

    fun encode(frame: WebSession.Frame): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES + frame.jpeg.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC)
            // JS safely represents these IDs as integers; epochs/sequences are process counters.
            .putDouble(frame.sessionId.toDouble()).putDouble(frame.videoEpoch.toDouble())
            .putDouble(frame.sequence.toDouble()).putInt(frame.width).putInt(frame.height)
            .put(frame.jpeg).array()
}
