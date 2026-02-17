# S3Uploader-Multipart

Advanced S3 upload module with pause, resume, and recovery support using AWS S3 Multipart Upload API.

## Features

- **Pause/Resume Uploads**: Manually pause and resume uploads at any time
- **Automatic Recovery**: Recover interrupted uploads after app crashes or network failures
- **Chunked Uploads**: Files are split into configurable chunks (default 5MB)
- **Concurrent Part Uploads**: Upload multiple parts simultaneously for better performance
- **Progress Tracking**: Real-time progress updates via Kotlin Flow
- **WorkManager Integration**: Background upload support with foreground notifications
- **Room Database Persistence**: Upload state is persisted for recovery
- **Configurable Retry Logic**: Exponential backoff with configurable retry attempts

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart:1.2.6")
```

This module depends on `S3Uploader` and includes it transitively.

## Backend API Specification

Your backend must implement four endpoints that proxy requests to AWS S3's Multipart Upload API. This section provides the complete specification for external developers to implement these endpoints.

### Overview

| Endpoint         | Purpose                                                               |
|------------------|-----------------------------------------------------------------------|
| **Initiate**     | Creates a new multipart upload session with S3                        |
| **Presign Part** | Generates a pre-signed URL for uploading a single part directly to S3 |
| **Complete**     | Finalizes the upload by combining all parts into the final file       |
| **Abort**        | Cancels an in-progress upload and cleans up uploaded parts            |

### Authentication

All endpoints require authentication. The mobile app sends a Bearer token in the `Authorization` header:

```
Authorization: Bearer <token>
```

Your backend should validate this token using your existing authentication system (e.g., Laravel Sanctum, JWT, etc.).

### Response Format

The library supports **two response formats**. Choose whichever fits your API conventions:

**Option A: Unwrapped (Direct Response)**
```json
{
  "upload_id": "abc123",
  "file_path": "uploads/file.jpg"
}
```

**Option B: Wrapped (Envelope Response)**
```json
{
  "success": true,
  "message": "Upload initiated",
  "data": {
    "upload_id": "abc123",
    "file_path": "uploads/file.jpg"
  }
}
```

Both formats are automatically handled by the library.

---

### 1. Initiate Multipart Upload

Creates a new multipart upload session with S3.

**Endpoint:** `POST /api/s3/multipart/initiate` (URL is configurable)

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
```

**Request Body:**

| Field          | Type   | Required | Description                                                             |
|----------------|--------|----------|-------------------------------------------------------------------------|
| `file_name`    | string | ✅ Yes    | Original filename (e.g., `"video.mp4"`)                                 |
| `content_type` | string | ❌ No     | MIME type (e.g., `"video/mp4"`). Defaults to `application/octet-stream` |

**Example Request:**
```json
{
  "file_name": "my-video.mp4",
  "content_type": "video/mp4"
}
```

**Response Body:**

| Field       | Type   | Required | Description                                               |
|-------------|--------|----------|-----------------------------------------------------------|
| `upload_id` | string | ✅ Yes    | The S3 multipart upload ID (from `createMultipartUpload`) |
| `file_path` | string | ✅ Yes    | The S3 object key where the file will be stored           |
| `key`       | string | ❌ No     | Alias for file_path (some S3 SDKs use this)               |
| `bucket`    | string | ❌ No     | The S3 bucket name (informational)                        |

**Example Response (Unwrapped):**
```json
{
  "upload_id": "VXBsb2FkIElEIGZvciBlbHZpbmcncyBteS1tb3ZpZS5tMnRzIHVwbG9hZA",
  "file_path": "uploads/multipart/a1b2c3d4e5-my-video.mp4"
}
```

**Example Response (Wrapped):**
```json
{
  "success": true,
  "message": "Multipart upload initiated",
  "data": {
    "upload_id": "VXBsb2FkIElEIGZvciBlbHZpbmcncyBteS1tb3ZpZS5tMnRzIHVwbG9hZA",
    "file_path": "uploads/multipart/a1b2c3d4e5-my-video.mp4"
  }
}
```

**Backend Implementation Notes:**
- Call AWS S3 `createMultipartUpload` with the bucket, key, and content type
- Generate a unique key (path) for the file - typically including a random prefix to avoid collisions
- Return the `UploadId` from S3's response as `upload_id`

---

### 2. Get Pre-signed URL for Part

Generates a pre-signed URL that allows the mobile app to upload a single part directly to S3.

