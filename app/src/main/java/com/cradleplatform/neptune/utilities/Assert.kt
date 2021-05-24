package com.cradleplatform.neptune.utilities

/**
 * Used to mark code paths which are known to be unreachable.
 *
 * This function never returns, always throwing an exception. You should never
 * try to catch this exception as it being thrown is indication of a programmer
 * error and not something that is catchable.
 */
fun unreachable(msg: String? = null): Nothing {
    throw UnreachableException(msg)
}

private class UnreachableException(msg: String?) : AssertionError(formatUnreachableMessage(msg))

private fun formatUnreachableMessage(msg: String?) =
    "Unreachable code entered${if (msg == null) "" else " : $msg"}"
