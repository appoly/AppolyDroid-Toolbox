package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.exceptions.SandwichNetworkException
import com.skydoves.sandwich.exceptions.SandwichTimeoutException
import com.skydoves.sandwich.message
import com.skydoves.sandwich.retrofit.exceptions.RetrofitExceptionClassifier
import com.skydoves.sandwich.retrofit.statusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import uk.co.appoly.droid.BaseRepoLog
import uk.co.appoly.droid.BaseRepoLogger
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.BaseService
import uk.co.appoly.droid.data.remote.ServiceManager
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.RootJson
import uk.co.appoly.droid.data.remote.model.response.RootJsonWithData
import uk.co.appoly.droid.util.NoConnectivityException
import uk.co.appoly.droid.util.asServerTimeoutException
import uk.co.appoly.droid.util.asServerUnreachableException
import uk.co.appoly.droid.util.firstNotNullOrBlank
import uk.co.appoly.droid.util.ifNullOrBlank
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Abstract base class for repository implementations.
 *
 * This class provides common functionality for API communication, error handling,
 * and response processing. It serves as the foundation for all repository classes
 * in the application.
 *
 * @property getRetrofitClient Lambda that provides a [BaseRetrofitClient] instance for API communication
 * @property logger Logger instance for logging API calls and errors
 */
