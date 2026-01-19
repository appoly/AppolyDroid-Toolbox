package uk.co.appoly.droid.s3upload.multipart.network

import com.skydoves.sandwich.ApiResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url
import uk.co.appoly.droid.s3upload.multipart.network.model.AbortMultipartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.AbortMultipartResponse
import uk.co.appoly.droid.s3upload.multipart.network.model.CompleteMultipartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.CompleteMultipartResponse
import uk.co.appoly.droid.s3upload.multipart.network.model.InitiateMultipartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.InitiateMultipartResponse
import uk.co.appoly.droid.s3upload.multipart.network.model.PresignPartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.PresignPartResponse

/**
 * Retrofit interface for multipart upload API calls.
 */
internal interface MultipartApis {

	// ==================== With Authorization ====================

	@POST
	suspend fun initiateMultipartUpload(
		@Header("Authorization") authorization: String,
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: InitiateMultipartRequest
	): ApiResponse<InitiateMultipartResponse>

	@POST
	suspend fun getPresignedUrlForPart(
		@Header("Authorization") authorization: String,
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: PresignPartRequest
	): ApiResponse<PresignPartResponse>

	@POST
	suspend fun completeMultipartUpload(
		@Header("Authorization") authorization: String,
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: CompleteMultipartRequest
	): ApiResponse<CompleteMultipartResponse>

	@POST
	suspend fun abortMultipartUpload(
		@Header("Authorization") authorization: String,
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: AbortMultipartRequest
	): ApiResponse<AbortMultipartResponse>

	// ==================== Without Authorization ====================

	@POST
	suspend fun initiateMultipartUpload(
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: InitiateMultipartRequest
	): ApiResponse<InitiateMultipartResponse>

	@POST
	suspend fun getPresignedUrlForPart(
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: PresignPartRequest
	): ApiResponse<PresignPartResponse>

	@POST
	suspend fun completeMultipartUpload(
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: CompleteMultipartRequest
	): ApiResponse<CompleteMultipartResponse>

	@POST
	suspend fun abortMultipartUpload(
		@Header("Accept") accept: String,
		@Url url: String,
		@Body body: AbortMultipartRequest
	): ApiResponse<AbortMultipartResponse>

	// ==================== Direct S3 Upload ====================

	@PUT
	suspend fun uploadPartToS3(
		@Url url: String,
		@HeaderMap headers: Map<String, String>,
		@Body body: RequestBody
	): ApiResponse<Unit>
}
