package com.shilapi.xcertplay.web

import android.os.Handler

/** Main-thread timer tied to a particular phone session, never to a transient socket. */
internal class BrowserOfflineMonitor(
    private val handler: Handler,
    private val session: () -> Any?,
    private val hasViewer: () -> Boolean,
    private val disconnect: (Any) -> Unit,
) {
    private data class State(val session: Any?, val viewer: Boolean, val timeout: Long?)
    private var state: State? = null
    private var pending: Runnable? = null
    private var generation = 0L

    fun refresh(timeoutMillis: Long?) {
        val next = State(session(), hasViewer(), timeoutMillis)
        if (next == state) return
        close()
        state = next
        val expected = next.session ?: return
        val timeout = next.timeout ?: return
        if (next.viewer) return
        val token = generation
        pending = Runnable {
            pending = null
            if (token == generation && session() === expected && !hasViewer()) disconnect(expected)
        }.also { handler.postDelayed(it, timeout) }
    }

    fun close() {
        generation++
        pending?.let(handler::removeCallbacks)
        pending = null
        state = null
    }
}
