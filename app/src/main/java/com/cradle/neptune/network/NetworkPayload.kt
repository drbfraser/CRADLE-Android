package com.cradle.neptune.network

import com.cradle.neptune.model.JsonArray
import com.cradle.neptune.model.JsonObject

sealed class NetworkPayload {
    val jsonObject: JsonObject
        get() = when (this) {
            is JsonObjectPayload -> json
            is JsonArrayPayload -> throw RuntimeException("attempt to unwrap json array as json object")
        }

    val jsonArray: JsonArray
        get() = when (this) {
            is JsonObjectPayload -> throw RuntimeException("attempt to unwrap json object as json array")
            is JsonArrayPayload -> json
        }
}

data class JsonObjectPayload(val json: JsonObject) : NetworkPayload()

data class JsonArrayPayload(val json: JsonArray) : NetworkPayload()
