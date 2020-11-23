package com.cradleVSA.neptune.ext.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.ObjectWriter
import java.io.StringWriter

inline fun ObjectWriter.createJsonStringWithGenerator(
    jsonGeneratorBlock: JsonGenerator.() -> Unit
): String {
    val stringWriter = StringWriter()
    createGenerator(stringWriter).apply(jsonGeneratorBlock)
    return stringWriter.toString()
}
