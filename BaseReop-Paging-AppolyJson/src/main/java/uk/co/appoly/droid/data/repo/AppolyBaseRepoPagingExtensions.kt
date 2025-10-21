package uk.co.appoly.droid.data.repo

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.statusCode
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.remote.model.response.PageData

/**
 * Extension function for AppolyBaseRepo to handle API calls that return paginated data
 * with a nested structure.
 *
 * This function is similar to [GenericBaseRepo.doAPICall] but specifically processes
 * [GenericNestedPagedResponse] responses and converts them to [PageData] for easier
 * consumption by Paging components.
 *
 * Example usage:
 * ```kotlin
 * suspend fun fetchUserList(page: Int): APIResult<PageData<User>> =
 *     doNestedPagedAPICall("fetchUserList") {
 *         userService.api.getUsers(page = page, perPage = 20)
 *     }
 * ```
 *
 * @param T The type of items in the paginated list
 * @param logDescription Description for logging purposes
 * @param call Lambda that performs the API call and returns an [ApiResponse] with [GenericNestedPagedResponse]
 * @return An [APIResult] wrapping [PageData] with the normalized pagination data
 */
inline fun <T : Any> AppolyBaseRepo.doNestedPagedAPICall(
	logDescription: String,
	call: () -> ApiResponse<GenericNestedPagedResponse<T>>
): APIResult<PageData<T>> {
	return when (val response = call()) {
		is ApiResponse.Success -> {
			val result = response.data
			if (result.success && result.pageData != null) {
				APIResult.Success(result.asPageData())
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