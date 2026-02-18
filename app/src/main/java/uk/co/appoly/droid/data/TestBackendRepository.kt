package uk.co.appoly.droid.data

import android.content.Context
import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LoggingLevel
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.message
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.appoly.droid.Log
import uk.co.appoly.droid.data.remote.BaseRetrofitClient
import uk.co.appoly.droid.data.remote.BaseService
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.data.repo.GenericBaseRepo
import uk.co.appoly.droid.network.AuthApi
import uk.co.appoly.droid.network.LoginRequest
import uk.co.appoly.droid.network.LoginResponse
import uk.co.appoly.droid.network.TestBackendRetrofitClient
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.interfaces.BeforeUploadResult
import uk.co.appoly.droid.s3upload.multipart.interfaces.DefaultUploadNotificationProvider
import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult

/**
 * Repository demonstrating the BaseRepo pattern for the test multipart upload backend.
 *
 * ## Purpose
 * This repository serves as a learning example for implementing the BaseRepo pattern.
 * It demonstrates:
 *
 * 1. **GenericBaseRepo Extension:** How to extend [GenericBaseRepo] and implement required methods
 * 2. **Service Caching:** Using [lazyService] to efficiently cache API service instances
 * 3. **APIResult Wrapping:** Converting Sandwich [ApiResponse] to [APIResult] for consistent handling
 * 4. **State Management:** Managing authentication state and providing access to dependent services
 *
 * ## Architecture Overview
 * ```
 * ┌─────────────────┐     ┌─────────────────────┐     ┌──────────────────┐
 * │    ViewModel    │ ──> │  TestBackendRepository │ ──> │     AuthApi      │
 * └─────────────────┘     └─────────────────────┘     └──────────────────┘
 *                                   │
 *                                   ▼
 *                         ┌─────────────────────┐
 *                         │ MultipartUploadManager │
 *                         └─────────────────────┘
 * ```
 *
 * ## Authentication Flow
 * 1. Create repository instance with a context
 * 2. Call [login] with credentials
 * 3. On success, token is stored internally
 * 4. [uploadManager] becomes available for multipart uploads
 * 5. All subsequent API requests automatically include the auth token
 *
 * ## Thread Safety
 * - The auth token is stored in a [StateFlow], ensuring thread-safe access
 * - Service instances are lazily created and cached by [uk.co.appoly.droid.data.remote.ServiceManager]
 * - The [MultipartUploadManager] is created once after successful login
 *
 * ## Usage Example
 * ```kotlin
 * // In your DI module or ViewModel factory
 * val repository = TestBackendRepository(context)
 *
 * // In ViewModel
 * viewModelScope.launch {
 *     when (val result = repository.login("user@example.com", "password")) {
 *         is APIResult.Success -> {
 *             // Login successful, can now use uploadManager
 *             val manager = repository.uploadManager
 *         }
 *         is APIResult.Error -> {
 *             // Handle error
 *             showError(result.message)
 *         }
 *     }
 * }
 * ```
 *
 * @param context Application context for initializing the upload manager
 * @param logger Logger instance for API logging (defaults to BaseRepoLogger)
 * @param loggingLevel Logging level for API calls (defaults to VERBOSE for demo)
 *
 * @see GenericBaseRepo
 * @see AuthApi
 * @see MultipartUploadManager
 */
