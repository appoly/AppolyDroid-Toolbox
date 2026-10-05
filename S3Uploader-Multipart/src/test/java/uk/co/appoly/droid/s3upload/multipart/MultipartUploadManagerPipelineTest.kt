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
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import uk.co.appoly.droid.s3upload.S3Uploader
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import uk.co.appoly.droid.s3upload.multipart.config.MultipartUploadConfig
import uk.co.appoly.droid.s3upload.multipart.database.S3UploaderDatabase
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import uk.co.appoly.droid.s3upload.multipart.network.model.MultipartApiUrls
import uk.co.appoly.droid.s3upload.multipart.result.MultipartUploadResult
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * End-to-end test of the [MultipartUploadManager] upload pipeline
 * (initiate -> presign -> S3 PUT -> complete) against [MockWebServer], for a single-part file.
 * Exercises initializeUpload + executeUpload + uploadParts + uploadSinglePart + completeUpload.
 */
@RunWith(AndroidJUnit4::class)
class MultipartUploadManagerPipelineTest {

	private lateinit var context: Context
	private lateinit var server: MockWebServer
	private lateinit var manager: MultipartUploadManager
	private val tempFiles = mutableListOf<File>()

	@Before
	fun setUp() {
		context = ApplicationProvider.getApplicationContext()
		val config = Configuration.Builder()
			.setExecutor(SynchronousExecutor())
			.setWorkerFactory(object : WorkerFactory() {
				override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
					object : Worker(appContext, workerParameters) { override fun doWork(): Result = Result.success() }
			})
			.build()
		WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
		S3Uploader.initS3Uploader(HeaderProvider { emptyMap() }, LoggingLevel.NONE)
		MultipartUploadManager.clearInstance()
		S3UploaderDatabase.clearInstance()
		manager = MultipartUploadManager.getInstance(context)

		server = MockWebServer()
		server.start()
	}

	@After
	fun tearDown() {
		server.shutdown()
		MultipartUploadManager.clearInstance()
		S3UploaderDatabase.clearInstance()
		tempFiles.forEach { it.delete() }
	}

	private fun tempFile() = File.createTempFile("e2e-upload", ".bin")
		.apply { writeText("hello world"); tempFiles += this }

	private fun apiUrls() = MultipartApiUrls(
		initiateUrl = server.url("/initiate").toString(),
		presignPartUrl = server.url("/presign").toString(),
		completeUrl = server.url("/complete").toString(),
		abortUrl = server.url("/abort").toString()
	)

