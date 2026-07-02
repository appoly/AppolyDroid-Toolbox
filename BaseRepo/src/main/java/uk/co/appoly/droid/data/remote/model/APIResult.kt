package uk.co.appoly.droid.data.remote.model

import uk.co.appoly.droid.util.NoConnectivityException
import uk.co.appoly.droid.util.ServerUnreachableException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A generic class that holds a value with its loading status.
 * @param <T>
 */
sealed class APIResult<out T : Any> {
	data class Success<out T : Any>(val data: T) : APIResult<T>()

	data class Error(val responseCode: Int, val message: String, val throwable: Throwable? = null) : APIResult<Nothing>() {
		constructor(other: Error) : this(responseCode = other.responseCode, message = other.message, throwable = other.exception)
		constructor(message: String, throwable: Throwable? = null) : this(responseCode = -1, message = message, throwable = throwable)

		val exception: Throwable
			get() = throwable ?: APIError(message)

		fun isNetworkError(): Boolean {
			return throwable is NoConnectivityException
		}

		fun isServerUnreachable(): Boolean {
			return throwable is ServerUnreachableException
		}
	}

	override fun toString(): String {
		return when (this) {
			is Success<*> -> "Success[data=$data]"
			is Error -> "Error[message=\"$message\",exception=$exception]"
		}
	}
}

/**
 * An exception that is thrown when an API call fails.
 */
class APIError(message: String) : Exception(message)

/**
 * Returns true if the [APIResult] is [APIResult.Success].
 *
 * @return true if the [APIResult] is [APIResult.Success].
 *
 * @see APIResult.Success
 */
@OptIn(ExperimentalContracts::class)
fun <T : Any> APIResult<T>?.isSuccess(): Boolean {
	contract {
		returns(true) implies (this@isSuccess is APIResult.Success)
	}
	return this is APIResult.Success
}

/**
 * Returns the data if the [APIResult] is [APIResult.Success] or null otherwise.
 *
 * @return the data if the [APIResult] is [APIResult.Success] or null if it is [APIResult.Error].
 *
 * @see APIResult.Success
 * @see APIResult.Error
 */
@OptIn(ExperimentalContracts::class)
fun <T : Any> APIResult<T>?.successOrNull(): T? {
	contract {
		returnsNotNull() implies (this@successOrNull is APIResult.Success)
		returns(null) implies (this@successOrNull is APIResult.Error)
	}
	return if (this.isSuccess()) {
		data
	} else {
		null
	}
}

/**
 * Returns true if the [APIResult] is [APIResult.Error].
 *
 * @return true if the [APIResult] is [APIResult.Error].
 *
 * @see APIResult.Error
 */
@OptIn(ExperimentalContracts::class)
fun <T : Any> APIResult<T>?.isError(): Boolean {
	contract {
		returns(true) implies (this@isError is APIResult.Error)
	}
	return this is APIResult.Error
}

/**
 * Returns true if the [APIResult] is [APIResult.Error] and the error is a network error.
 *
 * @return true if the [APIResult] is [APIResult.Error] and the error is a network error.
 *
 * @see APIResult.Error and [NoConnectivityException]
 */
@OptIn(ExperimentalContracts::class)
fun <T : Any> APIResult<T>?.isNetworkError(): Boolean {
	contract {
		returns(true) implies (this@isNetworkError is APIResult.Error)
	}
	return isError() && this.isNetworkError()
}

/**
 * Returns true if the [APIResult] is [APIResult.Error] and the device is online but the server could
 * not be reached (e.g. DNS failure, connection refused, timeout).
 *
 * Note: a [ServerUnreachableException] is also a [NoConnectivityException], so [isNetworkError] also
 * returns true for these errors — use this when you need to distinguish "no internet" from
 * "couldn't reach the server".
 *
 * @return true if the [APIResult] is [APIResult.Error] and the error is a server-unreachable error.
 *
 * @see APIResult.Error and [ServerUnreachableException]
 */
@OptIn(ExperimentalContracts::class)
fun <T : Any> APIResult<T>?.isServerUnreachable(): Boolean {
	contract {
		returns(true) implies (this@isServerUnreachable is APIResult.Error)
	}
	return isError() && this.isServerUnreachable()
}

@OptIn(ExperimentalContracts::class)
inline fun <T : Any> APIResult<T>.onSuccess(action: (APIResult.Success<T>) -> APIResult<T>): APIResult<T> {
	contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
	return if (this.isSuccess()) {
		action(this)
	} else {
		this
	}
}

@OptIn(ExperimentalContracts::class)
inline fun <T : Any> APIResult<T>.onError(action: (APIResult.Error) -> APIResult<T>): APIResult<T> {
	contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
	return if (this is APIResult.Error) {
		action(this)
	} else {
		this
	}
}

/**
 * Maps a [T] type of the [APIResult] to a [R] type of the [APIResult].
 *
 * @param transform A transformer that receives [APIResult] and returns [APIResult].
 *
 * @return A [R] type of the [APIResult].
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any, R : Any> APIResult<T>.map(transform: (APIResult<T>) -> APIResult<R>): APIResult<R> {
	contract { callsInPlace(transform, InvocationKind.AT_MOST_ONCE) }
	return transform(this)
}

/**
 * Maps a [T] type of the [APIResult] to a [V] type of the [APIResult] if the [APIResult] is [APIResult.Success].
 *
 * @param transformer A transformer that receives [T] and returns [V].
 *
 * @return A [V] type of the [APIResult].
 */
@OptIn(ExperimentalContracts::class)
@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any, reified V : Any> APIResult<T>.mapSuccess(
	crossinline transformer: T.() -> V,
): APIResult<V> {
	contract { callsInPlace(transformer, InvocationKind.AT_MOST_ONCE) }
	if (this.isSuccess()) {
		return APIResult.Success(transformer(data))
	}
	return this as APIResult<V>
}