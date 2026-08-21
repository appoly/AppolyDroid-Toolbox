package uk.co.appoly.droid.s3upload.multipart.network

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * A [RequestBody] that streams a byte range of [file] straight to the network sink instead of
 * materialising it on the heap.
 *
 * Multipart uploads previously read each part into a `ByteArray` before handing it to OkHttp, so a
 * single upload session held `chunkSize * maxConcurrentParts` bytes of live Java heap (15MB with the
 * defaults), multiplied again by every concurrently running upload worker. Streaming keeps the
 * per-part footprint at [SEGMENT_SIZE] regardless of chunk size.
 *
 * Each [writeTo] opens its own [FileInputStream] positioned at [offset], so the body is re-readable:
 * OkHttp may call [writeTo] more than once (redirects, auth challenges, connection retries), and the
 * manager's own retry loop reuses the same body. Concurrent parts therefore never contend on a
 * shared file handle.
 *
 * @property file The local file to read from. Must remain readable and at least `offset + byteCount`
 *   bytes long for the lifetime of the request.
 * @property offset Byte offset within [file] at which this part starts.
 * @property byteCount Number of bytes this part contains. Reported as the request's `Content-Length`.
 * @property contentType The media type sent to S3, or `null` to omit it.
 * @property onBytesWritten Optional progress callback, invoked as the part streams with the
 *   cumulative number of bytes written **for the current attempt**. Because [writeTo] restarts from
 *   [offset] on every attempt, this counter rewinds to 0 whenever the request is retried, so the
 *   reported figure is always the truth about what has reached the network this time round. Called
 *   on the network thread roughly every [SEGMENT_SIZE] bytes — keep it cheap and non-blocking.
 */
internal class FilePartRequestBody(
	private val file: File,
	private val offset: Long,
	private val byteCount: Long,
	private val contentType: MediaType?,
	private val onBytesWritten: ((Long) -> Unit)? = null,
) : RequestBody() {

	override fun contentType(): MediaType? = contentType

	override fun contentLength(): Long = byteCount

	override fun writeTo(sink: BufferedSink) {
		onBytesWritten?.invoke(0L)
		FileInputStream(file).use { input ->
			input.channel.position(offset)
			input.source().use { source ->
				val buffer = Buffer()
				var written = 0L
				while (written < byteCount) {
					val read = source.read(buffer, minOf(SEGMENT_SIZE, byteCount - written))
					if (read == -1L) {
						// Never short-write a part: S3 would accept the truncated body and the
						// upload would only fail later, on reassembly.
						throw EOFException(
							"Source file ended early: wrote $written of $byteCount bytes " +
								"from offset $offset in ${file.absolutePath}",
						)
					}
					sink.write(buffer, read)
					written += read
					onBytesWritten?.invoke(written)
				}
			}
		}
	}

	/**
	 * Checks up-front that [file] can still satisfy this part, so a vanished or truncated source
	 * fails with a clear message instead of a mid-flight transport error.
	 */
	@Throws(IOException::class)
	fun requireReadable() {
		if (!file.exists()) {
			throw IOException("Source file no longer exists: ${file.absolutePath}")
		}
		val available = file.length() - offset
		if (available < byteCount) {
			throw IOException(
				"Source file too short for part: need $byteCount bytes at offset $offset " +
					"but only $available available (file length ${file.length()})",
			)
		}
	}

	private companion object {
		/** Okio transfer size; also the granularity of [onBytesWritten] callbacks. */
		const val SEGMENT_SIZE = 8L * 1024L
	}
}
