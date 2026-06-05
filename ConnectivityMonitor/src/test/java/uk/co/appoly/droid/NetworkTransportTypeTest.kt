package uk.co.appoly.droid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the derived properties on [NetworkTransportType]
 * ([NetworkTransportType.isConnected], [NetworkTransportType.isTypicallyMetered]).
 */
class NetworkTransportTypeTest {

	@Test
	fun `isConnected is false only for NONE`() {
		assertFalse(NetworkTransportType.NONE.isConnected)
		assertTrue(NetworkTransportType.WIFI.isConnected)
		assertTrue(NetworkTransportType.CELLULAR.isConnected)
		assertTrue(NetworkTransportType.ETHERNET.isConnected)
		assertTrue(NetworkTransportType.VPN.isConnected)
		assertTrue(NetworkTransportType.OTHER.isConnected)
	}

	@Test
	fun `isTypicallyMetered is true only for CELLULAR`() {
		assertTrue(NetworkTransportType.CELLULAR.isTypicallyMetered)
		assertFalse(NetworkTransportType.WIFI.isTypicallyMetered)
		assertFalse(NetworkTransportType.NONE.isTypicallyMetered)
		assertFalse(NetworkTransportType.ETHERNET.isTypicallyMetered)
		assertFalse(NetworkTransportType.VPN.isTypicallyMetered)
		assertFalse(NetworkTransportType.OTHER.isTypicallyMetered)
	}

	@Test
	fun `enum exposes all six transport types`() {
		assertEquals(6, NetworkTransportType.entries.size)
	}
}