	@Test
	fun `startUpload completes a single-part upload end to end`() = runTest {
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				val path = request.path.orEmpty()
				return when {
					path.startsWith("/initiate") -> MockResponse().setResponseCode(200)
						.setBody("""{"success":true,"data":{"upload_id":"up-1","file_path":"remote/file.bin"}}""")

					path.startsWith("/presign") -> MockResponse().setResponseCode(200)
						.setBody("""{"success":true,"data":{"presigned_url":"${server.url("/s3put")}","part_number":1,"headers":{}}}""")

					path.startsWith("/s3put") -> MockResponse().setResponseCode(200).setHeader("ETag", "\"etag-1\"")

					path.startsWith("/complete") -> MockResponse().setResponseCode(200)
						.setBody("""{"success":true,"data":{"file_path":"remote/file.bin","location":"https://s3/final"}}""")

					else -> MockResponse().setResponseCode(404)
				}
			}
		}

		val result = manager.startUpload(tempFile(), apiUrls())

		assertTrue("expected Success but was $result", result is MultipartUploadResult.Success)
		result as MultipartUploadResult.Success
		assertEquals("remote/file.bin", result.filePath)
		assertEquals("https://s3/final", result.location)
		assertEquals(UploadSessionStatus.COMPLETED, manager.getSession(result.sessionId)?.status)
	}

	@Test
	fun `startUpload succeeds when complete returns 204 with no body`() = runTest {
		// A bodyless 2xx from complete still means S3 assembled the object. Sandwich substitutes
		// Unit for the missing body, which must not be read as a CompleteMultipartResponse.
		server.dispatcher = singlePartDispatcher(
			complete = MockResponse().setResponseCode(204),
		)

		val result = manager.startUpload(tempFile(), apiUrls())

		assertTrue("expected Success but was $result", result is MultipartUploadResult.Success)
		result as MultipartUploadResult.Success
		assertEquals("remote/file.bin", result.filePath)
		assertNull(result.location)
		assertEquals(UploadSessionStatus.COMPLETED, manager.getSession(result.sessionId)?.status)
	}

	@Test
	fun `startUpload returns a no-data Error when initiate returns 204 with no body`() = runTest {
		server.dispatcher = singlePartDispatcher(
			initiate = MockResponse().setResponseCode(204),
		)

		val result = manager.startUpload(tempFile(), apiUrls())

		result as MultipartUploadResult.Error
		assertFalse(
			"Expected a no-data error, got ${result.throwable}",
			result.throwable is ClassCastException
		)
	}

	@Test
	fun `bearerForHosts sends the token to the control endpoints but never to the S3 part PUT`() = runTest {
		S3Uploader.initS3Uploader(
			HeaderProvider.bearerForHosts({ setOf(server.hostName) }) { "t0k3n" },
			LoggingLevel.NONE
		)
		val authByPath = ConcurrentHashMap<String, String>()
		val delegate = singlePartDispatcher()
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				authByPath[request.path.orEmpty().substringBefore('?')] = request.getHeader("Authorization") ?: "<none>"
				return delegate.dispatch(request)
			}
		}

		val result = manager.startUpload(tempFile(), apiUrls())

		assertTrue("expected Success but was $result", result is MultipartUploadResult.Success)
		assertEquals("Bearer t0k3n", authByPath["/initiate"])
		assertEquals("Bearer t0k3n", authByPath["/presign"])
		assertEquals("Bearer t0k3n", authByPath["/complete"])
		assertEquals("<none>", authByPath["/s3put"])
	}

	@Test
	fun `bearerForHosts omits the token when the control endpoints are on another host`() = runTest {
		S3Uploader.initS3Uploader(
			HeaderProvider.bearerForHosts({ setOf("api.example.com") }) { "t0k3n" },
			LoggingLevel.NONE
		)
		val tokens = ConcurrentHashMap.newKeySet<String>()
		val delegate = singlePartDispatcher()
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				request.getHeader("Authorization")?.let { tokens += it }
				return delegate.dispatch(request)
			}
		}

		manager.startUpload(tempFile(), apiUrls())

		assertTrue("No request may carry the token, saw $tokens", tokens.isEmpty())
	}

	/** Happy-path responses for a single-part upload, with any step overridable. */
	private fun singlePartDispatcher(
		initiate: MockResponse = MockResponse().setResponseCode(200)
			.setBody("""{"success":true,"data":{"upload_id":"up-1","file_path":"remote/file.bin"}}"""),
		complete: MockResponse = MockResponse().setResponseCode(200)
			.setBody("""{"success":true,"data":{"file_path":"remote/file.bin","location":"https://s3/final"}}"""),
	) = object : Dispatcher() {
		override fun dispatch(request: RecordedRequest): MockResponse {
			val path = request.path.orEmpty()
			return when {
				path.startsWith("/initiate") -> initiate
				path.startsWith("/presign") -> MockResponse().setResponseCode(200)
					.setBody("""{"success":true,"data":{"presigned_url":"${server.url("/s3put")}","part_number":1,"headers":{}}}""")
				path.startsWith("/s3put") -> MockResponse().setResponseCode(200).setHeader("ETag", "\"etag-1\"")
				path.startsWith("/complete") -> complete
				else -> MockResponse().setResponseCode(404)
			}
		}
	}

	@Test
	fun `startUpload returns Error when initiate fails`() = runTest {
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse =
				MockResponse().setResponseCode(500).setBody("""{"success":false,"message":"server error"}""")
		}

		val result = manager.startUpload(tempFile(), apiUrls())
		assertTrue(result is MultipartUploadResult.Error)
	}

	@Test
	fun `startUpload streams every part intact for a multi-part file`() = runTest {
		// The load-bearing test for streaming parts off disk instead of buffering them: if an
		// offset or length is wrong, S3 accepts every part and only the reassembled object is
		// corrupt — which no status-code assertion would ever catch.
		val chunkSize = MultipartUploadConfig.MIN_CHUNK_SIZE.toInt()
		val trailingSize = 1234
		val expected = ByteArray(chunkSize + trailingSize) { (it % 251).toByte() }
		val file = File.createTempFile("e2e-multipart", ".bin")
			.apply { writeBytes(expected); tempFiles += this }

		val uploadedParts = ConcurrentHashMap<Int, ByteArray>()
		val declaredLengths = ConcurrentHashMap<Int, String>()

		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse {
				val path = request.path.orEmpty()
				return when {
					path.startsWith("/initiate") -> MockResponse().setResponseCode(200)
						.setBody("""{"success":true,"data":{"upload_id":"up-1","file_path":"remote/file.bin"}}""")

					// Hand each part its own S3 URL so the assertions do not depend on the order
					// concurrent parts happen to arrive in.
					path.startsWith("/presign") -> {
						val partNumber = PART_NUMBER.find(request.body.readUtf8())
							?.groupValues?.get(1)?.toInt() ?: -1
						MockResponse().setResponseCode(200).setBody(
							"""{"success":true,"data":{"presigned_url":"${server.url("/s3put/$partNumber")}","part_number":$partNumber,"headers":{}}}"""
						)
					}

					path.startsWith("/s3put/") -> {
						val partNumber = path.substringAfterLast('/').toInt()
						request.getHeader("Content-Length")?.let { declaredLengths[partNumber] = it }
						uploadedParts[partNumber] = request.body.readByteArray()
						MockResponse().setResponseCode(200).setHeader("ETag", "\"etag-$partNumber\"")
					}

					path.startsWith("/complete") -> MockResponse().setResponseCode(200)
						.setBody("""{"success":true,"data":{"file_path":"remote/file.bin","location":"https://s3/final"}}""")

					else -> MockResponse().setResponseCode(404)
				}
			}
		}

		MultipartUploadManager.clearInstance()
		val manager = MultipartUploadManager.getInstance(
			context,
			MultipartUploadConfig(chunkSize = chunkSize.toLong(), maxConcurrentParts = 3)
		)

		val result = manager.startUpload(file, apiUrls())

		assertTrue("expected Success but was $result", result is MultipartUploadResult.Success)
		assertEquals("expected two parts, got ${uploadedParts.keys.sorted()}", 2, uploadedParts.size)
		assertEquals(chunkSize, uploadedParts.getValue(1).size)
		assertEquals(trailingSize, uploadedParts.getValue(2).size)

		// Concatenated parts must reproduce the source file byte for byte.
		assertArrayEquals(expected, uploadedParts.getValue(1) + uploadedParts.getValue(2))

		// A streamed body must still declare its length, or the presigned PUT breaks.
		assertEquals(chunkSize.toString(), declaredLengths[1])
		assertEquals(trailingSize.toString(), declaredLengths[2])
	}

	private companion object {
		val PART_NUMBER = """"part_number"\s*:\s*(\d+)""".toRegex()
	}
}
