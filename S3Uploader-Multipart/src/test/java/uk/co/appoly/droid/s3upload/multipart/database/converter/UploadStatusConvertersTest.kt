package uk.co.appoly.droid.s3upload.multipart.database.converter

import uk.co.appoly.droid.s3upload.multipart.database.entity.PartUploadStatus
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the Room [UploadStatusConverters] enum <-> String type converters.
 */
class UploadStatusConvertersTest {

	private val converters = UploadStatusConverters()

	@Test
	fun `session status round-trips through its name for every value`() {
		UploadSessionStatus.entries.forEach { status ->
			val stored = converters.fromUploadSessionStatus(status)
			assertEquals(status.name, stored)
			assertEquals(status, converters.toUploadSessionStatus(stored))
		}
	}

	@Test
	fun `part status round-trips through its name for every value`() {
		PartUploadStatus.entries.forEach { status ->
			val stored = converters.fromPartUploadStatus(status)
			assertEquals(status.name, stored)
			assertEquals(status, converters.toPartUploadStatus(stored))
		}
	}
}
