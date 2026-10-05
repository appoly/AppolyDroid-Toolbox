package uk.co.appoly.droid.s3upload.interfaces

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Functional interface for providing HTTP headers to S3 upload API requests.
 *
 * This interface abstracts header injection, allowing the S3Uploader to include
 * authentication and other headers without being tied to any specific format.
 * The returned map is passed directly via Retrofit's `@HeaderMap` annotation.
 *
 * The headers go to the API URLs the caller passes in: the pre-signed URL endpoint, and the
 * multipart initiate/presign/complete/abort endpoints. They are never sent on the S3 upload
 * itself, which only carries the headers returned by the pre-sign response.
 *
 * ## Scope credentials to trusted hosts
 *
 * Those URLs are caller-supplied, and sometimes they come from data (a server or form JSON)
 * rather than code. A provider that ignores the URL sends its credentials to whatever host the
 * URL points at. For anything carrying a credential, use a host-scoped provider:
 *
 * ```kotlin
 * HeaderProvider.bearerForHosts(
 *     allowedHosts = { setOf(BuildConfig.API_HOST) },
 *     tokenProvider = { authRepository.getCurrentToken() },
 * )
 *
 * // Any provider can be scoped the same way:
 * HeaderProvider.custom("User-Api-Token") { apiKeyStore.getKey() }
 *     .restrictedToHosts { setOf(BuildConfig.API_HOST) }
 * ```
 *
 * ## Multiple headers
 *
 * ```kotlin
 * HeaderProvider { // it = the request URL
 *     buildMap {
 *         put("X-App-Version", BuildConfig.VERSION_NAME)
 *         put("X-Platform", "Android")
 *     }
 * }
 * ```
 */
fun interface HeaderProvider {
	/**
	 * Provides the HTTP headers for a request to [url].
	 *
	 * Return an empty map when no headers should be sent (e.g. token is unavailable, or [url]
	 * points at a host that must not receive them).
	 * Implementations must be safe to call from any thread.
	 *
	 * @param url The full URL of the request the headers are for
	 * @return A map of header name to header value pairs
	 */
	fun provideHeaders(url: String): Map<String, String>

	companion object {
		/**
		 * Creates a [HeaderProvider] that emits a single `Authorization: Bearer <token>` header
		 * on every request, whatever its URL.
		 *
		 * When the [tokenProvider] returns null or blank, an empty map is returned
		 * so the request proceeds without an Authorization header.
		 *
		 * @param tokenProvider Lambda that returns the current token, or null if unavailable
		 */
		@Deprecated(
			message = "Sends the token to every URL, including caller- or server-supplied ones. " +
				"Use bearerForHosts(allowedHosts = { setOf(<your API host>) }, tokenProvider) instead.",
		)
		fun bearer(tokenProvider: () -> String?): HeaderProvider = HeaderProvider {
			bearerHeader(tokenProvider())
		}

		/**
		 * Creates a [HeaderProvider] that emits `Authorization: Bearer <token>` only on requests
		 * whose host is in [allowedHosts]. See [restrictedToHosts] for the matching rules.
		 *
		 * When the [tokenProvider] returns null or blank, an empty map is returned
		 * so the request proceeds without an Authorization header.
		 *
		 * @param allowedHosts Lambda returning the hosts the token may be sent to, e.g.
		 * `setOf("api.example.com")`. Evaluated on every request.
		 * @param tokenProvider Lambda that returns the current token, or null if unavailable
		 */
		fun bearerForHosts(
			allowedHosts: () -> Set<String>,
			tokenProvider: () -> String?,
		): HeaderProvider = HeaderProvider { bearerHeader(tokenProvider()) }.restrictedToHosts(allowedHosts)

		/**
		 * Creates a [HeaderProvider] that emits a single header with a custom name.
		 *
		 * When the [valueProvider] returns null or blank, an empty map is returned
		 * so the request proceeds without the header.
		 *
		 * The header goes to every URL. If it carries a credential, scope it with
		 * [restrictedToHosts].
		 *
		 * @param headerName The HTTP header name (e.g. `"User-Api-Token"`)
		 * @param valueProvider Lambda that returns the current header value, or null if unavailable
		 */
		fun custom(headerName: String, valueProvider: () -> String?): HeaderProvider = HeaderProvider {
			val value = valueProvider()
			if (value.isNullOrBlank()) emptyMap()
			else mapOf(headerName to value)
		}

		private fun bearerHeader(token: String?): Map<String, String> =
			if (token.isNullOrBlank()) emptyMap()
			else mapOf("Authorization" to "Bearer $token")
	}
}

/**
 * Returns a [HeaderProvider] that delegates to this one only for requests whose host is in
 * [allowedHosts], and sends no headers anywhere else.
 *
 * - Hosts match exactly and case-insensitively. Subdomains are not included, so list each host.
 * - A URL that doesn't parse as an HTTP(S) URL is treated as not allowed.
 *
 * @param allowedHosts Lambda returning the allowed hosts, e.g. `setOf("api.example.com")`.
 * Evaluated on every request, so it can read runtime configuration.
 */
fun HeaderProvider.restrictedToHosts(allowedHosts: () -> Set<String>): HeaderProvider {
	val delegate = this
	return HeaderProvider { url ->
		val host = url.toHttpUrlOrNull()?.host
		if (host == null || allowedHosts().none { it.equals(host, ignoreCase = true) }) emptyMap()
		else delegate.provideHeaders(url)
	}
}
