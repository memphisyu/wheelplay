package com.shilapi.xcertplay.web

import android.os.Handler
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class BrowserOfflineMonitorTest {
    private var session: Any? = Any()
    private var viewer = false
    private val stopped = mutableListOf<Any>()
    private val monitor = BrowserOfflineMonitor(Handler(Looper.getMainLooper()), { session }, { viewer }, stopped::add)
    private fun advance(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    @Test fun timeoutStartsWithSessionAndRepeatedStatusUpdatesDoNotExtendIt() {
        monitor.refresh(60_000)
        advance(59)
        monitor.refresh(60_000)
        assertTrue(stopped.isEmpty())
        advance(1)
        assertSame(session, stopped.single())
    }
    @Test fun reconnectCancelsTimeoutAndASecondDisconnectStartsNewGrace() {
        monitor.refresh(60_000); advance(59)
        viewer = true; monitor.refresh(60_000); advance(120)
        assertTrue(stopped.isEmpty())
        viewer = false; monitor.refresh(60_000); advance(59)
        assertTrue(stopped.isEmpty())
        advance(1)
        assertSame(session, stopped.single())
    }
    @Test fun timerChecksLiveViewerEvenBeforeConnectionNotificationIsProcessed() {
        monitor.refresh(60_000); advance(59)
        viewer = true
        advance(1)
        assertTrue(stopped.isEmpty())
    }
    @Test fun switchingToKeepOrClosingMonitorCancelsPendingDisconnection() {
        monitor.refresh(60_000); advance(59)
        monitor.refresh(null); advance(300)
        assertTrue(stopped.isEmpty())
        monitor.refresh(60_000); advance(59)
        monitor.close(); advance(300)
        assertTrue(stopped.isEmpty())
    }
    @Test fun oldTimeoutCannotDisconnectReplacementSession() {
        monitor.refresh(60_000); advance(59)
        session = Any()
        advance(1)
        assertTrue(stopped.isEmpty())
        monitor.refresh(60_000); advance(60)
        assertSame(session, stopped.single())
    }
    @Test fun noTimerRunsWithoutSessionOrWithViewer() {
        session = null; monitor.refresh(60_000); advance(300)
        session = Any(); viewer = true; monitor.refresh(60_000); advance(300)
        assertTrue(stopped.isEmpty())
    }
    @Test fun changingGraceRestartsDeadlineAndClearedSessionCannotBeStopped() {
        monitor.refresh(60_000); advance(30)
        monitor.refresh(300_000); advance(299)
        assertTrue(stopped.isEmpty())
        session = null; advance(1)
        assertTrue(stopped.isEmpty())
    }
}
