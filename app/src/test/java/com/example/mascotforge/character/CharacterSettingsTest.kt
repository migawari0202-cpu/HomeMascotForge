package com.example.mascotforge.character

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterSettingsTest {

    @Test
    fun `no imageCutout returns null`() {
        val settings = CharacterSettingsLoader.parse(JSONObject("{}"))
        assertNull(settings.imageCutout)
    }

    @Test
    fun `byTag shorthand array parses and wildcard applies`() {
        val settings = CharacterSettingsLoader.parse(
            JSONObject(
                """
                {
                  "imageCutout": {
                    "defaultTolerance": 30,
                    "byTag": {
                      "*": ["#fc0000"],
                      "happy": ["#00ff00"]
                    }
                  }
                }
                """.trimIndent()
            )
        )
        assertNotNull(settings.imageCutout)
        assertEquals(30, settings.imageCutout!!.defaultTolerance)

        val happy = settings.findCutoutSpec("happy")
        assertNotNull(happy)
        assertEquals(1, happy!!.colors.size)

        val unknown = settings.findCutoutSpec("unknown")
        assertNotNull(unknown) // wildcard
        assertEquals(1, unknown!!.colors.size)
    }

    @Test
    fun `object form parses tolerance and caps colors to 10`() {
        val settings = CharacterSettingsLoader.parse(
            JSONObject(
                """
                {
                  "imageCutout": {
                    "defaultTolerance": 30,
                    "byTag": {
                      "normal": {
                        "tolerance": 5,
                        "colors": ["#000000","#111111","#222222","#333333","#444444","#555555","#666666","#777777","#888888","#999999","#aaaaaa"]
                      }
                    }
                  }
                }
                """.trimIndent()
            )
        )
        val spec = settings.findCutoutSpec("normal")
        assertNotNull(spec)
        assertEquals(5, spec!!.tolerance)
        assertEquals(10, spec.colors.size)
    }
}

