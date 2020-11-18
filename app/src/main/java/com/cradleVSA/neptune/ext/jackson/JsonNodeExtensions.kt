package com.cradleVSA.neptune.ext.jackson

import com.cradleVSA.neptune.ext.Field
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode

fun JsonNode.get(field: Field): JsonNode? = get(field.text)

@Suppress("unused")
inline fun <reified T> JsonNode.getObject(field: Field, codec: ObjectCodec): T {
    return findValue(field.text).traverse(codec).run { readValueAs(T::class.java) }
}

inline fun <reified T> JsonNode.getOptObjectArray(field: Field, codec: ObjectCodec): List<T>? {
    return (get(field) as? ArrayNode)?.map { jsonNode ->
        jsonNode.traverse(codec).readValueAs(T::class.java)
    }
}

inline fun <reified T> JsonNode.getOptObject(field: Field, codec: ObjectCodec): T? {
    return findValue(field.text)?.traverse(codec)?.run { readValueAs(T::class.java) }
}
