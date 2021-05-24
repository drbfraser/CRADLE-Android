package com.cradleplatform.neptune.utilities

import java.math.BigInteger

object UnixTimestamp {

    private const val MS_IN_SECOND = 1000L

    /**
     * The current time as a unix timestamp.
     */
    val now: BigInteger
        get() = BigInteger.valueOf(System.currentTimeMillis() / MS_IN_SECOND)

    /**
     * Returns a unix timestamp for as specific duration of time in the past
     * relative to the current time.
     *
     * @param duration an amount of time
     * @return a unix timestamp
     */
    fun ago(duration: Duration): BigInteger =
        now - BigInteger.valueOf(duration.seconds.value)
}
