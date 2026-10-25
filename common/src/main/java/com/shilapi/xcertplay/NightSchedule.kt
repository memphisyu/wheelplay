package com.shilapi.xcertplay

/** Half-open interval, including ranges that cross midnight. */
data class NightSchedule(val startMinute: Int = 18 * 60 + 30, val endMinute: Int = 7 * 60) {
    init { require(startMinute in 0..1439 && endMinute in 0..1439 && startMinute != endMinute) }
    fun nightAt(minute: Int): Boolean {
        require(minute in 0..1439)
        return if (startMinute < endMinute) minute >= startMinute && minute < endMinute
        else minute >= startMinute || minute < endMinute
    }
}
