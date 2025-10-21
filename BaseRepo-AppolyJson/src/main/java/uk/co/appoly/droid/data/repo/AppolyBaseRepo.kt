package uk.co.appoly.droid.data.repo

import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.errorBody
import uk.co.appoly.droid.BaseAppolyRepoLogger
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.model.response.ErrorBody
import uk.co.appoly.droid.util.parseBody

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
}