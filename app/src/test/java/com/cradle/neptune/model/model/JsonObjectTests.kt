package com.cradle.neptune.model.model

import com.cradle.neptune.model.JsonObject
import com.cradle.neptune.model.union
import org.junit.jupiter.api.Test

class JsonObjectTests {
    @Test
    fun jsonObject_unionInlinesJson() {
        val a = JsonObject("""{"a":1,"hello":"world"}""")
        val b = JsonObject("""{"b":2}""")
        a.union(b)

        assert(a.get("a") == 1)
        assert(a.get("b") == 2)
        assert(a.get("hello") == "world")
    }

    @Test
    fun jsonObject_unionOverwritesExisting() {
        val a = JsonObject("""{"a":1,"hello":"world"}""")
        val b = JsonObject("""{"a":2}""")
        a.union(b)

        assert(a.get("a") == 2)
        assert(a.get("hello") == "world")
    }
}
