package uk.co.appoly.droid.s3upload.multipart.network

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.retrofit.adapters.ApiResponseCallAdapterFactory
import com.skydoves.sandwich.retrofit.statusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uk.co.appoly.droid.s3upload.multipart.network.model.CompletedPart

/**
 * Drives the [MultipartApis] Retrofit interface and the [MultipartApiService] wrapper
 * against canned [MockWebServer] responses.
 *
 * Covers the happy path (wrapped + unwrapped JSON envelopes), HTTP failures
 * (sandwich [ApiResponse.Failure.Error]), and transport exceptions
 * (sandwich [ApiResponse.Failure.Exception]) for every API method:
 * initiate / presign / complete / abort.
 *
 * The @Serializable models are covered elsewhere; here we only assert that the
 * service maps responses into the right [ApiResponse] shape and exposes the data.
 */
class MultipartApiServiceTest {

	private lateinit var server: MockWebServer
	private lateinit var api: MultipartApis
	private lateinit var service: MultipartApiService

	private var providedHeaders: Map<String, String> = mapOf("Authorization" to "Bearer test-token")

	/** Mirrors [MultipartRetrofitClient]'s converter config (lenient JSON + sandwich adapter). */
	private val json = Json {
		ignoreUnknownKeys = true
		useAlternativeNames = true
		explicitNulls = false
		encodeDefaults = true
	}

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()

		api = Retrofit.Builder()
			.baseUrl(server.url("/"))
			.addConverterFactory(json.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
			.addCallAdapterFactory(ApiResponseCallAdapterFactory.create())
			.build()
			.create(MultipartApis::class.java)

		service = MultipartApiService(api) { providedHeaders }
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	private fun endpointUrl(path: String): String = server.url(path).toString()

	private fun takeRequest(): RecordedRequest = server.takeRequest()

	// ==================== initiate ====================

	@Test
	fun `initiate returns Success and maps wrapped data`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setBody(
					"""{"success":true,"message":"ok","data":{"upload_id":"u-123","file_path":"uploads/file.bin","key":"k","bucket":"b"}}"""
				)
		)

		val response = service.initiateMultipartUpload(
			url = endpointUrl("api/multipart/initiate"),
			fileName = "file.bin",
			contentType = "application/octet-stream"
		)

