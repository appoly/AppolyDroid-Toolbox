package uk.co.appoly.droid.s3upload.multipart.network

import okhttp3.MediaType.Companion.toMediaType
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Verifies that [FilePartRequestBody] streams exactly the byte range it was asked for.
 *
 * These are the guarantees the multipart uploader relies on: an off-by-one in the offset or
 * length would upload a subtly corrupt part that S3 accepts and only fails on reassembly, so
 * the ranges are asserted byte-for-byte rather than by size alone.
 */
class FilePartRequestBodyTest {

	private lateinit var file: File

	/** Deterministic content: byte at index i is `i % 251` (prime, so no alignment with part sizes). */
	private val contents = ByteArray(TOTAL_SIZE) { (it % 251).toByte() }

	@Before
	fun setUp() {
		file = File.createTempFile("multipart-part-body", ".bin")
		file.writeBytes(contents)
	}

	@After
	fun tearDown() {
		file.delete()
	}

	private fun body(offset: Long, byteCount: Long) = FilePartRequestBody(
		file = file,
		offset = offset,
		byteCount = byteCount,
		contentType = "application/octet-stream".toMediaType(),
	)

	@Test
	fun `contentLength reports the part size, not the file size`() {
		assertEquals(CHUNK.toLong(), body(offset = 0, byteCount = CHUNK.toLong()).contentLength())
		assertEquals(CHUNK.toLong(), body(offset = CHUNK.toLong(), byteCount = CHUNK.toLong()).contentLength())
	}

	@Test
	fun `contentType is passed through and may be null`() {
		assertEquals("application/octet-stream", body(0, 1).contentType().toString())
		assertNull(
			FilePartRequestBody(file = file, offset = 0, byteCount = 1, contentType = null).contentType(),
		)
	}

	@Test
	fun `writeTo emits exactly the requested byte range`() {
		// Middle part: catches an offset that is ignored as well as one applied twice.
		val offset = CHUNK.toLong()
		val sink = Buffer()
		body(offset = offset, byteCount = CHUNK.toLong()).writeTo(sink)

		assertEquals(CHUNK.toLong(), sink.size)
		assertArrayEquals(contents.copyOfRange(CHUNK, CHUNK * 2), sink.readByteArray())
	}

	@Test
	fun `writeTo emits a trailing part shorter than the chunk size`() {
		val offset = (CHUNK * 2).toLong()
		val remaining = TOTAL_SIZE - CHUNK * 2
		val sink = Buffer()
		body(offset = offset, byteCount = remaining.toLong()).writeTo(sink)

		assertEquals(remaining.toLong(), sink.size)
		assertArrayEquals(contents.copyOfRange(CHUNK * 2, TOTAL_SIZE), sink.readByteArray())
	}

	@Test
	fun `every part concatenates back into the original file`() {
		val reassembled = Buffer()
		var offset = 0L
		while (offset < TOTAL_SIZE) {
			val size = minOf(CHUNK.toLong(), TOTAL_SIZE - offset)
			body(offset = offset, byteCount = size).writeTo(reassembled)
			offset += size
		}

		assertArrayEquals(contents, reassembled.readByteArray())
	}

	@Test
	fun `writeTo is repeatable so OkHttp can retry the same body`() {
		// The body must not be one-shot: OkHttp re-writes it on redirects, auth challenges and
		// connection retries, and the manager's own retry loop reuses the same instance.
		val body = body(offset = CHUNK.toLong(), byteCount = CHUNK.toLong())
		val expected = contents.copyOfRange(CHUNK, CHUNK * 2)

		repeat(3) { attempt ->
			val sink = Buffer()
			body.writeTo(sink)
			assertArrayEquals("attempt $attempt differed", expected, sink.readByteArray())
		}
	}

