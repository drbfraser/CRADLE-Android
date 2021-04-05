package com.cradleVSA.neptune.utilities.jackson

import com.cradleVSA.neptune.utilitiles.jackson.JacksonMapper
import org.junit.jupiter.api.Test

class JacksonMapperTests {
    @Test
    fun `test string list writer`() {
        //val writer = JacksonMapper.writerForStringList
        //val stringList = listOf("A", "B", "C")
        //assertEquals("[\"A\",\"B\",\"C\"]", JacksonMapper.mapper.writeValueAsString(stringList))
    }

    @Test
    fun `creating multiple writers gets cached by Jackson`() {
        val x = listOf("A", "B")
        JacksonMapper.mapper.writeValueAsString(x)

        JacksonMapper.mapper.writeValueAsString(x)
    }
}