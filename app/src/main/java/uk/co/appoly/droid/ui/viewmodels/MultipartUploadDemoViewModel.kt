package uk.co.appoly.droid.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.co.appoly.droid.Log
import uk.co.appoly.droid.data.TestBackendRepository
import uk.co.appoly.droid.data.remote.model.APIResult
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import java.io.File
import java.io.FileOutputStream

/**
 * ViewModel demonstrating multipart upload functionality using the BaseRepo pattern.
 *
 * ## Purpose
 * This ViewModel serves as a learning example showing how to:
 * 1. Use a repository following the BaseRepo pattern
 * 2. Handle authentication flows with [APIResult]
 * 3. Manage multipart upload lifecycle (start, pause, resume, cancel, recover)
 * 4. Observe upload progress via [StateFlow]
 *
 * ## Architecture
 * ```
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                              UI Layer                                   │
 * │  ┌─────────────────────────────────────────────────────────────────┐   │
 * │  │              MultipartUploadDemoScreen                          │   │
 * │  │  • Observes StateFlows for UI state                            │   │
 * │  │  • Calls ViewModel methods for user actions                    │   │
 * │  └─────────────────────────────────────────────────────────────────┘   │
 * └───────────────────────────────────┬─────────────────────────────────────┘
 *                                     │
 *                                     ▼
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                           ViewModel Layer                               │
 * │  ┌─────────────────────────────────────────────────────────────────┐   │
 * │  │           MultipartUploadDemoViewModel (this class)             │   │
 * │  │  • Manages UI state via StateFlows                             │   │
 * │  │  • Coordinates authentication and upload operations            │   │
 * │  │  • Transforms repository results for UI consumption            │   │
 * │  └─────────────────────────────────────────────────────────────────┘   │
 * └───────────────────────────────────┬─────────────────────────────────────┘
 *                                     │
 *                                     ▼
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                          Repository Layer                               │
 * │  ┌─────────────────────────────────────────────────────────────────┐   │
 * │  │              TestBackendRepository                              │   │
 * │  │  • Extends GenericBaseRepo                                     │   │
 * │  │  • Manages authentication token                                │   │
 * │  │  • Provides MultipartUploadManager                             │   │
 * │  └─────────────────────────────────────────────────────────────────┘   │
 * └───────────────────────────────────┬─────────────────────────────────────┘
 *                                     │
 *                                     ▼
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │                           Network Layer                                 │
 * │  ┌───────────────────┐    ┌───────────────────────────────────────┐   │
 * │  │     AuthApi       │    │      MultipartUploadManager           │   │
 * │  │  (via Retrofit)   │    │  (handles S3 multipart operations)    │   │
 * │  └───────────────────┘    └───────────────────────────────────────┘   │
 * └─────────────────────────────────────────────────────────────────────────┘
 * ```
 *
 * ## Upload State Machine
 * The multipart upload follows this state machine:
 *
 * ```
 * PENDING ──start──> IN_PROGRESS ──parts done──> COMPLETING ──> COMPLETED
 *                        │ │
 *                        │ └──pause──> PAUSED ──resume──> IN_PROGRESS
 *                        │
 *                        └──cancel──> ABORTED
 *
 * FAILED (from any active state) ──resume──> IN_PROGRESS
 * ```
 *
 * ### State Descriptions
 * - **PENDING:** Upload created but not yet started
 * - **IN_PROGRESS:** Parts are actively being uploaded
 * - **PAUSED:** Upload paused by user, can be resumed
 * - **COMPLETING:** All parts uploaded, waiting for S3 to combine them
 * - **COMPLETED:** Upload finished successfully, file available on S3
 * - **FAILED:** Upload failed (network error, server error, etc.)
 * - **ABORTED:** Upload cancelled by user, cannot be resumed
 *
 * ## StateFlow Properties
 *
 * ### Authentication State
 * - [authToken]: Current auth token (null if not logged in)
 * - [isLoggingIn]: Whether a login request is in progress
 * - [loginError]: Error message from failed login attempt
 *
 * ### File Selection State
 * - [selectedFileUri]: Content URI of selected file
 * - [selectedFileName]: Display name of selected file
 *
 * ### Upload State
 * - [currentSessionId]: ID of the currently selected upload session
 * - [uploadProgress]: Progress details for the current upload
 * - [uploadError]: Error message from failed upload operation
 * - [allUploads]: List of all tracked upload sessions
 *
 * ### Debug State
 * - [logMessages]: List of debug log messages for the UI
 *
 * ## Configuration
 * The upload manager is configured with these values (via repository):
 * - **Chunk Size:** 5MB (minimum for S3 multipart uploads)
 * - **Concurrent Parts:** 3 (uploads 3 parts simultaneously)
 * - **Max Retries:** 3 (retries failed parts up to 3 times)
 *
 * @param application Application context for accessing content resolver and cache directory
 *
 * @see TestBackendRepository
 * @see MultipartUploadProgress
 * @see UploadSessionStatus
 */
