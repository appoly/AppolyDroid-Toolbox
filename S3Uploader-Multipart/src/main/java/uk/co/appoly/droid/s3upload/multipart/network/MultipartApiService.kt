package uk.co.appoly.droid.s3upload.multipart.network

import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import uk.co.appoly.droid.s3upload.multipart.network.model.AbortMultipartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.AbortMultipartResponse
import uk.co.appoly.droid.s3upload.multipart.network.model.CompleteMultipartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.CompleteMultipartResponse
import uk.co.appoly.droid.s3upload.multipart.network.model.CompletedPart
import uk.co.appoly.droid.s3upload.multipart.network.model.InitiateMultipartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.InitiateMultipartResponse
import uk.co.appoly.droid.s3upload.multipart.network.model.PresignPartRequest
import uk.co.appoly.droid.s3upload.multipart.network.model.PresignPartResponse
import uk.co.appoly.droid.s3upload.multipart.network.model.S3PartUploadResult
import java.util.concurrent.TimeUnit

/**
 * Service wrapper for multipart upload API operations.
 *
 * Handles authentication token injection and provides a clean API
 * for the upload manager.
 */
internal class MultipartApiService(
    private val api: MultipartApis,
    private val tokenProvider: () -> String?
) {

    /**
     * Initiates a multipart upload session.
     *
     * @param url API endpoint URL
     * @param fileName Name of the file to upload
     * @param contentType MIME type of the file
     * @param fileSize Size of the file in bytes
     * @return API response containing upload ID and file path
     */
    suspend fun initiateMultipartUpload(
        url: String,
        fileName: String,
        contentType: String,
        fileSize: Long
    ): ApiResponse<InitiateMultipartResponse> {
        val body = InitiateMultipartRequest(
            fileName = fileName,
            contentType = contentType,
            fileSize = fileSize
        )

        return try {
            val token = tokenProvider()
            if (token.isNullOrBlank()) {
                api.initiateMultipartUpload(
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            } else {
                api.initiateMultipartUpload(
                    authorization = "Bearer $token",
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            }
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    /**
     * Gets a pre-signed URL for uploading a specific part.
     *
     * @param url API endpoint URL
     * @param uploadId S3 multipart upload ID
     * @param filePath S3 file path/key
     * @param partNumber Part number (1-based)
     * @return API response containing pre-signed URL
     */
    suspend fun getPresignedUrlForPart(
        url: String,
        uploadId: String,
        filePath: String,
        partNumber: Int
    ): ApiResponse<PresignPartResponse> {
        val body = PresignPartRequest(
            uploadId = uploadId,
            filePath = filePath,
            partNumber = partNumber
        )

        return try {
            val token = tokenProvider()
            if (token.isNullOrBlank()) {
                api.getPresignedUrlForPart(
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            } else {
                api.getPresignedUrlForPart(
                    authorization = "Bearer $token",
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            }
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    /**
     * Completes a multipart upload.
     *
     * @param url API endpoint URL
     * @param uploadId S3 multipart upload ID
     * @param filePath S3 file path/key
     * @param parts List of completed parts with part numbers and ETags
     * @return API response containing final file path
     */
    suspend fun completeMultipartUpload(
        url: String,
        uploadId: String,
        filePath: String,
        parts: List<CompletedPart>
    ): ApiResponse<CompleteMultipartResponse> {
        val body = CompleteMultipartRequest(
            uploadId = uploadId,
            filePath = filePath,
            parts = parts.sortedBy { it.partNumber }
        )

        return try {
            val token = tokenProvider()
            if (token.isNullOrBlank()) {
                api.completeMultipartUpload(
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            } else {
                api.completeMultipartUpload(
                    authorization = "Bearer $token",
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            }
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    /**
     * Aborts a multipart upload.
     *
     * @param url API endpoint URL
     * @param uploadId S3 multipart upload ID
     * @param filePath S3 file path/key
     * @return API response
     */
    suspend fun abortMultipartUpload(
        url: String,
        uploadId: String,
        filePath: String
    ): ApiResponse<AbortMultipartResponse> {
        val body = AbortMultipartRequest(
            uploadId = uploadId,
            filePath = filePath
        )

        return try {
            val token = tokenProvider()
            if (token.isNullOrBlank()) {
                api.abortMultipartUpload(
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            } else {
                api.abortMultipartUpload(
                    authorization = "Bearer $token",
                    accept = ACCEPT_JSON,
                    url = url,
                    body = body
                )
            }
        } catch (e: Exception) {
            ApiResponse.Failure.Exception(e)
        }
    }

    /**
     * Uploads a part directly to S3 using OkHttp.
     *
     * We use OkHttp directly instead of Retrofit because we need access
     * to the ETag response header, which S3 returns after successfully
     * uploading a part.
     *
     * @param presignedUrl Pre-signed URL for uploading
     * @param headers Headers to include (from presign response)
     * @param body Request body containing part data
     * @return Result containing ETag on success
     */
    suspend fun uploadPartToS3(
        presignedUrl: String,
        headers: Map<String, String>,
        body: RequestBody
    ): S3PartUploadResult = withContext(Dispatchers.IO) {
        try {
            val requestBuilder = Request.Builder()
                .url(presignedUrl)
                .put(body)

            // Add headers from presign response
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            val request = requestBuilder.build()
            val response = s3OkHttpClient.newCall(request).execute()

            response.use { resp ->
                if (resp.isSuccessful) {
                    // S3 returns ETag in response headers
                    val etag = resp.header("ETag")
                        ?: resp.header("etag")
                        ?: "\"unknown\""
                    S3PartUploadResult.Success(etag)
                } else {
                    S3PartUploadResult.HttpError(
                        statusCode = resp.code,
                        message = resp.message.ifBlank { "Upload failed with code ${resp.code}" }
                    )
                }
            }
        } catch (e: Exception) {
            S3PartUploadResult.Exception(e)
        }
    }

    companion object {
        private const val ACCEPT_JSON = "application/json"

        /**
         * OkHttpClient configured for S3 part uploads with longer timeouts.
         */
        private val s3OkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS) // 2 minutes for large chunks
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}
