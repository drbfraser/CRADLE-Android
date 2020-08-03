package com.cradle.neptune.utilitiles

object UnixTimestamp {

    private const val MS_IN_SECOND = 1000L

    /**
     * The current time as a unix timestamp.
     */
    val now: Long
        get() = System.currentTimeMillis() / MS_IN_SECOND
}
