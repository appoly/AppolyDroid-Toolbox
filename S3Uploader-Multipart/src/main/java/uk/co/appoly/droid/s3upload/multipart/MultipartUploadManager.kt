package uk.co.appoly.droid.s3upload.multipart

import android.content.Context
import android.webkit.MimeTypeMap
import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.message
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.utils.MultipartUploadLogger
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import uk.co.appoly.droid.s3upload.multipart.database.dao.MultipartUploadDao
import uk.co.appoly.droid.s3upload.multipart.database.entity.PartUploadStatus
import uk.co.appoly.droid.s3upload.multipart.database.entity.SessionWithParts
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadPartEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import uk.co.appoly.droid.s3upload.multipart.network.MultipartApiService
import uk.co.appoly.droid.s3upload.multipart.network.MultipartRetrofitClient
import uk.co.appoly.droid.s3upload.multipart.network.model.CompletedPart
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.network.model.S3PartUploadResult
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult
import uk.co.appoly.droid.s3upload.multipart.utils.MultipartUploadLog
import java.io.File
import java.io.RandomAccessFile
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager class for handling multipart uploads with pause/resume/recovery support.
 *
 * This class orchestrates the entire multipart upload process:
 * - Initiating uploads with the backend
 * - Splitting files into parts
 * - Uploading parts with progress tracking
 * - Handling retries and errors
 * - Persisting state for recovery
 * - Completing or aborting uploads
 *
 * @param context Application context
 * @param config Upload configuration
 */
