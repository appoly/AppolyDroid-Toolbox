package uk.co.appoly.droid.util

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import uk.co.appoly.droid.BaseRepoLog
import java.io.IOException

class NetworkConnectionInterceptor(
	private val isInternetAvailable: () -> Boolean,
	private val retryDelayMillis: Long = 0L,
	private val retryCount: Int = 1,
) : Interceptor {

	@Throws(IOException::class)
	override fun intercept(chain: Interceptor.Chain): Response {
		if (!isInternetAvailable()) {
			if (retryDelayMillis > 0 && retryCount > 0) {
				for (attempt in 1..retryCount) {
					BaseRepoLog.v(this, "Interceptor check says: No internet connection, retry $attempt/$retryCount after ${retryDelayMillis}ms...")
					Thread.sleep(retryDelayMillis)
					if (isInternetAvailable()) {
						BaseRepoLog.v(this, "Interceptor retry $attempt says: Connection recovered")
						break
					}
					if (attempt == retryCount) {
						BaseRepoLog.v(this, "Interceptor: No internet connection after $retryCount retries")
						throw NoConnectivityException()
					}
				}
			} else {
				BaseRepoLog.v(this, "Interceptor check says: No internet connection!")
				throw NoConnectivityException()
			}
		}
		BaseRepoLog.v(this, "Interceptor check says: Has internet connection")
		val builder: Request.Builder = chain.request().newBuilder()
		return chain.proceed(builder.build())
	}
}

/**
 * No connectivity exception
 *
 * Thrown by [NetworkConnectionInterceptor] when the device is genuinely offline (the connectivity
 * check fails pre-flight, so [cause] is `null`).
 *
 * Open so that the "online but the server couldn't be reached" case can be represented by the
 * [ServerUnreachableException] subclass while still satisfying `is NoConnectivityException` checks
 * (e.g. [uk.co.appoly.droid.data.remote.model.APIResult.Error.isNetworkError]).
 */
open class NoConnectivityException : IOException {
	constructor() : super()
	constructor(cause: Throwable) : super(cause)

	override val message: String
		get() = "No Internet Connection"
}

/**
 * Server unreachable exception
 *
 * Represents the case where the device *does* have connectivity but the request still failed to
 * reach the server (e.g. DNS resolution failed, connection refused) — typically wrapping an
 * [java.net.UnknownHostException], [java.net.ConnectException] or [java.net.SocketException].
 *
 * Subclasses [NoConnectivityException] deliberately so existing network-error checks keep working,
 * while exposing a more accurate [message] and type for consumers that want to distinguish it.
 */
open class ServerUnreachableException(cause: Throwable) : NoConnectivityException(cause) {
	override val message: String
		get() = "Couldn't reach the server"
}

/**
 * Server timeout exception
 *
 * A more specific [ServerUnreachableException] for the case where the server was reachable but did
 * not respond in time — typically wrapping a [java.net.SocketTimeoutException].
 */
class ServerTimeoutException(cause: Throwable) : ServerUnreachableException(cause) {
	override val message: String
		get() = "Server took too long to respond"
}

fun Throwable.asNoConnectivityException(): NoConnectivityException {
	return this as? NoConnectivityException ?: NoConnectivityException(this)
}

fun Throwable.asServerUnreachableException(): ServerUnreachableException {
	return this as? ServerUnreachableException ?: ServerUnreachableException(this)
}

fun Throwable.asServerTimeoutException(): ServerTimeoutException {
	return this as? ServerTimeoutException ?: ServerTimeoutException(this)
}