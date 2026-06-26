package com.example.mascotforge

import com.example.mascotforge.character.CharacterState
import com.example.mascotforge.speech.SpeechContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechContextTest {

    private fun createSpeechContext(
        customVars: MutableMap<String, Int> = mutableMapOf()
    ): SpeechContext {
        return SpeechContext(
            timeSlot = "afternoon",
            hour = 12,
            minute = 0,
            month = 6,
            day = 26,
            dayOfWeek = "金曜日",
            isWeekend = false,
            isHoliday = false,
            holidayName = null,
            season = "夏",
            isSpecialDay = false,
            specialDayName = null,
            weatherEmoji = "☀️",
            weatherCode = "晴れ",
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
    fun testCustomVariableDirectMatching() {
        val customVars = mutableMapOf("favorability" to 85, "is_happy" to 1)
        val ctx = createSpeechContext(customVars)

        // 1. Existing behavior: matches customVars[varName]
        assertTrue(ctx.matches("customVars[favorability]", "85"))
        assertTrue(ctx.matches("customVars[favorability]", ">=80"))
        
        // 2. New behavior: matches direct varName if it exists in characterState's custom variables
        assertTrue(ctx.matches("favorability", "85"))
        assertTrue(ctx.matches("favorability", ">=80"))
        assertFalse(ctx.matches("favorability", "<50"))
        
        // Non-existent key should return false
        assertFalse(ctx.matches("non_existent_var", "10"))
    }

    @Test
    fun testBooleanCustomVariableComparison() {
        val customVars = mutableMapOf("is_happy" to 1, "is_tired" to 0)
        val ctx = createSpeechContext(customVars)

        // Test with customVars[...] format
        assertTrue(ctx.matches("customVars[is_happy]", "true"))
        assertFalse(ctx.matches("customVars[is_happy]", "false"))
        assertTrue(ctx.matches("customVars[is_tired]", "false"))
        assertFalse(ctx.matches("customVars[is_tired]", "true"))

        // Test with direct varName format
        assertTrue(ctx.matches("is_happy", "true"))
        assertFalse(ctx.matches("is_happy", "false"))
        assertTrue(ctx.matches("is_tired", "false"))
        assertFalse(ctx.matches("is_tired", "true"))
    }
}
