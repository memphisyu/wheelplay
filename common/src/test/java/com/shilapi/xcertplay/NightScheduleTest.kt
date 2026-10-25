package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class NightScheduleTest {
    @Test fun defaultRangeCrossesMidnightAndHasExactBoundaries() {
        val schedule = NightSchedule()
        for (minute in listOf(0, 419, 1110, 1439)) assertTrue(schedule.nightAt(minute))
        for (minute in listOf(420, 720, 1109)) assertFalse(schedule.nightAt(minute))
    }
    @Test fun customDaytimeRangeAndInvalidRanges() {
        val schedule = NightSchedule(8 * 60, 12 * 60)
        assertFalse(schedule.nightAt(479)); assertTrue(schedule.nightAt(480))
        assertTrue(schedule.nightAt(719)); assertFalse(schedule.nightAt(720))
        for ((start, end) in listOf(-1 to 1, 1 to 1440, 420 to 420))
            assertThrows(IllegalArgumentException::class.java) { NightSchedule(start, end) }
    }
    @Test fun browserSignalOverridesScheduleOnlyInBrowserMode() {
        val schedule = NightSchedule()
        assertTrue(CarPlayAppearance.BROWSER.night(false, 720, schedule, true))
        assertFalse(CarPlayAppearance.BROWSER.night(true, 0, schedule, false))
        assertTrue(CarPlayAppearance.BROWSER.night(false, 0, schedule, null))
        assertFalse(CarPlayAppearance.BROWSER.night(true, 720, schedule, null))
        assertFalse(CarPlayAppearance.SCHEDULE.night(true, 720, schedule, true))
        assertFalse(CarPlayAppearance.DAY.night(true, 0, schedule, true))
        assertTrue(CarPlayAppearance.NIGHT.night(false, 720, schedule, false))
        assertTrue(CarPlayAppearance.SYSTEM.night(true, 720, schedule, false))
    }
}