	@Test
	fun `concurrent parts each read their own range`() {
		// Regression guard for the shared-RandomAccessFile design this replaced: each writeTo
		// must open its own handle, so parallel parts cannot disturb each other's file position.
		val partCount = TOTAL_SIZE / CHUNK
		val start = CountDownLatch(1)
		val done = CountDownLatch(partCount)
		val results = arrayOfNulls<ByteArray>(partCount)
		val failures = mutableListOf<Throwable>()

		val threads = (0 until partCount).map { index ->
			Thread {
				try {
					start.await()
					val sink = Buffer()
					body(offset = (index * CHUNK).toLong(), byteCount = CHUNK.toLong()).writeTo(sink)
					results[index] = sink.readByteArray()
				} catch (t: Throwable) {
					synchronized(failures) { failures += t }
				} finally {
					done.countDown()
				}
			}.also { it.start() }
		}

		start.countDown()
		assertTrue("threads did not finish", done.await(30, TimeUnit.SECONDS))
		threads.forEach { it.join() }

		synchronized(failures) {
			assertTrue("writeTo threw concurrently: $failures", failures.isEmpty())
		}
		for (index in 0 until partCount) {
			assertArrayEquals(
				"part $index read the wrong range",
				contents.copyOfRange(index * CHUNK, (index + 1) * CHUNK),
				results[index],
			)
		}
	}

	@Test
	fun `requireReadable rejects a file that is too short for the part`() {
		val body = body(offset = TOTAL_SIZE.toLong() - 10, byteCount = CHUNK.toLong())
		try {
			body.requireReadable()
			fail("expected IOException for a part that runs past the end of the file")
		} catch (e: IOException) {
			assertTrue("message should report the shortfall: ${e.message}", e.message!!.contains("too short"))
		}
	}

	@Test
	fun `requireReadable rejects a missing file`() {
		val body = body(offset = 0, byteCount = CHUNK.toLong())
		assertTrue(file.delete())
		try {
			body.requireReadable()
			fail("expected IOException for a deleted file")
		} catch (e: IOException) {
			assertTrue("message should report the missing file: ${e.message}", e.message!!.contains("no longer exists"))
		}
	}

	@Test
	fun `requireReadable accepts a part that ends exactly at the end of the file`() {
		body(offset = (TOTAL_SIZE - CHUNK).toLong(), byteCount = CHUNK.toLong()).requireReadable()
	}

	@Test
	fun `writeTo fails loudly if the file shrinks after validation`() {
		// A part must never be silently short-written: S3 would accept a truncated part.
		val body = body(offset = 0, byteCount = CHUNK.toLong())
		body.requireReadable()
		file.writeBytes(contents.copyOfRange(0, CHUNK / 2))

		try {
			body.writeTo(Buffer())
			fail("expected EOFException once the source file was truncated")
		} catch (e: EOFException) {
			// Surfaces as a non-recoverable part failure rather than a corrupt upload.
		}
	}

	@Test
	fun `onBytesWritten reports cumulative progress ending at the part size`() {
		val reported = mutableListOf<Long>()
		FilePartRequestBody(
			file = file,
			offset = CHUNK.toLong(),
			byteCount = CHUNK.toLong(),
			contentType = null,
			onBytesWritten = { reported += it },
		).writeTo(Buffer())

		assertTrue("expected several intermediate reports, got $reported", reported.size > 2)
		assertEquals("should open at zero", 0L, reported.first())
		assertEquals("should close at the part size", CHUNK.toLong(), reported.last())
		assertEquals("progress went backwards: $reported", reported.sorted(), reported)
		assertTrue("progress overshot the part: $reported", reported.all { it <= CHUNK.toLong() })
	}

	@Test
	fun `onBytesWritten rewinds at the start of every attempt`() {
		// Each attempt re-reads from `offset`, so the count must restart. Reporting a stale high
		// water mark would make a retrying upload look further along than it is.
		val reported = mutableListOf<Long>()
		val body = FilePartRequestBody(
			file = file,
			offset = 0,
			byteCount = CHUNK.toLong(),
			contentType = null,
			onBytesWritten = { reported += it },
		)

		body.writeTo(Buffer())
		assertEquals(CHUNK.toLong(), reported.last())

		reported.clear()
		body.writeTo(Buffer())
		assertEquals(0L, reported.first())
		assertEquals(CHUNK.toLong(), reported.last())
	}

	@Test
	fun `progress reporting is optional`() {
		// The default null callback must not change what gets written.
		val sink = Buffer()
		FilePartRequestBody(file, 0, CHUNK.toLong(), null).writeTo(sink)
		assertArrayEquals(contents.copyOfRange(0, CHUNK), sink.readByteArray())
	}

	private companion object {
		const val CHUNK = 64 * 1024
		const val TOTAL_SIZE = CHUNK * 3 + 1234
	}
}
