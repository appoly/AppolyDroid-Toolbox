package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.errorBody
import com.skydoves.sandwich.retrofit.statusCode
import uk.co.appoly.droid.BaseAppolyRepoLogger
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.BaseResponse
import uk.co.appoly.droid.data.remote.model.response.ErrorBody
import uk.co.appoly.droid.data.remote.model.response.RootJsonWithData
import uk.co.appoly.droid.util.parseBody
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
abstract class AppolyBaseRepo(
	getRetrofitClient: () -> BaseRetrofitClient,
	logger: FlexiLog = BaseAppolyRepoLogger,
	loggingLevel: LoggingLevel = LoggingLevel.V
) : GenericBaseRepo(
	getRetrofitClient,
	logger,
	loggingLevel
) {
	override fun extractErrorMessage(response: ApiResponse.Failure.Error): String? {
		return response.errorBody.parseBody<ErrorBody>(getRetrofitClient())?.message
	}

	/**
	 * Executes an API call that returns a [BaseResponse] and processes the response into an [APIResult].
	 *
	 * This method is similar to [doAPICall] but handles API calls that return a [BaseResponse]
	 * instead of a [RootJsonWithData].
	 *
	 * @param logDescription Description of the API call for logging purposes
	 * @param call Lambda that performs the actual API call and returns an [ApiResponse]
	 * @return An [APIResult] representing the outcome of the API call
	 */
	protected inline fun doAPICallWithBaseResponse(
		logDescription: String,
		call: () -> ApiResponse<BaseResponse>
	): APIResult<BaseResponse> {
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
}