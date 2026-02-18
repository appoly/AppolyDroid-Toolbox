package uk.co.appoly.droid.s3upload.interfaces

/**
 * Functional interface for providing HTTP headers to S3 upload API requests.
 *
 * This interface abstracts header injection, allowing the S3Uploader to include
 * authentication and other headers without being tied to any specific format.
 * The returned map is passed directly via Retrofit's `@HeaderMap` annotation.
 *
 * ## Common patterns
 *
 * **Bearer token (most common):**
 * ```kotlin
 * HeaderProvider.bearer { authRepository.getCurrentToken() }
 * ```
 *
 * **Custom header name:**
 * ```kotlin
 * HeaderProvider.custom("User-Api-Token") { apiKeyStore.getKey() }
 * ```
 *
 * **Multiple headers (auth + metadata):**
 * ```kotlin
 * HeaderProvider {
 *     buildMap {
 *         val token = getToken()
 *         if (!token.isNullOrBlank()) {
 *             put("Authorization", "Bearer $token")
 *         }
 *         put("X-App-Version", BuildConfig.VERSION_NAME)
 *         put("X-Platform", "Android")
 *     }
 * }
 * ```
 */
fun interface HeaderProvider {
	/**
	 * Provides HTTP headers to include in API requests.
	 *
	 * Return an empty map when no headers should be sent (e.g. token is unavailable).
	 * Implementations must be safe to call from any thread.
	 *
	 * @return A map of header name to header value pairs
	 */
	fun provideHeaders(): Map<String, String>

	companion object {
		/**
		 * Creates a [HeaderProvider] that emits a single `Authorization: Bearer <token>` header.
		 *
		 * When the [tokenProvider] returns null or blank, an empty map is returned
		 * so the request proceeds without an Authorization header.
		 *
		 * @param tokenProvider Lambda that returns the current token, or null if unavailable
		 */
		fun bearer(tokenProvider: () -> String?): HeaderProvider = HeaderProvider {
			val token = tokenProvider()
			if (token.isNullOrBlank()) emptyMap()
			else mapOf("Authorization" to "Bearer $token")
		}

		/**
		 * Creates a [HeaderProvider] that emits a single header with a custom name.
		 *
		 * When the [valueProvider] returns null or blank, an empty map is returned
		 * so the request proceeds without the header.
		 *
		 * @param headerName The HTTP header name (e.g. `"User-Api-Token"`)
		 * @param valueProvider Lambda that returns the current header value, or null if unavailable
		 */
		fun custom(headerName: String, valueProvider: () -> String?): HeaderProvider = HeaderProvider {
			val value = valueProvider()
			if (value.isNullOrBlank()) emptyMap()
			else mapOf(headerName to value)
		}
	}
}
