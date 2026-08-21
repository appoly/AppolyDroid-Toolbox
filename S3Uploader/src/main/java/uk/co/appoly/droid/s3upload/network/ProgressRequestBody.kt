package uk.co.appoly.droid.s3upload.network

import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.EOFException
import java.io.File
import java.io.IOException

/**
 * A custom OkHttp RequestBody implementation that tracks upload progress.
 *
 * This class wraps a file and reports upload progress to a MutableStateFlow
 * as the file is being uploaded. Progress values range from 0.0 to 100.0.
 *
 * The file's length is captured once, at construction, and used as both the declared
 * `Content-Length` and the exact number of bytes written. A body that promises one length and then
 * streams another fails as an opaque transport error, so pinning the length keeps a file that
 * changes mid-upload from producing a confusing failure — or, worse, a silently truncated object
 * in S3.
 *
 * @property file The file to be uploaded
 * @property mediaType The MIME type of the file content
 * @property progressFlow A flow that receives progress updates during upload (0.0f-100.0f)
 */
class ProgressRequestBody(
	private val file: File,
	private val mediaType: MediaType?,
	private val progressFlow: MutableStateFlow<Float>
) : RequestBody() {

	/** Length of [file] as observed when this body was created. See the class docs. */
	private val declaredLength: Long = file.length()

	/**
	 * Returns the content type (MIME type) of the request body.
	 *
	 * @return The MIME type of the file, or null if unknown
	 */
	override fun contentType(): MediaType? = mediaType

	/**
	 * Returns the length of the request body in bytes.
	 *
	 * @return The size of the file in bytes, as captured when this body was created
	 */
	override fun contentLength(): Long = declaredLength

	/**
	 * Checks up-front that [file] can still supply the bytes this body has promised, so a vanished
	 * or truncated source fails with a clear message instead of a mid-flight transport error.
	 *
	 * @throws IOException if the file is missing, unreadable, or shorter than [contentLength]
	 */
	@Throws(IOException::class)
	fun requireReadable() = file.requireReadableForUpload(declaredLength)

	/**
	 * Writes the file content to the given sink, updating progress as bytes are written.
	 *
	 * This is where progress tracking happens. As chunks of the file are read and written
	 * to the network, the progressFlow is updated with the current percentage complete.
	 *
	 * Exactly [contentLength] bytes are written. OkHttp may call this more than once (redirects,
	 * auth challenges, connection retries), so progress rewinds to 0 on each attempt and always
	 * reflects what has actually reached the network this time round.
	 *
	 * @param sink The destination where file contents are written
	 * @throws EOFException if the file ended before [contentLength] bytes could be written
	 */
	override fun writeTo(sink: BufferedSink) {
		progressFlow.value = 0f
		file.source().use { source ->
			val buffer = Buffer()
			var uploadedBytes = 0L
			while (uploadedBytes < declaredLength) {
				val bytesRead = source.read(buffer, minOf(SEGMENT_SIZE, declaredLength - uploadedBytes))
				if (bytesRead == -1L) {
					// Never short-write: S3 would accept the truncated body and store a corrupt object.
					throw EOFException(
						"Source file ended early: wrote $uploadedBytes of $declaredLength bytes " +
							"from ${file.absolutePath}",
					)
				}
				sink.write(buffer, bytesRead)
				uploadedBytes += bytesRead
				progressFlow.value = (uploadedBytes * 100f / declaredLength).coerceIn(0f, 100f)
			}
		}
	}

	private companion object {
		/** Okio transfer size; also the granularity of progress updates. */
		const val SEGMENT_SIZE = 8L * 1024L
	}
}
