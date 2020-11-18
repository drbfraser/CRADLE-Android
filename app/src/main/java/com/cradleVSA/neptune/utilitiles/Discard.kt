package com.cradleVSA.neptune.utilitiles

/**
 * Explicitly discards this value.
 *
 * Can be used in expression-style functions (usually setters) to remove the
 * return type of return type of some function being called.
 *
 * ## Example
 *
 * In this example, let's assume that `setTheFoo` returns and integer. It would
 * usually be a compiler error to write `set(value) = setTheFoo(value)` because
 * setters require a return type of `Unit` but using `=` function notation
 * implicitly sees the return type as `Int`. Here we use [discard] to eliminate
 * that return type.
 *
 * ```
 * var foo: Int
 *     get() = getTheFoo()
 *     set(value) = setTheFoo(value).discard()
 * ```
 *
 * ref: https://stackoverflow.com/a/47379745
 */
@Suppress("unused")
fun Any?.discard() = Unit