class MultipartUploadManager internal constructor(
    context: Context,
    private val config: MultipartUploadConfig
) {
    private val database = S3UploaderDatabase.getInstance(context)
    private val dao: MultipartUploadDao = database.multipartUploadDao()

    private val apiService = MultipartApiService(
        api = MultipartRetrofitClient.multipartApis,
        tokenProvider = { S3Uploader.getTokenProvider().provideToken() }
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val progressFlows = ConcurrentHashMap<String, MutableStateFlow<MultipartUploadProgress>>()

    // ==================== Public API ====================

    /**
     * Starts a new multipart upload.
     *
     * @param file The file to upload
     * @param apiUrls API endpoints for multipart operations
     * @return Result containing the session ID on success
     */
    suspend fun startUpload(
        file: File,
        apiUrls: MultipartApiUrls
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Validate file
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist: ${file.absolutePath}"))
            }
            if (!file.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Cannot read file: ${file.absolutePath}"))
            }

            // Check for existing active session for this file
            val existingSession = dao.findActiveSessionForFile(file.absolutePath)
            if (existingSession != null) {
                MultipartUploadLog.d(this@MultipartUploadManager, "Found existing session for file, resuming: ${existingSession.sessionId}")
                resumeUpload(existingSession.sessionId)
                return@withContext Result.success(existingSession.sessionId)
            }

            val sessionId = UUID.randomUUID().toString()
            val fileName = file.name
		val contentType = getMimeType(file) ?: "application/octet-stream"
            val fileSize = file.length()
            val totalParts = config.calculatePartCount(fileSize)

            MultipartUploadLog.d(this@MultipartUploadManager, "Starting multipart upload: $fileName, size=$fileSize, parts=$totalParts, concurrent=${config.maxConcurrentParts}")

            // Initiate multipart upload with backend
            val response = apiService.initiateMultipartUpload(
                url = apiUrls.initiateUrl,
                fileName = fileName,
                contentType = contentType
            )

            when (response) {
                is ApiResponse.Success -> {
                    val data = response.data.data
                        ?: return@withContext Result.failure(IllegalStateException("No data in initiate response"))

                    val now = System.currentTimeMillis()

                    // Create session entity
                    val session = UploadSessionEntity(
                        sessionId = sessionId,
                        uploadId = data.uploadId,
                        localFilePath = file.absolutePath,
                        remoteFilePath = data.filePath,
                        fileName = fileName,
                        contentType = contentType,
                        totalFileSize = fileSize,
                        chunkSize = config.chunkSize,
                        totalParts = totalParts,
                        status = UploadSessionStatus.PENDING,
                        initiateUrl = apiUrls.initiateUrl,
                        presignPartUrl = apiUrls.presignPartUrl,
                        completeUrl = apiUrls.completeUrl,
                        abortUrl = apiUrls.abortUrl,
                        createdAt = now,
                        updatedAt = now,
                        maxRetries = config.maxRetries
                    )

                    // Create part entities
                    val parts = createPartEntities(sessionId, fileSize, config.chunkSize, now)

                    // Save to database
                    dao.insertSession(session)
                    dao.insertParts(parts)

                    MultipartUploadLog.d(this@MultipartUploadManager, "Created session: $sessionId with ${parts.size} parts")

                    // Start uploading
                    startUploadJob(sessionId)

                    Result.success(sessionId)
                }

                is ApiResponse.Failure.Error -> {
                    val message = response.message()
                    MultipartUploadLog.e(this@MultipartUploadManager, "Failed to initiate upload: $message")
                    Result.failure(IllegalStateException("Failed to initiate upload: $message"))
                }

                is ApiResponse.Failure.Exception -> {
                    MultipartUploadLog.e(this@MultipartUploadManager, "Exception initiating upload", response.throwable)
                    Result.failure(response.throwable)
                }
            }
        } catch (e: Exception) {
            MultipartUploadLog.e(this@MultipartUploadManager, "Error starting upload", e)
            Result.failure(e)
        }
    }

    /**
     * Pauses an active upload.
     *
     * @param sessionId The session ID to pause
     * @return Result indicating success or failure
     */
    suspend fun pauseUpload(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = dao.getSession(sessionId)
                ?: return@withContext Result.failure(IllegalArgumentException("Session not found: $sessionId"))

            if (session.status !in listOf(UploadSessionStatus.PENDING, UploadSessionStatus.IN_PROGRESS)) {
                return@withContext Result.failure(IllegalStateException("Cannot pause session in ${session.status} state"))
            }

            // Cancel active job
            activeJobs[sessionId]?.cancel()
            activeJobs.remove(sessionId)

            // Reset any parts that were uploading to pending
            dao.resetUploadingParts(sessionId)

            // Update session status
            dao.updateSessionStatus(sessionId, UploadSessionStatus.PAUSED, System.currentTimeMillis())

            MultipartUploadLog.d(this@MultipartUploadManager, "Paused upload: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            MultipartUploadLog.e(this@MultipartUploadManager, "Error pausing upload", e)
            Result.failure(e)
        }
    }

    /**
     * Resumes a paused upload.
     *
     * @param sessionId The session ID to resume
     * @return Result indicating success or failure
     */
    suspend fun resumeUpload(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = dao.getSession(sessionId)
                ?: return@withContext Result.failure(IllegalArgumentException("Session not found: $sessionId"))

            if (session.status !in listOf(UploadSessionStatus.PAUSED, UploadSessionStatus.PENDING, UploadSessionStatus.FAILED)) {
                return@withContext Result.failure(IllegalStateException("Cannot resume session in ${session.status} state"))
            }

            // Verify file still exists
            val file = File(session.localFilePath)
            if (!file.exists()) {
                dao.updateSessionStatusWithError(
                    sessionId,
                    UploadSessionStatus.FAILED,
                    "Source file no longer exists",
                    System.currentTimeMillis()
                )
                return@withContext Result.failure(IllegalStateException("Source file no longer exists: ${session.localFilePath}"))
            }

            // Reset failed parts if recovering from FAILED state
            if (session.status == UploadSessionStatus.FAILED) {
                dao.resetFailedParts(sessionId)
            }

            // Start upload job
            startUploadJob(sessionId)

            MultipartUploadLog.d(this@MultipartUploadManager, "Resumed upload: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            MultipartUploadLog.e(this@MultipartUploadManager, "Error resuming upload", e)
            Result.failure(e)
        }
    }

    /**
     * Cancels and aborts an upload.
     *
     * @param sessionId The session ID to cancel
     * @return Result indicating success or failure
     */
    suspend fun cancelUpload(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = dao.getSession(sessionId)
                ?: return@withContext Result.failure(IllegalArgumentException("Session not found: $sessionId"))

            if (session.status in listOf(UploadSessionStatus.COMPLETED, UploadSessionStatus.ABORTED)) {
                return@withContext Result.failure(IllegalStateException("Cannot cancel session in ${session.status} state"))
            }

            // Cancel active job
            activeJobs[sessionId]?.cancel()
            activeJobs.remove(sessionId)

            // Abort with S3
            try {
                apiService.abortMultipartUpload(
                    url = session.abortUrl,
                    uploadId = session.uploadId,
                    filePath = session.remoteFilePath
                )
            } catch (e: Exception) {
                MultipartUploadLog.w(this@MultipartUploadManager, "Failed to abort with S3 (may already be aborted)", e)
            }

            // Update session status
            dao.updateSessionStatus(sessionId, UploadSessionStatus.ABORTED, System.currentTimeMillis())

            MultipartUploadLog.d(this@MultipartUploadManager, "Cancelled upload: $sessionId")
            Result.success(Unit)
        } catch (e: Exception) {
            MultipartUploadLog.e(this@MultipartUploadManager, "Error cancelling upload", e)
            Result.failure(e)
        }
    }

    /**
     * Gets the current progress for a session.
     *
     * @param sessionId The session ID
     * @return Flow of progress updates
     */
    fun observeProgress(sessionId: String): Flow<MultipartUploadProgress?> {
        return dao.observeSessionWithParts(sessionId).map { sessionWithParts ->
            sessionWithParts?.toProgress()
        }
    }

    /**
     * Observes all active uploads.
     *
     * @return Flow of all active upload progress
     */
    fun observeAllUploads(): Flow<List<MultipartUploadProgress>> {
        return dao.observeActiveSessionsWithParts().map { sessions ->
            sessions.map { it.toProgress() }
        }
    }

    /**
     * Recovers interrupted uploads (e.g., after app restart).
     *
     * @return List of recovered session IDs
     */
    suspend fun recoverInterruptedUploads(): List<String> = withContext(Dispatchers.IO) {
        val recoverableSessions = dao.getRecoverableSessions()
        MultipartUploadLog.d(this@MultipartUploadManager, "Found ${recoverableSessions.size} recoverable sessions: ${recoverableSessions.map { "${it.sessionId.take(8)}...(${it.status})" }}")
        val recoveredIds = mutableListOf<String>()

        for (session in recoverableSessions) {
            // Reset any parts that were marked as uploading
            dao.resetUploadingParts(session.sessionId)

            // Verify file still exists
            val file = File(session.localFilePath)
            if (!file.exists()) {
                MultipartUploadLog.w(this@MultipartUploadManager, "Recovery failed - file no longer exists: ${session.localFilePath}")
                dao.updateSessionStatusWithError(
                    session.sessionId,
                    UploadSessionStatus.FAILED,
                    "Source file no longer exists",
                    System.currentTimeMillis()
                )
                continue
            }

            // If session was IN_PROGRESS (interrupted mid-upload), change to PAUSED so it can be resumed
            if (session.status == UploadSessionStatus.IN_PROGRESS || session.status == UploadSessionStatus.PENDING) {
                dao.updateSessionStatus(session.sessionId, UploadSessionStatus.PAUSED, System.currentTimeMillis())
                MultipartUploadLog.d(this@MultipartUploadManager, "Changed session ${session.sessionId.take(8)}... from ${session.status} to PAUSED for recovery")
            }

            // Resume the upload
            MultipartUploadLog.d(this@MultipartUploadManager, "Resuming recovered session: ${session.sessionId}")
            val result = resumeUpload(session.sessionId)
            if (result.isSuccess) {
                recoveredIds.add(session.sessionId)
            } else {
                MultipartUploadLog.e(this@MultipartUploadManager, "Failed to resume session: ${result.exceptionOrNull()?.message}")
            }
        }

        MultipartUploadLog.d(this@MultipartUploadManager, "Recovered ${recoveredIds.size} interrupted uploads")
        recoveredIds
    }

    /**
     * Cleans up old completed/aborted/failed sessions.
     *
     * @param olderThanMs Only delete sessions older than this (default 7 days)
     * @return Number of sessions deleted
     */
    suspend fun cleanupOldSessions(olderThanMs: Long = 7 * 24 * 60 * 60 * 1000L): Int {
        val threshold = System.currentTimeMillis() - olderThanMs
        return dao.deleteOldCompletedSessions(threshold)
    }

    /**
     * Gets a session by ID.
     */
    suspend fun getSession(sessionId: String): UploadSessionEntity? {
        return dao.getSession(sessionId)
    }

    // ==================== Internal Methods ====================

    private fun startUploadJob(sessionId: String) {
        // Cancel existing job if any
        activeJobs[sessionId]?.cancel()

        val job = scope.launch {
            executeUpload(sessionId)
        }
        activeJobs[sessionId] = job
    }

    internal suspend fun executeUpload(sessionId: String): MultipartUploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val session = dao.getSession(sessionId)
                    ?: return@withContext MultipartUploadResult.Error(sessionId, "Session not found", isRecoverable = false)

                // Update status to in progress
                dao.updateSessionStatus(sessionId, UploadSessionStatus.IN_PROGRESS, System.currentTimeMillis())

                val file = File(session.localFilePath)
                if (!file.exists()) {
                    dao.updateSessionStatusWithError(sessionId, UploadSessionStatus.FAILED, "File not found", System.currentTimeMillis())
                    return@withContext MultipartUploadResult.Error(sessionId, "File not found: ${session.localFilePath}", isRecoverable = false)
                }

                // Upload remaining parts
                val result = uploadParts(session, file)

                when (result) {
                    is PartUploadResult.AllPartsUploaded -> {
                        // Complete the multipart upload
                        completeUpload(session)
                    }
                    is PartUploadResult.Paused -> {
                        val uploadedParts = dao.getUploadedPartsCount(sessionId)
                        val uploadedBytes = dao.getTotalUploadedBytes(sessionId)
                        MultipartUploadResult.Paused(
                            sessionId = sessionId,
                            uploadedParts = uploadedParts,
                            totalParts = session.totalParts,
                            uploadedBytes = uploadedBytes,
                            totalBytes = session.totalFileSize
                        )
                    }
                    is PartUploadResult.Failed -> {
                        dao.updateSessionStatusWithError(
                            sessionId,
                            UploadSessionStatus.FAILED,
                            result.message,
                            System.currentTimeMillis()
                        )
                        MultipartUploadResult.Error(
                            sessionId = sessionId,
                            message = result.message,
                            throwable = result.throwable,
                            isRecoverable = result.isRecoverable
                        )
                    }
                }
            } catch (e: CancellationException) {
                // Upload was cancelled/paused
                val uploadedParts = dao.getUploadedPartsCount(sessionId)
                val uploadedBytes = dao.getTotalUploadedBytes(sessionId)
                val session = dao.getSession(sessionId)
                MultipartUploadResult.Paused(
                    sessionId = sessionId,
                    uploadedParts = uploadedParts,
                    totalParts = session?.totalParts ?: 0,
                    uploadedBytes = uploadedBytes,
                    totalBytes = session?.totalFileSize ?: 0
                )
            } catch (e: Exception) {
                MultipartUploadLog.e(this@MultipartUploadManager, "Upload failed", e)
                dao.updateSessionStatusWithError(sessionId, UploadSessionStatus.FAILED, e.message, System.currentTimeMillis())
                MultipartUploadResult.Error(
                    sessionId = sessionId,
                    message = e.message ?: "Unknown error",
                    throwable = e,
                    isRecoverable = isRecoverableError(e)
                )
            } finally {
                activeJobs.remove(sessionId)
            }
        }
    }

    private sealed class PartUploadResult {
        data object AllPartsUploaded : PartUploadResult()
        data object Paused : PartUploadResult()
        data class Failed(val message: String, val throwable: Throwable?, val isRecoverable: Boolean) : PartUploadResult()
    }

    private suspend fun uploadParts(session: UploadSessionEntity, file: File): PartUploadResult {
        val semaphore = Semaphore(config.maxConcurrentParts)
        val resultHolder = java.util.concurrent.atomic.AtomicReference<PartUploadResult?>(null)

        RandomAccessFile(file, "r").use { raf ->
            coroutineScope {
                while (resultHolder.get() == null) {
                    // Check if we should stop
                    val currentSession = dao.getSession(session.sessionId)
                    if (currentSession?.status == UploadSessionStatus.PAUSED) {
                        // Signal pause and cancel all running jobs
                        resultHolder.set(PartUploadResult.Paused)
                        coroutineContext.cancelChildren()
                        break
                    }

                    // Get next pending part
                    val part = dao.getNextPendingPart(session.sessionId) ?: break

                    // Mark part as UPLOADING immediately to prevent race condition
                    // where multiple coroutines grab the same part
                    dao.updatePartStatus(
                        partId = part.partId,
                        status = PartUploadStatus.UPLOADING,
                        etag = null,
                        uploadedBytes = 0,
                        updatedAt = System.currentTimeMillis()
                    )

                    // Launch part upload in parallel, limited by semaphore
                    launch {
                        semaphore.withPermit {
                            // Check again if we should stop (might have been set while waiting for permit)
                            if (resultHolder.get() != null) {
                                // Reset part to pending if we're stopping
                                dao.updatePartStatus(
                                    partId = part.partId,
                                    status = PartUploadStatus.PENDING,
                                    etag = null,
                                    uploadedBytes = 0,
                                    updatedAt = System.currentTimeMillis()
                                )
                                return@withPermit
                            }

                            val result = uploadSinglePart(session, part, raf)
                            if (result is SinglePartResult.Failed && !result.shouldRetry) {
                                resultHolder.set(
                                    PartUploadResult.Failed(result.message, result.throwable, result.isRecoverable)
                                )
                                // Cancel sibling coroutines
                                coroutineContext.cancelChildren()
                            }
                        }
                    }
                }
                // coroutineScope waits for all children to complete
            }
        }

        // Check if there was a failure or pause
        resultHolder.get()?.let { return it }

        // Verify all parts uploaded
        val uploadedCount = dao.getUploadedPartsCount(session.sessionId)
        return if (uploadedCount == session.totalParts) {
            PartUploadResult.AllPartsUploaded
        } else {
            PartUploadResult.Failed("Not all parts uploaded: $uploadedCount/${session.totalParts}", null, true)
        }
    }

    private sealed class SinglePartResult {
        data object Success : SinglePartResult()
        data class Failed(val message: String, val throwable: Throwable?, val shouldRetry: Boolean, val isRecoverable: Boolean) : SinglePartResult()
    }

    private suspend fun uploadSinglePart(
        session: UploadSessionEntity,
        part: UploadPartEntity,
        raf: RandomAccessFile
    ): SinglePartResult {
        var lastError: Throwable? = null

        repeat(session.maxRetries + 1) { attempt ->
            if (attempt > 0) {
                val delayMs = config.getRetryDelay(attempt - 1)
                MultipartUploadLog.d(this@MultipartUploadManager, "Retrying part ${part.partNumber} after ${delayMs}ms (attempt $attempt)")
                delay(delayMs)
            }

            try {
                // Update part status to uploading
                dao.updatePartStatus(
                    part.partId,
                    PartUploadStatus.UPLOADING,
                    null,
                    0,
                    System.currentTimeMillis()
                )

                // Get presigned URL for this part
                val presignResponse = apiService.getPresignedUrlForPart(
                    url = session.presignPartUrl,
                    uploadId = session.uploadId,
                    filePath = session.remoteFilePath,
                    partNumber = part.partNumber
                )

                val presignData = when (presignResponse) {
                    is ApiResponse.Success -> presignResponse.data.data
                        ?: return SinglePartResult.Failed("No data in presign response", null, false, false)
                    is ApiResponse.Failure.Error -> {
                        lastError = IllegalStateException(presignResponse.message())
                        return@repeat // Retry
                    }
                    is ApiResponse.Failure.Exception -> {
                        lastError = presignResponse.throwable
                        if (!isRecoverableError(presignResponse.throwable)) {
                            return SinglePartResult.Failed(presignResponse.throwable.message ?: "Presign failed", presignResponse.throwable, false, false)
                        }
                        return@repeat // Retry
                    }
                }

                // Read part data
                val buffer = ByteArray(part.partSize.toInt())
                synchronized(raf) {
                    raf.seek(part.startByte)
                    raf.readFully(buffer)
                }

                val contentType = session.contentType.toMediaTypeOrNull()
                val requestBody: RequestBody = buffer.toRequestBody(contentType)

                // Upload to S3
                val uploadResult = apiService.uploadPartToS3(
                    presignedUrl = presignData.presignedUrl,
                    headers = presignData.headers,
                    body = requestBody
                )

                when (uploadResult) {
                    is S3PartUploadResult.Success -> {
                        // Update part as uploaded with ETag from S3
                        dao.updatePartStatus(
                            part.partId,
                            PartUploadStatus.UPLOADED,
                            uploadResult.etag,
                            part.partSize,
                            System.currentTimeMillis()
                        )

                        MultipartUploadLog.d(this@MultipartUploadManager, "Uploaded part ${part.partNumber}/${session.totalParts}")
                        return SinglePartResult.Success
                    }
                    is S3PartUploadResult.HttpError -> {
                        lastError = IllegalStateException("Upload failed: ${uploadResult.message}")
                        return@repeat // Retry
                    }
                    is S3PartUploadResult.Exception -> {
                        lastError = uploadResult.throwable
                        if (!isRecoverableError(uploadResult.throwable)) {
                            return SinglePartResult.Failed(uploadResult.throwable.message ?: "Upload failed", uploadResult.throwable, false, false)
                        }
                        return@repeat // Retry
                    }
                }
            } catch (e: CancellationException) {
                throw e // Don't catch cancellation
            } catch (e: Exception) {
                lastError = e
                if (!isRecoverableError(e)) {
                    return SinglePartResult.Failed(e.message ?: "Upload failed", e, false, false)
                }
            }
        }

        // All retries exhausted
        dao.updatePartStatus(part.partId, PartUploadStatus.FAILED, null, 0, System.currentTimeMillis())
        return SinglePartResult.Failed(
            lastError?.message ?: "Max retries exceeded",
            lastError,
            shouldRetry = false,
            isRecoverable = true
        )
    }

    private suspend fun completeUpload(session: UploadSessionEntity): MultipartUploadResult {
        dao.updateSessionStatus(session.sessionId, UploadSessionStatus.COMPLETING, System.currentTimeMillis())

        val uploadedParts = dao.getUploadedParts(session.sessionId)
        val completedParts = uploadedParts.mapNotNull { part ->
            part.etag?.let { etag ->
                CompletedPart(partNumber = part.partNumber, etag = etag)
            }
        }

        if (completedParts.size != session.totalParts) {
            dao.updateSessionStatusWithError(
                session.sessionId,
                UploadSessionStatus.FAILED,
                "Missing ETags for some parts",
                System.currentTimeMillis()
            )
            return MultipartUploadResult.Error(session.sessionId, "Missing ETags for some parts", isRecoverable = true)
        }

        val response = apiService.completeMultipartUpload(
            url = session.completeUrl,
            uploadId = session.uploadId,
            filePath = session.remoteFilePath,
            parts = completedParts
        )

        return when (response) {
            is ApiResponse.Success -> {
                val data = response.data.data
                dao.updateSessionStatus(session.sessionId, UploadSessionStatus.COMPLETED, System.currentTimeMillis())
                MultipartUploadLog.d(this@MultipartUploadManager, "Completed upload: ${session.sessionId}")
                MultipartUploadResult.Success(
                    sessionId = session.sessionId,
                    filePath = data?.filePath ?: session.remoteFilePath,
                    location = data?.location
                )
            }
            is ApiResponse.Failure.Error -> {
                val message = response.message()
                dao.updateSessionStatusWithError(session.sessionId, UploadSessionStatus.FAILED, message, System.currentTimeMillis())
                MultipartUploadResult.Error(session.sessionId, "Failed to complete: $message", isRecoverable = true)
            }
            is ApiResponse.Failure.Exception -> {
                dao.updateSessionStatusWithError(session.sessionId, UploadSessionStatus.FAILED, response.throwable.message, System.currentTimeMillis())
                MultipartUploadResult.Error(session.sessionId, response.throwable.message ?: "Failed to complete", response.throwable, isRecoverable = true)
            }
        }
    }

    // ==================== Helper Methods ====================

    private fun createPartEntities(sessionId: String, fileSize: Long, chunkSize: Long, timestamp: Long): List<UploadPartEntity> {
        val parts = mutableListOf<UploadPartEntity>()
        var partNumber = 1
        var offset = 0L

        while (offset < fileSize) {
            val endByte = minOf(offset + chunkSize, fileSize)
            val partSize = endByte - offset

            parts.add(
                UploadPartEntity(
                    partId = "${sessionId}_$partNumber",
                    sessionId = sessionId,
                    partNumber = partNumber,
                    startByte = offset,
                    endByte = endByte,
                    partSize = partSize,
                    status = PartUploadStatus.PENDING,
                    updatedAt = timestamp
                )
            )

            partNumber++
            offset = endByte
        }

        return parts
    }

    private fun getMimeType(file: File): String? {
        val extension = file.extension
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun isRecoverableError(throwable: Throwable): Boolean {
        return throwable is SocketTimeoutException ||
                throwable is SocketException ||
                throwable is UnknownHostException ||
                throwable is ConnectException
    }

    private fun SessionWithParts.toProgress(): MultipartUploadProgress {
        val uploadedParts = parts.count { it.status == PartUploadStatus.UPLOADED }
        val uploadedBytes = parts.filter { it.status == PartUploadStatus.UPLOADED }.sumOf { it.partSize }
        val currentPart = parts.find { it.status == PartUploadStatus.UPLOADING }
        val overallProgress = if (session.totalFileSize > 0) {
            (uploadedBytes.toFloat() / session.totalFileSize.toFloat()) * 100f
        } else 0f

        return MultipartUploadProgress(
            sessionId = session.sessionId,
            fileName = session.fileName,
            totalBytes = session.totalFileSize,
            uploadedBytes = uploadedBytes,
            totalParts = session.totalParts,
            uploadedParts = uploadedParts,
            currentPartNumber = currentPart?.partNumber,
            currentPartProgress = 0f, // Would need real-time tracking for this
            overallProgress = overallProgress,
            status = session.status,
            errorMessage = session.errorMessage
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: MultipartUploadManager? = null

        /**
         * Gets the singleton instance of MultipartUploadManager.
         *
         * This method also syncs the logging configuration from S3Uploader, so you only need
         * to call [S3Uploader.initS3Uploader] once and both modules will use the same logger.
         *
         * @param context Application context
         * @param config Upload configuration (only used on first call)
         * @return The manager instance
         */
        fun getInstance(
            context: Context,
            config: MultipartUploadConfig = MultipartUploadConfig.DEFAULT
        ): MultipartUploadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MultipartUploadManager(context.applicationContext, config).also {
                    INSTANCE = it
                    // Sync logger configuration from S3Uploader
                    syncLoggerConfig()
                }
            }
        }

        /**
         * Syncs the logging configuration from S3Uploader.
         * Called automatically when getInstance creates the manager, but can also be called
         * manually if S3Uploader is initialized after MultipartUploadManager.
         */
        fun syncLoggerConfig() {
            val customLogger = S3Uploader.getCustomLogger()
            if (customLogger != null) {
                MultipartUploadLog.updateLogger(customLogger, S3Uploader.loggingLevel)
            } else {
                MultipartUploadLog.updateLogger(MultipartUploadLogger, S3Uploader.loggingLevel)
            }
        }

        /**
         * Clears the singleton instance. Used for testing.
         */
        internal fun clearInstance() {
            INSTANCE = null
        }
    }
}
