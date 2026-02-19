# S3Uploader

Standalone module for Amazon S3 file uploading with progress tracking and error handling.

## Features

- Direct file uploads to Amazon S3 buckets
- Two upload flows:
    - Standard flow with API-generated pre-signed URLs
    - Direct flow with user-provided pre-signed URLs
- Progress tracking for uploads
- Custom header support for direct uploads
- Error handling and retry mechanisms
- Standalone implementation that doesn't depend on BaseRepo

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:1.2.7")
```

## Usage

### Initializing the S3Uploader

Before using the S3Uploader functionality, you need to initialize it in your Application class:

```kotlin
class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()
        initS3Uploader()
    }

    private fun initS3Uploader() {
        S3Uploader.initS3Uploader(
            // Bearer token auth (most common)
            headerProvider = HeaderProvider.bearer { authManager.getToken() },
            loggingLevel =,// Set desired LoggingLevel. e.g: if (isDebug) LoggingLevel.W else LoggingLevel.NONE
            logger = // Your implementation of FlexiLogger
        )
    }
}
```

The `HeaderProvider` controls which HTTP headers are sent with pre-signed URL requests to your backend. Common patterns:

```kotlin
// Bearer token (Authorization: Bearer <token>)
HeaderProvider.bearer { authManager.getToken() }

// Custom header name (e.g. User-Api-Token: <token>)
HeaderProvider.custom("User-Api-Token") { apiKeyStore.getKey() }

// Multiple headers (auth + metadata)
HeaderProvider {
    buildMap {
        val token = getToken()
        if (!token.isNullOrBlank()) {
            put("Authorization", "Bearer $token")
        }
        put("X-App-Version", BuildConfig.VERSION_NAME)
    }
}
```

### Basic File Upload

```kotlin
class FileUploadViewModel : ViewModel() {
    // MutableStateFlow to track upload progress (0-100)
    val uploadProgress = MutableStateFlow(0f)

