package com.shilapi.xcertplay.web

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class VideoPacketTest {
    @Test fun headerPreservesSafeIntegerIdentityGeometryAndJpegBytes() {
        val jpeg = byteArrayOf(-1, -40, -1)
        val packet = VideoPacket.encode(WebSession.Frame(jpeg, 10, 99, 9007199254740991L, 42, 720, 1280))
        assertEquals(36 + jpeg.size, packet.size)
        val view = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x32565057, view.int)
        assertEquals(9007199254740991L, view.double.toLong())
        assertEquals(42L, view.double.toLong()); assertEquals(99L, view.double.toLong())
        assertEquals(720, view.int); assertEquals(1280, view.int)
        assertArrayEquals(jpeg, packet.copyOfRange(36, packet.size))
    }
}
