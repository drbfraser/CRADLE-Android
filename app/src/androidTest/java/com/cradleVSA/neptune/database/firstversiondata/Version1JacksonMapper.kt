package com.cradleVSA.neptune.database.firstversiondata

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Holds the mapper used in version 1 of the database schema.
 *
 * DO NOT EDIT
 */
internal object Version1JacksonMapper {
    val mapper by lazy {
        jacksonObjectMapper()
    }
}