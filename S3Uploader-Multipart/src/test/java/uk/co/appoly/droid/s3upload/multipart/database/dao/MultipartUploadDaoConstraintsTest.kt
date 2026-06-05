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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Further in-memory Room DAO tests (Robolectric) covering the constraint-violation flow,
 * observe-style Flow queries, and the remaining session/part mutations.
 */
@RunWith(AndroidJUnit4::class)
class MultipartUploadDaoConstraintsTest {

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

	private fun session(id: String, status: UploadSessionStatus = UploadSessionStatus.IN_PROGRESS) =
		UploadSessionEntity(
			sessionId = id, uploadId = "u_$id", localFilePath = "/f/$id", remoteFilePath = "r/$id",
			fileName = "$id.bin", contentType = "application/octet-stream", totalFileSize = 100,
			chunkSize = 50, totalParts = 2, status = status, initiateUrl = "i", presignPartUrl = "p",
			completeUrl = "c", abortUrl = "a", createdAt = 1_000, updatedAt = 1_000
		)

	private fun part(sessionId: String, number: Int, status: PartUploadStatus = PartUploadStatus.PENDING) =
		UploadPartEntity(
			partId = "${sessionId}_$number", sessionId = sessionId, partNumber = number,
			startByte = 0, endByte = 50, partSize = 50, status = status, updatedAt = 1_000
		)

	@Test
	fun `updateSessionConstraints stores the constraints json`() = runTest {
		dao.insertSession(session("s1"))
		dao.updateSessionConstraints("s1", """{"networkType":"UNMETERED"}""", updatedAt = 2_000)
		val s = dao.getSession("s1")!!
		assertEquals("""{"networkType":"UNMETERED"}""", s.constraintsJson)
		assertEquals(2_000, s.updatedAt)
	}

	@Test
	fun `constraint violation is recorded then cleared`() = runTest {
		dao.insertSession(session("s1"))
		dao.updateSessionForConstraintViolation(
			sessionId = "s1", pauseReason = "WiFi lost", stopReasonCode = 7,
			violatedAt = 5_000, updatedAt = 5_000
		)
		val violated = dao.getSession("s1")!!
		assertEquals(UploadSessionStatus.PAUSED_CONSTRAINT_VIOLATION, violated.status)
		assertEquals("WiFi lost", violated.pauseReason)
		assertEquals(7, violated.stopReasonCode)
		assertEquals(listOf("s1"), dao.getConstraintViolatedSessions().map { it.sessionId })

		dao.clearConstraintViolation("s1", updatedAt = 6_000)
		val cleared = dao.getSession("s1")!!
		assertNull(cleared.pauseReason)
		assertNull(cleared.stopReasonCode)
		assertNull(cleared.constraintViolatedAt)
	}

	@Test
	fun `getActiveSessions and getSessionsByStatus filter correctly`() = runTest {
		dao.insertSession(session("a", UploadSessionStatus.IN_PROGRESS))
		dao.insertSession(session("b", UploadSessionStatus.PAUSED))
		dao.insertSession(session("c", UploadSessionStatus.COMPLETED))
		assertEquals(setOf("a", "b"), dao.getActiveSessions().map { it.sessionId }.toSet())
		assertEquals(listOf("c"), dao.getSessionsByStatus(UploadSessionStatus.COMPLETED).map { it.sessionId })
	}

	@Test
	fun `updateSession and deleteSession entity operations`() = runTest {
		dao.insertSession(session("s1"))
		dao.updateSession(dao.getSession("s1")!!.copy(fileName = "renamed.bin"))
		assertEquals("renamed.bin", dao.getSession("s1")?.fileName)
		dao.deleteSession(dao.getSession("s1")!!)
		assertNull(dao.getSession("s1"))
	}

	@Test
	fun `single part insert, update and getNextPendingPart`() = runTest {
		dao.insertSession(session("s1"))
		dao.insertPart(part("s1", 1))
		assertEquals(1, dao.getNextPendingPart("s1")?.partNumber)

		dao.updatePart(dao.getPartsForSession("s1").single().copy(status = PartUploadStatus.UPLOADED, etag = "etag1"))
		assertEquals(PartUploadStatus.UPLOADED, dao.getPartsForSession("s1").single().status)
		assertNull(dao.getNextPendingPart("s1"))
	}

	@Test
	fun `resetUploadingParts returns stuck uploading parts to pending`() = runTest {
		dao.insertSession(session("s1"))
		dao.insertParts(listOf(part("s1", 1, PartUploadStatus.UPLOADING), part("s1", 2, PartUploadStatus.UPLOADED)))
		dao.resetUploadingParts("s1")
		assertEquals(PartUploadStatus.PENDING, dao.getPartsByStatus("s1", PartUploadStatus.PENDING).single().status)
		// The UPLOADED part is untouched.
		assertEquals(1, dao.getUploadedPartsCount("s1"))
	}

	@Test
	fun `observe flows emit current rows`() = runTest {
		dao.insertSession(session("s1", UploadSessionStatus.IN_PROGRESS))
		dao.insertParts(listOf(part("s1", 1)))

		assertEquals(listOf("s1"), dao.observeAllSessions().first().map { it.sessionId })
		assertEquals(
			listOf("s1"),
			dao.observeSessionsByStatus(listOf(UploadSessionStatus.IN_PROGRESS)).first().map { it.sessionId }
		)
		assertEquals(1, dao.observePartsForSession("s1").first().size)
		assertNotNull(dao.observeSessionWithParts("s1").first())
		assertEquals(1, dao.observeActiveSessionsWithParts().first().size)
	}
}
