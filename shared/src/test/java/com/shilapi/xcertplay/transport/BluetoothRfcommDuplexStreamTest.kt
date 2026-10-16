package com.shilapi.xcertplay.transport

import android.bluetooth.BluetoothSocket
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [RfcommSocketTestShadow::class])
class BluetoothRfcommDuplexStreamTest {
    @Test fun consumingAWholeChunkResumesAFullReaderWithoutLosingBytes() {
        val expected = payload(QUEUE_CAPACITY + READ_CHUNK)
        Fixture(expected).use { fixture ->
            fixture.awaitFullQueue(QUEUE_CAPACITY)
            val first = fixture.stream.recv(READ_CHUNK, TIMEOUT_MILLIS)!!
            assertEquals(READ_CHUNK, first.size)

            // The reader must refill the freed space before any further consumption.
            fixture.awaitFullQueue(expected.size)
            val remaining = readExactly(fixture.stream, expected.size - first.size, READ_CHUNK)
            assertArrayEquals(expected, first + remaining)
            assertArrayEquals(ByteArray(0), fixture.stream.recv(1, TIMEOUT_MILLIS))
        }
    }

    @Test fun consumingPartOfAChunkResumesAFullReaderAndPreservesTheTail() {
        val expected = payload(QUEUE_CAPACITY + READ_CHUNK)
        Fixture(expected).use { fixture ->
            fixture.awaitFullQueue(QUEUE_CAPACITY)
            val first = fixture.stream.recv(1, TIMEOUT_MILLIS)!!
            assertEquals(1, first.size)

            // Freeing one byte must also wake the reader, even while the head chunk has a tail.
            fixture.awaitFullQueue(QUEUE_CAPACITY + 1)
            val remaining = readExactly(fixture.stream, expected.size - first.size, 997)
            assertArrayEquals(expected, first + remaining)
            assertArrayEquals(ByteArray(0), fixture.stream.recv(1, TIMEOUT_MILLIS))
        }
    }

    @Test fun closeWakesAReaderWaitingForQueueSpaceAndClosesTheSocketOnce() {
        val expected = payload(QUEUE_CAPACITY + 1)
        Fixture(expected).use { fixture ->
            fixture.awaitFullQueue(QUEUE_CAPACITY)
            fixture.stream.close()
            fixture.stream.close()

            assertFalse("The reader must terminate on close", fixture.reader.isAlive)
            assertEquals(1, fixture.socketShadow.closeCalls.get())
            assertEquals("Close must not resume input reads", QUEUE_CAPACITY, fixture.input.bytesRead.get())
            assertArrayEquals(expected.copyOf(QUEUE_CAPACITY), readExactly(fixture.stream, QUEUE_CAPACITY, 997))
            assertArrayEquals(ByteArray(0), fixture.stream.recv(1, TIMEOUT_MILLIS))
        }
    }

    private fun readExactly(stream: BluetoothRfcommDuplexStream, size: Int, chunkSize: Int): ByteArray {
        val received = ByteArrayOutputStream(size)
        while (received.size() < size) {
            val chunk = stream.recv(minOf(chunkSize, size - received.size()), TIMEOUT_MILLIS)
                ?: throw AssertionError("Receive timed out after ${received.size()} of $size bytes")
            assertTrue("Unexpected EOF after ${received.size()} of $size bytes", chunk.isNotEmpty())
            received.write(chunk)
        }
        return received.toByteArray()
    }

    private class Fixture(bytes: ByteArray) : Closeable {
        val input = CountingInput(bytes)
        private val socket = Shadow.newInstanceOf(BluetoothSocket::class.java)
        val socketShadow = Shadow.extract<RfcommSocketTestShadow>(socket).apply { incoming = input }
        val stream = BluetoothRfcommDuplexStream(socket)
        val reader: Thread = ReflectionHelpers.getField(stream, "reader")
        private val queueLock: Any = ReflectionHelpers.getField(stream, "lock")

        fun awaitFullQueue(expectedInputBytes: Int) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MILLIS)
            while (System.nanoTime() < deadline) {
                val waitingAtCapacity = synchronized(queueLock) {
                    ReflectionHelpers.getField<Int>(stream, "pendingBytes") == QUEUE_CAPACITY &&
                        reader.state == Thread.State.WAITING && input.bytesRead.get() == expectedInputBytes
                }
                if (waitingAtCapacity) return
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1))
            }
            throw AssertionError(
                "Reader did not wait at capacity after $expectedInputBytes input bytes: " +
                    "state=${reader.state}, read=${input.bytesRead.get()}",
            )
        }

        override fun close() = stream.close()
    }

    private class CountingInput(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        val bytesRead = AtomicInteger()
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytesRead.addAndGet(it) }
    }

    private fun payload(size: Int) = ByteArray(size) { (it * 31 + it / 256).toByte() }

    private companion object {
        const val QUEUE_CAPACITY = 65_536
        const val READ_CHUNK = 8_192
        const val TIMEOUT_MILLIS = 3_000L
    }
}

/** Only the Android socket boundary is replaced; the production queue and reader thread run. */
@Implements(BluetoothSocket::class)
class RfcommSocketTestShadow {
    lateinit var incoming: InputStream
    private val outgoing: OutputStream = ByteArrayOutputStream()
    val closeCalls = AtomicInteger()

    @Implementation fun getInputStream(): InputStream = incoming
    @Implementation fun getOutputStream(): OutputStream = outgoing
    @Implementation fun close() {
        closeCalls.incrementAndGet()
        incoming.close()
        outgoing.close()
    }
}
