# BaseRepo-S3Uploader-Multipart

Extension module that bridges BaseRepo and S3Uploader-Multipart, enabling pausable, resumable file uploads within the repository pattern.

## Features

- Integration bridge between BaseRepo and S3Uploader-Multipart
- Automatic conversion between `MultipartUploadResult` and `APIResult`
- Repository-pattern extension functions for multipart uploads
- WorkManager scheduling support
- Auto-recovery enablement
- Progress observation via Flow

## Installation

```gradle.kts
// Requires the base modules
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.2.1")
implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart:1.2.1")
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader-Multipart:1.2.1")
```

## Usage

### 1. Initialize S3Uploader

First, initialize the S3Uploader in your Application class:

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        S3Uploader.initS3Uploader(
            tokenProvider = { authManager.getToken() },
            loggingLevel = if (BuildConfig.DEBUG) LoggingLevel.D else LoggingLevel.NONE
        )

        // Optionally enable auto-recovery
        enableMultipartUploadAutoRecovery(this)
    }
}
```

### 2. Start Upload from Repository

```kotlin
class MediaRepository(private val context: Context) : AppolyBaseRepo({ YourRetrofitClient }) {

    private val apiUrls = MultipartApiUrls(
        initiateUrl = "https://api.example.com/s3/multipart/initiate",
        presignPartUrl = "https://api.example.com/s3/multipart/presign-part",
        completeUrl = "https://api.example.com/s3/multipart/complete",
        abortUrl = "https://api.example.com/s3/multipart/abort"
    )

    /**
     * Start a resumable upload for large files
     */
    suspend fun uploadLargeVideo(videoFile: File): APIResult<String> {
        return startMultipartUpload(
            context = context,
            file = videoFile,
            apiUrls = apiUrls
        )
    }

    /**
     * Pause an active upload
     */
    suspend fun pauseUpload(sessionId: String): APIResult<Unit> {
        return pauseMultipartUpload(context, sessionId)
    }

    /**
     * Resume a paused upload
     */
    suspend fun resumeUpload(sessionId: String): APIResult<Unit> {
        return resumeMultipartUpload(context, sessionId)
    }

    /**
     * Cancel and abort an upload
     */
    suspend fun cancelUpload(sessionId: String): APIResult<Unit> {
        return cancelMultipartUpload(context, sessionId)
    }

    /**
     * Observe upload progress
     */
    fun observeProgress(sessionId: String): Flow<MultipartUploadProgress?> {
        return observeMultipartUploadProgress(context, sessionId)
    }
}
```

### 3. Using with ViewModel

```kotlin
class UploadViewModel(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    private var currentSessionId: String? = null

    fun uploadVideo(file: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Starting

            when (val result = mediaRepository.uploadLargeVideo(file)) {
                is APIResult.Success -> {
                    currentSessionId = result.data
                    observeProgress(result.data)
                }
                is APIResult.Error -> {
                    _uploadState.value = UploadState.Error(result.message)
                }
            }
        }
    }

    private fun observeProgress(sessionId: String) {
        viewModelScope.launch {
            mediaRepository.observeProgress(sessionId).collect { progress ->
                progress?.let {
                    _uploadState.value = when (it.status) {
                        UploadSessionStatus.IN_PROGRESS -> UploadState.Uploading(it)
                        UploadSessionStatus.PAUSED -> UploadState.Paused(it)
                        UploadSessionStatus.COMPLETED -> UploadState.Completed(it.sessionId)
                        UploadSessionStatus.FAILED -> UploadState.Error(it.errorMessage ?: "Upload failed")
                        else -> _uploadState.value
                    }
                }
            }
        }
    }

    fun pause() {
        viewModelScope.launch {
            currentSessionId?.let { mediaRepository.pauseUpload(it) }
        }
    }

    fun resume() {
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                when (mediaRepository.resumeUpload(sessionId)) {
                    is APIResult.Success -> observeProgress(sessionId)
                    is APIResult.Error -> { /* Handle error */ }
                }
            }
        }
    }

    fun cancel() {
        viewModelScope.launch {
            currentSessionId?.let { mediaRepository.cancelUpload(it) }
        }
    }

    sealed class UploadState {
        data object Idle : UploadState()
        data object Starting : UploadState()
        data class Uploading(val progress: MultipartUploadProgress) : UploadState()
        data class Paused(val progress: MultipartUploadProgress) : UploadState()
        data class Completed(val sessionId: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }
}
```

### 4. Using WorkManager for Background Uploads

```kotlin
class MediaRepository(private val context: Context) : AppolyBaseRepo({ YourRetrofitClient }) {

