package uk.co.appoly.droid.s3upload.multipart.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import uk.co.appoly.droid.s3upload.multipart.database.entity.PartUploadStatus
import uk.co.appoly.droid.s3upload.multipart.database.entity.SessionWithParts
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadPartEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus

/**
 * Data Access Object for multipart upload operations.
 */
@Dao
interface MultipartUploadDao {

	// ==================== Session Operations ====================

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertSession(session: UploadSessionEntity)

	@Update
	suspend fun updateSession(session: UploadSessionEntity)

	@Delete
	suspend fun deleteSession(session: UploadSessionEntity)

	@Query("DELETE FROM multipart_upload_sessions WHERE session_id = :sessionId")
	suspend fun deleteSessionById(sessionId: String)

	@Query("SELECT * FROM multipart_upload_sessions WHERE session_id = :sessionId")
	suspend fun getSession(sessionId: String): UploadSessionEntity?

	@Query("SELECT * FROM multipart_upload_sessions WHERE session_id = :sessionId")
	fun observeSession(sessionId: String): Flow<UploadSessionEntity?>

	@Query("SELECT * FROM multipart_upload_sessions WHERE status IN (:statuses) ORDER BY created_at DESC")
	fun observeSessionsByStatus(statuses: List<UploadSessionStatus>): Flow<List<UploadSessionEntity>>

	@Query("SELECT * FROM multipart_upload_sessions ORDER BY created_at DESC")
	fun observeAllSessions(): Flow<List<UploadSessionEntity>>

	@Query(
		"""
        SELECT * FROM multipart_upload_sessions
        WHERE local_file_path = :filePath
        AND status NOT IN ('COMPLETED', 'ABORTED', 'FAILED')
    """
	)
	suspend fun findActiveSessionForFile(filePath: String): UploadSessionEntity?

	@Query(
		"""
        SELECT * FROM multipart_upload_sessions
        WHERE status IN ('PENDING', 'IN_PROGRESS', 'PAUSED', 'PAUSED_CONSTRAINT_VIOLATION')
    """
	)
	suspend fun getRecoverableSessions(): List<UploadSessionEntity>

	/**
	 * Gets all active sessions (not completed, aborted, or failed).
	 * Used for updating constraints across all active uploads.
	 */
	@Query(
		"""
        SELECT * FROM multipart_upload_sessions
        WHERE status IN ('PENDING', 'IN_PROGRESS', 'PAUSED', 'PAUSED_CONSTRAINT_VIOLATION')
    """
	)
	suspend fun getActiveSessions(): List<UploadSessionEntity>

	/**
	 * Gets all sessions that were paused due to constraint violations.
	 * These are candidates for auto-resume when constraints are satisfied.
	 */
	@Query(
		"""
        SELECT * FROM multipart_upload_sessions
        WHERE status = 'PAUSED_CONSTRAINT_VIOLATION'
    """
	)
	suspend fun getConstraintViolatedSessions(): List<UploadSessionEntity>

	/**
	 * Gets sessions by specific status.
	 */
	@Query("SELECT * FROM multipart_upload_sessions WHERE status = :status")
	suspend fun getSessionsByStatus(status: UploadSessionStatus): List<UploadSessionEntity>

	@Query("UPDATE multipart_upload_sessions SET status = :status, updated_at = :updatedAt WHERE session_id = :sessionId")
	suspend fun updateSessionStatus(sessionId: String, status: UploadSessionStatus, updatedAt: Long)

	@Query(
		"""
        UPDATE multipart_upload_sessions
        SET status = :status, error_message = :errorMessage, updated_at = :updatedAt
        WHERE session_id = :sessionId
    """
	)
	suspend fun updateSessionStatusWithError(
		sessionId: String,
		status: UploadSessionStatus,
		errorMessage: String?,
		updatedAt: Long
	)

	/**
	 * Updates session constraints.
	 * Used when dynamically changing constraints for active uploads.
	 */
	@Query(
		"""
        UPDATE multipart_upload_sessions
        SET constraints_json = :constraintsJson, updated_at = :updatedAt
        WHERE session_id = :sessionId
    """
	)
	suspend fun updateSessionConstraints(
		sessionId: String,
		constraintsJson: String,
		updatedAt: Long = System.currentTimeMillis()
	)