class TestBackendRepository(
    private val context: Context,
    logger: FlexiLog = Log,
    loggingLevel: LoggingLevel = LoggingLevel.V
) : GenericBaseRepo(
    getRetrofitClient = { retrofitClient },
    logger = logger,
    loggingLevel = loggingLevel
) {

    companion object {
        /**
         * Internal mutable state for the auth token.
         * Accessed via the [authToken] StateFlow property.
         */
        private val _authToken = MutableStateFlow<String?>(null)

        /**
         * Singleton Retrofit client instance.
         *
         * This is created lazily and shared across all repository instances.
         * The token provider lambda captures the [_authToken] StateFlow,
         * ensuring that token changes are reflected in subsequent requests.
         */
        val retrofitClient: BaseRetrofitClient by lazy {
            TestBackendRetrofitClient(
                tokenProvider = { _authToken.value }
            )
        }
    }

    // ==================== Authentication ====================

    /**
     * Observable authentication token state.
     *
     * Emits:
     * - `null` when not authenticated
     * - The Bearer token string when authenticated
     *
     * UI layers can observe this to:
     * - Show/hide authenticated content
     * - Display token info for debugging
     * - Trigger navigation on auth state changes
     */
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    /**
     * Whether the user is currently authenticated.
     *
     * This is a convenience property derived from [authToken].
     */
    val isAuthenticated: Boolean
        get() = _authToken.value != null

    /**
     * Lazily initialized authentication service.
     *
     * The [lazyService] delegate:
     * 1. Gets or creates the [uk.co.appoly.droid.data.remote.ServiceManager] singleton
     * 2. Retrieves or creates a [BaseService] for [AuthApi]
     * 3. Caches the service for subsequent calls
     *
     * Access the actual Retrofit interface via `authService.api`.
     */
    private val authService by lazyService<AuthApi>()

    /**
     * Authenticates with the test backend server.
     *
     * ## Request
     * - **Endpoint:** `POST /api/login`
     * - **Body:** `{"email": "...", "password": "..."}`
     *
     * ## Response
     * - **Success:** `{"token": "...", "user": {...}}`
     * - **Error:** `{"message": "Invalid credentials"}`
     *
     * ## Side Effects
     * On successful login:
     * 1. Stores the token in [_authToken] StateFlow
     * 2. Initializes the [S3Uploader] with the token provider
     * 3. Creates the [uploadManager] instance
     *
     * @param email User's email address
     * @param password User's password
     * @return [APIResult.Success] with [LoginResponse] on success,
     *         [APIResult.Error] with error details on failure
     *
     * @see LoginRequest
     * @see LoginResponse
     */
    suspend fun login(email: String, password: String): APIResult<LoginResponse> {
        val response = authService.api.login(LoginRequest(email, password))

        return when (response) {
            is ApiResponse.Success -> {
                val loginResponse = response.data
                val token = loginResponse.token

                if (token != null) {
                    // Store the token - this will automatically be used by the AuthInterceptor
                    _authToken.value = token

                    // Initialize S3Uploader with our token provider
                    initializeS3Uploader()

                    // Initialize the upload manager
                    initializeUploadManager()

                    APIResult.Success(loginResponse)
                } else {
                    APIResult.Error(
                        responseCode = 200,
                        message = "Login response missing token"
                    )
                }
            }

            is ApiResponse.Failure.Error -> {
                handleFailureError(response, "Login")
            }

            is ApiResponse.Failure.Exception -> {
                handleFailureException(response, "Login")
            }
        }
    }

    /**
     * Logs out the current user.
     *
     * Clears the authentication token, which:
     * - Causes subsequent API calls to be unauthenticated
     * - Invalidates the [uploadManager] (will need to login again)
     */
    fun logout() {
        _authToken.value = null
        _uploadManager = null
    }

    /**
     * Extracts an error message from a failed API response.
     *
     * This method is required by [GenericBaseRepo] and is called when an API
     * returns an error response. The test backend returns errors in the format:
     * ```json
     * {"message": "Error description"}
     * ```
     *
     * @param response The error response from the API
     * @return The extracted error message, or null if not available
     */
    override fun extractErrorMessage(response: ApiResponse.Failure.Error): String? {
        return response.message()
    }

    // ==================== Upload Manager ====================

    /**
     * Internal mutable reference to the upload manager.
     * Created after successful login.
     */
    private var _uploadManager: MultipartUploadManager? = null

    /**
     * Multipart upload manager for handling large file uploads.
     *
     * This is only available after successful authentication via [login].
     * Returns `null` if not authenticated.
     *
     * ## Configuration
     * The upload manager is configured with:
     * - **Chunk Size:** 5MB (minimum for S3 multipart uploads)
     * - **Concurrent Parts:** 3 (balance between speed and resource usage)
     * - **Max Retries:** 3 (resilient to temporary network issues)
     *
     * ## Usage
     * ```kotlin
     * repository.uploadManager?.let { manager ->
     *     val result = manager.startUpload(file, apiUrls)
     *     result.onSuccess { sessionId ->
     *         // Observe progress
     *         manager.observeProgress(sessionId).collect { progress ->
     *             updateUI(progress)
     *         }
     *     }
     * }
     * ```
     *
     * @see MultipartUploadManager
     * @see MultipartUploadConfig
     */
    val uploadManager: MultipartUploadManager?
        get() = _uploadManager

    /**
     * Initializes the S3Uploader library with our token provider.
     *
     * The S3Uploader needs to be initialized once with:
     * - A token provider that returns the current auth token
     * - Logging configuration for debugging
     *
     * This is called automatically after successful login.
     */
    private fun initializeS3Uploader() {
        S3Uploader.initS3Uploader(
            headerProvider = HeaderProvider.bearer { _authToken.value },
            loggingLevel = LoggingLevel.V,
            logger = uk.co.appoly.droid.Log
        )
    }

    /**
     * Initializes the multipart upload manager with the configured settings.
     *
     * ## Configuration Details
     *
     * ### Chunk Size: 5MB (5 * 1024 * 1024 bytes)
     * S3 requires a minimum chunk size of 5MB for multipart uploads.
     * Larger chunks mean:
     * - Fewer HTTP requests (less overhead)
     * - More data lost on retry
     * - Higher memory usage
     *
     * ### Concurrent Parts: 3
     * Number of parts uploaded simultaneously. Balances:
     * - Upload speed (more concurrent = faster)
     * - Network bandwidth usage
     * - Memory consumption
     * - Battery usage on mobile
     *
     * ### Max Retries: 3
     * Number of retry attempts for failed part uploads.
     * Uses exponential backoff between retries.
     * Handles temporary network issues gracefully.
     *
     * ### Notification Provider
     * Custom notification provider with app-specific channel and text.
     *
     * ### Lifecycle Callbacks
     * Demonstrates the lifecycle callback system for pre/post upload hooks.
     */
    private fun initializeUploadManager() {
        _uploadManager = MultipartUploadManager.getInstance(
            context = context,
            config = MultipartUploadConfig(
                chunkSize = 5 * 1024 * 1024, // 5MB - minimum for S3
                maxConcurrentParts = 3,       // Concurrent upload threads
                maxRetries = 3,               // Retry attempts per part
                // Custom notification provider with app branding
                notificationProvider = DefaultUploadNotificationProvider(
                    channelId = "demo_uploads",
                    channelName = "Demo Uploads",
                    channelDescription = "File upload progress notifications",
                    titleProvider = { progress ->
                        progress?.fileName?.let { "Uploading: $it" } ?: "Preparing upload..."
                    },
                    contentTextProvider = { progress ->
                        progress?.let {
                            "${it.overallProgress.toInt()}% complete (${it.uploadedParts}/${it.totalParts} parts)"
                        } ?: "Starting upload..."
                    }
                ),
                // Lifecycle callbacks for demonstrating the hook system
                lifecycleCallbacks = createLifecycleCallbacks()
            )
        )
    }

    /**
     * Creates lifecycle callbacks that log upload events.
     *
     * This demonstrates how apps can hook into the upload lifecycle for:
     * - Pre-upload validation/registration
     * - Post-upload confirmation/cleanup
     * - Progress tracking
     * - Pause/resume event handling
     */
    private fun createLifecycleCallbacks(): UploadLifecycleCallbacks {
        return object : UploadLifecycleCallbacks {
            override suspend fun onBeforeUpload(
                filePath: String
            ): BeforeUploadResult {
                Log.d("UploadLifecycle", "onBeforeUpload: file=$filePath")
                // In a real app, you might:
                // - Validate the file before upload
                // - Check user permissions/quotas
                // - Register the upload intent with your backend
                return BeforeUploadResult.Continue
            }

            override suspend fun onUploadComplete(
                sessionId: String,
                result: MultipartUploadResult
            ) {
                when (result) {
                    is MultipartUploadResult.Success -> {
                        Log.d("UploadLifecycle", "onUploadComplete: SUCCESS - sessionId=$sessionId, location=${result.location}")
                        // In a real app, you might:
                        // - Confirm upload with backend
                        // - Update local database
                        // - Delete temp file: File(result.filePath).delete()
                    }
                    is MultipartUploadResult.Error -> {
                        Log.d("UploadLifecycle", "onUploadComplete: ERROR - sessionId=$sessionId, message=${result.message}")
                    }
                    is MultipartUploadResult.Paused -> {
                        Log.d("UploadLifecycle", "onUploadComplete: PAUSED - sessionId=$sessionId, ${result.uploadedParts}/${result.totalParts} parts")
                    }
                    is MultipartUploadResult.Cancelled -> {
                        Log.d("UploadLifecycle", "onUploadComplete: CANCELLED - sessionId=$sessionId")
                    }
                }
            }

            override suspend fun onUploadPaused(
                sessionId: String,
                reason: String,
                isConstraintViolation: Boolean
            ) {
                Log.d("UploadLifecycle", "onUploadPaused: sessionId=$sessionId, reason=$reason, isConstraintViolation=$isConstraintViolation")
                // In a real app, you might:
                // - Notify user about the pause
                // - Log analytics
                // - Schedule notifications for when to retry
            }

            override suspend fun onUploadResumed(sessionId: String) {
                Log.d("UploadLifecycle", "onUploadResumed: sessionId=$sessionId")
                // In a real app, you might:
                // - Update UI state
                // - Log analytics
            }

            override suspend fun onProgressUpdate(
                sessionId: String,
                progress: MultipartUploadProgress
            ) {
                // Note: This is called frequently, so we only log at debug level
                // In a real app, you might update a local progress store
                // Log.v("UploadLifecycle", "onProgressUpdate: sessionId=$sessionId, ${progress.overallProgress}%")
            }
        }
    }

    // ==================== Multipart API URLs ====================

    /**
     * Base URL for multipart upload API endpoints.
     *
     * Use with [uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls.fromBaseUrl]
     * to generate the full set of URLs for multipart operations.
     */
    val multipartBaseUrl: String
        get() = TestBackendRetrofitClient.MULTIPART_BASE_URL
}
