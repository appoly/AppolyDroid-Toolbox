# S3Uploader

Standalone module for Amazon S3 file uploading with progress tracking and error handling.

## Features

- Direct file uploads to Amazon S3 buckets
- Progress tracking for uploads
- Pre-signed URL generation support
- Error handling and retry mechanisms
- Standalone implementation that doesn't depend on BaseRepo

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:1.0.34")
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
            tokenProvider = {
                // Provide your API auth token if needed
            },
            loggingLevel =,// Set desired LoggingLevel. e.g: if (isDebug) LoggingLevel.W else LoggingLevel.NONE
            logger = // Your implementation of FlexiLogger
        )
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

```kotlin
object S3Uploader {
    suspend fun uploadFile(
        presignedUrl: String,
        file: File,
        progressMutableFlow: MutableStateFlow<Float>? = null,
        contentType: String? = null,
        chunkSize: Int = DEFAULT_CHUNK_SIZE,
        bufferSize: Int = DEFAULT_BUFFER_SIZE
    ): String

    companion object {
        const val DEFAULT_CHUNK_SIZE = 512 * 1024 // 512KB
        const val DEFAULT_BUFFER_SIZE = 4096 // 4KB
    }
}
```

### Progress Tracking

The `progressMutableFlow` parameter accepts a MutableStateFlow that will be updated with values from 0 to 100, representing the upload progress percentage.

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
- The S3Uploader requires a presigned URL, which should be generated by your backend service.

