package uk.co.appoly.droid.s3upload.network.api

import com.skydoves.sandwich.ApiResponse
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Url
import uk.co.appoly.droid.s3upload.network.GetPreSignedUrlBody
import uk.co.appoly.droid.s3upload.network.GetPreSignedUrlResponse

internal interface APIs {
	@POST
	suspend fun getPreSignedURL(
		@HeaderMap headers: Map<String, String>,
		@Header("Accepts") accepts: String,
		@Url url: String,
		@Body body: GetPreSignedUrlBody
	): ApiResponse<GetPreSignedUrlResponse>

	@PUT
	suspend fun uploadToS3(
		@Url url: String,
		@HeaderMap headersMap: Map<String, String>,
		@Body file: RequestBody
	): ApiResponse<Unit>
}
