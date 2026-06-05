package uk.co.appoly.droid.s3upload.multipart.network.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Serialization tests for the multipart network models, including the
 * [EmptyArrayAsEmptyMapSerializer] (handles `headers: []`) and the wrapped/unwrapped
 * [PresignPartResponse.data] resolution.
 */
class NetworkModelsSerializationTest {

	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun `EmptyArrayAsEmptyMapSerializer treats an empty array as an empty map`() {
		assertEquals(emptyMap<String, String>(), json.decodeFromString(EmptyArrayAsEmptyMapSerializer, "[]"))
	}

	@Test
	fun `EmptyArrayAsEmptyMapSerializer decodes an object, joining array values`() {
		val result = json.decodeFromString(
			EmptyArrayAsEmptyMapSerializer,
			"""{"Host":"bucket.s3","x-amz-tags":["a","b"]}"""
		)
		assertEquals("bucket.s3", result["Host"])
		assertEquals("a, b", result["x-amz-tags"])
	}

	@Test
	fun `PresignPartResponse resolves data from the wrapped envelope`() {
		val response = json.decodeFromString<PresignPartResponse>(
			"""{"success":true,"data":{"presigned_url":"https://s3/part1","part_number":1,"headers":{}}}"""
		)
		assertTrue(response.success)
		assertEquals("https://s3/part1", response.data?.presignedUrl)
		assertEquals(1, response.data?.partNumber)
	}

	@Test
	fun `PresignPartResponse synthesises data from an unwrapped body with empty-array headers`() {
		val response = json.decodeFromString<PresignPartResponse>(
			"""{"presigned_url":"https://s3/part2","part_number":2,"headers":[]}"""
		)
		assertEquals("https://s3/part2", response.data?.presignedUrl)
		assertEquals(2, response.data?.partNumber)
		assertEquals(emptyMap<String, String>(), response.data?.headers)
	}

	@Test
	fun `PresignPartResponse data is null when neither envelope nor root fields are present`() {
		val response = json.decodeFromString<PresignPartResponse>("""{"success":false}""")
		assertNull(response.data)
	}

	@Test
	fun `InitiateMultipartResponse decodes its data payload`() {
		val response = json.decodeFromString<InitiateMultipartResponse>(
			"""{"success":true,"data":{"upload_id":"abc","file_path":"images/x.bin","key":"k","bucket":"b"}}"""
		)
		assertTrue(response.success)
		assertEquals("abc", response.data?.uploadId)
		assertEquals("images/x.bin", response.data?.filePath)
	}
}