**Endpoint:** `POST /api/s3/multipart/presign-part` (URL is configurable)

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
```

**Request Body:**

| Field         | Type    | Required | Description                                                                              |
|---------------|---------|----------|------------------------------------------------------------------------------------------|
| `upload_id`   | string  | ✅ Yes    | The upload ID from the initiate response                                                 |
| `file_path`   | string  | ✅ Yes    | The file path/key from the initiate response                                             |
| `part_number` | integer | ✅ Yes    | Part number (1 to 10,000). Parts are uploaded in parallel, not necessarily in order.     |

**Example Request:**
```json
{
  "upload_id": "VXBsb2FkIElEIGZvciBlbHZpbmcncyBteS1tb3ZpZS5tMnRzIHVwbG9hZA",
  "file_path": "uploads/multipart/a1b2c3d4e5-my-video.mp4",
  "part_number": 1
}
```

**Response Body:**

| Field           | Type    | Required | Description                                                                                         |
|-----------------|---------|----------|-----------------------------------------------------------------------------------------------------|
| `presigned_url` | string  | ✅ Yes    | The pre-signed URL for uploading this part via HTTP PUT                                             |
| `part_number`   | integer | ✅ Yes    | Echo of the requested part number                                                                   |
| `headers`       | object  | ❌ No     | Additional headers the client should include when uploading to S3. Can be empty `{}` or omitted.    |

**Example Response (Unwrapped):**
```json
{
  "presigned_url": "https://my-bucket.s3.amazonaws.com/uploads/multipart/a1b2c3d4e5-my-video.mp4?uploadId=VXBsb2Fk...&partNumber=1&X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...",
  "part_number": 1,
  "headers": {}
}
```

**Example Response (Wrapped):**
```json
{
  "success": true,
  "message": "Pre-signed URL generated",
  "data": {
    "presigned_url": "https://my-bucket.s3.amazonaws.com/uploads/multipart/...",
    "part_number": 1,
    "headers": {}
  }
}
```

**Backend Implementation Notes:**
- Call AWS S3 `createPresignedRequest` for the `uploadPart` command
- Set an appropriate expiry time (recommended: 60 minutes)
- The `headers` field can be used if your S3 configuration requires additional headers (e.g., custom encryption headers)

**Important - ETag Handling:**
When the mobile app uploads a part to the pre-signed URL, S3 returns an `ETag` header in its response. The app captures this automatically and will send it back in the Complete request. Your backend does not need to track ETags.

---

### 3. Complete Multipart Upload

Finalizes the upload by instructing S3 to combine all uploaded parts into the final file.

**Endpoint:** `POST /api/s3/multipart/complete` (URL is configurable)

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
```

**Request Body:**

| Field       | Type   | Required | Description                                       |
|-------------|--------|----------|---------------------------------------------------|
| `upload_id` | string | ✅ Yes    | The upload ID from the initiate response          |
| `file_path` | string | ✅ Yes    | The file path/key from the initiate response      |
| `parts`     | array  | ✅ Yes    | Array of completed parts with their ETags         |

**Parts Array Item:**

| Field         | Type    | Required | Description                                         |
|---------------|---------|----------|-----------------------------------------------------|
| `part_number` | integer | ✅ Yes    | The part number (1-indexed)                         |
| `etag`        | string  | ✅ Yes    | The ETag returned by S3 when the part was uploaded  |

**Example Request:**
```json
{
  "upload_id": "VXBsb2FkIElEIGZvciBlbHZpbmcncyBteS1tb3ZpZS5tMnRzIHVwbG9hZA",
  "file_path": "uploads/multipart/a1b2c3d4e5-my-video.mp4",
  "parts": [
    { "part_number": 1, "etag": "\"a54357aff0632cce46d942af68356b38\"" },
    { "part_number": 2, "etag": "\"0c78aef83f66abc1fa1e8477f296d394\"" },
    { "part_number": 3, "etag": "\"acbd18db4cc2f85cedef654fccc4a4d8\"" }
  ]
}
```

**Response Body:**

| Field       | Type   | Required | Description                           |
|-------------|--------|----------|---------------------------------------|
| `file_path` | string | ✅ Yes    | The final S3 object key               |
| `location`  | string | ❌ No     | The full URL to the completed file    |
| `etag`      | string | ❌ No     | The ETag of the completed file        |

**Example Response (Unwrapped):**
```json
{
  "file_path": "uploads/multipart/a1b2c3d4e5-my-video.mp4",
  "location": "https://my-bucket.s3.amazonaws.com/uploads/multipart/a1b2c3d4e5-my-video.mp4",
  "etag": "\"17fbc0a106abbb6f381aac6e331f2a19-3\""
}
```