	/**
	 * Updates session status to PAUSED_CONSTRAINT_VIOLATION with reason details.
	 */
	@Query(
		"""
        UPDATE multipart_upload_sessions
        SET status = 'PAUSED_CONSTRAINT_VIOLATION',
            pause_reason = :pauseReason,
            constraint_violated_at = :violatedAt,
            stop_reason_code = :stopReasonCode,
            updated_at = :updatedAt
        WHERE session_id = :sessionId
    """
	)
	suspend fun updateSessionForConstraintViolation(
		sessionId: String,
		pauseReason: String,
		stopReasonCode: Int,
		violatedAt: Long = System.currentTimeMillis(),
		updatedAt: Long = System.currentTimeMillis()
	)

	/**
	 * Clears constraint violation data when resuming.
	 */
	@Query(
		"""
        UPDATE multipart_upload_sessions
        SET pause_reason = NULL,
            constraint_violated_at = NULL,
            stop_reason_code = NULL,
            updated_at = :updatedAt
        WHERE session_id = :sessionId
    """
	)
	suspend fun clearConstraintViolation(
		sessionId: String,
		updatedAt: Long = System.currentTimeMillis()
	)

	@Query(
		"""
        DELETE FROM multipart_upload_sessions
        WHERE status IN ('COMPLETED', 'ABORTED', 'FAILED')
        AND updated_at < :olderThan
    """
	)
	suspend fun deleteOldCompletedSessions(olderThan: Long): Int

	// ==================== Part Operations ====================

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertParts(parts: List<UploadPartEntity>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertPart(part: UploadPartEntity)

	@Update
	suspend fun updatePart(part: UploadPartEntity)

	@Query("SELECT * FROM multipart_upload_parts WHERE session_id = :sessionId ORDER BY part_number")
	suspend fun getPartsForSession(sessionId: String): List<UploadPartEntity>

	@Query("SELECT * FROM multipart_upload_parts WHERE session_id = :sessionId ORDER BY part_number")
	fun observePartsForSession(sessionId: String): Flow<List<UploadPartEntity>>

	@Query("SELECT * FROM multipart_upload_parts WHERE session_id = :sessionId AND status = :status ORDER BY part_number")
	suspend fun getPartsByStatus(sessionId: String, status: PartUploadStatus): List<UploadPartEntity>

	@Query("SELECT * FROM multipart_upload_parts WHERE session_id = :sessionId AND status = 'UPLOADED' ORDER BY part_number")
	suspend fun getUploadedParts(sessionId: String): List<UploadPartEntity>

	@Query("SELECT COUNT(*) FROM multipart_upload_parts WHERE session_id = :sessionId AND status = 'UPLOADED'")
	suspend fun getUploadedPartsCount(sessionId: String): Int

	@Query("SELECT * FROM multipart_upload_parts WHERE session_id = :sessionId AND status IN ('PENDING', 'FAILED') ORDER BY part_number LIMIT 1")
	suspend fun getNextPendingPart(sessionId: String): UploadPartEntity?

	@Query("SELECT COALESCE(SUM(uploaded_bytes), 0) FROM multipart_upload_parts WHERE session_id = :sessionId")
	suspend fun getTotalUploadedBytes(sessionId: String): Long

	@Query(
		"""
        UPDATE multipart_upload_parts
        SET status = :status, etag = :etag, uploaded_bytes = :uploadedBytes, updated_at = :updatedAt
        WHERE part_id = :partId
    """
	)
	suspend fun updatePartStatus(
		partId: String,
		status: PartUploadStatus,
		etag: String?,
		uploadedBytes: Long,
		updatedAt: Long
	)

	@Query("UPDATE multipart_upload_parts SET status = 'PENDING', retry_count = 0 WHERE session_id = :sessionId AND status = 'FAILED'")
	suspend fun resetFailedParts(sessionId: String)

	@Query("UPDATE multipart_upload_parts SET status = 'PENDING' WHERE session_id = :sessionId AND status = 'UPLOADING'")
	suspend fun resetUploadingParts(sessionId: String)

	// ==================== Combined Queries ====================

	@Transaction
	@Query("SELECT * FROM multipart_upload_sessions WHERE session_id = :sessionId")
	suspend fun getSessionWithParts(sessionId: String): SessionWithParts?

	@Transaction
	@Query("SELECT * FROM multipart_upload_sessions WHERE session_id = :sessionId")
	fun observeSessionWithParts(sessionId: String): Flow<SessionWithParts?>

	@Transaction
	@Query("SELECT * FROM multipart_upload_sessions WHERE status NOT IN ('COMPLETED', 'ABORTED') ORDER BY created_at DESC")
	fun observeActiveSessionsWithParts(): Flow<List<SessionWithParts>>
}
