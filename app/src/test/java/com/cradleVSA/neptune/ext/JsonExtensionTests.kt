package com.cradleVSA.neptune.ext

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonExtensionTests {
    @Test
    fun jsonObject_unionInlinesJson() {
        val a = JSONObject("""{"a":1,"hello":"world"}""")
        val b = JSONObject("""{"b":2}""")
        a.union(b)

        assertEquals(1, a.get("a"))
        assertEquals(2, a.get("b"))
        assertEquals("world", a.get("hello"))
    }

    @Test
    fun jsonObject_unionOverwritesExisting() {
        val a = JSONObject("""{"a":1,"hello":"world"}""")
        val b = JSONObject("""{"a":2}""")
        a.union(b)

        assertEquals(2, a.get("a"))
        assertEquals("world", a.get("hello"))
    }
}