    /**
     * Schedule upload to run in background (survives app restarts)
     */
    fun scheduleBackgroundUpload(file: File): String {
        return scheduleMultipartUploadWork(
            context = context,
            file = file,
            apiUrls = apiUrls,
            requiresNetwork = true,
            requiresCharging = false
        )
    }

    /**
     * Enable auto-recovery for interrupted uploads
     */
    fun enableAutoRecovery() {
        enableMultipartUploadAutoRecovery(context)
    }
}
```

### 5. Upload and Associate with API Record

```kotlin
class UserRepository(private val context: Context) : AppolyBaseRepo({ YourRetrofitClient }) {
    private val userService by lazyService<UserAPI>()

    /**
     * Upload a large video and associate it with a post
     */
    suspend fun uploadPostVideo(postId: String, videoFile: File): APIResult<Post> {
        // Start the multipart upload
        val uploadResult = startMultipartUpload(
            context = context,
            file = videoFile,
            apiUrls = apiUrls
        )

        // If upload started successfully, wait for completion and update post
        return when (uploadResult) {
            is APIResult.Success -> {
                val sessionId = uploadResult.data
                // In production, you'd observe progress and wait for completion
                // For simplicity, we'll demonstrate the pattern
                doAPICall("updatePostVideo") {
                    userService.api.updatePostVideo(postId, sessionId)
                }
            }
            is APIResult.Error -> uploadResult
        }
    }
}
```

## Extension Methods

### Upload Control

```kotlin
// Start a new multipart upload
suspend fun GenericBaseRepo.startMultipartUpload(
    context: Context,
    file: File,
    apiUrls: MultipartApiUrls,
    config: MultipartUploadConfig = MultipartUploadConfig.DEFAULT
): APIResult<String>

// Pause an active upload
suspend fun GenericBaseRepo.pauseMultipartUpload(
    context: Context,
    sessionId: String
): APIResult<Unit>

// Resume a paused upload
suspend fun GenericBaseRepo.resumeMultipartUpload(
    context: Context,
    sessionId: String
): APIResult<Unit>

// Cancel and abort an upload
suspend fun GenericBaseRepo.cancelMultipartUpload(
    context: Context,
    sessionId: String
): APIResult<Unit>
```

### Progress Observation

```kotlin
// Observe progress of a specific upload
fun GenericBaseRepo.observeMultipartUploadProgress(
    context: Context,
    sessionId: String
): Flow<MultipartUploadProgress?>

// Observe all active uploads
fun GenericBaseRepo.observeAllMultipartUploads(
    context: Context
): Flow<List<MultipartUploadProgress>>
```

### WorkManager Integration

```kotlin
// Schedule a background upload
fun GenericBaseRepo.scheduleMultipartUploadWork(
    context: Context,
    file: File,
    apiUrls: MultipartApiUrls,
    requiresNetwork: Boolean = true,
    requiresCharging: Boolean = false
): String  // Returns work name for tracking

// Enable auto-recovery for interrupted uploads
fun GenericBaseRepo.enableMultipartUploadAutoRecovery(context: Context)
```

### Result Conversion

```kotlin
// Convert MultipartUploadResult to APIResult
fun MultipartUploadResult.toAPIResult(): APIResult<String>
```

## Error Handling

All extension methods return `APIResult`, enabling standard error handling:

```kotlin
when (val result = mediaRepository.uploadLargeVideo(videoFile)) {
    is APIResult.Success -> {
        val sessionId = result.data
        Log.d("Upload", "Started upload with session: $sessionId")
    }
    is APIResult.Error -> {
        Log.e("Upload", "Upload failed: ${result.message}")
        result.throwable?.let { Log.e("Upload", "Exception", it) }
    }
}
```

## Relationship to Other Modules

- **S3Uploader-Multipart** - Provides the core multipart upload functionality with Room persistence and WorkManager workers
- **BaseRepo** - Provides the repository pattern and `APIResult` type
- **BaseRepo-S3Uploader-Multipart** - Bridges the two, converting result types and providing extension functions

## See Also

- [S3Uploader-Multipart](../S3Uploader-Multipart/README.md) - Core multipart upload functionality
- [S3Uploader](../S3Uploader/README.md) - Simple single-request uploads
- [BaseRepo-S3Uploader](../BaseRepo-S3Uploader/README.md) - Simple upload integration with BaseRepo
- [BaseRepo](../BaseRepo/README.md) - Repository pattern implementation
