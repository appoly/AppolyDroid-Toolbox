package uk.co.appoly.droid.s3upload.multipart.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Data class representing an upload session with all its parts.
 * Used for Room @Transaction queries.
 */
data class SessionWithParts(
    @Embedded
    val session: UploadSessionEntity,

    @Relation(
        parentColumn = "session_id",
        entityColumn = "session_id"
    )
    val parts: List<UploadPartEntity>
)
