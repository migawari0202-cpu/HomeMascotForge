package com.example.mascotforge.character

import com.example.mascotforge.speech.SpeechContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeExpressionEvaluatorTest {

    private fun createDummySpeechContext(
        hour: Int = 12,
        isWeekend: Boolean = false,
        weatherCode: String = "晴れ 時々 曇り",
        customVars: MutableMap<String, Int> = mutableMapOf()
    ): SpeechContext {
        return SpeechContext(
            timeSlot = "afternoon",
            hour = hour,
            minute = 0,
            month = 6,
            day = 26,
            dayOfWeek = "金曜日",
            isWeekend = isWeekend,
            isHoliday = false,
            holidayName = null,
            season = "夏",
            isSpecialDay = false,
            specialDayName = null,
            weatherEmoji = "☀️",
            weatherCode = weatherCode,
            temperature = 25,
            temperatureFeeling = "ちょうどいい",
            humidity = 50,
            batteryLevel = 80,
            batteryStatus = "通常",
            isCharging = false,
            isLowBattery = false,
            launchCount = 1,
            lastLaunchHoursAgo = null,
            isFirstLaunchToday = true,
            consecutiveDays = 1,
            userName = "ユーザー",
            userGender = null,
            isNearBedtime = false,
            isNearWakeup = false,
            moonPhase = null,
            wasTouched = false,
            touchCount = 0,
            touchCountToday = 0,
            lastTouchMinutesAgo = 0,
            consecutiveTouchCount = 0,
            pettingLevel = 0,
            isBeingPetted = false,
            characterState = CharacterState("test_char", customVars = customVars)
        )
    }

    @Test
    fun testSpaceStrippingOutsideQuotesOnly() {
        val ctx = createDummySpeechContext(weatherCode = "晴れ 時々 曇り")
        
        // Expression has spaces inside quotes and outside.
        // Spaces outside should be stripped (e.g. around == and &&), spaces inside quotes preserved.
        val evaluator = SafeExpressionEvaluator(ctx)
        
        assertTrue(evaluator.evaluate("weatherCode == \"晴れ 時々 曇り\""))
        assertFalse(evaluator.evaluate("weatherCode == \"晴れ時々曇り\""))
    }

    @Test
    fun testNegationWithParentheses() {
        // hour = 12 (not in 6..11)
        val ctx1 = createDummySpeechContext(hour = 12)
        val evaluator1 = SafeExpressionEvaluator(ctx1)
        
        // hour >= 6 && hour < 12 is: 12 >= 6 (true) && 12 < 12 (false) -> false
        // !(hour >= 6 && hour < 12) should be true!
        assertTrue(evaluator1.evaluate("!(hour >= 6 && hour < 12)"))
        
        // hour = 8 (in 6..11)
        val ctx2 = createDummySpeechContext(hour = 8)
        val evaluator2 = SafeExpressionEvaluator(ctx2)
        
        // hour >= 6 && hour < 12 is: 8 >= 6 (true) && 8 < 12 (true) -> true
        // !(hour >= 6 && hour < 12) should be false!
        assertFalse(evaluator2.evaluate("!(hour >= 6 && hour < 12)"))
    }

    @Test
    fun testMultipleNegationAndNestedParentheses() {
        val ctx = createDummySpeechContext(isWeekend = true)
        val evaluator = SafeExpressionEvaluator(ctx)
        
        assertTrue(evaluator.evaluate("isWeekend"))
        assertFalse(evaluator.evaluate("!isWeekend"))
        assertTrue(evaluator.evaluate("!!isWeekend"))
        assertTrue(evaluator.evaluate("!(!isWeekend)"))
    }
}
