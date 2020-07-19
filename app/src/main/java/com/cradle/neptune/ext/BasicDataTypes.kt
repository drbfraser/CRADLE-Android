package com.cradle.neptune.ext

import com.cradle.neptune.model.Referral
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import java.util.Calendar

fun Long.ConvertToZoneTimeFromUnix(): ZonedDateTime {
    val i = Instant.ofEpochSecond(this)
    return ZonedDateTime.ofInstant(
        i,ZoneId.systemDefault())
}