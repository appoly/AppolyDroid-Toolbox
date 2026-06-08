package uk.co.appoly.droid.s3upload.multipart.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import uk.co.appoly.droid.s3upload.multipart.interfaces.BeforeUploadResult
import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Robolectric tests for [MultipartUploadWorker.doWork] validation/failure branches, built with
 * [TestListenableWorkerBuilder]. The full upload pipeline (network) is out of scope.
 */
@RunWith(AndroidJUnit4::class)
class MultipartUploadWorkerTest {

	private lateinit var context: Context

	@Before
	fun setUp() {
		context = ApplicationProvider.getApplicationContext()
		S3Uploader.initS3Uploader(HeaderProvider { emptyMap() }, LoggingLevel.NONE)
		MultipartUploadManager.clearInstance()
		S3UploaderDatabase.clearInstance()
	}

	@After
	fun tearDown() {
		MultipartUploadManager.clearInstance()
		S3UploaderDatabase.clearInstance()
	}

	@Test
	fun `doWork fails when no session id or file path is provided`() = runTest {
		val worker = TestListenableWorkerBuilder<MultipartUploadWorker>(context).build()
		val result = worker.doWork()
		assertTrue(result is ListenableWorker.Result.Failure)
	}

	@Test
	fun `doWork resume fails for an unknown session`() = runTest {
		val worker = TestListenableWorkerBuilder<MultipartUploadWorker>(context)
			.setInputData(MultipartUploadWorker.createResumeInputData("does-not-exist"))
			.build()
		val result = worker.doWork()
		// Unknown session can't be resumed -> failure (not success).
		assertTrue(result !is ListenableWorker.Result.Success)
	}

	@Test
	fun `doWork fails when the file does not exist`() = runTest {
		val worker = TestListenableWorkerBuilder<MultipartUploadWorker>(context)
			.setInputData(workDataOf(MultipartUploadWorker.KEY_FILE_PATH to "/no/such/file.bin"))
			.build()
		val result = worker.doWork()
		assertTrue(result is ListenableWorker.Result.Failure)
	}

	@Test
	fun `doWork fails when a required api url is missing`() = runTest {
		// File exists and onBeforeUpload (default) continues, but no initiate URL is provided,
		// so the apiUrls construction short-circuits to failure before any S3 interaction.
		val file = File.createTempFile("wm-upload", ".bin").apply { writeText("x") }
		val worker = TestListenableWorkerBuilder<MultipartUploadWorker>(context)
			.setInputData(workDataOf(MultipartUploadWorker.KEY_FILE_PATH to file.absolutePath))
			.build()
		val result = worker.doWork()
		assertTrue(result is ListenableWorker.Result.Failure)
		file.delete()
	}

	@Test
	fun `doWork aborts when onBeforeUpload returns Abort`() = runTest {
		// Seed the singleton with a config whose lifecycle callback aborts; the worker's internal
		// getInstance(context) returns this same instance.
		MultipartUploadManager.getInstance(
			context,
			MultipartUploadConfig(
				lifecycleCallbacks = object : UploadLifecycleCallbacks {
					override suspend fun onBeforeUpload(filePath: String): BeforeUploadResult =
						BeforeUploadResult.Abort("test abort")
				}
			)
		)
		val file = File.createTempFile("wm-upload", ".bin").apply { writeText("x") }
		val worker = TestListenableWorkerBuilder<MultipartUploadWorker>(context)
			.setInputData(
				MultipartUploadWorker.createInputData(
					file = file,
					apiUrls = uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls(
						initiateUrl = "https://example.com/initiate",
						presignPartUrl = "https://example.com/presign",
						completeUrl = "https://example.com/complete",
						abortUrl = "https://example.com/abort"
					)
				)
			)
			.build()
		val result = worker.doWork()
		assertTrue("Abort should fail the work", result is ListenableWorker.Result.Failure)
		file.delete()
	}
}