@OptIn(ExperimentalContracts::class)
abstract class GenericBaseRepo(
	val getRetrofitClient: () -> BaseRetrofitClient,
	logger: FlexiLog = BaseRepoLogger,
	loggingLevel: LoggingLevel = LoggingLevel.V
) {
	init {
		BaseRepoLog.updateLogger(logger, loggingLevel)
	}

	/**
	 * Gets or creates a [ServiceManager] instance for managing API services.
	 *
	 * @return A [ServiceManager] instance configured with this repository's retrofit client and logger
	 */
	fun getServiceManager(): ServiceManager {
		return ServiceManager.getInstance(
			getRetrofitClient = getRetrofitClient,
		)
	}

	companion object {
		/**
		 * Response code used for general exceptions that don't have a specific HTTP status code
		 */
		const val RESPONSE_EXCEPTION_CODE = -1

		/**
		 * Response code used for [ApiResponse.Failure.Error] responses whose payload is not an
		 * HTTP response, so no HTTP status code is available.
		 *
		 * The main source of such errors is Sandwich's `ApiEnvelopeMapper` (registered globally
		 * by default since Sandwich 2.4.0), which demotes an HTTP 200 response whose body
		 * implements `ApiEnvelope` and reports a business failure into an
		 * [ApiResponse.Failure.Error] carrying the envelope's error object as its payload.
		 */
		const val RESPONSE_NON_HTTP_ERROR_CODE = -2
	}

	/**
	 * Helper method to lazily initialize a service
	 *
	 * @param T The API interface type to get a service for
	 * @return A lazy-initialized [BaseService] instance for the requested API type
	 */
	protected inline fun <reified T : BaseService.API> GenericBaseRepo.lazyService(): Lazy<BaseService<T>> =
		lazy { getServiceManager().getService() }

	/**
	 * Executes an API call and processes the response into an [APIResult].
	 *
	 * This method handles successful responses, error responses, and exceptions,
	 * converting them all into the appropriate [APIResult] type.
	 *
	 * @param logDescription Description of the API call for logging purposes
	 * @param call Lambda that performs the actual API call and returns an [ApiResponse]
	 * @return An [APIResult] representing the outcome of the API call
	 */
	protected inline fun <T : Any> doAPICall(
		logDescription: String,
		call: () -> ApiResponse<RootJsonWithData<T>>
	): APIResult<T> {
		contract {
			callsInPlace(call, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
		}
		return when (val response = call()) {
			is ApiResponse.Success -> {
				val result = response.data
				val resultData = result.data
				if (result.success && resultData != null) {
					APIResult.Success(resultData)
				} else {
					handleFailure(
						result = result,
						statusCode = response.statusCode.code,
						logDescription = logDescription
					)
				}
			}

			is ApiResponse.Failure.Error -> {
				handleFailureError(
					response = response,
					logDescription = logDescription
				)
			}

			is ApiResponse.Failure.Exception -> {
				handleFailureException(
					response = response,
					logDescription = logDescription
				)
			}
		}
	}

	/**
	 * Executes an API call that returns a [RootJson] and processes the response into an [APIResult].
	 *
	 * This method is similar to [doAPICall] but handles API calls that return a [RootJson]
	 * instead of a [RootJsonWithData].
	 *
	 * @param logDescription Description of the API call for logging purposes
	 * @param call Lambda that performs the actual API call and returns an [ApiResponse]
	 * @return An [APIResult] representing the outcome of the API call
	 */
	protected inline fun doAPICallWithRootJson(
		logDescription: String,
		call: () -> ApiResponse<RootJson>
	): APIResult<RootJson> {
		contract {
			callsInPlace(call, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
		}
		return when (val response = call()) {
			is ApiResponse.Success -> {
				val result = response.data
				if (result.success) {
					APIResult.Success(result)
				} else {
					handleFailure(
						result = result,
						statusCode = response.statusCode.code,
						logDescription = logDescription
					)
				}
			}

			is ApiResponse.Failure.Error -> {
				handleFailureError(
					response = response,
					logDescription = logDescription
				)
			}

			is ApiResponse.Failure.Exception -> {
				handleFailureException(
					response = response,
					logDescription = logDescription
				)
			}
		}
	}

	/**
	 * Converts an API call into a Flow that emits loading state followed by the API result.
	 *
	 * @param apiCall Suspend function that performs the actual API call
	 * @return A Flow that emits [APIFlowState.Loading] followed by the result of the API call
	 */
	protected inline fun <T : Any> callApiAsFlow(
		crossinline apiCall: suspend () -> APIResult<T>
	): Flow<APIFlowState<T>> = flow {
		emit(APIFlowState.Loading)
		emit(apiCall().asApiFlowState())
	}

	/**
	 * Creates a [RefreshableAPIFlow] that wraps an API call, providing refresh functionality.
	 *
	 * @param initialValue Optional initial value to use before the first API call completes
	 * @param initialRefresh Whether to automatically refresh the data when created (defaults to true if initialValue is null)
	 * @param scope CoroutineScope to use for API calls
	 * @param apiCall Suspend function that performs the actual API call
	 * @return A [RefreshableAPIFlow] that wraps the API call
	 */
	protected fun <T : Any> callApiAsRefreshableFlow(
		initialValue: T? = null,
		initialRefresh: Boolean = initialValue == null,
		scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
		apiCall: suspend () -> APIResult<T>
	): RefreshableAPIFlow<T> {
		return RefreshableAPIFlow(
			initialValue = initialValue,
			initialRefresh = initialRefresh,
			apiCall = apiCall,
			scope = scope
		)
	}

	fun handleFailure(
		result: RootJson,
		statusCode: Int,
		logDescription: String
	): APIResult.Error {
		val message = result.message.ifNullOrBlank { "Unknown error" }
		BaseRepoLog.e(
			this,
			"$logDescription failed! code:$statusCode, message:\"$message\""
		)
		return APIResult.Error(statusCode, message)
	}

	/**
	 * Extracts a meaningful error message from an error API response.
	 *
	 * This method should be implemented by subclasses to parse the error body
	 * and retrieve a user-friendly error message.
	 *
	 * @param response The error API response
	 * @return A string containing the error message, or null if not available
	 */
	abstract fun extractErrorMessage(response: ApiResponse.Failure.Error): String?

	fun handleFailureError(response: ApiResponse.Failure.Error, logDescription: String): APIResult.Error {
		// The payload is only a retrofit2.Response for errors produced by the call adapter.
		// Errors created elsewhere (e.g. Sandwich's ApiEnvelopeMapper demoting an HTTP 200
		// business failure) carry an arbitrary payload, on which Sandwich's statusCode/errorBody
		// accessors throw.
		val statusCode = (response.payload as? Response<*>)?.code() ?: RESPONSE_NON_HTTP_ERROR_CODE
		val message = try {
			firstNotNullOrBlank(
				{ extractErrorMessage(response) },
				{ response.message() },
				fallback = { "Unknown error" }
			)
		} catch (e: Exception) {
			"Unknown error"
		}
		BaseRepoLog.e(
			this,
			"$logDescription failed! code:$statusCode, message:\"$message\""
		)
		return APIResult.Error(statusCode, message)
	}

	fun handleFailureException(response: ApiResponse.Failure.Exception, logDescription: String): APIResult.Error {
		val throwable = response.throwable
		// Already a connectivity exception: either the genuinely-offline NoConnectivityException
		// thrown pre-flight by NetworkConnectionInterceptor (cause == null), or a Server* type we
		// produced upstream. Preserve its own (accurate) message. Must be checked before the
		// classifier, which would report it as a plain network failure (it is an IOException).
		if (throwable is NoConnectivityException) {
			BaseRepoLog.w(
				this,
				"$logDescription failed: ${throwable.message}",
				throwable
			)
			return APIResult.Error(RESPONSE_EXCEPTION_CODE, throwable.message, throwable)
		}
		// Transport-specific sniffing is delegated to Sandwich's classifier, called directly so
		// the consumer's global SandwichInitializer state is left untouched. It encodes OkHttp
		// quirks, e.g. a call-level timeout (OkHttpClient.Builder.callTimeout) surfaces as a
		// plain InterruptedIOException("timeout"), not a SocketTimeoutException.
		return when (RetrofitExceptionClassifier.classify(throwable)) {
			// Device is online but the server didn't respond in time.
			is SandwichTimeoutException -> {
				val exception = throwable.asServerTimeoutException()
				BaseRepoLog.w(
					this,
					"$logDescription failed: ${exception.message}",
					throwable
				)
				APIResult.Error(RESPONSE_EXCEPTION_CODE, exception.message, exception)
			}

			// Device is online but the server couldn't be resolved/reached.
			is SandwichNetworkException -> {
				val exception = throwable.asServerUnreachableException()
				BaseRepoLog.w(
					this,
					"$logDescription failed: ${exception.message}",
					throwable
				)
				APIResult.Error(RESPONSE_EXCEPTION_CODE, exception.message, exception)
			}

			// HTTP, serialization and unrecognised exceptions are not connectivity problems.
			else -> {
				val message = firstNotNullOrBlank(
					{ throwable.message },
					{ response.message() },
					fallback = { "Unknown error" }
				)
				BaseRepoLog.e(
					this,
					"$logDescription failed with exception! message:\"$message\"",
					throwable
				)
				APIResult.Error(RESPONSE_EXCEPTION_CODE, message, throwable)
			}
		}
	}
}