		assertTrue("expected Success but was $response", response is ApiResponse.Success)
		val data = (response as ApiResponse.Success).data.data
		assertNotNull(data)
		assertEquals("u-123", data!!.uploadId)
		assertEquals("uploads/file.bin", data.filePath)
		assertEquals("k", data.key)
		assertEquals("b", data.bucket)
	}

	@Test
	fun `initiate synthesizes data from unwrapped response`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setBody("""{"upload_id":"u-999","file_path":"uploads/raw.bin"}""")
		)

		val response = service.initiateMultipartUpload(
			url = endpointUrl("api/multipart/initiate"),
			fileName = "raw.bin"
		)

		val data = (response as ApiResponse.Success).data.data
		assertNotNull(data)
		assertEquals("u-999", data!!.uploadId)
		assertEquals("uploads/raw.bin", data.filePath)
	}

	@Test
	fun `initiate sends headers, accept, body and uses POST`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(200)
				.setBody("""{"success":true,"data":{"upload_id":"u","file_path":"p"}}""")
		)

		service.initiateMultipartUpload(
			url = endpointUrl("api/multipart/initiate"),
			fileName = "photo.jpg",
			contentType = "image/jpeg"
		)

		val recorded = takeRequest()
		assertEquals("POST", recorded.method)
		assertEquals("/api/multipart/initiate", recorded.path)
		assertEquals("Bearer test-token", recorded.getHeader("Authorization"))
		assertEquals("application/json", recorded.getHeader("Accept"))
		val sentBody = recorded.body.readUtf8()
		assertTrue(sentBody.contains("\"file_name\":\"photo.jpg\""))
		assertTrue(sentBody.contains("\"content_type\":\"image/jpeg\""))
	}

	@Test
	fun `initiate returns Failure_Error on HTTP 422`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setResponseCode(422)
				.setBody("""{"success":false,"message":"validation failed"}""")
		)

		val response = service.initiateMultipartUpload(
			url = endpointUrl("api/multipart/initiate"),
			fileName = "bad.bin"
		)

		assertTrue("expected Failure.Error but was $response", response is ApiResponse.Failure.Error)
		assertEquals(422, (response as ApiResponse.Failure.Error).statusCode.code)
	}

	@Test
	fun `initiate returns Failure_Exception when server drops connection`() = runBlocking {
		server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))

		val response = service.initiateMultipartUpload(
			url = endpointUrl("api/multipart/initiate"),
			fileName = "drop.bin"
		)

		assertTrue("expected Failure.Exception but was $response", response is ApiResponse.Failure.Exception)
	}

	// ==================== presign ====================

	@Test
	fun `presign returns Success and maps wrapped data with headers`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setBody(
					"""{"success":true,"data":{"presigned_url":"https://s3.example/part?sig=abc","part_number":3,"headers":{"x-amz-meta":"v"}}}"""
				)
		)

		val response = service.getPresignedUrlForPart(
			url = endpointUrl("api/multipart/presign"),
			uploadId = "u-1",
			filePath = "uploads/file.bin",
			partNumber = 3
		)

		val data = (response as ApiResponse.Success).data.data
		assertNotNull(data)
		assertEquals("https://s3.example/part?sig=abc", data!!.presignedUrl)
		assertEquals(3, data.partNumber)
		assertEquals("v", data.headers["x-amz-meta"])
	}

	@Test
	fun `presign sends part number in request body`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(200)
				.setBody("""{"data":{"presigned_url":"https://s3/p","part_number":7}}""")
		)

		service.getPresignedUrlForPart(
			url = endpointUrl("api/multipart/presign"),
			uploadId = "upl-42",
			filePath = "uploads/big.bin",
			partNumber = 7
		)

		val sentBody = takeRequest().body.readUtf8()
		assertTrue(sentBody.contains("\"upload_id\":\"upl-42\""))
		assertTrue(sentBody.contains("\"file_path\":\"uploads/big.bin\""))
		assertTrue(sentBody.contains("\"part_number\":7"))
	}

	@Test
	fun `presign returns Failure_Error on HTTP 500`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))

		val response = service.getPresignedUrlForPart(
			url = endpointUrl("api/multipart/presign"),
			uploadId = "u",
			filePath = "p",
			partNumber = 1
		)

		assertTrue(response is ApiResponse.Failure.Error)
		assertEquals(500, (response as ApiResponse.Failure.Error).statusCode.code)
	}

	// ==================== complete ====================

	@Test
	fun `complete returns Success and maps wrapped data`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setResponseCode(200)
				.setBody(
					"""{"success":true,"data":{"file_path":"uploads/final.bin","location":"https://s3/final.bin","etag":"\"final-etag\""}}"""
				)
		)

		val response = service.completeMultipartUpload(
			url = endpointUrl("api/multipart/complete"),
			uploadId = "u-1",
			filePath = "uploads/final.bin",
			parts = listOf(
				CompletedPart(partNumber = 2, etag = "\"e2\""),
				CompletedPart(partNumber = 1, etag = "\"e1\"")
			)
		)

		val data = (response as ApiResponse.Success).data.data
		assertNotNull(data)
		assertEquals("uploads/final.bin", data!!.filePath)
		assertEquals("https://s3/final.bin", data.location)
		assertEquals("\"final-etag\"", data.etag)
	}

	@Test
	fun `complete sorts parts by part number before sending`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(200)
				.setBody("""{"data":{"file_path":"p"}}""")
		)

		service.completeMultipartUpload(
			url = endpointUrl("api/multipart/complete"),
			uploadId = "u",
			filePath = "p",
			parts = listOf(
				CompletedPart(partNumber = 3, etag = "c"),
				CompletedPart(partNumber = 1, etag = "a"),
				CompletedPart(partNumber = 2, etag = "b")
			)
		)

		val sentBody = takeRequest().body.readUtf8()
		val idx1 = sentBody.indexOf("\"part_number\":1")
		val idx2 = sentBody.indexOf("\"part_number\":2")
		val idx3 = sentBody.indexOf("\"part_number\":3")
		assertTrue("parts should be ordered 1<2<3 in body: $sentBody", idx1 in 0 until idx2 && idx2 < idx3)
	}

	@Test
	fun `complete returns Failure_Error on HTTP 409`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(409).setBody("""{"message":"conflict"}"""))

		val response = service.completeMultipartUpload(
			url = endpointUrl("api/multipart/complete"),
			uploadId = "u",
			filePath = "p",
			parts = emptyList()
		)

		assertTrue(response is ApiResponse.Failure.Error)
		assertEquals(409, (response as ApiResponse.Failure.Error).statusCode.code)
	}

	// ==================== abort ====================

	@Test
	fun `abort returns Success`() = runBlocking {
		server.enqueue(
			MockResponse().setResponseCode(200)
				.setBody("""{"success":true,"message":"aborted"}""")
		)

		val response = service.abortMultipartUpload(
			url = endpointUrl("api/multipart/abort"),
			uploadId = "u-1",
			filePath = "uploads/file.bin"
		)

		assertTrue(response is ApiResponse.Success)
		val body = (response as ApiResponse.Success).data
		assertTrue(body.success)
		assertEquals("aborted", body.message)
	}

	@Test
	fun `abort sends upload id and file path in body`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))

		service.abortMultipartUpload(
			url = endpointUrl("api/multipart/abort"),
			uploadId = "abort-id",
			filePath = "uploads/abort.bin"
		)

		val sentBody = takeRequest().body.readUtf8()
		assertTrue(sentBody.contains("\"upload_id\":\"abort-id\""))
		assertTrue(sentBody.contains("\"file_path\":\"uploads/abort.bin\""))
	}

	@Test
	fun `abort returns Failure_Error on HTTP 404`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"not found"}"""))

		val response = service.abortMultipartUpload(
			url = endpointUrl("api/multipart/abort"),
			uploadId = "missing",
			filePath = "p"
		)

		assertTrue(response is ApiResponse.Failure.Error)
		assertEquals(404, (response as ApiResponse.Failure.Error).statusCode.code)
	}

	// ==================== header provider behaviour ====================

	@Test
	fun `service calls header provider for each request`() = runBlocking {
		providedHeaders = mapOf("Authorization" to "Bearer first", "X-Trace" to "abc")
		server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true}"""))

		service.abortMultipartUpload(
			url = endpointUrl("api/multipart/abort"),
			uploadId = "u",
			filePath = "p"
		)

		val recorded = takeRequest()
		assertEquals("Bearer first", recorded.getHeader("Authorization"))
		assertEquals("abc", recorded.getHeader("X-Trace"))
	}

	// ==================== empty / null data mapping ====================

	@Test
	fun `initiate Success with no usable fields yields null data`() = runBlocking {
		// success=true envelope, but no data block and no unwrapped fields
		server.enqueue(MockResponse().setResponseCode(200).setBody("""{"success":true,"message":"queued"}"""))

		val response = service.initiateMultipartUpload(
			url = endpointUrl("api/multipart/initiate"),
			fileName = "x.bin"
		)

		assertTrue(response is ApiResponse.Success)
		val payload = (response as ApiResponse.Success).data
		assertTrue(payload.success)
		assertNull(payload.data)
	}
}
