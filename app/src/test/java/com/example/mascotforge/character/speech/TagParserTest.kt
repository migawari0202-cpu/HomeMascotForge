package com.example.mascotforge.character.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TagParserTest {

    @Test
    fun testParseMultiByteVariableAssignment() {
        val parser = TagParser()
        
        // Test Japanese string value assignment
        val result1 = parser.parse("[var: mood = うれしい]")
        assertEquals(1, result1.variableOperations.size)
        val op1 = result1.variableOperations[0]
        assertEquals(OperationType.SET_STRING, op1.type)
        assertEquals("mood", op1.varName)
        assertEquals("うれしい", op1.stringValue)
        assertEquals("", result1.cleanedText)

        // Test normal string value assignment
        val result2 = parser.parse("[var: mood = happy]")
        assertEquals(1, result2.variableOperations.size)
        val op2 = result2.variableOperations[0]
        assertEquals(OperationType.SET_STRING, op2.type)
        assertEquals("mood", op2.varName)
        assertEquals("happy", op2.stringValue)
        assertEquals("", result2.cleanedText)
    }

    @Test
    fun testParseVariableAssignmentWithBracketsFails() {
        val parser = TagParser()
        
        // Having brackets inside RHS of assignment should fail to parse
        val result = parser.parse("[var: mood = abc[def]]")
        // Since it's parsed via Regex VAR_REGEX = "\\[(?:v|var):\\s*([^\\]]+)\\]".toRegex()
        // Wait, the outer VAR_REGEX will match "[var: mood = abc[def]" since it matches up to the first ']'!
        // The first ']' is after "def", so it parses "mood = abc[def" as the expression.
        // In parseNameBasedOperation, this "abc[def" has a bracket '[', so it should fail and return null!
        assertEquals(0, result.variableOperations.size)
    }

    @Test
    fun testParseOtherOperationsKeepWorking() {
        val parser = TagParser()
        
        val result = parser.parse("[v: favorability + 5] [v: trust = 100] [e:happy] 今日はいい天気だね！")
        assertEquals(2, result.variableOperations.size)
        
        val op1 = result.variableOperations[0]
        assertEquals(OperationType.ADD, op1.type)
        assertEquals("favorability", op1.varName)
        assertEquals(5, op1.operationValue)
        
        val op2 = result.variableOperations[1]
        assertEquals(OperationType.SET, op2.type)
        assertEquals("trust", op2.varName)
        assertEquals(100, op2.operationValue)

        assertEquals("happy", result.emotion)
        assertEquals("今日はいい天気だね！", result.cleanedText)
    }
}