**Example Response (Wrapped):**
```json
{
  "success": true,
  "message": "Multipart upload completed",
  "data": {
    "file_path": "uploads/multipart/a1b2c3d4e5-my-video.mp4",
    "location": "https://my-bucket.s3.amazonaws.com/uploads/multipart/a1b2c3d4e5-my-video.mp4"
  }
}
```

**Backend Implementation Notes:**
- Call AWS S3 `completeMultipartUpload` with the parts array
- **Important:** Sort parts by `part_number` before sending to S3 (the library sends them in order, but sorting server-side is safer)
- Format each part as `{ PartNumber: int, ETag: string }` for the AWS SDK

---

### 4. Abort Multipart Upload

Cancels an in-progress upload and instructs S3 to delete any uploaded parts.

**Endpoint:** `POST /api/s3/multipart/abort` (URL is configurable)

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
```

**Request Body:**

| Field       | Type   | Required | Description                                       |
|-------------|--------|----------|---------------------------------------------------|
| `upload_id` | string | ✅ Yes    | The upload ID from the initiate response          |
| `file_path` | string | ✅ Yes    | The file path/key from the initiate response      |

**Example Request:**
```json
{
  "upload_id": "VXBsb2FkIElEIGZvciBlbHZpbmcncyBteS1tb3ZpZS5tMnRzIHVwbG9hZA",
  "file_path": "uploads/multipart/a1b2c3d4e5-my-video.mp4"
}
```

**Response Body:**

| Field     | Type    | Required | Description                           |
|-----------|---------|----------|---------------------------------------|
| `success` | boolean | ❌ No     | Whether the abort was successful      |
| `message` | string  | ❌ No     | Status message                        |

**Example Response:**
```json
{
  "success": true,
  "message": "Multipart upload aborted"
}
```

**Backend Implementation Notes:**
- Call AWS S3 `abortMultipartUpload`
- This cleans up any parts already uploaded to S3
- It's safe to call even if no parts have been uploaded yet

---

### Error Responses

When an error occurs, return an appropriate HTTP status code with an error response:

**Example Error Response:**
```json
{
  "success": false,
  "message": "Upload ID not found or expired"
}
```

**Recommended HTTP Status Codes:**

| Code  | When to Use                                   |
|-------|-----------------------------------------------|
| `200` | Success                                       |
| `400` | Bad request (missing/invalid parameters)      |
| `401` | Unauthorized (invalid or missing token)       |
| `404` | Upload ID not found or expired                |
| `500` | Server error (S3 communication failed, etc.)  |

---

### AWS S3 Configuration

Your S3 bucket needs appropriate CORS configuration to allow direct uploads from mobile apps:

```json
{
  "CORSRules": [
    {
      "AllowedHeaders": ["*"],
      "AllowedMethods": ["PUT"],
      "AllowedOrigins": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3600
    }
  ]
}
```

**Important:** The `ExposeHeaders: ["ETag"]` is critical - without it, the mobile app cannot read the ETag header from S3's response, and the complete request will fail.

---

### Upload Flow Diagram

```
┌─────────────────┐                    ┌─────────────────┐                    ┌─────────────────┐
│   Mobile App    │                    │  Your Backend   │                    │    AWS S3       │
└────────┬────────┘                    └────────┬────────┘                    └────────┬────────┘
         │                                      │                                      │
         │  1. POST /initiate                   │                                      │
         │  {file_name, content_type}           │                                      │
         │─────────────────────────────────────>│                                      │
         │                                      │  createMultipartUpload               │
         │                                      │─────────────────────────────────────>│
         │                                      │<─────────────────────────────────────│
         │                                      │  {UploadId}                          │
         │<─────────────────────────────────────│                                      │
         │  {upload_id, file_path}              │                                      │
         │                                      │                                      │
         │  2. POST /presign-part               │                                      │
         │  {upload_id, file_path, part_number} │                                      │
         │─────────────────────────────────────>│                                      │
         │                                      │  createPresignedRequest              │
         │                                      │─────────────────────────────────────>│
         │                                      │<─────────────────────────────────────│
         │<─────────────────────────────────────│                                      │
         │  {presigned_url}                     │                                      │
         │                                      │                                      │
         │  3. PUT presigned_url (binary data)  │                                      │
         │─────────────────────────────────────────────────────────────────────────────>│
         │<─────────────────────────────────────────────────────────────────────────────│
         │  Response with ETag header           │                                      │
         │                                      │                                      │
         │     ... repeat 2-3 for each part ... │                                      │
         │                                      │                                      │
         │  4. POST /complete                   │                                      │
         │  {upload_id, file_path, parts[]}     │                                      │
         │─────────────────────────────────────>│                                      │
         │                                      │  completeMultipartUpload             │
         │                                      │─────────────────────────────────────>│
         │                                      │<─────────────────────────────────────│
         │<─────────────────────────────────────│                                      │
         │  {file_path, location}               │                                      │
         │                                      │                                      │
