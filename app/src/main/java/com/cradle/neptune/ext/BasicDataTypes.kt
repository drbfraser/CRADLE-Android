package com.cradle.neptune.ext

import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

/**
 * Converts any [Long] to [ZonedDateTime]
 * The value must be in unix timestamp
 */
fun Long.convertToZoneTimeFromUnix(): ZonedDateTime {
    val i = Instant.ofEpochSecond(this)
    return ZonedDateTime.ofInstant(
        i, ZoneId.systemDefault()
    )
}