class MultipartUploadDemoViewModel(application: Application) : AndroidViewModel(application) {

    // ==================== Repository ====================

    /**
     * Repository for authentication and upload management.
     *
     * Provides:
     * - [TestBackendRepository.login] for authentication
     * - [TestBackendRepository.uploadManager] for multipart uploads
     * - [TestBackendRepository.authToken] for observing auth state
     */
    private val repository = TestBackendRepository(application)

    // ==================== Authentication State ====================

    /**
     * Current authentication token.
     *
     * Exposed directly from the repository to enable:
     * - UI observation of auth state
     * - Conditional rendering of authenticated content
     */
    val authToken: StateFlow<String?> = repository.authToken

    /**
     * Email input for login.
     */
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    /**
     * Password input for login.
     */
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    /**
     * Whether a login request is currently in progress.
     *
     * Used to:
     * - Disable the login button during login
     * - Show a loading indicator
     */
    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    /**
     * Error message from the most recent failed login attempt.
     *
     * Cleared at the start of each login attempt.
     * Set when login fails with the error message.
     */
    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // ==================== File Selection State ====================

    /**
     * Content URI of the currently selected file.
     *
     * This is a `content://` URI from the system file picker.
     * The file is copied to the cache directory before upload.
     */
    private val _selectedFileUri = MutableStateFlow<Uri?>(null)
    val selectedFileUri: StateFlow<Uri?> = _selectedFileUri.asStateFlow()

    /**
     * Display name of the currently selected file.
     *
     * Retrieved from the content resolver using [android.provider.OpenableColumns.DISPLAY_NAME].
     */
    private val _selectedFileName = MutableStateFlow<String?>(null)
    val selectedFileName: StateFlow<String?> = _selectedFileName.asStateFlow()

    // ==================== Upload State ====================

    /**
     * Session ID of the currently selected/active upload.
     *
     * Used to:
     * - Identify which upload to pause/resume/cancel
     * - Observe progress for the specific upload
     * - Highlight the selected upload in the UI list
     */
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    /**
     * Progress details for the currently selected upload.
     *
     * Contains:
     * - [MultipartUploadProgress.status]: Current upload state
     * - [MultipartUploadProgress.overallProgress]: Percentage complete (0-100)
     * - [MultipartUploadProgress.uploadedParts]: Number of completed parts
     * - [MultipartUploadProgress.totalParts]: Total number of parts
     * - [MultipartUploadProgress.errorMessage]: Error details if failed
     */
    private val _uploadProgress = MutableStateFlow<MultipartUploadProgress?>(null)
    val uploadProgress: StateFlow<MultipartUploadProgress?> = _uploadProgress.asStateFlow()

