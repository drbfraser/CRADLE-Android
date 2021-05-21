package com.cradleplatform.neptune.ext

import androidx.test.espresso.idling.CountingIdlingResource

/**
 * The [CountingIdlingResource] is incremented during execution of the [block] and then
 * decremented after. This lets Espresso UI tests synchronize itself. If the
 * [CountingIdlingResource] is null, then it doesn't do anything.
 */
inline fun CountingIdlingResource?.use(block: () -> Unit) {
    // micro-optimize by not using try-finally; does it actually matter?
    if (this == null) {
        block()
    } else {
        increment()
        try {
            block()
        } finally {
            decrement()
        }
    }
}
