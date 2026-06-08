package uk.co.appoly.droid.s3upload.utils

import kotlinx.serialization.json.Json
import uk.co.appoly.droid.s3upload.network.GetPreSignedUrlResponse
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [StringOrListSerialiser], which coerces a JSON value that may be either a string
 * or an array of strings into a single [String]. Also covers it in its real usage site — the
 * `headers` map of [GetPreSignedUrlResponse], where S3 returns some header values as arrays.
 */
class StringOrListSerialiserTest {

	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun `deserialises a plain string primitive as-is`() {
		assertEquals("image/jpeg", json.decodeFromString(StringOrListSerialiser, "\"image/jpeg\""))
	}

	@Test
	fun `deserialises an array of strings joined by comma-space`() {
		assertEquals(
			"a, b, c",
			json.decodeFromString(StringOrListSerialiser, """["a","b","c"]""")
		)
	}

	@Test
	fun `deserialises a non-string primitive via its content`() {
		assertEquals("42", json.decodeFromString(StringOrListSerialiser, "42"))
	}

	@Test
	fun `deserialises an unexpected object to empty string`() {
		assertEquals("", json.decodeFromString(StringOrListSerialiser, "{}"))
	}

	@Test
	fun `serialises a string back out as a JSON string`() {
		assertEquals("\"public-read\"", json.encodeToString(StringOrListSerialiser, "public-read"))
	}

	@Test
	fun `decodes a presigned-url response with mixed string and array headers`() {
		val decoded = json.decodeFromString<GetPreSignedUrlResponse>(
			"""
			{
			  "success": true,
			  "data": {
			    "file_path": "images/profile/user123.jpg",
			    "presigned_url": "https://bucket-name.s3.amazonaws.com/x",
			    "headers": {
			      "Host": ["bucket-name.s3.amazonaws.com"],
			      "x-amz-acl": ["public-read"],
			      "Content-Type": "image/jpeg"
			    }
			  }
			}
			""".trimIndent()
		)
		assertEquals(true, decoded.success)
		val data = decoded.data!!
		assertEquals("images/profile/user123.jpg", data.filePath)
		assertEquals("bucket-name.s3.amazonaws.com", data.headers["Host"])      // single-element array -> bare value
		assertEquals("public-read", data.headers["x-amz-acl"])
		assertEquals("image/jpeg", data.headers["Content-Type"])               // plain string -> as-is
	}
}
