package com.cradleplatform.neptune.utilities

/**
 * Runs with a rate limit
 */
class RateLimitRunner(seconds: Double) {

    constructor(seconds: Long) : this(seconds.toDouble())

    var rateLimitTimeNanoSeconds: Long = (seconds * NANO_SECONDS_PER_SECOND).toLong()
        private set

    var lastRunTime: Long = 0

    suspend inline fun runSuspend(crossinline block: suspend () -> Unit) {
        val now = System.nanoTime()
        if (now - lastRunTime > rateLimitTimeNanoSeconds) {
            block()
            lastRunTime = now
        }
    }

    companion object {
        private const val NANO_SECONDS_PER_SECOND = 1000000000L
    }
}
