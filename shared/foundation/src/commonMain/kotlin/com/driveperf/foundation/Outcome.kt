package com.driveperf.foundation

/** The result of an operation that can fail in an expected way (ADR-002 section 7). */
public sealed interface Outcome<out T> {
    /** The operation succeeded with [value]. */
    public data class Success<out T>(val value: T) : Outcome<T>

    /** The operation failed with an expected [error]. */
    public data class Failure(val error: DomainError) : Outcome<Nothing>
}

/** Transforms the success value and passes a failure through unchanged. */
public inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

/** Chains another fallible step after a success. */
public inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

/** Returns the success value, or [fallback] applied to the error. */
public inline fun <T> Outcome<T>.getOrElse(fallback: (DomainError) -> T): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> fallback(error)
}
