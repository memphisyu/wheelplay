package com.shilapi.xcertplay.orchestration

import org.junit.Assert.*
import org.junit.Test

class WirelessRecoveryBudgetTest {
    @Test fun repeatedStartupFailuresStopUntilStableOrExplicitReconnect() {
        val budget = WirelessRecoveryBudget(2)
        assertTrue(budget.retry())
        assertTrue(budget.retry())
        repeat(3) { assertFalse(budget.retry()) }
        budget.reset()
        assertTrue(budget.retry())
    }
}