```

---

### Quick Reference: Field Names

The library uses `snake_case` for all JSON field names:

| Field           | Used In                                   |
|-----------------|-------------------------------------------|
| `file_name`     | Initiate request                          |
| `content_type`  | Initiate request                          |
| `upload_id`     | All endpoints                             |
| `file_path`     | All endpoints                             |
| `part_number`   | Presign request, Complete request parts   |
| `presigned_url` | Presign response                          |
| `headers`       | Presign response                          |
| `parts`         | Complete request                          |
| `etag`          | Complete request parts, Complete response |
| `location`      | Complete response                         |
| `success`       | All responses (optional)                  |
| `message`       | All responses (optional)                  |
| `data`          | Wrapped responses (optional)              |

---

See the [Backend Implementation Guide](#backend-implementation) section for Laravel PHP sample code.

## Usage

### 1. Initialize S3Uploader

First, initialize the S3Uploader in your Application class (same as standard S3Uploader):

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        S3Uploader.initS3Uploader(
            tokenProvider = { authManager.getToken() },
            loggingLevel = if (BuildConfig.DEBUG) LoggingLevel.D else LoggingLevel.NONE
        )
    }
}
```

### 2. Basic Upload with MultipartUploadManager

```kotlin
class UploadViewModel(application: Application) : AndroidViewModel(application) {
    private val uploadManager = MultipartUploadManager.getInstance(application)

    fun startUpload(file: File) {
        viewModelScope.launch {
            val apiUrls = MultipartApiUrls(
                initiateUrl = "https://api.example.com/s3/multipart/initiate",
                presignPartUrl = "https://api.example.com/s3/multipart/presign-part",
                completeUrl = "https://api.example.com/s3/multipart/complete",
                abortUrl = "https://api.example.com/s3/multipart/abort"
            )

            val result = uploadManager.startUpload(file, apiUrls)

            result.onSuccess { sessionId ->
                // Upload started, observe progress
                observeProgress(sessionId)
            }.onFailure { error ->
                // Handle error
                Log.e("Upload", "Failed to start upload", error)
            }
        }
    }

    private fun observeProgress(sessionId: String) {
        viewModelScope.launch {
            uploadManager.observeProgress(sessionId).collect { progress ->
                progress?.let {
                    Log.d("Upload", "Progress: ${it.overallProgress}%")
                    Log.d("Upload", "Parts: ${it.uploadedParts}/${it.totalParts}")
                }
            }
        }
    }
}
```

### 3. Pause and Resume

```kotlin
class UploadViewModel(application: Application) : AndroidViewModel(application) {
    private val uploadManager = MultipartUploadManager.getInstance(application)
    private var currentSessionId: String? = null

    fun pauseUpload() {
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                uploadManager.pauseUpload(sessionId)
                    .onSuccess { Log.d("Upload", "Upload paused") }
                    .onFailure { Log.e("Upload", "Failed to pause", it) }
            }
        }
    }

    fun resumeUpload() {
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                uploadManager.resumeUpload(sessionId)
                    .onSuccess { Log.d("Upload", "Upload resumed") }
                    .onFailure { Log.e("Upload", "Failed to resume", it) }
            }
        }
    }

    fun cancelUpload() {
        viewModelScope.launch {
            currentSessionId?.let { sessionId ->
                uploadManager.cancelUpload(sessionId)
                    .onSuccess { Log.d("Upload", "Upload cancelled") }
                    .onFailure { Log.e("Upload", "Failed to cancel", it) }
            }
        }
    }
}
```

### 4. Using WorkManager for Background Uploads

For uploads that should continue when the app is in the background:

