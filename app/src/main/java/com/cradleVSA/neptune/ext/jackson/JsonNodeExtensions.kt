package com.cradleVSA.neptune.ext.jackson

import com.cradleVSA.neptune.ext.Field
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.ObjectCodec
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode

fun JsonNode.get(field: Field): JsonNode? = get(field.text)

@Suppress("unused")
inline fun <reified T> JsonNode.getObject(field: Field, codec: ObjectCodec): T {
    return findValue(field.text).traverse(codec).use { it.readValueAs(T::class.java) }
}

inline fun <reified T> JsonNode.getOptObject(field: Field, codec: ObjectCodec): T? {
    return findValue(field.text)?.traverse(codec)?.use { it.readValueAs(T::class.java) }
}

/**
 * Throws if array isn't present or if the [field] exists but it's not an array.
 */
inline fun <reified T> JsonNode.getObjectArray(field: Field, codec: ObjectCodec): List<T> =
    getOptObjectArray(field, codec) ?: error("expected object array, but was null or not array")

/**
 * Gets an optional array field of objects.
 * Note that if there is a field with the name but it's not an array, then it will return null.
 */
@PublishedApi
internal inline fun <reified T> JsonNode.getOptObjectArray(
    field: Field,
    codec: ObjectCodec
): List<T>? {
    val arrayNode = get(field) as? ArrayNode ?: return null
    if (arrayNode.size() == 0) return emptyList()
    val list = ArrayList<T>(arrayNode.size())
    arrayNode.traverse(codec).use { parser ->
        // Parser stream is before the start of the array; advance to START_ARRAY
        if (parser.nextToken() != JsonToken.START_ARRAY) error("not array")
        // The current token is START_ARRAY
        parser.nextToken()
        // We can use readValuesAs now.
        val iterator = parser.readValuesAs(T::class.java)
        while (iterator.hasNext()) {
            list.add(iterator.next())
        }
    }
    return list
}
