package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.network.WirelessStartupPolicy

/** A stable session or explicit reconnect resets the bounded startup retry allowance. */
internal class WirelessRecoveryBudget(private val maximum: Int = WirelessStartupPolicy.MAX_STARTUP_RETRIES) {
    private var attempts = 0
    @Synchronized fun retry(): Boolean = if (attempts < maximum) { attempts++; true } else false
    @Synchronized fun reset() { attempts = 0 }
}
