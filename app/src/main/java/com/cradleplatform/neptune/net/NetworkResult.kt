package com.cradleplatform.neptune.net

import android.content.Context
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.net.NetworkResult.Failure
import com.cradleplatform.neptune.net.NetworkResult.NetworkException
import com.cradleplatform.neptune.net.NetworkResult.Success

/**
 * The result of a network request.
 *
 * Objects of this type can only be instances of one of the three implementing
 * types defined in this file: [Success], [Failure], or [NetworkException].
 *
 * Types like this are known as *discriminated (or tagged) unions* and are
 * useful when dealing with data which has a fixed number of various forms it
 * may take. For more information on how these types work in Kotlin, see
 * [sealed class](https://kotlinlang.org/docs/reference/sealed-classes.html).
 *
 * @param T wrapped type of the [Success] variant
 */
sealed interface NetworkResult<T> {

    companion object {
        private const val UNAUTHORIZED = 401
        private const val BAD_REQUEST = 400
        private const val NOT_FOUND = 404
        private const val CONFLICT = 409
    }

    /**
     * Unwraps this network result into an optional value.
     *
     * @return the result value or null depending on whether the result is a
     *  [Success], [Failure], [NetworkException] variant
     */
    val unwrapped: T?
        get() = when (this) {
            is Success -> value
            else -> null
        }

    /**
     * Returns `true` if this result is a [Failure] or [Exception] variant.
     */
    val failed: Boolean
        get() = when (this) {
            is Success -> false
            else -> true
        }

    /**
     * A friendly error message for this [NetworkResult].
     *
     * @return A message describing the error contained in this result or null
     *  if this result is a [Success] variant.
     */
    fun getErrorMessage(context: Context): String? = when (this) {
        is Success -> null
        is NetworkException -> this.cause.message
        is Failure -> when (this.statusCode) {
            UNAUTHORIZED -> context.getString(
                R.string.network_result_error_server_rejected_credentials
            )
            BAD_REQUEST -> context.getString(
                R.string.network_result_error_server_rejected_upload_request
            )
            NOT_FOUND -> context.getString(R.string.network_result_error_server_rejected_url)
            CONFLICT -> context.getString(
                R.string.network_result_error_reading_or_patient_might_already_exist
            )
            else -> context.getString(
                R.string.network_result_error_generic_status_code,
                statusCode
            )
        }
    }

    /**
     * Casts error variants of [NetworkResult] into a different inner type.
     *
     * @throws RuntimeException if `this` is a [Success] variant
     * @return the same error result with an inner type of [U] instead of [T]
     */
    fun <U> cast(): NetworkResult<U> = when (this) {
        is Success -> throw RuntimeException("cast called on Success variant")
        is Failure -> Failure(body, statusCode)
        is NetworkException -> NetworkException(cause)
    }

    /**
     * Sequences two [NetworkResult] monads into a single result which will
     * be a [Success] variant iff both results are [Success] variants. If
     * either are erroneous variants, the first erroneous result is returned.
     *
     * @return A sequenced [NetworkResult]
     */
    infix fun <U> sequence(other: NetworkResult<U>) = when {
        // Results in Success
        this is Success && other is Success -> other
        // Results if Failure or NetworkException
        this is Success -> other
        else -> this.cast()
    }

    /**
     * The result of a successful network request.
     *
     * A request is considered successful if the response has a status code in the
     * 200..<300 range.
     *
     * @property value The result value
     * @property statusCode Status code of the response which generated this result
     */
    data class Success<T>(val value: T, val statusCode: Int) : NetworkResult<T>

    /**
     * The result of a network request which made it to the server but the status
     * code of the response indicated a failure (e.g., 404, 500, etc.).
     *
     * Contains the response status code along with the response body as a byte
     * array. Note that the body is not of type [T] like in [Success] since the
     * response for a failed request may not be the same type as the response for
     * a successful request.
     *
     * @property body The body of the response
     * @property statusCode The status code of the response
     */
    data class Failure<T>(val body: ByteArray, val statusCode: Int) : NetworkResult<T> {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Failure<*>

            if (!body.contentEquals(other.body)) return false
            if (statusCode != other.statusCode) return false

            return true
        }

        override fun hashCode(): Int {
            var result = body.contentHashCode()
            result = 31 * result + statusCode
            return result
        }
    }

    /**
     * Represents an exception that occurred whilst making a network request.
     *
     * @property cause the exception which caused the failure
     */
    @JvmInline
    value class NetworkException<T>(val cause: Exception) : NetworkResult<T>
}

/**
 * Applies a closure [f] to transform the value field of a [NetworkResult.Success] result.
 *
 * In the case of [NetworkResult.Failure] and [NetworkResult.NetworkException] variants, this method
 * simply changes the type of the result. If you know that a result is
 * a [NetworkResult.Failure] or [NetworkResult.NetworkException], consider using [cast] instead.
 *
 * @param f transformation to apply to the result value
 * @return a new [NetworkResult] with the transformed value
 */
inline fun <T, U> NetworkResult<T>.map(f: (T) -> U): NetworkResult<U> = when (this) {
    is NetworkResult.Success -> NetworkResult.Success(f(value), statusCode)
    is NetworkResult.Failure -> NetworkResult.Failure(body, statusCode)
    is NetworkResult.NetworkException -> NetworkResult.NetworkException(cause)
}
