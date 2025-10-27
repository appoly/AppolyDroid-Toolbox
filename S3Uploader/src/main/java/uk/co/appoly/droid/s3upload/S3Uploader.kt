package uk.co.appoly.droid.s3upload

import android.webkit.MimeTypeMap
import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LogType
import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.message
import com.skydoves.sandwich.retrofit.errorBody
import com.skydoves.sandwich.retrofit.statusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import uk.co.appoly.droid.s3upload.S3Uploader.initS3Uploader
import uk.co.appoly.droid.s3upload.interfaces.AuthTokenProvider
import uk.co.appoly.droid.s3upload.network.ErrorBody
import uk.co.appoly.droid.s3upload.network.GetPreSignedUrlResponse
import uk.co.appoly.droid.s3upload.network.PreSignedURLData
import uk.co.appoly.droid.s3upload.network.ProgressRequestBody
import uk.co.appoly.droid.s3upload.network.RetrofitClient
import uk.co.appoly.droid.s3upload.utils.S3UploadLog
import uk.co.appoly.droid.s3upload.utils.S3UploadLogger
import uk.co.appoly.droid.s3upload.utils.firstNotNullOrBlank
import uk.co.appoly.droid.s3upload.utils.parseBody
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Main entry point for S3 file upload functionality.
 *
 * This singleton object provides methods to upload files to Amazon S3 storage
 * using pre-signed URLs. It handles authentication, progress tracking, and error
 * management for the entire upload process.
 *
 * Must be initialized with [initS3Uploader] before use.
 */
object S3Uploader {
	private lateinit var tokenProvider: AuthTokenProvider
	internal var loggingLevel: LoggingLevel = LoggingLevel.NONE

	internal fun canLog(type: LogType): Boolean = loggingLevel.canLog(type)

	private fun isInitDone(): Boolean {
		return this::tokenProvider.isInitialized
	}

	/**
	 * Initializes the S3Uploader with required configuration.
	 *
	 * This must be called before using any upload functionality, typically in your Application class.
	 *
	 * @param tokenProvider Provider for authentication tokens required for API calls
	 * @param loggingLevel Controls the verbosity of logging (default is no logging)
	 * @param logger Custom logger implementation
	 */
	fun initS3Uploader(
		tokenProvider: AuthTokenProvider,
		loggingLevel: LoggingLevel = LoggingLevel.NONE,
		logger: FlexiLog = S3UploadLogger
	) {
		this.tokenProvider = tokenProvider
		this.loggingLevel = loggingLevel
		S3UploadLog.updateLogger(logger, loggingLevel)
	}

	/**
	 * Determines the MIME type of a file based on its extension.
	 *
	 * @param file The file whose MIME type should be determined
	 * @return The MIME type as a string, or null if it cannot be determined
	 */
	private fun getMimeType(file: File): String? {
		var mimeType: String? = null
		val extension: String = file.name.split(".").last()
		if (MimeTypeMap.getSingleton().hasExtension(extension)) {
			mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
		}
		return mimeType
	}

	/**
	 * Asynchronously uploads a file to S3 storage.
	 *
	 * This method returns immediately with a [Deferred] that will complete when the upload finishes.
	 *
	 * @param file The file to upload
	 * @param getPresignedUrlAPI API endpoint URL that generates a pre-signed S3 URL
	 * @param dispatcher Coroutine dispatcher to use for the operation (default is IO)
	 * @param progressFlow Optional flow to track upload progress (0.0f to 100.0f)
	 * @return A [Deferred] containing the result of the upload operation
	 */
	suspend fun uploadFileAsync(
		file: File,
		getPresignedUrlAPI: String,
		dispatcher: CoroutineDispatcher = Dispatchers.IO,
		progressFlow: MutableStateFlow<Float>? = null, // Optional progress tracking
	): Deferred<UploadResult>  = withContext(dispatcher) {
		async {
			uploadFile(
				file = file,
				getPresignedUrlAPI = getPresignedUrlAPI,
				progressFlow = progressFlow
			)
		}
	}

	/**
	 * Asynchronously uploads a file to S3 storage with a specific media type.
	 *
	 * This method returns immediately with a [Deferred] that will complete when the upload finishes.
	 *
	 * @param file The file to upload
	 * @param mediaType The media type (MIME type) of the file
	 * @param getPresignedUrlAPI API endpoint URL that generates a pre-signed S3 URL
	 * @param dispatcher Coroutine dispatcher to use for the operation (default is IO)
	 * @param progressFlow Optional flow to track upload progress (0.0f to 100.0f)
	 * @return A [Deferred] containing the result of the upload operation
	 */
	suspend fun uploadFileAsync(
		file: File,
		mediaType: MediaType?,
		getPresignedUrlAPI: String,
		dispatcher: CoroutineDispatcher = Dispatchers.IO,
		progressFlow: MutableStateFlow<Float>? = null, // Optional progress tracking
	): Deferred<UploadResult> = withContext(dispatcher) {
		async {
			uploadFile(
				file = file,
				mediaType = mediaType,
				getPresignedUrlAPI = getPresignedUrlAPI,
				progressFlow = progressFlow
			)
		}
	}

	/**
	 * Uploads a file to S3 storage.
	 *
	 * This method automatically determines the file's media type and uses it for the upload.
	 *
	 * @param file The file to upload
	 * @param getPresignedUrlAPI API endpoint URL that generates a pre-signed S3 URL
	 * @param progressFlow Optional flow to track upload progress (0.0f to 100.0f)
	 * @return Result of the upload operation
	 */
	suspend fun uploadFile(
		file: File,
		getPresignedUrlAPI: String,
		progressFlow: MutableStateFlow<Float>? = null, // Optional progress tracking
	): UploadResult = uploadFile(
			file = file,
			mediaType = getMimeType(file)?.toMediaTypeOrNull(),
			getPresignedUrlAPI = getPresignedUrlAPI,
			progressFlow = progressFlow
		)

