package uk.co.appoly.droid.s3upload.multipart.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Robolectric tests for [S3UploadWorkManager] scheduling/cancellation. A no-op WorkerFactory is
 * installed so enqueued work doesn't run the real upload worker.
 */
@RunWith(AndroidJUnit4::class)
class S3UploadWorkManagerTest {

	private lateinit var context: Context

	@Before
	fun setUp() {
		context = ApplicationProvider.getApplicationContext()
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
	}

	private fun urls() = MultipartApiUrls(
		initiateUrl = "http://example/initiate",
		presignPartUrl = "http://example/presign",
		completeUrl = "http://example/complete",
		abortUrl = "http://example/abort"
	)

	private fun tempFile() = File.createTempFile("wm-upload", ".bin").apply { writeText("data") }

	@Test
	fun `scheduleUpload enqueues unique work`() {
		val name = S3UploadWorkManager.scheduleUpload(context, tempFile(), urls(), UploadConstraints.DEFAULT)
		val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
		assertTrue(infos.isNotEmpty())
	}

	@Test
	fun `scheduleResume enqueues and cancelBySessionId cancels it`() {
		val name = S3UploadWorkManager.scheduleResume(
			context, sessionId = "s1", constraints = UploadConstraints.DEFAULT, initialDelayMs = 10_000
		)
		assertTrue(WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get().isNotEmpty())

		S3UploadWorkManager.cancelBySessionId(context, "s1")
		val after = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
		assertEquals(WorkInfo.State.CANCELLED, after.first().state)
	}

	@Test
	fun `cancelByWorkName cancels delayed work`() {
		val name = S3UploadWorkManager.scheduleResume(
			context, sessionId = "s2", constraints = UploadConstraints.DEFAULT, initialDelayMs = 10_000
		)
		S3UploadWorkManager.cancelByWorkName(context, name)
		val after = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()
		assertEquals(WorkInfo.State.CANCELLED, after.first().state)
	}

	@Test
	fun `cancelAllUploads cancels by tag`() {
		S3UploadWorkManager.scheduleResume(
			context, sessionId = "s3", constraints = UploadConstraints.DEFAULT, initialDelayMs = 10_000
		)
		S3UploadWorkManager.cancelAllUploads(context)
		val infos = WorkManager.getInstance(context)
			.getWorkInfosByTag(S3UploadWorkManager.TAG_MULTIPART_UPLOAD).get()
		assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
	}
}
