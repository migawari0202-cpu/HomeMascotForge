package com.example.mascotforge.character

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomVariableAnyTest {
    @Test
    fun parsesAnyTypeAndPreservesJsonInitialValue() {
        val variable = parseCustomVariable(
            "freeValue",
            JSONObject("""{"type":"any","initial":5}""")
        )

        assertEquals(CustomVariable.VariableType.ANY, variable.type)
        assertEquals(5, variable.initialValue)
        assertTrue(variable.options == null)
    }

    @Test
    fun characterStateStoresAnyValuesSeparatelyFromLegacyInts() {
        val state = CharacterState("test")
        state.setCustomVar("number", 7)
        state.setCustomStrVar("freeValue", "自由文字列")

        assertEquals(7, state.getCustomVar("number"))
        assertEquals("自由文字列", state.getCustomStrVar("freeValue"))
    }
}
