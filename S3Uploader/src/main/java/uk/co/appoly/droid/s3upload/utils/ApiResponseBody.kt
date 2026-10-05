package uk.co.appoly.droid.s3upload.utils

import com.skydoves.sandwich.ApiResponse

/**
 * Returns the body of a successful response, or `null` when the response had none.
 *
 * When a 2xx response has no body (HTTP 204/205, where Retrofit skips the converter, or a
 * literal JSON `null`), Sandwich substitutes [Unit] for it, so [ApiResponse.Success.data] is
 * not of type [T] at all and reading it as [T] throws [ClassCastException]. [T] is erased inside
 * this generic function, so no cast runs until the caller receives a value that really is a [T].
 */
internal fun <T : Any> ApiResponse.Success<T>.bodyOrNull(): T? {
	val body: Any = data
	return if (body is Unit) null else data
}
