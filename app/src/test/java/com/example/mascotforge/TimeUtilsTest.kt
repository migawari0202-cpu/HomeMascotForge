package com.example.mascotforge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun getTimeSlot_coversAllRanges() {
        assertEquals("midnight", getTimeSlot(0))
        assertEquals("midnight", getTimeSlot(4))
        assertEquals("morning", getTimeSlot(5))
        assertEquals("morning", getTimeSlot(11))
        assertEquals("afternoon", getTimeSlot(12))
        assertEquals("afternoon", getTimeSlot(16))
        assertEquals("evening", getTimeSlot(17))
        assertEquals("evening", getTimeSlot(20))
        assertEquals("night", getTimeSlot(21))
        assertEquals("night", getTimeSlot(23))
    }

    @Test
    fun isDaytime_range() {
        assertFalse(isDaytime(5))
        assertTrue(isDaytime(6))
        assertTrue(isDaytime(17))
        assertFalse(isDaytime(18))
    }

    @Test
    fun lateNightAndEarlyMorning() {
        assertTrue(isLateNight(1))
        assertTrue(isLateNight(4))
        assertFalse(isLateNight(0))
        assertFalse(isLateNight(5))

        assertTrue(isEarlyMorning(5))
        assertTrue(isEarlyMorning(6))
        assertFalse(isEarlyMorning(4))
        assertFalse(isEarlyMorning(7))
    }

    @Test
    fun rushHour_weekdayOnly() {
        assertTrue(isRushHour(8, isWeekend = false))
        assertTrue(isRushHour(18, isWeekend = false))
        assertFalse(isRushHour(8, isWeekend = true))
        assertFalse(isRushHour(12, isWeekend = false))
    }

    @Test
    fun nearBedtimeAndWakeup() {
        assertTrue(isNearBedtime(21))
        assertTrue(isNearBedtime(1))
        assertFalse(isNearBedtime(2))

        assertTrue(isNearWakeup(6))
        assertTrue(isNearWakeup(8))
        assertFalse(isNearWakeup(9))
        assertEquals(isWakeupTime(7), isNearWakeup(7))
    }

    @Test
    fun lunchSnackMidnightHelpers() {
        assertTrue(isLunchTime(11, 30))
        assertTrue(isLunchTime(12, 0))
        assertFalse(isLunchTime(11, 29))

        assertTrue(isSnackTime(10))
        assertTrue(isSnackTime(15))
        assertFalse(isSnackTime(14))

        assertTrue(isAlmostMidnight(23, 50))
        assertFalse(isAlmostMidnight(23, 49))
        assertTrue(isExactMidnight(0, 0))
        assertFalse(isExactMidnight(0, 1))
    }
}
