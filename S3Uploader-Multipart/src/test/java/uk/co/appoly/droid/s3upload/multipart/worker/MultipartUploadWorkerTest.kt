package uk.co.appoly.droid.s3upload.multipart.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import android.app.Notification
import androidx.core.app.NotificationCompat
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.test.runTest
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import uk.co.appoly.droid.s3upload.multipart.MultipartUploadManager
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import uk.co.appoly.droid.s3upload.multipart.interfaces.BeforeUploadResult
import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadLifecycleCallbacks
import uk.co.appoly.droid.s3upload.multipart.interfaces.UploadNotificationProvider
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

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

	@Test
	fun `doWork feeds live progress to the notification provider and the lifecycle callback`() = runTest {
		// Regression test for two defects found on-device: setForeground was called exactly once,
		// with no progress argument, so a provider only ever saw its null-progress branch; and
		// onProgressUpdate was never invoked anywhere in the module. Every progress field the
		// library computes was therefore unreachable from a notification for the whole upload.
		val server = MockWebServer()
		server.start()
		try {
			val chunkSize = MultipartUploadConfig.MIN_CHUNK_SIZE.toInt()
			val bytes = ByteArray(chunkSize + 4_096) { (it % 251).toByte() }
			val file = File.createTempFile("wm-progress", ".bin").apply { writeBytes(bytes) }

			server.dispatcher = object : Dispatcher() {
				override fun dispatch(request: RecordedRequest): MockResponse {
					val path = request.path.orEmpty()
					return when {
						path.startsWith("/initiate") -> MockResponse().setResponseCode(200)
							.setBody("""{"success":true,"data":{"upload_id":"up-1","file_path":"remote/f.bin"}}""")

						path.startsWith("/presign") -> MockResponse().setResponseCode(200)
							.setBody("""{"success":true,"data":{"presigned_url":"${server.url("/s3put")}","part_number":1,"headers":{}}}""")

						// Slow the transfer slightly so the Room-backed progress flow has room to
						// emit more than once; otherwise a local upload finishes before any tick.
						path.startsWith("/s3put") -> MockResponse().setResponseCode(200)
							.setHeader("ETag", "\"etag\"")
							.setBodyDelay(150, TimeUnit.MILLISECONDS)

						path.startsWith("/complete") -> MockResponse().setResponseCode(200)
							.setBody("""{"success":true,"data":{"file_path":"remote/f.bin","location":"https://s3/final"}}""")

						else -> MockResponse().setResponseCode(404)
					}
				}
			}

			// Records every progress value handed to the provider, nulls included.
			val renderedProgress = Collections.synchronizedList(mutableListOf<MultipartUploadProgress?>())
			val callbackProgress = Collections.synchronizedList(mutableListOf<MultipartUploadProgress>())

			MultipartUploadManager.clearInstance()
			MultipartUploadManager.getInstance(
				context,
				MultipartUploadConfig(
					chunkSize = chunkSize.toLong(),
					maxConcurrentParts = 1,
					progressUpdateIntervalMs = MultipartUploadConfig.MIN_PROGRESS_UPDATE_INTERVAL_MS,
					notificationProvider = object : UploadNotificationProvider {
						override fun createNotificationChannel(context: android.content.Context) = Unit
						override fun createNotification(
							context: android.content.Context,
							sessionId: String,
							progress: MultipartUploadProgress?
						): Notification {
							renderedProgress += progress
							return NotificationCompat.Builder(context, "test-channel")
								.setSmallIcon(android.R.drawable.stat_sys_upload)
								.setContentTitle("test")
								.build()
						}
					},
					lifecycleCallbacks = object : UploadLifecycleCallbacks {
						override suspend fun onProgressUpdate(sessionId: String, progress: MultipartUploadProgress) {
							callbackProgress += progress
						}
					}
				)
			)

			val worker = TestListenableWorkerBuilder<MultipartUploadWorker>(context)
				.setInputData(
					MultipartUploadWorker.createInputData(
						file = file,
						apiUrls = MultipartApiUrls(
							initiateUrl = server.url("/initiate").toString(),
							presignPartUrl = server.url("/presign").toString(),
							completeUrl = server.url("/complete").toString(),
							abortUrl = server.url("/abort").toString()
						)
					)
				)
				.build()

			val result = worker.doWork()
			assertTrue("expected Success but was $result", result is ListenableWorker.Result.Success)

			// The first render legitimately has no progress: no session exists yet, so the provider
			// renders its "preparing" state. The defect was that this was the ONLY render.
			assertTrue("provider was never asked to render", renderedProgress.isNotEmpty())
			assertEquals("first render should be the preparing state", null, renderedProgress.first())

			val withProgress = renderedProgress.filterNotNull()
			assertTrue(
				"provider only ever saw null progress — the notification never updated",
				withProgress.isNotEmpty()
			)
			assertTrue(
				"onProgressUpdate was never invoked",
				callbackProgress.isNotEmpty()
			)

			// Whatever reached the provider must be coherent, not a placeholder.
			withProgress.forEach { progress ->
				assertEquals(file.length(), progress.totalBytes)
				assertTrue("progress out of range: ${progress.overallProgress}", progress.overallProgress in 0f..100f)
			}

			file.delete()
		} finally {
			server.shutdown()
		}
	}
}
