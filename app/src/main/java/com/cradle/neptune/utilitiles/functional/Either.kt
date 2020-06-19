package com.cradle.neptune.utilitiles.functional

/**
 * A discriminated union of two values primarily used to return either a good
 * value or an error value from a function.
 *
 * By convention, the [Right] variant is used to carry the good, or "right",
 * value and the [Left] variant is used to carry the error value.
 *
 * @param T Type of the left variant's value
 * @param U Type of the right variant's value
 */
sealed class Either<T, U> {

    /**
     * True if this is a left variant.
     */
    abstract val isLeft: Boolean

    /**
     * True if this is a right variant.
     */
    abstract val isRight: Boolean

    /**
     * Unwraps a left variant.
     *
     * Throws an exception if this is not a left variant.
     */
    abstract val left: T

    /**
     * Unwraps a right variant.
     *
     * Throws an exception if this is not a right variant.
     */
    abstract val right: U

    /**
     * Maps the left variant of this either.
     *
     * If [f] is not a pure function (i.e., has side-effects) then it is
     * important to note that it will only be called if this either is a
     * [Left] variant.
     */
    fun <A> mapLeft(f: (T) -> A): Either<A, U> = when (this) {
        is Left -> Left(f(this.value))
        is Right -> Right(this.value)
    }

    /**
     * Maps the right variant of this either.
     *
     * If [f] is not a pure function (i.e., has side-effects) then it is
     * important to note that it will only be called if this either is a
     * [Right] variant.
     */
    fun <B> mapRight(f: (U) -> B): Either<T, B> = when (this) {
        is Left -> Left(this.value)
        is Right -> Right(f(this.value))
    }
}

/**
 * Coalesces this either into a single value.
 */
fun <T> Either<T, T>.coalesce(): T = when (this) {
    is Left -> this.value
    is Right -> this.value
}

/**
 * The left variant of an [Either].
 *
 * By convention, this variant carries an erroneous value.
 */
class Left<T, U>(val value: T) : Either<T, U>() {
    override val isLeft = true
    override val isRight = false

    override val left = value
    override val right
        get() = throw RuntimeException("unwrap right variant of left either")
}

/**
 * The right variant of an [Either].
 *
 * By convention, this variant carries the good, or "right", value.
 */
class Right<T, U>(val value: U) : Either<T, U>() {
    override val isLeft = false
    override val isRight = true

    override val left
        get() = throw RuntimeException("unwrap right variant of left either")
    override val right = value
}
