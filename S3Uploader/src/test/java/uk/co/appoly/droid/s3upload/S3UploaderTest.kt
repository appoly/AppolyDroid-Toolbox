package uk.co.appoly.droid.s3upload

import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import uk.co.appoly.droid.s3upload.interfaces.HeaderProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
	fun `uploadFile returns Error instead of a ClassCastException when presign has no body`() = runTest {
		// Sandwich substitutes Unit for a bodyless 2xx, so Success.data is not the declared type.
		server.enqueue(MockResponse().setResponseCode(204))

		val result = S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		result as UploadResult.Error
		assertFalse(
			"Expected a no-data error, got ${result.throwable}",
			result.throwable is ClassCastException
		)
	}

	// ==================== Header scoping ====================

	@Test
	fun `uploadFile passes the presign url to the header provider`() = runTest {
		val seen = mutableListOf<String>()
		S3Uploader.initS3Uploader(
			headerProvider = HeaderProvider { url ->
				seen += url
				emptyMap()
			},
			loggingLevel = LoggingLevel.NONE
		)
		enqueueHappyPath()
		val presignUrl = server.url("/presign").toString()

		S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = presignUrl
		)

		assertEquals(listOf(presignUrl), seen)
	}

	@Test
	fun `bearerForHosts sends the token to an allowed presign host but never to the S3 PUT`() = runTest {
		S3Uploader.initS3Uploader(
			// MockWebServer speaks plain http, so opt out of the HTTPS requirement here.
			headerProvider = HeaderProvider.bearerForHosts({ setOf(server.hostName) }, requireHttps = false) { "t0k3n" },
			loggingLevel = LoggingLevel.NONE
		)
		enqueueHappyPath()

		val result = S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		assertTrue("Expected Success but was $result", result is UploadResult.Success)
		assertEquals("Bearer t0k3n", server.takeRequest().getHeader("Authorization"))
		assertNull("S3 PUT must not carry the provider's token", server.takeRequest().getHeader("Authorization"))
	}

	@Test
	fun `bearerForHosts omits the token from an http presign url on an allowed host`() = runTest {
		S3Uploader.initS3Uploader(
			headerProvider = HeaderProvider.bearerForHosts({ setOf(server.hostName) }) { "t0k3n" },
			loggingLevel = LoggingLevel.NONE
		)
		enqueueHappyPath()

		S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		assertNull("Token must not go out over cleartext", server.takeRequest().getHeader("Authorization"))
	}

	@Test
	fun `bearerForHosts omits the token when the presign url points at another host`() = runTest {
		S3Uploader.initS3Uploader(
			headerProvider = HeaderProvider.bearerForHosts({ setOf("api.example.com") }) { "t0k3n" },
			loggingLevel = LoggingLevel.NONE
		)
		enqueueHappyPath()

		S3Uploader.uploadFile(
			file = tempFile(),
			mediaType = "text/plain".toMediaType(),
			getPresignedUrlAPI = server.url("/presign").toString()
		)

		assertNull(server.takeRequest().getHeader("Authorization"))
	}

	private fun enqueueHappyPath() {
		val s3Url = server.url("/s3-put").toString()
		server.enqueue(
			MockResponse().setResponseCode(200).setBody(
				"""{"success":true,"data":{"file_path":"images/a.txt","presigned_url":"$s3Url","headers":{}}}"""
			)
		)
		server.enqueue(MockResponse().setResponseCode(200))
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
