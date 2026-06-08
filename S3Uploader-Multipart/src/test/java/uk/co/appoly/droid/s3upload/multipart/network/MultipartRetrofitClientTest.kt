package uk.co.appoly.droid.s3upload.multipart.network

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.co.appoly.droid.s3upload.multipart.network.model.InitiateMultipartResponse

/**
 * Tests for the [MultipartRetrofitClient] singleton: lazy service creation,
 * caching of the [MultipartApis] instance, [MultipartRetrofitClient.reset]
 * behaviour, and the lenient JSON configuration it exposes.
 *
 * No network is hit here - we only exercise the client's wiring/config.
 */
class MultipartRetrofitClientTest {

	@Before
	fun setUp() {
		MultipartRetrofitClient.reset()
	}

	@After
	fun tearDown() {
		MultipartRetrofitClient.reset()
	}

	@Test
	fun `multipartApis returns a usable MultipartApis instance`() {
		val apis = MultipartRetrofitClient.multipartApis
		assertNotNull(apis)
		// Retrofit hands back a dynamic proxy implementing the interface
		assertTrue(MultipartApis::class.java.isInstance(apis))
	}

	@Test
	fun `multipartApis is cached across accesses`() {
		val first = MultipartRetrofitClient.multipartApis
		val second = MultipartRetrofitClient.multipartApis
		assertSame("repeated access should return the cached instance", first, second)
	}

	@Test
	fun `reset forces a new MultipartApis instance`() {
		val first = MultipartRetrofitClient.multipartApis
		MultipartRetrofitClient.reset()
		val second = MultipartRetrofitClient.multipartApis
		assertNotSame("reset() should recreate the service", first, second)
	}

	@Test
	fun `createService builds a new proxy for the given interface`() {
		val service = MultipartRetrofitClient.createService(MultipartApis::class.java)
		assertNotNull(service)
		assertTrue(MultipartApis::class.java.isInstance(service))
	}

	@Test
	fun `json ignores unknown keys when decoding responses`() {
		// extra "totally_unexpected" field must not blow up decoding
		val decoded = MultipartRetrofitClient.json.decodeFromString(
			InitiateMultipartResponse.serializer(),
			"""{"success":true,"totally_unexpected":42,"data":{"upload_id":"u","file_path":"p"}}"""
		)
		assertTrue(decoded.success)
		assertNotNull(decoded.data)
	}

	@Test
	fun `json synthesizes unwrapped data via configured leniency`() {
		val decoded = MultipartRetrofitClient.json.decodeFromString(
			InitiateMultipartResponse.serializer(),
			"""{"upload_id":"only-root","file_path":"root/path.bin"}"""
		)
		assertNotNull(decoded.data)
		assertTrue(decoded.data!!.uploadId == "only-root")
	}
}