    suspend fun uploadFile(file: File): Result<String> {
        return try {
            val uploadResult = S3Uploader.uploadFile(
                presignedUrl = "https://your-s3-presigned-url",
                file = file,
                progressMutableFlow = uploadProgress
            )
            Result.success(uploadResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Getting a Pre-signed URL from Your Backend

```kotlin
class FileUploadRepository {
    private val apiService: ApiService // Your API service interface

    suspend fun getPresignedUrl(fileType: String): String {
        val response = apiService.getPresignedUploadUrl(fileType)
        return response.presignedUrl
    }

    suspend fun uploadFile(file: File, progressFlow: MutableStateFlow<Float>? = null): Result<String> {
        return try {
            val presignedUrl = getPresignedUrl(file.extension)
            val uploadResult = S3Uploader.uploadFile(
                presignedUrl = presignedUrl,
                file = file,
                progressMutableFlow = progressFlow
            )
            Result.success(uploadResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Direct Upload (With Pre-signed URL)

If you already have a pre-signed URL from another source and want to bypass the API call to generate one, you can use the direct upload methods:

```kotlin
class FileUploadViewModel : ViewModel() {
	val uploadProgress = MutableStateFlow(0f)

	suspend fun uploadFileDirect(file: File, presignedUrl: String): Result<Unit> {
		return try {
			val mediaType = "image/jpeg".toMediaTypeOrNull()!!

			val uploadResult = S3Uploader.uploadFileDirect(
				file = file,
				presignedUrl = presignedUrl,
				mediaType = mediaType,
				progressFlow = uploadProgress
			)

			when (uploadResult) {
				is DirectUploadResult.Success -> Result.success(Unit)
				is DirectUploadResult.Error -> Result.failure(
					uploadResult.throwable ?: Exception(uploadResult.message)
				)
			}
		} catch (e: Exception) {
			Result.failure(e)
		}
	}
}
```

#### Direct Upload with Custom Headers

```kotlin
suspend fun uploadWithHeaders(file: File, presignedUrl: String): DirectUploadResult {
	val headers = mapOf(
		"x-amz-acl" to "public-read",
		"Content-Type" to "image/jpeg"
	)

	return S3Uploader.uploadFileDirect(
		file = file,
		presignedUrl = presignedUrl,
		mediaType = "image/jpeg".toMediaTypeOrNull()!!,
		headers = headers
	)
}
```

#### Async Direct Upload

```kotlin
suspend fun uploadFileAsync(file: File, presignedUrl: String): DirectUploadResult {
	val deferred = S3Uploader.uploadFileDirectAsync(
		file = file,
		presignedUrl = presignedUrl,
		mediaType = "image/jpeg".toMediaTypeOrNull()!!,
		dispatcher = Dispatchers.IO
	)

	return deferred.await()
}
```

### Complete Upload Example with UI

```kotlin
class UploadViewModel : ViewModel() {
    private val repository: FileUploadRepository

    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState

    fun uploadImage(file: File) {
        _uploadState.value = UploadState.Uploading
        viewModelScope.launch {
            repository.uploadFile(file, _uploadProgress)
                .onSuccess { url ->
                    _uploadState.value = UploadState.Success(url)
                }
                .onFailure { error ->
                    _uploadState.value = UploadState.Error(error.message ?: "Upload failed")
                }
        }
    }

    sealed class UploadState {
        object Idle : UploadState()
        object Uploading : UploadState()
        data class Success(val downloadUrl: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }
}

@Composable
fun UploadScreen(viewModel: UploadViewModel) {
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Image picker or camera button
        Button(onClick = { /* Show image picker */ }) {
            Text("Select Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uploadState) {
            is UploadViewModel.UploadState.Idle -> {
                // Idle state
            }
            is UploadViewModel.UploadState.Uploading -> {
                Text("Uploading... ${uploadProgress.toInt()}%")
                LinearProgressIndicator(
                    progress = { uploadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
            }
            is UploadViewModel.UploadState.Success -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color.Green,
                    modifier = Modifier.size(48.dp)
                )
                Text("Upload Successful!")
                Text("URL: ${state.downloadUrl}", style = MaterialTheme.typography.bodySmall)
            }
            is UploadViewModel.UploadState.Error -> {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = Color.Red,
                    modifier = Modifier.size(48.dp)
                )
                Text("Upload Failed")
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { /* Retry upload */ }) {
                    Text("Retry")
                }
            }
        }
    }
}
```

### Handling Multiple File Types

```kotlin
class MediaUploadRepository {
    suspend fun uploadMedia(file: File, progressFlow: MutableStateFlow<Float>? = null): Result<String> {
        return try {
            val fileExtension = file.extension.lowercase()
            val contentType = when (fileExtension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "mp4" -> "video/mp4"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }

            val presignedUrl = getPresignedUrlForType(fileExtension, contentType)

            val uploadResult = S3Uploader.uploadFile(
                presignedUrl = presignedUrl,
                file = file,
                progressMutableFlow = progressFlow,
                contentType = contentType
            )

            Result.success(uploadResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getPresignedUrlForType(extension: String, contentType: String): String {
        // Call your backend to get a presigned URL
        return apiService.getPresignedUrl(extension, contentType).presignedUrl
    }
}
```

### Custom Upload Configuration

```kotlin
class CustomS3UploaderExample {
    suspend fun uploadLargeFile(file: File, progressFlow: MutableStateFlow<Float>): Result<String> {
        return try {
            val presignedUrl = getPresignedUrl(file.name)

            val uploadResult = S3Uploader.uploadFile(
                presignedUrl = presignedUrl,
                file = file,
                progressMutableFlow = progressFlow,
                contentType = "application/octet-stream",
                chunkSize = 1024 * 1024, // 1MB chunks
                bufferSize = 8192 // 8KB buffer
            )

            Result.success(uploadResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getPresignedUrl(fileName: String): String {
        // Implementation to get presigned URL
        return ""
    }
}
```

## API Reference

### S3Uploader Object

#### Standard Upload (with API-generated Pre-signed URL)

```kotlin
object S3Uploader {
	// Async upload that returns Deferred
	suspend fun uploadFileAsync(
		file: File,
		getPresignedUrlAPI: String,
		dispatcher: CoroutineDispatcher = Dispatchers.IO,
		progressFlow: MutableStateFlow<Float>? = null
	): Deferred<UploadResult>

	// Suspend function for direct coroutine use
    suspend fun uploadFile(
		file: File,
		getPresignedUrlAPI: String,
		progressFlow: MutableStateFlow<Float>? = null
	): UploadResult
}
```

**Returns:** `UploadResult.Success(filePath: String)` or `UploadResult.Error(message: String, throwable: Throwable?)`

#### Direct Upload (with User-provided Pre-signed URL)

```kotlin
object S3Uploader {
	// Async direct upload that returns Deferred
	suspend fun uploadFileDirectAsync(
		file: File,
        presignedUrl: String,
		mediaType: MediaType,
		headers: Map<String, String> = emptyMap(),
		dispatcher: CoroutineDispatcher = Dispatchers.IO,
		progressFlow: MutableStateFlow<Float>? = null
	): Deferred<DirectUploadResult>

	// Suspend function for direct coroutine use
	suspend fun uploadFileDirect(
        file: File,
		presignedUrl: String,
		mediaType: MediaType,
		headers: Map<String, String> = emptyMap(),
		progressFlow: MutableStateFlow<Float>? = null
	): DirectUploadResult
}
```

**Returns:** `DirectUploadResult.Success` or `DirectUploadResult.Error(message: String, throwable: Throwable?)`

### Result Types

```kotlin
// Standard upload result (includes S3 file path)
sealed interface UploadResult {
	data class Success(val filePath: String) : UploadResult
	data class Error(val message: String, val throwable: Throwable? = null) : UploadResult
}

// Direct upload result (no file path returned)
sealed interface DirectUploadResult {
	data object Success : DirectUploadResult
	data class Error(val message: String, val throwable: Throwable? = null) : DirectUploadResult
}
```

## Choosing Between Upload Flows

### Standard Upload (`uploadFile`)

**Use when:**

- Your backend generates pre-signed URLs and provides the S3 file path
- You need to update database records with the S3 file path
- You want the server to control file naming and organization
- You need the S3 file path returned after upload

**API Response Format Required:**

```json
{
  "success": true,
  "data": {
    "file_path": "images/profile/user123.jpg",
    "presigned_url": "https://bucket.s3.amazonaws.com/...",
    "headers": {
      "Host": [
        "bucket.s3.amazonaws.com"
      ],
      "Content-Type": "image/jpeg"
    }
  }
}
```

### Direct Upload (`uploadFileDirect`)

**Use when:**

- You already have a pre-signed URL from another source
- You don't need the S3 file path returned
- You want to bypass the API call to your backend
- You need to provide custom headers for the S3 upload
- You're integrating with a third-party service that provides pre-signed URLs

**Example Use Cases:**

- Uploading to a third-party S3 bucket
- Client-side file path generation
- Integration with external services (e.g., AWS Amplify, AWS SDK)
- Reducing backend API calls

### Progress Tracking

Both upload methods support progress tracking. The `progressFlow` parameter accepts a MutableStateFlow that will be updated with values from 0 to 100, representing the upload progress percentage.

## Error Handling

The S3Uploader throws exceptions for various error conditions:

- `IllegalArgumentException` - If the file doesn't exist or can't be read
- `IOException` - If there's an error during file reading or upload
- `HttpException` - If the S3 server returns an error response
- `Exception` - For other unexpected errors

## Dependencies

- [FlexiLogger](https://github.com/projectdelta6/FlexiLogger) - For logging
- OkHttp for HTTP communication
- [Retrofit](https://square.github.io/retrofit/) - For network requests
- [Sandwich](https://github.com/skydoves/sandwich) - For API response handling
- Kotlin Coroutines

## Notes

- For an S3 uploader that integrates with the BaseRepo module, see the [BaseRepo-S3Uploader](../BaseRepo-S3Uploader/README.md) module.
- **For large files** that need pause/resume/recovery support, see the [S3Uploader-Multipart](../S3Uploader-Multipart/README.md) module which uses AWS S3 Multipart Upload API.
- The S3Uploader requires a presigned URL, which should be generated by your backend service.

