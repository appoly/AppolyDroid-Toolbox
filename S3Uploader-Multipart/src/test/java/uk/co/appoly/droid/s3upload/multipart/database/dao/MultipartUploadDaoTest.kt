package uk.co.appoly.droid.s3upload.multipart.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import uk.co.appoly.droid.s3upload.multipart.database.entity.PartUploadStatus
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadPartEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-memory Room DAO tests (Robolectric) for [MultipartUploadDao]. These exercise the
 * Room-generated DAO implementation and type converters end to end.
 */
@RunWith(AndroidJUnit4::class)
class MultipartUploadDaoTest {

	private lateinit var db: S3UploaderDatabase
	private lateinit var dao: MultipartUploadDao

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		db = Room.inMemoryDatabaseBuilder(context, S3UploaderDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		dao = db.multipartUploadDao()
	}

	@After
	fun tearDown() = db.close()

	private fun session(
		id: String,
		status: UploadSessionStatus = UploadSessionStatus.PENDING,
		filePath: String = "/files/$id"
	) = UploadSessionEntity(
		sessionId = id,
		uploadId = "upload_$id",
		localFilePath = filePath,
		remoteFilePath = "remote/$id",
		fileName = "$id.bin",
		contentType = "application/octet-stream",
		totalFileSize = 100,
		chunkSize = 50,
		totalParts = 2,
		status = status,
		initiateUrl = "i",
		presignPartUrl = "p",
		completeUrl = "c",
		abortUrl = "a",
		createdAt = 1_000,
		updatedAt = 1_000
	)

	private fun part(
		sessionId: String,
		number: Int,
		status: PartUploadStatus = PartUploadStatus.PENDING,
		uploadedBytes: Long = 0
	) = UploadPartEntity(
		partId = "${sessionId}_$number",
		sessionId = sessionId,
		partNumber = number,
		startByte = 0,
		endByte = 50,
		partSize = 50,
		status = status,
		uploadedBytes = uploadedBytes,
		updatedAt = 1_000
	)

	@Test
	fun `insert and get session`() = runTest {
		dao.insertSession(session("s1"))
		assertEquals("upload_s1", dao.getSession("s1")?.uploadId)
		assertNull(dao.getSession("missing"))
	}

	@Test
	fun `updateSessionStatus changes status and timestamp`() = runTest {
		dao.insertSession(session("s1"))
		dao.updateSessionStatus("s1", UploadSessionStatus.IN_PROGRESS, updatedAt = 2_000)
		val updated = dao.getSession("s1")!!
		assertEquals(UploadSessionStatus.IN_PROGRESS, updated.status)
		assertEquals(2_000, updated.updatedAt)
	}

	@Test
	fun `updateSessionStatusWithError stores the error message`() = runTest {
		dao.insertSession(session("s1"))
		dao.updateSessionStatusWithError("s1", UploadSessionStatus.FAILED, "network down", 2_000)
		val updated = dao.getSession("s1")!!
		assertEquals(UploadSessionStatus.FAILED, updated.status)
		assertEquals("network down", updated.errorMessage)
	}

	@Test
	fun `findActiveSessionForFile ignores terminal statuses`() = runTest {
		dao.insertSession(session("done", UploadSessionStatus.COMPLETED, "/a"))
		assertNull(dao.findActiveSessionForFile("/a"))

		dao.insertSession(session("live", UploadSessionStatus.IN_PROGRESS, "/b"))
		assertEquals("live", dao.findActiveSessionForFile("/b")?.sessionId)
	}

	@Test
	fun `getRecoverableSessions returns only resumable statuses`() = runTest {
		dao.insertSession(session("pending", UploadSessionStatus.PENDING))
		dao.insertSession(session("completed", UploadSessionStatus.COMPLETED))
		dao.insertSession(session("paused", UploadSessionStatus.PAUSED))
		assertEquals(
			setOf("pending", "paused"),
			dao.getRecoverableSessions().map { it.sessionId }.toSet()
		)
	}

	@Test
	fun `part queries cover counts, totals and status filters`() = runTest {
		dao.insertSession(session("s1"))
		dao.insertParts(
			listOf(
				part("s1", 1, PartUploadStatus.UPLOADED, uploadedBytes = 50),
				part("s1", 2, PartUploadStatus.PENDING)
			)
		)
		assertEquals(2, dao.getPartsForSession("s1").size)
		assertEquals(1, dao.getUploadedPartsCount("s1"))
		assertEquals(50L, dao.getTotalUploadedBytes("s1"))
		assertEquals(listOf(2), dao.getPartsByStatus("s1", PartUploadStatus.PENDING).map { it.partNumber })
		assertEquals(listOf(1), dao.getUploadedParts("s1").map { it.partNumber })
	}

	@Test
	fun `claimNextPendingPart atomically marks the next part uploading`() = runTest {
		dao.insertSession(session("s1"))
		dao.insertParts(listOf(part("s1", 1), part("s1", 2)))

		assertEquals(1, dao.claimNextPendingPart("s1")?.partNumber)
		// Part 1 is now UPLOADING, so the next pending claim returns part 2.
		assertEquals(2, dao.claimNextPendingPart("s1")?.partNumber)
		// Nothing left to claim.
		assertNull(dao.claimNextPendingPart("s1"))
	}

	@Test
	fun `resetFailedParts returns failed parts to pending`() = runTest {
		dao.insertSession(session("s1"))
		dao.insertParts(listOf(part("s1", 1, PartUploadStatus.FAILED)))
		dao.resetFailedParts("s1")
		assertEquals(PartUploadStatus.PENDING, dao.getPartsForSession("s1").single().status)
	}

	@Test
	fun `deleting a session cascades to its parts`() = runTest {
		dao.insertSession(session("s1"))
		dao.insertParts(listOf(part("s1", 1)))
		dao.deleteSessionById("s1")
		assertTrue(dao.getPartsForSession("s1").isEmpty())
		assertNull(dao.getSession("s1"))
	}

	@Test
	fun `getSessionWithParts returns the session and its parts`() = runTest {
		dao.insertSession(session("s1"))
		dao.insertParts(listOf(part("s1", 1), part("s1", 2)))
		val withParts = dao.getSessionWithParts("s1")
		assertEquals("s1", withParts?.session?.sessionId)
		assertEquals(2, withParts?.parts?.size)
	}

	@Test
	fun `observeSession emits the current session`() = runTest {
		dao.insertSession(session("s1"))
		assertEquals("s1", dao.observeSession("s1").first()?.sessionId)
	}

	@Test
	fun `deleteOldCompletedSessions only removes terminal rows older than cutoff`() = runTest {
		dao.insertSession(session("old", UploadSessionStatus.COMPLETED).copy(updatedAt = 100))
		dao.insertSession(session("recent", UploadSessionStatus.COMPLETED).copy(updatedAt = 5_000))
		dao.insertSession(session("active", UploadSessionStatus.IN_PROGRESS).copy(updatedAt = 100))

		val deleted = dao.deleteOldCompletedSessions(olderThan = 1_000)
		assertEquals(1, deleted)
		assertNull(dao.getSession("old"))
		assertEquals("recent", dao.getSession("recent")?.sessionId)
		assertEquals("active", dao.getSession("active")?.sessionId)
	}
}
