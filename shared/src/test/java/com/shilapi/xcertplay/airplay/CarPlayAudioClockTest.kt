package com.shilapi.xcertplay.airplay

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class CarPlayAudioClockTest {
    @Test fun opusKeeps48KhzPcmButUsesTheNegotiatedClockOnTheWire() {
        for ((bits, rate) in listOf(0x10000000L to 16000, 0x20000000L to 24000, 0x40000000L to 48000)) {
            val format = AudioStreamCodec.fromFormatBits(bits, 100, "telephony")
            assertEquals(48000, format.sampleRate)
            assertEquals(rate, format.rtpClockRate)
            assertEquals(20000L, format.timestampMicros(rate / 50))
            val config = MicrophoneConfig("telephony", 48000, 1, 100, 20,
                InetAddress.getLoopbackAddress(), 1234, ByteArray(32), AudioCodecKind.OPUS,
                opusClockRate = format.rtpClockRate)
            assertEquals(1920, config.frameBytes)
            assertEquals(960, config.samplesPerPacket)
            val counters = MicrophoneCounters()
            repeat(2) {
                MicrophonePacketizer.sealPacket(config.key, 100, counters, byteArrayOf(1),
                    config.rtpSamplesPerPacket)
            }
            assertEquals(rate * 40 / 1000, counters.timestamp)
            assertEquals(2L, counters.nonce)
        }
    }

    @Test fun aacAndPcmRetainTheirClockAndUnsignedRtpWrapIsHandled() {
        for (bits in listOf(0x400000L, 0x800000L, 0x4000L)) {
            val format = AudioStreamCodec.fromFormatBits(bits, 100)
            assertEquals(format.sampleRate, format.rtpClockRate)
            assertEquals(1000000L, format.timestampMicros(format.sampleRate))
            assertEquals(0xffffffffL * 1000000L / format.rtpClockRate, format.timestampMicros(-1))
        }
        val pcm = MicrophoneConfig("telephony", 16000, 1, 100, 20,
            InetAddress.getLoopbackAddress(), 1234, ByteArray(32))
        assertEquals(640, pcm.frameBytes)
        assertEquals(320, pcm.rtpSamplesPerPacket)
    }
}
