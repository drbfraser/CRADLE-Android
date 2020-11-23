package com.cradleVSA.neptune.ext.jackson

import com.fasterxml.jackson.databind.MappingIterator
import java.io.IOException

/**
 * A forEach extension to use with MappingIterator's. This will throw checked exceptions.
 * Do not use Kotlin's default forEach implementation for Iterator interfaces, as that
 * can throw RuntimeExceptions when used with [MappingIterator]s.
 *
 * Kotlin's default extension for general
 * Iterator<T> interfaces involve this:
 *
 *      public inline fun <T> Iterator<T>.forEach(operation: (T) -> Unit): Unit {
 *          for (element in this) operation(element)
 *      }
 *
 * However, when this throws an IOException (like JsonEOFException), it will be wrapped in a
 * RuntimeException instead of just throwing the IOException. This is because Kotlin's
 * implementation calls [Iterator.next] and [Iterator.hasNext] for which MappingIterator
 * throws RuntimeExceptions for. The Jackson way is to use [MappingIterator.nextValue] and
 * [MappingIterator.hasNextValue]; these methods will throw checked exceptions.
 *
 * @throws IOException Checked exceptions may be thrown from Jackson due to invalid input.
 */
inline fun <T> MappingIterator<T>.forEachJackson(operation: (T) -> Unit) {
    while (hasNextValue()) operation(nextValue())
}