	/**
	 * Uploads a file to S3 storage with a specific media type.
	 *
	 * This is the main implementation that handles the entire upload process:
	 * 1. Gets a pre-signed URL from the API
	 * 2. Uploads the file to S3 using the pre-signed URL
	 * 3. Reports progress via the optional flow
	 *
	 * @param file The file to upload
	 * @param mediaType The media type (MIME type) of the file
	 * @param getPresignedUrlAPI API endpoint URL that generates a pre-signed S3 URL
	 * @param progressFlow Optional flow to track upload progress (0.0f to 100.0f)
	 * @return Result of the upload operation as [UploadResult.Success] or [UploadResult.Error]
	 * @throws IllegalStateException If S3Uploader has not been initialized
	 */
	suspend fun uploadFile(
		file: File,
		mediaType: MediaType?,
		getPresignedUrlAPI: String,
		progressFlow: MutableStateFlow<Float>? = null, // Optional progress tracking
	): UploadResult {
		if(!isInitDone()) {
			throw IllegalStateException("S3Uploader is not initialized. Please call S3Uploader.initS3Uploader() before using it.")
		}
		S3UploadLog.v(this, "Getting Pre-Signed URL for file: ${file.name}, from API:\"$getPresignedUrlAPI\"")
		return try {
			val response: ApiResponse<GetPreSignedUrlResponse> = RetrofitClient.apiService.getPreSignedURL(
				authToken = tokenProvider.provideToken(),
				url = getPresignedUrlAPI,
				file.name
			)
			when(response) {
				is ApiResponse.Success -> {
					val body = response.data
					val preSignedUrlData = body.data
					if (preSignedUrlData != null) {
						S3UploadLog.d(this, "Request is successful with response: $body")
						makeUploadRequestSuspend(file, mediaType, preSignedUrlData, progressFlow)
					} else {
						S3UploadLog.e(this, "Error getting pre-signed URL for file upload, PreSignedURLData was Null!")
						UploadResult.Error("Error getting pre-signed URL for file upload, PreSignedURLData was Null!")
					}
				}

				is ApiResponse.Failure.Error -> {
					val message = firstNotNullOrBlank({ response.errorBody.parseBody<ErrorBody>()?.message }, { response.message() }, fallback = "Unknown error")
					S3UploadLog.e(this, "Error getting pre-signed URL for file upload, $message")
					UploadResult.Error("Error generating presignedUrl", IOException("Response code: ${response.statusCode.code}"))
				}

				is ApiResponse.Failure.Exception -> {
					when (response.throwable) {
						is UnknownHostException,
						is ConnectException,
						is SocketException,
						is SocketTimeoutException -> {
							S3UploadLog.w(this, "Error getting pre-signed URL for file upload", response.throwable)
						}
						else -> {
							val message = firstNotNullOrBlank({ response.throwable.message }, { response.message() }, fallback = "Unknown error")
							S3UploadLog.e(this, "Error getting pre-signed URL for file upload, $message", response.throwable)
						}
					}
					UploadResult.Error("Error generating presignedUrl", response.throwable)
				}
			}
		} catch (e: Exception) {
			S3UploadLog.e(this, "Error getting pre-signed URL for file upload", e)
			UploadResult.Error("Error generating presignedUrl", e)
		}
	}

	/**
	 * Performs the actual upload of the file to S3 using a pre-signed URL.
	 *
	 * This is an internal implementation method called after successfully obtaining a pre-signed URL.
	 *
	 * @param file The file to upload
	 * @param mediaType The media type (MIME type) of the file
	 * @param data Pre-signed URL data containing upload URL, headers, and file path
	 * @param progressFlow Optional flow to track upload progress
	 * @return Result of the upload operation
	 */
	private suspend fun makeUploadRequestSuspend(
		file: File,
		mediaType: MediaType?,
		data: PreSignedURLData,
		progressFlow: MutableStateFlow<Float>?
	): UploadResult {
		S3UploadLog.v(this, "Start uploading file:\"${file.name}\", mediaType:\"$mediaType\"")
		return try {
			val requestBody = if (progressFlow != null) {
				ProgressRequestBody(file, mediaType, progressFlow)
			} else {
				file.asRequestBody(mediaType)
			}
			val response = RetrofitClient.apiService.uploadToS3(
				uploadUrl = data.presignedUrl,
				headers = data.headers,
				body = requestBody
			)
			when(response) {
				is ApiResponse.Success -> {
					progressFlow?.value = 100f // Ensure progress hits 100% on success, if provided
					S3UploadLog.d(this, "file Uploading is successful!")
					UploadResult.Success(data.filePath)
				}
				is ApiResponse.Failure.Error -> {
					S3UploadLog.e(this, "Uploading is failed with code: ${response.statusCode.code}, message: ${response.message()}")
					UploadResult.Error("Error uploading file", IOException("Response code: ${response.statusCode.code}"))
				}
				is ApiResponse.Failure.Exception -> {
					S3UploadLog.e(this, "Uploading is failed with exception: ", response.throwable)
					UploadResult.Error("Error uploading file", response.throwable)
				}
			}
		} catch (e: Exception) {
			S3UploadLog.e(this, "Error uploading file", e)
			UploadResult.Error("Error uploading file", e)
		}
	}
}