```kotlin
class UploadService(private val context: Context) {

    fun scheduleUpload(file: File): String {
        val apiUrls = MultipartApiUrls(
            initiateUrl = "https://api.example.com/s3/multipart/initiate",
            presignPartUrl = "https://api.example.com/s3/multipart/presign-part",
            completeUrl = "https://api.example.com/s3/multipart/complete",
            abortUrl = "https://api.example.com/s3/multipart/abort"
        )

        // Returns the work name for tracking
        return S3UploadWorkManager.scheduleUpload(
            context = context,
            file = file,
            apiUrls = apiUrls,
            requiresNetwork = true,
            requiresCharging = false
        )
    }

    fun pauseUpload(sessionId: String) {
        S3UploadWorkManager.cancelBySessionId(context, sessionId)
    }

    fun resumeUpload(sessionId: String): String {
        return S3UploadWorkManager.scheduleResume(context, sessionId)
    }

    fun observeUpload(workName: String): LiveData<List<WorkInfo>> {
        return S3UploadWorkManager.observeWorkInfo(context, workName)
    }

    fun getActiveUploadCount(): LiveData<Int> {
        return S3UploadWorkManager.getActiveUploadCount(context)
    }
}
```

### 5. Enable Automatic Recovery

To automatically recover interrupted uploads (e.g., after app restart):

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // ... S3Uploader initialization ...

        // Enable automatic recovery (checks every 15 minutes)
        S3UploadWorkManager.enableAutoRecovery(this)
    }
}
```

Or run recovery manually:

```kotlin
// Run recovery check immediately
val workId = S3UploadWorkManager.runRecoveryNow(context)

// Or use the manager directly
viewModelScope.launch {
    val recoveredIds = uploadManager.recoverInterruptedUploads()
    Log.d("Recovery", "Recovered ${recoveredIds.size} uploads")
}
```

### 6. Custom Configuration

```kotlin
val config = MultipartUploadConfig(
    chunkSize = 10 * 1024 * 1024L, // 10MB chunks (minimum 5MB for S3)
    maxConcurrentParts = 4,        // Upload 4 parts simultaneously
    maxRetries = 5,                // Retry failed parts up to 5 times
    retryDelayMs = 2000L           // Initial retry delay
)

val uploadManager = MultipartUploadManager.getInstance(context, config)
```

### 7. Worker Extensibility

The upload worker can be customized in two ways: **Provider Interfaces** (recommended) or **Custom Worker Subclass** (advanced).

#### Option A: Provider Interfaces (Recommended)

For most use cases, use the provider interfaces via `MultipartUploadConfig`:

```kotlin
val config = MultipartUploadConfig(
    // Custom notification appearance
    notificationProvider = DefaultUploadNotificationProvider(
        channelId = "my_app_uploads",
        channelName = "File Uploads",
        smallIconResId = R.drawable.ic_upload,
        titleProvider = { progress ->
            progress?.fileName?.let { "Uploading: $it" } ?: "Preparing upload..."
        },
        contentTextProvider = { progress ->
            progress?.let { "${it.overallProgress.toInt()}% complete" } ?: "Starting..."
        }
    ),
    // Lifecycle hooks for pre/post upload logic
    lifecycleCallbacks = object : UploadLifecycleCallbacks {
        override suspend fun onBeforeUpload(
            filePath: String
        ): BeforeUploadResult {
            // Validate or register upload with your backend before S3 interaction
            return try {
                backendApi.validateUpload(filePath)
                BeforeUploadResult.Continue
            } catch (e: Exception) {
                BeforeUploadResult.Abort("Validation failed: ${e.message}")
            }
        }

        override suspend fun onUploadComplete(
            sessionId: String,
            result: MultipartUploadResult
        ) {
            when (result) {
                is MultipartUploadResult.Success -> {
                    // Confirm with backend, cleanup temp files
                    backendApi.confirmUpload(sessionId, result.location)
                    File(result.filePath).delete()
                }
                is MultipartUploadResult.Error -> {
                    analytics.trackUploadError(result.message)
                }
                else -> {}
            }
        }

        override suspend fun onUploadPaused(
            sessionId: String,
            reason: String,
            isConstraintViolation: Boolean
        ) {
            // Log pause events, show user notification, etc.
        }

        override suspend fun onUploadResumed(sessionId: String) {
            // Track resume events
        }
    }
)

val uploadManager = MultipartUploadManager.getInstance(context, config)
```

**Available Lifecycle Callbacks:**

| Callback | When Called | Use Cases |
|----------|-------------|-----------|
| `onBeforeUpload` | Before any S3 interaction | Pre-upload validation, abort if needed |
| `onUploadComplete` | After upload finishes (success/error/cancel) | Backend confirmation, file cleanup, analytics |
| `onUploadPaused` | When upload pauses (user or constraint) | User notification, analytics |
| `onUploadResumed` | When upload resumes | UI updates, analytics |
| `onProgressUpdate` | After each part completes | Custom progress tracking (called frequently) |

#### Option B: Custom Worker Subclass (Advanced)

For full control over the worker, subclass `MultipartUploadWorker` and register via `WorkerFactory`.

**Step 1: Create your custom worker**

Subclasses can override both notification methods and lifecycle hooks directly:

```kotlin
class MyUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : MultipartUploadWorker(appContext, params) {