    /**
     * Error message from the most recent failed upload operation.
     *
     * Set when operations like start, pause, resume, or cancel fail.
     * Cleared when a new upload is started.
     */
    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    /**
     * List of all tracked upload sessions.
     *
     * Includes uploads in all states (pending, in progress, completed, failed, etc.).
     * Observed from the [uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager].
     */
    private val _allUploads = MutableStateFlow<List<MultipartUploadProgress>>(emptyList())
    val allUploads: StateFlow<List<MultipartUploadProgress>> = _allUploads.asStateFlow()

    // ==================== Debug State ====================

    /**
     * Debug log messages for the UI.
     *
     * Contains timestamped messages showing:
     * - API calls and responses
     * - Upload state changes
     * - Error details
     *
     * Useful for understanding the upload flow and debugging issues.
     */
    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    // ==================== Authentication Operations ====================

    /**
     * Updates the email input field.
     */
    fun setEmail(value: String) {
        _email.value = value
    }

    /**
     * Updates the password input field.
     */
    fun setPassword(value: String) {
        _password.value = value
    }

    /**
     * Authenticates with the test backend server.
     *
     * Uses the [TestBackendRepository] to perform login via the BaseRepo pattern.
     * On success:
     * - Token is stored in the repository
     * - Upload manager is initialized
     * - All uploads are observed
     *
     * On failure:
     * - Error message is set in [loginError]
     */
    fun login() {
        val emailValue = _email.value.trim()
        val passwordValue = _password.value

        if (emailValue.isBlank() || passwordValue.isBlank()) {
            _loginError.value = "Email and password are required"
            return
        }

        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            addLog("Logging in with email: $emailValue")

            when (val result = repository.login(emailValue, passwordValue)) {
                is APIResult.Success -> {
                    val token = result.data.token
                    addLog("Login successful! Token: ${token?.take(20)}...")
                    observeAllUploads()
                }

                is APIResult.Error -> {
                    _loginError.value = result.message
                    addLog("Login failed: ${result.message}")
                }
            }

            _isLoggingIn.value = false
        }
    }

    // ==================== Upload Observation ====================

    /**
     * Starts observing all uploads from the upload manager.
     *
     * Called automatically after successful login.
     * Updates [allUploads] with the current list of all tracked uploads.
     */
    private fun observeAllUploads() {
        viewModelScope.launch {
            repository.uploadManager?.observeAllUploads()?.collect { uploads ->
                _allUploads.value = uploads
            }
        }
    }

    /**
     * Starts observing progress for a specific upload session.
     *
     * Called when:
     * - A new upload is started
     * - An existing upload is selected
     *
     * Updates [uploadProgress] with detailed progress information.
     *
     * @param sessionId The upload session ID to observe
     */
    private fun observeCurrentUpload(sessionId: String) {
        viewModelScope.launch {
            repository.uploadManager?.observeProgress(sessionId)?.collect { progress ->
                _uploadProgress.value = progress
                if (progress != null) {
                    addLog("Progress: ${progress.toProgressString()} - ${progress.status}")
                }
            }
        }
    }

    // ==================== File Selection ====================

    /**
     * Sets the selected file for upload.
     *
     * Called from the UI when the user picks a file using the system file picker.
     *
     * @param uri Content URI of the selected file
     * @param fileName Display name of the selected file
     */
    fun setSelectedFile(uri: Uri, fileName: String) {
        _selectedFileUri.value = uri
        _selectedFileName.value = fileName
        addLog("Selected file: $fileName")
    }

    // ==================== Upload Operations ====================

    /**
     * Starts uploading the selected file.
     *
     * ## Process
     * 1. Validates that a file is selected
     * 2. Copies the file from content URI to cache directory
     * 3. Generates multipart API URLs from the repository's base URL
     * 4. Starts the upload via the upload manager
     * 5. Begins observing the upload progress
     *
     * ## Error Handling
     * Errors are captured in [uploadError] for:
     * - No file selected
     * - Failed to read file from URI
     * - Upload manager not initialized (not logged in)
     * - Upload start failure
     */
    fun startUpload() {
        val uri = _selectedFileUri.value ?: run {
            _uploadError.value = "No file selected"
            addLog("Error: No file selected")
            return
        }
        val fileName = _selectedFileName.value ?: "unknown_file"

        viewModelScope.launch {
            _uploadError.value = null
            addLog("Starting upload for: $fileName")

            // Copy file from content URI to cache directory
            // This is necessary because the multipart uploader needs a File path
            val file = copyUriToFile(uri, fileName)
            if (file == null) {
                _uploadError.value = "Failed to read file"
                addLog("Error: Failed to read file from URI")
                return@launch
            }

            addLog("File copied to cache: ${file.absolutePath} (${file.length()} bytes)")

            // Generate API URLs from the repository's base URL
            val apiUrls = MultipartApiUrls.fromBaseUrl(repository.multipartBaseUrl)
            addLog("Using API URLs: $apiUrls")

            repository.uploadManager?.let { manager ->
                val result = manager.startUpload(file, apiUrls)
                result.onSuccess { sessionId ->
                    _currentSessionId.value = sessionId
                    addLog("Upload started with session: $sessionId")
                    observeCurrentUpload(sessionId)
                }.onFailure { error ->
                    _uploadError.value = error.message
                    addLog("Upload failed to start: ${error.message}")
                }
            } ?: run {
                _uploadError.value = "Upload manager not initialized. Please login first."
                addLog("Error: Upload manager not initialized")
            }
        }
    }

    /**
     * Pauses the currently selected upload.
     *
     * The upload can be resumed later with [resumeUpload].
     * Only works for uploads in PENDING or IN_PROGRESS state.
     *
     * @see canPause
     */
    fun pauseUpload() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            addLog("Pausing upload: $sessionId")
            val result = repository.uploadManager?.pauseUpload(sessionId)
            result?.onFailure { error ->
                _uploadError.value = error.message
                addLog("Pause failed: ${error.message}")
            }
        }
    }

    /**
     * Resumes a paused or failed upload.
     *
     * Continues uploading remaining parts from where it left off.
     * Only works for uploads in PAUSED or FAILED state.
     *
     * @see canResume
     */
    fun resumeUpload() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            addLog("Resuming upload: $sessionId")
            val result = repository.uploadManager?.resumeUpload(sessionId)
            result?.onFailure { error ->
                _uploadError.value = error.message
                addLog("Resume failed: ${error.message}")
            }
        }
    }

    /**
     * Cancels and aborts the currently selected upload.
     *
     * This action is irreversible. The upload will be marked as ABORTED
     * and cannot be resumed.
     *
     * On success:
     * - Clears the current session selection
     * - Clears the upload progress display
     *
     * @see canCancel
     */
    fun cancelUpload() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            addLog("Cancelling upload: $sessionId")
            val result = repository.uploadManager?.cancelUpload(sessionId)
            result?.onSuccess {
                _currentSessionId.value = null
                _uploadProgress.value = null
                addLog("Upload cancelled")
            }?.onFailure { error ->
                _uploadError.value = error.message
                addLog("Cancel failed: ${error.message}")
            }
        }
    }

    /**
     * Recovers uploads that were interrupted (e.g., by app kill or crash).
     *
     * Scans the database for uploads in recoverable states and attempts
     * to resume them. This is useful for:
     * - App restart after crash
     * - App restart after being killed by the system
     * - App update
     *
     * Recoverable states: IN_PROGRESS, PENDING (with partial progress)
     */
    fun recoverUploads() {
        viewModelScope.launch {
            addLog("Recovering interrupted uploads...")
            val recovered = repository.uploadManager?.recoverInterruptedUploads() ?: emptyList()
            addLog("Recovered ${recovered.size} uploads: $recovered")
        }
    }

    // ==================== Selection Management ====================

    /**
     * Selects an upload session from the list of all uploads.
     *
     * Updates the UI to show progress for the selected upload.
     *
     * @param sessionId The upload session ID to select
     */
    fun selectUpload(sessionId: String) {
        _currentSessionId.value = sessionId
        observeCurrentUpload(sessionId)
        addLog("Selected upload: $sessionId")
    }

    /**
     * Clears the current selection and file.
     *
     * Resets:
     * - Current session ID
     * - Upload progress display
     * - Selected file URI and name
     */
    fun clearSelection() {
        _currentSessionId.value = null
        _uploadProgress.value = null
        _selectedFileUri.value = null
        _selectedFileName.value = null
    }

    // ==================== Debug Logging ====================

    /**
     * Clears all debug log messages.
     */
    fun clearLogs() {
        _logMessages.value = emptyList()
    }

    /**
     * Adds a timestamped message to the debug log.
     *
     * Messages are displayed in the UI log section and also
     * logged to Logcat via [uk.co.appoly.droid.Log].
     *
     * @param message The message to log
     */
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logEntry = "[$timestamp] $message"
        _logMessages.value = _logMessages.value + logEntry
        Log.d("MultipartUploadDemo", message)
    }

    // ==================== Helper Methods ====================

    /**
     * Copies a file from a content URI to the app's cache directory.
     *
     * This is necessary because:
     * - Content URIs can't be read directly as file paths
     * - The multipart uploader needs access to the file via path
     * - The cache directory is always accessible to the app
     *
     * @param uri Content URI of the source file
     * @param fileName Name to use for the cached file
     * @return The copied File, or null if copying failed
     */
    private fun copyUriToFile(uri: Uri, fileName: String): File? {
        return try {
            val context = getApplication<Application>()
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val cacheDir = File(context.cacheDir, "uploads")
            cacheDir.mkdirs()
            val file = File(cacheDir, fileName)
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()
            file
        } catch (e: Exception) {
            addLog("Error copying file: ${e.message}")
            null
        }
    }

    // ==================== State Helpers ====================

    /**
     * Determines if the current upload can be paused.
     *
     * Pausable states: PENDING, IN_PROGRESS
     *
     * @return true if pause is allowed, false otherwise
     */
    fun canPause(): Boolean {
        val progress = _uploadProgress.value ?: return false
        return progress.status in listOf(
            UploadSessionStatus.PENDING,
            UploadSessionStatus.IN_PROGRESS
        )
    }

    /**
     * Determines if the current upload can be resumed.
     *
     * Resumable states: PAUSED, FAILED
     *
     * @return true if resume is allowed, false otherwise
     */
    fun canResume(): Boolean {
        val progress = _uploadProgress.value ?: return false
        return progress.status in listOf(
            UploadSessionStatus.PAUSED,
            UploadSessionStatus.FAILED
        )
    }

    /**
     * Determines if the current upload can be cancelled.
     *
     * Non-cancellable states: COMPLETED, ABORTED
     * All other states can be cancelled.
     *
     * @return true if cancel is allowed, false otherwise
     */
    fun canCancel(): Boolean {
        val progress = _uploadProgress.value ?: return false
        return progress.status !in listOf(
            UploadSessionStatus.COMPLETED,
            UploadSessionStatus.ABORTED
        )
    }

    // ==================== Testing Helpers ====================

    /**
     * Simulates an app crash by killing the process.
     *
     * This is useful for testing the upload recovery functionality.
     * After the crash, reopen the app and tap "Recover Uploads" to
     * resume any interrupted uploads.
     *
     * WARNING: This will immediately terminate the app process.
     */
    fun simulateCrash() {
        addLog("Simulating crash in 1 second...")
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /**
     * Returns true if there's an active upload that can be interrupted.
     */
    fun canSimulateCrash(): Boolean {
        val progress = _uploadProgress.value ?: return false
        return progress.status == UploadSessionStatus.IN_PROGRESS
    }
}
