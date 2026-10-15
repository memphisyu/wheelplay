package com.shilapi.xcertplay.orchestration

import android.os.Looper
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.transport.BlockingDuplexByteStream
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.net.Socket
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Drives the controller's actual handoff timer, rendered-frame listener and late tunnel callback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WirelessHandoffRecoveryTest {
    private lateinit var controller: CarPlayController
    private lateinit var session: AirPlaySession
    private lateinit var bootstrap: Iap2Session
    private lateinit var wirelessListener: AirPlaySessionListener
    private val statuses = mutableListOf<CarPlayStatus>()
    private val transportErrors = mutableListOf<String>()
    private val streamClosed = CountDownLatch(1)
    private val config = AirPlayConfig("test", "00:00:00:00:00:01", "00:00:00:00:00:02",
        "1", AirPlayDisplayConfig(800, 480))
    private val identity = AirPlayIdentity(ByteArray(32), ByteArray(32), "test")

    @Before fun setup() {
        controller = CarPlayController(RuntimeEnvironment.getApplication(),
            CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL, transport = CarPlayTransport.WIRELESS,
                identification = Iap2IdentificationConfig("test", "test", "test", "1", "1", "1", 3)),
            config, identity, PairingStore(), object : AirPlaySessionListener {
                override fun onTransportError(message: String) { transportErrors.add(message) }
            },
            object : AirPlayMediaHandler {}, statuses::add)
        val phase = CarPlayController::class.java.getDeclaredField("phase").type.enumConstants!!
            .first { (it as Enum<*>).name == "WIRELESS" }
        ReflectionHelpers.setField(controller, "phase", phase)
        generation().set(7)
        bootstrap = Iap2Session.openWireless(object : BlockingDuplexByteStream {
            override fun send(data: ByteArray) = Unit
            override fun recv(maxBytes: Int, timeoutMillis: Long): ByteArray? {
                streamClosed.await(minOf(timeoutMillis, 20), TimeUnit.MILLISECONDS)
                return if (streamClosed.count == 0L) ByteArray(0) else null
            }
            override fun close() { streamClosed.countDown() }
        })
        ReflectionHelpers.setField(controller, "csm", bootstrap)
        flag("wirelessBootstrapAuthenticated").set(true)
        flag("wirelessHandoffRequested").set(true)
        session = AirPlaySession(Socket(), config, identity, PairingStore(), null,
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {})
        val proof = ReflectionHelpers.getField<WirelessConnectionProof<AirPlaySession>>(controller, "wirelessConnectionProof")
        proof.begin(7) {}
        val watchdog = FirstTcpWatchdog(AirPlayListenerIdentity(7), { _, _ -> {} }, {})
        val listener = ReflectionHelpers.callInstanceMethod<AirPlaySessionListener>(controller, "wirelessSessionListener",
            ClassParameter.from(Int::class.javaPrimitiveType, 7), ClassParameter.from(FirstTcpWatchdog::class.java, watchdog))
        wirelessListener = listener
        listener.onSessionActive(session)
        listener.onVideoFrameRendered(session)
    }

    @After fun teardown() {
        controller.close()
        assertTrue(controller.awaitClosed(3_000))
        session.close()
    }

    @Test fun renderedFallbackKeepsBluetoothUntilTheLateAuthenticatedTunnelTakesOver() {
        armAndAdvance()
        assertTrue(statuses.contains(CarPlayStatus.WirelessActiveFallback))
        assertFalse(bootstrap.isClosed)
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "onWirelessTunnelReady",
            ClassParameter.from(Int::class.javaPrimitiveType, 7))
        assertTrue(streamClosed.await(2, TimeUnit.SECONDS))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bootstrap.isClosed)
        assertFalse(flag("wirelessHandoffFellBack").get())
    }

    @Test fun aStaleTimerCannotPromoteOrCloseTheNextGenerationsControl() {
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "armWirelessHandoffWatchdog",
            ClassParameter.from(Int::class.javaPrimitiveType, 7))
        generation().set(8)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45))
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "closeBluetoothBootstrapTransport",
            ClassParameter.from(Int::class.javaObjectType, 7))
        assertFalse(bootstrap.isClosed)
        assertFalse(flag("wirelessHandoffFellBack").get())
        assertFalse(statuses.contains(CarPlayStatus.WirelessActiveFallback))
    }

    @Test fun theOldListenersTransportErrorCannotRestartTheNewConnection() {
        generation().set(8)
        wirelessListener.onTransportError("old listener closed")
        assertTrue(transportErrors.isEmpty())
        generation().set(7)
        wirelessListener.onTransportError("current listener failed")
        assertEquals(listOf("current listener failed"), transportErrors)
    }

    @Test fun renderedVideoCannotSubstituteForBluetoothAuthenticationOrAnOpenChannel() {
        flag("wirelessBootstrapAuthenticated").set(false)
        assertFalse(keepAlive())
        flag("wirelessBootstrapAuthenticated").set(true)
        assertTrue(keepAlive())
        bootstrap.close()
        assertFalse(keepAlive())
    }

    @Test fun exhaustedRecoveryStopsBeforeTheUiCanRebuildAndResetTheBudget() {
        ReflectionHelpers.setField(controller, "wirelessRecovery", WirelessRecoveryBudget(0))
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "recoverWireless",
            ClassParameter.from(Int::class.javaPrimitiveType, 7),
            ClassParameter.from(Throwable::class.java, IllegalStateException("startup exhausted")),
            ClassParameter.from(Boolean::class.javaPrimitiveType, false))
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (statuses.none { it is CarPlayStatus.Failed } && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        val failed = statuses.filterIsInstance<CarPlayStatus.Failed>().single()
        assertEquals("startup exhausted", failed.message)
        assertFalse(failed.automaticRetry)
        assertTrue(bootstrap.isClosed)
    }

    private fun armAndAdvance() {
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "armWirelessHandoffWatchdog",
            ClassParameter.from(Int::class.javaPrimitiveType, 7))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(45))
    }
    private fun keepAlive(): Boolean = ReflectionHelpers.callInstanceMethod(controller, "keepBluetoothControlAlive",
        ClassParameter.from(Int::class.javaPrimitiveType, 7))
    private fun generation() = ReflectionHelpers.getField<AtomicInteger>(controller, "wirelessGeneration")
    private fun flag(name: String) = ReflectionHelpers.getField<AtomicBoolean>(controller, name)
}
