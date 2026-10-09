package com.example.mascotforge.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetTouchEventTest {
    @Test
    fun earlierTapWinsRegardlessOfPriority() {
        val weather = WidgetTouchEvent(WidgetTouchTarget.WEATHER, 1)
        val character = WidgetTouchEvent(WidgetTouchTarget.CHARACTER, 2)
        assertEquals(weather, selectWidgetTouchEvent(listOf(character, weather), 100, 0))
        assertEquals(WidgetTouchTarget.CHARACTER, selectWidgetTouchEvent(listOf(WidgetTouchEvent(WidgetTouchTarget.WEATHER, 2), WidgetTouchEvent(WidgetTouchTarget.CHARACTER, 1)), 0, 100)?.target)
    }

    @Test
    fun simultaneousTapUsesCharacterRulesPriorityThenCharacterTieBreak() {
        val weather = WidgetTouchEvent(WidgetTouchTarget.WEATHER, 1)
        val character = WidgetTouchEvent(WidgetTouchTarget.CHARACTER, 1)
        assertEquals(weather, selectWidgetTouchEvent(listOf(character, weather), 10, 20))
        assertEquals(character, selectWidgetTouchEvent(listOf(weather, character), 20, 10))
        assertEquals(character, selectWidgetTouchEvent(listOf(weather, character), 10, 10))
    }
}