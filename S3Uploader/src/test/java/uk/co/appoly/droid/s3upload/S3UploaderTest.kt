package uk.co.appoly.droid.s3upload

import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration tests for the [S3Uploader] upload orchestration, driving the real Retrofit/OkHttp
 * stack against [MockWebServer]. A concrete [mediaType] is supplied so the Android MimeTypeMap
 * path is bypassed and the tests run on plain JVM.
 */
class S3UploaderTest {

	private lateinit var server: MockWebServer

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		S3Uploader.initS3Uploader(
			headerProvider = HeaderProvider { emptyMap() },
			loggingLevel = LoggingLevel.NONE
		)
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	private fun tempFile(name: String = "upload.txt", body: String = "hello"): File =
		File.createTempFile(name, null).apply { writeText(body) }

	@Test
	fun `uploadFile returns Success with the file path on the happy path`() = runTest {
		val s3Url = server.url("/s3-put").toString()
		// 1) pre-signed URL response, 2) the S3 PUT.
		server.enqueue(
			MockResponse().setResponseCode(200).setBody(
				"""{"success":true,"data":{"file_path":"images/a.txt","presigned_url":"$s3Url","headers":{}}}"""
			)
		)
		server.enqueue(MockResponse().setResponseCode(200))

		val result = S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		assertTrue(result is UploadResult.Success)
		assertEquals("images/a.txt", (result as UploadResult.Success).filePath)
	}

	@Test
	fun `uploadFile returns Error when the presign request fails`() = runTest {
		server.enqueue(MockResponse().setResponseCode(500).setBody("""{"success":false,"message":"nope"}"""))

		val result = S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		assertTrue(result is UploadResult.Error)
	}

	@Test
	fun `uploadFile returns Error when presign succeeds but data is null`() = runTest {
		server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"data":null}"""))

		val result = S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		assertTrue(result is UploadResult.Error)
	}

	@Test
	fun `uploadFile returns Error when the S3 upload fails`() = runTest {
		val s3Url = server.url("/s3-put").toString()
		server.enqueue(
			MockResponse().setResponseCode(200).setBody(
				"""{"success":true,"data":{"file_path":"images/a.txt","presigned_url":"$s3Url","headers":{}}}"""
			)
		)
		server.enqueue(MockResponse().setResponseCode(403))

		val result = S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		assertTrue(result is UploadResult.Error)
	}

	@Test
	fun `uploadFileDirect returns Success when the S3 PUT succeeds`() = runTest {
		server.enqueue(MockResponse().setResponseCode(200))
		val result = S3Uploader.uploadFileDirect(
			file = tempFile(),
			presignedUrl = server.url("/s3-direct").toString(),
			mediaType = "text/plain".toMediaType()
		)
		assertTrue(result is DirectUploadResult.Success)
	}

	@Test
	fun `uploadFileDirect returns Error when the S3 PUT fails`() = runTest {
		server.enqueue(MockResponse().setResponseCode(403))
		val result = S3Uploader.uploadFileDirect(
			file = tempFile(),
			presignedUrl = server.url("/s3-direct").toString(),
			mediaType = "text/plain".toMediaType()
		)
		assertTrue(result is DirectUploadResult.Error)
	}
}
