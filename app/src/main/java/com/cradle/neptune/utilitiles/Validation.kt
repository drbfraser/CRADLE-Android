package com.cradle.neptune.utilitiles

import com.cradle.neptune.utilitiles.functional.Either
import com.cradle.neptune.utilitiles.functional.Left
import com.cradle.neptune.utilitiles.functional.Right
import kotlin.text.isLetterOrDigit

/**
 * Ensures that [port] is a valid port number.
 *
 * A port is considered to be valid if it can be parsed as a base-10 number
 * and has a value in the inclusive range [1, 655353].
 *
 * @param port A string which may or may not be a port
 * @return `Right(Unit)` if [port] is valid, or `Left(err)` if its not. Here
 * `err` is some error message describing why [port] is not valid.
 */
fun validatePort(port: String): Either<String, Unit> {
    // Allow black values as they will be treated as the default port number
    if (port.isEmpty()) {
        return Right(Unit)
    }

    // Ensure that the port is actually a number
    val n = port.toIntOrNull() ?: return Left("Port must be a number")

    // Check to make sure it is a valid port number
    if (n !in 1..655353) {
        return Left("'$port' is not a valid port number")
    }

    return Right(Unit)
}

/**
 * Ensures that [hostname] is a valid fully qualified domain name.
 *
 * Note that this function is rather lax on the requirements for a fully
 * qualified domain name. For example, we don't take into account the max
 * length for sub-domain parts. With that being said, the implementation here
 * will be good enough for most use cases.
 *
 * @param hostname A string which may or may not be a hostname.
 * @return `Right(Unit)` if [hostname] is valid, or `Left(err)` if its not.
 * Here `err` is some error message describing why [hostname] is not valid.
 */
fun validateHostname(hostname: String): Either<String, Unit> {
    // We don't allow blank values.
    if (hostname.isBlank()) {
        return Left("Hostname may not be blank")
    }

    val isValid = hostname.split('.')
        .all { subDomain ->
            subDomain.isNotEmpty() && subDomain.all { c ->
                val isAscii = c.toInt() < 128
                isAscii && (c.isLetterOrDigit() || c == '-')
            }
        }

    return if (isValid) {
        Right(Unit)
    } else {
        Left("'$hostname' is not a valid hostname")
    }
}
