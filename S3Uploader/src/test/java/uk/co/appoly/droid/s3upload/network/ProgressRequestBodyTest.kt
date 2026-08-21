package uk.co.appoly.droid.s3upload.network

import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.EOFException
import java.io.File
import java.io.IOException

/**
 * Covers [ProgressRequestBody]'s two guarantees: it reports progress as the file streams, and it
 * writes exactly the number of bytes it declared as `Content-Length`.
 *
 * The second matters more than it looks. A body that writes fewer bytes than it promised produces an
 * opaque transport failure; one that writes more can put a corrupt object in S3. Both are asserted
 * against a file that changes after the body was constructed.
 */
class ProgressRequestBodyTest {

	private lateinit var file: File
	private lateinit var progress: MutableStateFlow<Float>

	private val contents = ByteArray(TOTAL_SIZE) { (it % 251).toByte() }

	@Before
	fun setUp() {
		file = File.createTempFile("progress-request-body", ".bin")
		file.writeBytes(contents)
		progress = MutableStateFlow(0f)
	}

	@After
	fun tearDown() {
		file.delete()
	}

	private fun body() = ProgressRequestBody(file, "application/octet-stream".toMediaType(), progress)

	@Test
	fun `contentLength is pinned at construction`() {
		val body = body()
		assertEquals(TOTAL_SIZE.toLong(), body.contentLength())

		// The file changing must not move the goalposts mid-request.
		file.writeBytes(ByteArray(TOTAL_SIZE * 2))
		assertEquals(TOTAL_SIZE.toLong(), body.contentLength())
	}

	@Test
	fun `writeTo streams the whole file`() {
		val sink = Buffer()
		body().writeTo(sink)

		assertEquals(TOTAL_SIZE.toLong(), sink.size)
		assertArrayEquals(contents, sink.readByteArray())
	}

	@Test
	fun `writeTo reports rising progress and ends at 100 percent`() {
		// StateFlow conflates, so sample it from inside the write rather than collecting after.
		val samples = mutableListOf<Float>()
		ProgressRequestBody(file, null, progress).writeTo(samplingSink { samples += progress.value })

		assertEquals(100f, progress.value, 0.001f)
		assertTrue("expected several intermediate samples, got $samples", samples.size > 1)
		assertTrue("progress went backwards mid-attempt: $samples", samples == samples.sorted())
		assertTrue("progress left 0..100: $samples", samples.all { it in 0f..100f })
	}

	@Test
	fun `progress never reports NaN for an empty file`() {
		file.writeBytes(ByteArray(0))
		val body = body()

		body.writeTo(Buffer())

		assertEquals(0L, body.contentLength())
		assertFalse("progress was NaN", progress.value.isNaN())
	}

	@Test
	fun `progress rewinds so a retried attempt reports only what it has sent`() {
		// OkHttp re-writes the body on redirects, auth challenges and connection retries. Progress
		// must restart rather than stay pinned at 100% while the bytes actually go out again.
		val body = body()
		body.writeTo(Buffer())
		assertEquals(100f, progress.value, 0.001f)

		val samples = mutableListOf<Float>()
		body.writeTo(samplingSink { samples += progress.value })

		assertTrue("expected samples on the second attempt", samples.isNotEmpty())
		assertTrue("progress stayed at 100% instead of rewinding: $samples", samples.first() < 100f)
		assertEquals(100f, progress.value, 0.001f)
	}

	@Test
	fun `writeTo refuses to short-write when the file shrank after construction`() {
		val body = body()
		file.writeBytes(contents.copyOfRange(0, TOTAL_SIZE / 2))

		try {
			body.writeTo(Buffer())
			fail("expected EOFException rather than a body shorter than its Content-Length")
		} catch (e: EOFException) {
			assertTrue("message should name the shortfall: ${e.message}", e.message!!.contains("ended early"))
		}
	}

	@Test
	fun `writeTo does not overrun when the file grew after construction`() {
		val body = body()
		file.writeBytes(ByteArray(TOTAL_SIZE * 2) { 7 })

		val sink = Buffer()
		body.writeTo(sink)

		// Exactly the declared length: writing more would desync the request from Content-Length.
		assertEquals(TOTAL_SIZE.toLong(), sink.size)
	}

	@Test
	fun `requireReadable accepts an intact file`() {
		body().requireReadable()
	}

	@Test
	fun `requireReadable rejects a deleted file`() {
		val body = body()
		assertTrue(file.delete())
		try {
			body.requireReadable()
			fail("expected IOException for a deleted source")
		} catch (e: IOException) {
			assertTrue(e.message!!.contains("no longer exists"))
		}
	}

	@Test
	fun `requireReadable rejects a truncated file`() {
		val body = body()
		file.writeBytes(contents.copyOfRange(0, 10))
		try {
			body.requireReadable()
			fail("expected IOException for a truncated source")
		} catch (e: IOException) {
			assertTrue(e.message!!.contains("too short"))
		}
	}

	/**
	 * A sink that discards bytes but invokes [onWrite] as each batch lands, so a test can observe
	 * the progress flow part-way through a write instead of only after it finishes.
	 */
	private fun samplingSink(onWrite: () -> Unit) = object : Sink {
		override fun write(source: Buffer, byteCount: Long) {
			source.skip(byteCount)
			onWrite()
		}

		override fun flush() = Unit
		override fun timeout(): Timeout = Timeout.NONE
		override fun close() = Unit
	}.buffer()

	private companion object {
		const val TOTAL_SIZE = 64 * 1024 + 517
	}
}
