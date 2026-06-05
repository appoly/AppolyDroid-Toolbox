package uk.co.appoly.droid.s3upload.multipart.config

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [UploadConstraints] defaults, factory presets, validation and serialization.
 */
class UploadConstraintsTest {

	@Test
	fun `DEFAULT has connected network and no extra requirements`() {
		val c = UploadConstraints.DEFAULT
		assertEquals(UploadNetworkType.CONNECTED, c.networkType)
		assertFalse(c.requiresCharging)
		assertFalse(c.requiresBatteryNotLow)
		assertFalse(c.requiresStorageNotLow)
		assertTrue(c.autoResumeWhenSatisfied)
		assertEquals(2000L, c.autoResumeDelayMs)
	}

	@Test
	fun `wifiOnly requires an unmetered network`() {
		assertEquals(UploadNetworkType.UNMETERED, UploadConstraints.wifiOnly().networkType)
	}

	@Test
	fun `powerSaving requires unmetered, charging and battery-not-low`() {
		val c = UploadConstraints.powerSaving()
		assertEquals(UploadNetworkType.UNMETERED, c.networkType)
		assertTrue(c.requiresCharging)
		assertTrue(c.requiresBatteryNotLow)
	}

	@Test
	fun `lowPriority requires battery and storage not low on any network`() {
		val c = UploadConstraints.lowPriority()
		assertEquals(UploadNetworkType.CONNECTED, c.networkType)
		assertTrue(c.requiresBatteryNotLow)
		assertTrue(c.requiresStorageNotLow)
	}

	@Test(expected = IllegalArgumentException::class)
	fun `a negative auto-resume delay is rejected`() {
		UploadConstraints(autoResumeDelayMs = -1)
	}

	@Test
	fun `round-trips through JSON`() {
		val original = UploadConstraints.powerSaving()
		val restored = Json.decodeFromString<UploadConstraints>(Json.encodeToString(original))
		assertEquals(original, restored)
	}
}
