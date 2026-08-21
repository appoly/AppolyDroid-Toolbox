package uk.co.appoly.droid.s3upload.network

import java.io.File
import java.io.IOException

/**
 * Checks that this file can still satisfy an upload of [expectedBytes] bytes.
 *
 * A request body declares its `Content-Length` up front but streams its contents later, so a file
 * that is deleted, replaced or truncated in between produces an opaque transport failure — OkHttp
 * simply reports that the body disagreed with the length it promised. Validating first turns that
 * into a clear, actionable error.
 *
 * @param expectedBytes The number of bytes the request body has declared it will send.
 * @throws IOException if the file is missing, unreadable, or shorter than [expectedBytes].
 */
@Throws(IOException::class)
internal fun File.requireReadableForUpload(expectedBytes: Long) {
	if (!exists()) {
		throw IOException("Source file no longer exists: $absolutePath")
	}
	if (!canRead()) {
		throw IOException("Source file is not readable: $absolutePath")
	}
	val actualLength = length()
	if (actualLength < expectedBytes) {
		throw IOException(
			"Source file too short for upload: declared $expectedBytes bytes " +
				"but only $actualLength available ($absolutePath)",
		)
	}
}