    // Override notification methods
    override fun createForegroundInfo(
        sessionId: String,
        progress: MultipartUploadProgress?
    ): ForegroundInfo {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(context, "my_upload_channel")
            .setContentTitle("My App - Uploading")
            .setContentText(progress?.let { "${it.overallProgress.toInt()}%" } ?: "Starting...")
            .setSmallIcon(R.drawable.ic_my_upload)
            .setProgress(100, progress?.overallProgress?.toInt() ?: 0, progress == null)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                sessionId.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(sessionId.hashCode(), notification)
        }
    }

    override fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "my_upload_channel",
                "My App Uploads",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    // Override lifecycle hooks directly (alternative to UploadLifecycleCallbacks)
    override suspend fun onBeforeUpload(
        filePath: String
    ): BeforeUploadResult {
        // Validate or register upload with backend before S3 interaction
        return try {
            backendApi.validateUpload(filePath)
            BeforeUploadResult.Continue
        } catch (e: Exception) {
            BeforeUploadResult.Abort("Validation failed: ${e.message}")
        }
    }

    override suspend fun onUploadComplete(
        sessionId: String,
        result: MultipartUploadResult
    ) {
        when (result) {
            is MultipartUploadResult.Success -> {
                // Confirm with backend, cleanup temp files
                backendApi.confirmUpload(sessionId, result.location)
                File(result.filePath).delete()
            }
            is MultipartUploadResult.Error -> {
                analytics.trackUploadError(result.message)
            }
            else -> {}
        }
    }

    override suspend fun onUploadPaused(
        sessionId: String,
        reason: String,
        isConstraintViolation: Boolean
    ) {
        // Log pause events
        analytics.trackUploadPaused(sessionId, reason, isConstraintViolation)
    }
}
```

**Available lifecycle hooks in custom workers:**

| Method | When Called | Can Abort |
|--------|-------------|-----------|
| `onBeforeUpload()` | Before upload starts | Yes |
| `onUploadResumed()` | When paused upload resumes | No |
| `onUploadComplete()` | After upload finishes | No |
| `onUploadPaused()` | When upload pauses | No |
| `onProgressUpdate()` | Periodically with progress | No |

> **Note:** The `doWork()` method is `final` to ensure core upload logic isn't accidentally broken.
> Use the lifecycle hooks above instead of overriding `doWork()`.

**Step 2: Create a WorkerFactory**

```kotlin
class MyWorkerFactory : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            // Intercept requests for MultipartUploadWorker
            MultipartUploadWorker::class.java.name -> {
                MyUploadWorker(appContext, workerParameters)
            }
            // Let default factory handle other workers
            else -> null
        }
    }
}
```

**Step 3: Register the factory with WorkManager**

Disable default WorkManager initialization in `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

