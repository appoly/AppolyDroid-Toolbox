package uk.co.appoly.droid.s3upload.multipart

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import uk.co.appoly.droid.s3upload.multipart.database.dao.MultipartUploadDao
import uk.co.appoly.droid.s3upload.multipart.database.entity.PartUploadStatus
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadPartEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Robolectric tests for [MultipartUploadManager]'s lifecycle and query API. Sessions are
 * pre-seeded directly into the manager's singleton Room database; WorkManager runs via the
 * test harness. The full upload pipeline (network part uploads) is out of scope here.
 */
@RunWith(AndroidJUnit4::class)
class MultipartUploadManagerTest {

	private lateinit var context: Context
	private lateinit var manager: MultipartUploadManager
	private lateinit var dao: MultipartUploadDao
	private val tempFiles = mutableListOf<File>()

	@Before
	fun setUp() {
		context = ApplicationProvider.getApplicationContext()
		// Replace scheduled workers with a no-op so enqueuing (resume/recover) doesn't run the
		// real upload worker against the fake URLs and hang. The real worker is tested separately.
		val config = Configuration.Builder()
			.setExecutor(SynchronousExecutor())
			.setWorkerFactory(object : WorkerFactory() {
				override fun createWorker(
					appContext: Context,
					workerClassName: String,
					workerParameters: WorkerParameters
				): ListenableWorker = object : Worker(appContext, workerParameters) {
					override fun doWork(): Result = Result.success()
				}
			})
			.build()
		WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
		S3Uploader.initS3Uploader(HeaderProvider { emptyMap() }, LoggingLevel.NONE)
		// Robolectric keeps Java statics between tests — reset the singletons.
		MultipartUploadManager.clearInstance()
		S3UploaderDatabase.clearInstance()
		manager = MultipartUploadManager.getInstance(context)
		dao = S3UploaderDatabase.getInstance(context).multipartUploadDao()
	}

	@After
	fun tearDown() {
		MultipartUploadManager.clearInstance()
		S3UploaderDatabase.clearInstance()
		tempFiles.forEach { it.delete() }
	}

	private fun realFile(): File =
		File.createTempFile("mp-upload", ".bin").also { it.writeText("data"); tempFiles += it }

	private suspend fun seedSession(
		id: String,
		status: UploadSessionStatus,
		filePath: String = realFile().absolutePath,
		parts: List<UploadPartEntity> = emptyList()
	) {
		dao.insertSession(
			UploadSessionEntity(
				sessionId = id, uploadId = "u_$id", localFilePath = filePath, remoteFilePath = "r/$id",
				fileName = "$id.bin", contentType = "application/octet-stream", totalFileSize = 100,
				chunkSize = 50, totalParts = 2, status = status, initiateUrl = "http://x/i",
				presignPartUrl = "http://x/p", completeUrl = "http://x/c", abortUrl = "http://x/a",
				createdAt = 1_000, updatedAt = 1_000
			)
		)
		if (parts.isNotEmpty()) dao.insertParts(parts)
	}

	private fun part(sessionId: String, number: Int, status: PartUploadStatus) = UploadPartEntity(
		partId = "${sessionId}_$number", sessionId = sessionId, partNumber = number,
		startByte = 0, endByte = 50, partSize = 50, status = status, updatedAt = 1_000
	)

	@Test
	fun `pauseUpload pauses an in-progress session and resets uploading parts`() = runTest {
		seedSession("s1", UploadSessionStatus.IN_PROGRESS, parts = listOf(part("s1", 1, PartUploadStatus.UPLOADING)))
		val result = manager.pauseUpload("s1")
		assertTrue(result.isSuccess)
		assertEquals(UploadSessionStatus.PAUSED, dao.getSession("s1")?.status)
		assertEquals(PartUploadStatus.PENDING, dao.getPartsForSession("s1").single().status)
	}

	@Test
	fun `pauseUpload fails for a completed session`() = runTest {
		seedSession("done", UploadSessionStatus.COMPLETED)
		assertTrue(manager.pauseUpload("done").isFailure)
	}

	@Test
	fun `pauseUpload fails for an unknown session`() = runTest {
		assertTrue(manager.pauseUpload("nope").isFailure)
	}

	@Test
	fun `resumeUpload succeeds for a paused session whose file exists`() = runTest {
		seedSession("s1", UploadSessionStatus.PAUSED)
		assertTrue(manager.resumeUpload("s1").isSuccess)
	}

	@Test
	fun `resumeUpload fails and marks failed when the source file is gone`() = runTest {
		seedSession("s1", UploadSessionStatus.PAUSED, filePath = "/no/such/file.bin")
		assertTrue(manager.resumeUpload("s1").isFailure)
		assertEquals(UploadSessionStatus.FAILED, dao.getSession("s1")?.status)
	}

	@Test
	fun `cancelUpload aborts an active session`() = runTest {
		seedSession("s1", UploadSessionStatus.IN_PROGRESS)
		// The S3 abort call has no server and fails silently; status still moves to ABORTED.
		assertTrue(manager.cancelUpload("s1").isSuccess)
		assertEquals(UploadSessionStatus.ABORTED, dao.getSession("s1")?.status)
	}

	@Test
	fun `cancelUpload fails for an already-completed session`() = runTest {
		seedSession("done", UploadSessionStatus.COMPLETED)
		assertTrue(manager.cancelUpload("done").isFailure)
	}

	@Test
	fun `getSession returns the stored session or null`() = runTest {
		seedSession("s1", UploadSessionStatus.PENDING)
		assertEquals("u_s1", manager.getSession("s1")?.uploadId)
		assertNull(manager.getSession("missing"))
	}

	@Test
	fun `observeProgress emits progress for a seeded session`() = runTest {
		seedSession(
			"s1", UploadSessionStatus.IN_PROGRESS,
			parts = listOf(part("s1", 1, PartUploadStatus.UPLOADED), part("s1", 2, PartUploadStatus.PENDING))
		)
		val progress = manager.observeProgress("s1").first()
		assertNotNull(progress)
		assertEquals("s1", progress?.sessionId)
		assertEquals(2, progress?.totalParts)
	}

	@Test
	fun `cleanupOldSessions deletes terminal sessions past the cutoff`() = runTest {
		seedSession("old", UploadSessionStatus.COMPLETED)
		// Force the updated_at into the distant past.
		dao.updateSession(dao.getSession("old")!!.copy(updatedAt = 1L))
		val deleted = manager.cleanupOldSessions(olderThanMs = 0L)
		assertEquals(1, deleted)
		assertNull(manager.getSession("old"))
	}

	@Test
	fun `recoverInterruptedUploads moves interrupted sessions toward resume`() = runTest {
		seedSession("s1", UploadSessionStatus.IN_PROGRESS)
		val recovered = manager.recoverInterruptedUploads()
		assertTrue(recovered.contains("s1"))
	}
}
