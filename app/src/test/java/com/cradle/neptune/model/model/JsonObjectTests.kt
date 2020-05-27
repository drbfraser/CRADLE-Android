package com.cradle.neptune.model.model

import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.union
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JsonObjectTests {
    @Test
    fun jsonObject_unionInlinesJson() {
        val a = JsonObject("""{"a":1,"hello":"world"}""")
        val b = JsonObject("""{"b":2}""")
        a.union(b)

        Assertions.assertEquals(1, a.get("a"))
        Assertions.assertEquals(2, a.get("b"))
        Assertions.assertEquals("world", a.get("hello"))
    }

    @Test
    fun jsonObject_unionOverwritesExisting() {
        val a = JsonObject("""{"a":1,"hello":"world"}""")
        val b = JsonObject("""{"a":2}""")
        a.union(b)

        Assertions.assertEquals(2, a.get("a"))
        Assertions.assertEquals("world", a.get("hello"))
    }
}