Initialize WorkManager manually in your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager with custom factory
        val config = Configuration.Builder()
            .setWorkerFactory(MyWorkerFactory())
            .build()
        WorkManager.initialize(this, config)

        // Initialize S3Uploader as usual
        S3Uploader.initS3Uploader(
            tokenProvider = { authManager.getToken() }
        )
    }
}
```

**Using with S3UploadWorkManager:**

Once your factory is registered, `S3UploadWorkManager` works normally. WorkManager intercepts requests for `MultipartUploadWorker` and your factory returns your custom worker instead:

```kotlin
// This will use MyUploadWorker via your factory
S3UploadWorkManager.scheduleUpload(context, file, apiUrls)
```

### 8. Progress Tracking with Compose

```kotlin
@Composable
fun UploadProgressScreen(viewModel: UploadViewModel) {
    val progress by viewModel.uploadProgress.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        progress?.let { p ->
            Text("Uploading: ${p.fileName}")

            LinearProgressIndicator(
                progress = { p.overallProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            Text("${p.overallProgress.toInt()}%")
            Text("Parts: ${p.uploadedParts}/${p.totalParts}")
            Text("${p.uploadedBytes / 1024 / 1024}MB / ${p.totalBytes / 1024 / 1024}MB")

            when (p.status) {
                UploadSessionStatus.PAUSED -> {
                    Button(onClick = { viewModel.resumeUpload() }) {
                        Text("Resume")
                    }
                }
                UploadSessionStatus.IN_PROGRESS -> {
                    Button(onClick = { viewModel.pauseUpload() }) {
                        Text("Pause")
                    }
                }
                UploadSessionStatus.FAILED -> {
                    Text("Error: ${p.errorMessage}", color = Color.Red)
                    Button(onClick = { viewModel.resumeUpload() }) {
                        Text("Retry")
                    }
                }
                UploadSessionStatus.COMPLETED -> {
                    Text("Upload Complete!", color = Color.Green)
                }
                else -> {}
            }
        }
    }
}
```

## API Reference

### MultipartUploadManager

```kotlin
class MultipartUploadManager {
    // Start a new upload
    suspend fun startUpload(file: File, apiUrls: MultipartApiUrls): Result<String>

    // Control operations
    suspend fun pauseUpload(sessionId: String): Result<Unit>
    suspend fun resumeUpload(sessionId: String): Result<Unit>
    suspend fun cancelUpload(sessionId: String): Result<Unit>

    // Progress observation
    fun observeProgress(sessionId: String): Flow<MultipartUploadProgress?>
    fun observeAllUploads(): Flow<List<MultipartUploadProgress>>

    // Recovery
    suspend fun recoverInterruptedUploads(): List<String>
    suspend fun cleanupOldSessions(olderThanMs: Long = 7 days): Int
}
```

### S3UploadWorkManager

```kotlin
object S3UploadWorkManager {
    // Schedule uploads
    fun scheduleUpload(context: Context, file: File, apiUrls: MultipartApiUrls, ...): String
    fun scheduleResume(context: Context, sessionId: String): String

    // Cancel operations
    fun cancelByWorkName(context: Context, workName: String)
    fun cancelBySessionId(context: Context, sessionId: String)
    fun cancelAllUploads(context: Context)

    // Observation
    fun observeWorkInfo(context: Context, workName: String): LiveData<List<WorkInfo>>
    fun observeAllUploads(context: Context): LiveData<List<WorkInfo>>
    fun getActiveUploadCount(context: Context): LiveData<Int>

    // Recovery
    fun enableAutoRecovery(context: Context)
    fun disableAutoRecovery(context: Context)
    fun runRecoveryNow(context: Context): UUID
}
```

### MultipartUploadProgress

```kotlin
data class MultipartUploadProgress(
    val sessionId: String,
    val fileName: String,
    val totalBytes: Long,
    val uploadedBytes: Long,
    val totalParts: Int,
    val uploadedParts: Int,
    val currentPartNumber: Int?,
    val currentPartProgress: Float,
    val overallProgress: Float,     // 0-100
    val status: UploadSessionStatus,
    val errorMessage: String?
)
```

### MultipartUploadConfig

```kotlin
data class MultipartUploadConfig(
    val chunkSize: Long = 5 * 1024 * 1024L,  // 5MB (AWS minimum)
    val maxConcurrentParts: Int = 3,
    val maxRetries: Int = 3,
    val retryDelayMs: Long = 1000L,
    val useExponentialBackoff: Boolean = true,
    val defaultConstraints: UploadConstraints = UploadConstraints.DEFAULT,
    val notificationProvider: UploadNotificationProvider? = null,
    val lifecycleCallbacks: UploadLifecycleCallbacks? = null
)
```

### UploadNotificationProvider

```kotlin
interface UploadNotificationProvider {
    fun createNotificationChannel(context: Context)
    fun createNotification(context: Context, sessionId: String, progress: MultipartUploadProgress?): Notification
    fun getNotificationId(sessionId: String): Int
    fun getForegroundServiceType(): Int
    fun createForegroundInfo(context: Context, sessionId: String, progress: MultipartUploadProgress?): ForegroundInfo
}
```

### UploadLifecycleCallbacks

```kotlin
interface UploadLifecycleCallbacks {
    suspend fun onBeforeUpload(filePath: String): BeforeUploadResult
    suspend fun onUploadComplete(sessionId: String, result: MultipartUploadResult)
    suspend fun onUploadPaused(sessionId: String, reason: String, isConstraintViolation: Boolean)
    suspend fun onUploadResumed(sessionId: String)
    suspend fun onProgressUpdate(sessionId: String, progress: MultipartUploadProgress)
}

sealed class BeforeUploadResult {
    data object Continue : BeforeUploadResult()
    data class Abort(val reason: String) : BeforeUploadResult()
}
```

## Upload States

```
PENDING ──start──> IN_PROGRESS ──all parts done──> COMPLETING ──> COMPLETED
    │                  │ │                              │
    │                  │ └──pause──> PAUSED ────────────┤
    │                  │               │                │
    │                  │               └──resume────────┘
    │                  │                                │
    └──cancel──> ABORTED <──cancel───────────┴─────────┘
                    ▲
                    │
                 FAILED <──max retries exceeded──
```

## Backend Implementation

### Laravel PHP Sample Code

#### MultipartUploadService.php

```php
<?php

namespace App\Services\Storage;

use Aws\S3\S3Client;
use Exception;
use Illuminate\Http\Request;
use Illuminate\Support\Str;

class MultipartUploadService
{
    private S3Client $s3Client;
    private string $bucket;

    public function __construct(public Request $request)
    {
        $this->s3Client = $this->createStorageClient();
        $this->bucket = config('filesystems.disks.s3.bucket');
    }

    public function initiateMultipartUpload(): array
    {
        $fileName = $this->request->input('file_name');
        $contentType = $this->request->input('content_type', 'application/octet-stream');

        $key = 'uploads/multipart/' . Str::random(10) . '-' . $fileName;

        $result = $this->s3Client->createMultipartUpload([
            'Bucket' => $this->bucket,
            'Key' => $key,
            'ContentType' => $contentType,
        ]);

        return [
            'upload_id' => $result['UploadId'],
            'file_path' => $key,
        ];
    }

    public function getPresignedUrlForPart(): array
    {
        $uploadId = $this->request->input('upload_id');
        $key = $this->request->input('file_path');
        $partNumber = (int) $this->request->input('part_number');

        $command = $this->s3Client->getCommand('uploadPart', [
            'Bucket' => $this->bucket,
            'Key' => $key,
            'UploadId' => $uploadId,
            'PartNumber' => $partNumber,
        ]);

        $signedRequest = $this->s3Client->createPresignedRequest($command, '+60 minutes');
        $uri = $signedRequest->getUri();

        return [
            'presigned_url' => (string) $uri,
            'part_number' => $partNumber,
            'headers' => [],
        ];
    }

    public function completeMultipartUpload(): array
    {
        $uploadId = $this->request->input('upload_id');
        $key = $this->request->input('file_path');
        $parts = $this->request->input('parts', []);

        $formattedParts = collect($parts)
            ->map(fn($part) => [
                'PartNumber' => (int) $part['part_number'],
                'ETag' => $part['etag'],
            ])
            ->sortBy('PartNumber')
            ->values()
            ->all();

        $result = $this->s3Client->completeMultipartUpload([
            'Bucket' => $this->bucket,
            'Key' => $key,
            'UploadId' => $uploadId,
            'MultipartUpload' => ['Parts' => $formattedParts],
        ]);

        return [
            'file_path' => $key,
            'location' => $result['Location'] ?? null,
        ];
    }

    public function abortMultipartUpload(): array
    {
        $uploadId = $this->request->input('upload_id');
        $key = $this->request->input('file_path');

        $this->s3Client->abortMultipartUpload([
            'Bucket' => $this->bucket,
            'Key' => $key,
            'UploadId' => $uploadId,
        ]);

        return ['message' => 'Multipart upload aborted'];
    }

    private function createStorageClient(): S3Client
    {
        return new S3Client([
            'region' => config('filesystems.disks.s3.region'),
            'version' => 'latest',
            'credentials' => [
                'key' => config('filesystems.disks.s3.key'),
                'secret' => config('filesystems.disks.s3.secret'),
            ],
        ]);
    }
}
```

#### Routes (api.php)

```php
Route::prefix('s3/multipart')->middleware('auth:sanctum')->group(function () {
    Route::post('/initiate', [MultipartUploadController::class, 'initiate']);
    Route::post('/presign-part', [MultipartUploadController::class, 'presignPart']);
    Route::post('/complete', [MultipartUploadController::class, 'complete']);
    Route::post('/abort', [MultipartUploadController::class, 'abort']);
});
```

## Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

## Dependencies

- [S3Uploader](../S3Uploader/README.md) - Base S3 upload functionality
- [Room](https://developer.android.com/training/data-storage/room) - Database persistence
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) - Background processing
- [FlexiLogger](https://github.com/projectdelta6/FlexiLogger) - Logging

## See Also

- [S3Uploader](../S3Uploader/README.md) - Simple single-request uploads
- [BaseRepo-S3Uploader-Multipart](../BaseRepo-S3Uploader-Multipart/README.md) - BaseRepo integration
