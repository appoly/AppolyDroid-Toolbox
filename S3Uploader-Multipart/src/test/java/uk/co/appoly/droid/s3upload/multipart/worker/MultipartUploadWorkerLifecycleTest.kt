package uk.co.appoly.droid.s3upload.multipart.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestListenableWorkerBuilder
import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import uk.co.appoly.droid.s3upload.multipart.interfaces.BeforeUploadResult
import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the [MultipartUploadWorker] lifecycle hook methods (onBeforeUpload, onUploadResumed,
 * onUploadComplete, onUploadPaused, onProgressUpdate). These delegate to the configured
 * [UploadLifecycleCallbacks]; the tests cover the delegating, the callback-throws (swallowed),
 * and the no-callbacks-configured branches.
 */
@RunWith(AndroidJUnit4::class)
class MultipartUploadWorkerLifecycleTest {

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

	private fun worker() = TestListenableWorkerBuilder<MultipartUploadWorker>(context).build()

	private fun progress() = MultipartUploadProgress(
		sessionId = "s1", fileName = "f", totalBytes = 10, uploadedBytes = 5,
		totalParts = 2, uploadedParts = 1, currentPartNumber = 1,
		currentPartProgress = 0.5f, overallProgress = 50f, status = UploadSessionStatus.IN_PROGRESS
	)

	@Test
	fun `lifecycle hooks delegate to the configured callbacks`() = runTest {
		val seen = mutableListOf<String>()
		MultipartUploadManager.getInstance(
			context,
			MultipartUploadConfig(lifecycleCallbacks = object : UploadLifecycleCallbacks {
				override suspend fun onBeforeUpload(filePath: String): BeforeUploadResult {
					seen += "before:$filePath"; return BeforeUploadResult.Continue
				}
				override suspend fun onUploadResumed(sessionId: String) { seen += "resumed:$sessionId" }
				override suspend fun onUploadComplete(sessionId: String, result: MultipartUploadResult) {
					seen += "complete:$sessionId"
				}
				override suspend fun onUploadPaused(sessionId: String, reason: String, isConstraintViolation: Boolean) {
					seen += "paused:$sessionId:$isConstraintViolation"
				}
				override suspend fun onProgressUpdate(sessionId: String, progress: MultipartUploadProgress) {
					seen += "progress:$sessionId"
				}
			})
		)
		val w = worker()
		assertEquals(BeforeUploadResult.Continue, w.onBeforeUpload("/tmp/x"))
		w.onUploadResumed("s1")
		w.onUploadComplete("s1", MultipartUploadResult.Success("s1", "/tmp/x", "https://loc"))
		w.onUploadPaused("s1", "reason", isConstraintViolation = true)
		w.onProgressUpdate("s1", progress())

		assertTrue(seen.contains("before:/tmp/x"))
		assertTrue(seen.contains("resumed:s1"))
		assertTrue(seen.contains("complete:s1"))
		assertTrue(seen.contains("paused:s1:true"))
		assertTrue(seen.contains("progress:s1"))
	}

	@Test
	fun `lifecycle hooks swallow callback exceptions`() = runTest {
		MultipartUploadManager.getInstance(
			context,
			MultipartUploadConfig(lifecycleCallbacks = object : UploadLifecycleCallbacks {
				override suspend fun onBeforeUpload(filePath: String): BeforeUploadResult =
					throw RuntimeException("boom")
				override suspend fun onUploadResumed(sessionId: String) = throw RuntimeException("boom")
				override suspend fun onUploadComplete(sessionId: String, result: MultipartUploadResult) =
					throw RuntimeException("boom")
				override suspend fun onUploadPaused(sessionId: String, reason: String, isConstraintViolation: Boolean) =
					throw RuntimeException("boom")
				override suspend fun onProgressUpdate(sessionId: String, progress: MultipartUploadProgress) =
					throw RuntimeException("boom")
			})
		)
		val w = worker()
		// onBeforeUpload swallows and defaults to Continue.
		assertEquals(BeforeUploadResult.Continue, w.onBeforeUpload("/tmp/x"))
		// The rest must not propagate.
		w.onUploadResumed("s1")
		w.onUploadComplete("s1", MultipartUploadResult.Cancelled("s1"))
		w.onUploadPaused("s1", "r", isConstraintViolation = false)
		w.onProgressUpdate("s1", progress())
	}

	@Test
	fun `lifecycle hooks are no-ops when no callbacks are configured`() = runTest {
		MultipartUploadManager.getInstance(context) // default config, no callbacks
		val w = worker()
		assertEquals(BeforeUploadResult.Continue, w.onBeforeUpload("/tmp/x"))
		w.onUploadResumed("s1")
		w.onUploadComplete("s1", MultipartUploadResult.Success("s1", "/tmp/x"))
		w.onUploadPaused("s1", "r", isConstraintViolation = false)
		w.onProgressUpdate("s1", progress())
	}
}
