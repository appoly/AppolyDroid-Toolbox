package uk.co.appoly.droid.s3upload.multipart.result

import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus

/**
 * Represents the current progress of a multipart upload.
 */
data class MultipartUploadProgress(
    /** The local session ID */
    val sessionId: String,

    /** Original file name */
    val fileName: String,

    /** Total file size in bytes */
    val totalBytes: Long,

    /** Total bytes uploaded so far */
    val uploadedBytes: Long,

    /** Total number of parts */
    val totalParts: Int,

    /** Number of parts that have been successfully uploaded */
    val uploadedParts: Int,

    /** Current part being uploaded (null if none active) */
    val currentPartNumber: Int?,

    /** Progress of current part (0-100) */
    val currentPartProgress: Float,

    /** Overall upload progress (0-100) */
    val overallProgress: Float,

    /** Current status of the upload */
    val status: UploadSessionStatus,

    /** Upload speed in bytes per second (null if not calculated) */
    val bytesPerSecond: Long? = null,

    /** Estimated time remaining in milliseconds (null if not calculated) */
    val estimatedTimeRemainingMs: Long? = null,

    /** Error message if status is FAILED */
    val errorMessage: String? = null
) {
    companion object {
        /**
         * Creates an initial progress object for a new upload.
         */
        fun initial(
            sessionId: String,
            fileName: String,
            totalBytes: Long,
            totalParts: Int
        ): MultipartUploadProgress = MultipartUploadProgress(
            sessionId = sessionId,
            fileName = fileName,
            totalBytes = totalBytes,
            uploadedBytes = 0,
            totalParts = totalParts,
            uploadedParts = 0,
            currentPartNumber = null,
            currentPartProgress = 0f,
            overallProgress = 0f,
            status = UploadSessionStatus.PENDING
        )
    }

    /**
     * Returns a human-readable progress string.
     */
    fun toProgressString(): String {
        val mb = 1024 * 1024
        val uploadedMb = uploadedBytes.toFloat() / mb
        val totalMb = totalBytes.toFloat() / mb
        return String.format("%.1f / %.1f MB (%.1f%%)", uploadedMb, totalMb, overallProgress)
    }

    /**
     * Returns a human-readable parts string.
     */
    fun toPartsString(): String = "$uploadedParts / $totalParts parts"

    /**
     * Returns a human-readable speed string, or null if speed not available.
     */
    fun toSpeedString(): String? {
        return bytesPerSecond?.let { bps ->
            when {
                bps >= 1024 * 1024 -> String.format("%.1f MB/s", bps.toFloat() / (1024 * 1024))
                bps >= 1024 -> String.format("%.1f KB/s", bps.toFloat() / 1024)
                else -> "$bps B/s"
            }
        }
    }

    /**
     * Returns a human-readable ETA string, or null if not available.
     */
    fun toEtaString(): String? {
        return estimatedTimeRemainingMs?.let { ms ->
            val seconds = ms / 1000
            when {
                seconds >= 3600 -> String.format("%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
                seconds >= 60 -> String.format("%d:%02d", seconds / 60, seconds % 60)
                else -> "${seconds}s"
            }
        }
    }
}
