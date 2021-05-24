package com.cradleplatform.neptune.testutils

inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
    try {
        block()
    } catch (throwable: Throwable) {
        when (throwable) {
            is T -> {
                return throwable
            }
            is OutOfMemoryError -> {
                throw throwable
            }
            else -> {
                throw AssertionError(
                    "Unexpected exception type thrown ==> " +
                        "expected: <${T::class.qualifiedName}> " +
                        "but was: <${throwable::class.qualifiedName}>"
                )
            }
        }
    }
    throw AssertionError(
        "Expected <${T::class.qualifiedName}> to be thrown, but " +
            "nothing was thown."
    )
}

inline fun assert(value: Boolean, lazyMessage: () -> String) {
    if (!value) {
        throw AssertionError(lazyMessage())
    }
}

inline fun <T> assertEquals(expected: T, actual: T, lazyMessage: () -> String) {
    if (expected != actual) {
        throw AssertionError(lazyMessage())
    }
}

inline fun <T> assertNotEquals(expected: T, actual: T, lazyMessage: () -> String) {
    if (expected == actual) {
        throw AssertionError(lazyMessage())
    }
}
