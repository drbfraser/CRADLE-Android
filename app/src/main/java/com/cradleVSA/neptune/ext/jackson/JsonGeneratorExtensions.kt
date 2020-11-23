package com.cradleVSA.neptune.ext.jackson

import com.cradleVSA.neptune.ext.Field
import com.fasterxml.jackson.core.JsonGenerator

fun JsonGenerator.writeObjectField(field: Field, v: Any) = writeObjectField(field.text, v)

fun JsonGenerator.writeOptObjectField(field: Field, v: Any?) = v?.let {
    writeObjectField(field.text, it)
}

fun JsonGenerator.writeStringField(field: Field, v: String) = writeStringField(field.text, v)

fun JsonGenerator.writeOptStringField(field: Field, v: String?) = v?.let {
    writeStringField(field.text, it)
}

fun JsonGenerator.writeBooleanField(field: Field, v: Boolean) = writeBooleanField(field.text, v)

@Suppress("unused")
fun JsonGenerator.writeOptBooleanField(field: Field, v: Boolean?) = v?.let {
    writeBooleanField(field.text, it)
}

fun JsonGenerator.writeIntField(field: Field, v: Int) = writeNumberField(field.text, v)

fun JsonGenerator.writeOptIntField(field: Field, v: Int?) = v?.let { writeNumberField(field.text, v) }

fun JsonGenerator.writeLongField(field: Field, v: Long) = writeNumberField(field.text, v)

fun JsonGenerator.writeOptLongField(field: Field, v: Long?) = v?.let {
    writeNumberField(field.text, it)
}
