package com.cradle.neptune.ext

import androidx.test.espresso.idling.CountingIdlingResource

/**
 * \[idlingResource] is incremented during execution of the [block] and then
 * decremented after. This lets Espresso UI tests synchronize itself.
 */
inline fun withIdlingResource(idlingResource: CountingIdlingResource?, block: () -> Unit) {
    // micro-optimize by not using try-finally; does it actually matter?
    if (idlingResource == null) {
        block()
    } else {
        idlingResource.increment()
        try {
            block()
        } finally {
            idlingResource.decrement()
        }
    }
}
