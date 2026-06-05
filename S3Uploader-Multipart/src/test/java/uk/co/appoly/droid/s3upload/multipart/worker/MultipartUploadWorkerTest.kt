package uk.co.appoly.droid.s3upload.multipart.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
}
