# BaseRepo-S3Uploader

An extension module that bridges BaseRepo and S3Uploader, enabling seamless file uploads to Amazon S3 within the repository pattern.

## Features

- Integration bridge between BaseRepo and S3Uploader
- Automatic conversion between S3Uploader's `UploadResult` and BaseRepo's `APIResult`
- Support for progress tracking via Flow
- Convenience methods for uploading and associating files with API records
- Maintains all error handling capabilities of both systems

## Installation

```gradle.kts
// Requires both the base modules
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.0.33")
implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:1.0.33")
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader:1.0.33")
```

## How it Works

This module acts as a bridge between:

1. **S3Uploader Module** - Handles the direct S3 upload functionality with pre-signed URLs
2. **BaseRepo Module** - Provides repository pattern implementation with standardized API call handling

The bridge enables your repository classes to seamlessly upload files to S3 and receive results in the standard `APIResult` format used throughout your app.

## Required API Format

### Pre-signed URL Generator Endpoint

Your backend must provide an endpoint that generates pre-signed URLs for S3 uploads. This endpoint should return a response in the following format:

```json
{
  "success": true,
  "data": {
    "file_path": "images/profile/user123.jpg",
    "presigned_url": "https://bucket-name.s3.amazonaws.com/images/profile/user123.jpg?X-Amz-Algorithm=...",
    "headers": {
      "Host": ["bucket-name.s3.amazonaws.com"],
      "x-amz-acl": ["public-read"],
      "Content-Type": "image/jpeg"
    }
  }
}
```

## Usage

### 1. Initialize S3Uploader

First, initialize the S3Uploader in your Application class:

```kotlin
class MyApp: Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize S3Uploader with your auth token provider
        S3Uploader.initS3Uploader(
            tokenProvider = {
                // Return your authentication token
                authManager.getToken()
            },
            logger = YourFlexiLogImplementation,
            loggingLevel = if (BuildConfig.DEBUG) LoggingLevel.D else LoggingLevel.NONE
        )
    }
}
```

### 2. Simple File Upload

```kotlin
class MediaRepository : AppolyBaseRepo({ YourRetrofitClient }) {
    /**
     * Upload a file to S3 and return the S3 file path
     */
    suspend fun uploadImage(imageFile: File): APIResult<String> = uploadFileToS3(
        generatePresignedURL = "https://api.example.com/uploads/generate-presigned-url",
        file = imageFile
    )
}
```

### 3. Upload with Progress Tracking

```kotlin
class MediaRepository : AppolyBaseRepo({ YourRetrofitClient }) {
    /**
     * Upload a file to S3 with progress tracking
     */
    suspend fun uploadVideo(
        videoFile: File,
        progressFlow: MutableStateFlow<Float>
    ): APIResult<String> = uploadFileToS3(
        generatePresignedURL = "https://api.example.com/uploads/generate-presigned-url",
        file = videoFile,
        progressFlow = progressFlow
    )
}

// In your ViewModel:
class UploadViewModel(private val mediaRepository: MediaRepository) : ViewModel() {
    private val _uploadProgress = MutableStateFlow(0f)
    val uploadProgress: StateFlow<Float> = _uploadProgress.asStateFlow()

    suspend fun uploadVideo(file: File): APIResult<String> {
        return mediaRepository.uploadVideo(file, _uploadProgress)
    }
}

// In your Composable:
@Composable
fun UploadScreen(viewModel: UploadViewModel) {
    val progress by viewModel.uploadProgress.collectAsState()

    LinearProgressIndicator(
        progress = { progress / 100f },
        modifier = Modifier.fillMaxWidth()
    )

    Text("${progress.toInt()}% Uploaded")
}
```

### 4. Upload and Associate with API Record

This approach combines uploading a file and then sending the resulting S3 path to another API endpoint in one operation:

```kotlin
class UserRepository : AppolyBaseRepo({ YourRetrofitClient }) {
    private val userService by lazyService<UserAPI>()

    /**
     * Upload a profile picture and associate it with a user
     */
    suspend fun updateProfilePicture(userId: String, imageFile: File): APIResult<UserProfile> =
        uploadFileToS3(
            generatePresignedURL = "https://api.example.com/uploads/generate-presigned-url",
            file = imageFile,
            sendPathApiCall = { s3FilePath ->
                // This API call is only made if the S3 upload succeeds
                doAPICall("updateProfilePicture") {
                    userService.api.updateProfilePicture(userId, s3FilePath)
                }
            }
        )
}

interface UserAPI : BaseService.API {
    @POST("/users/{userId}/profile-picture")
    suspend fun updateProfilePicture(
        @Path("userId") userId: String,
        @Body request: UpdateProfilePictureRequest
    ): ApiResponse<GenericResponse<UserProfile>>
}
```

## Error Handling

The extension methods automatically convert between S3Uploader's `UploadResult` and BaseRepo's `APIResult`:

```kotlin
when (val result = userRepository.updateProfilePicture(userId, imageFile)) {
    is APIResult.Success -> {
        // Success case
        val updatedProfile = result.data
        displayProfile(updatedProfile)
    }
    is APIResult.Error -> {
        // Error handling
        showError(result.message)
        if (result.throwable != null) {
            Log.e("Upload", "Upload failed", result.throwable)
        }
    }
}
```

## Extension Methods

### uploadFileToS3

```kotlin
// Simple upload
suspend fun AppolyBaseRepo.uploadFileToS3(
    generatePresignedURL: String,
    file: File,
    progressFlow: MutableStateFlow<Float>? = null
): APIResult<String>

// Upload and send path to API
suspend fun <T : Any> AppolyBaseRepo.uploadFileToS3(
    generatePresignedURL: String,
    file: File,
    sendPathApiCall: (String) -> APIResult<T>
): APIResult<T>
```

## Under the Hood

1. The extension calls S3Uploader to get a pre-signed URL from your backend
2. S3Uploader uploads the file directly to S3 using the pre-signed URL
3. On successful upload, S3Uploader returns the file path
4. The extension converts this to an APIResult
5. Optionally, it calls another API with the file path

## Relationship to S3Uploader and BaseRepo

- **S3Uploader** - Handles the core S3 upload functionality with pre-signed URLs
- **BaseRepo** - Provides the repository pattern and API result handling
- **BaseRepo-S3Uploader** - Bridges the two, converting between result types and providing a unified API

## See Also

- [S3Uploader](../S3Uploader/README.md) - Standalone S3 upload functionality
- [BaseRepo](../BaseRepo/README.md) - Repository pattern implementation

