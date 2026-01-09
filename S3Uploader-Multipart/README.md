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
implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart:1.1.11")
```

This module depends on `S3Uploader` and includes it transitively.

## Backend Requirements

Your backend must implement the following S3 Multipart Upload API endpoints:

### Required Endpoints

1. **Initiate Upload** - `POST /api/s3/multipart/initiate`
2. **Get Pre-signed URL for Part** - `POST /api/s3/multipart/presign-part`
3. **Complete Upload** - `POST /api/s3/multipart/complete`
4. **Abort Upload** - `POST /api/s3/multipart/abort`

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

### 7. Progress Tracking with Compose

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
    val retryDelayMs: Long = 1000L
)
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
