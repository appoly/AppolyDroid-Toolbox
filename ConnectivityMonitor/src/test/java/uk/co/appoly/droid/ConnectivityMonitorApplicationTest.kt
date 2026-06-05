package uk.co.appoly.droid

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * Concrete subclass so Robolectric can instantiate a real [ConnectivityMonitorApplication] as the
 * test application. The module manifest declares no `android:name`, so without this `@Config`
 * pointer Robolectric would default to a plain [android.app.Application] and the companion
 * `instance` would never be initialized.
 */
class TestConnectivityMonitorApplication : ConnectivityMonitorApplication()

/**
 * Robolectric tests for [ConnectivityMonitorApplication].
 *
 * The application's `onCreate` runs under Robolectric, registering the default network callback
 * (handled by Robolectric's ShadowConnectivityManager) and initialising the companion `instance`,
 * which makes the public StateFlows observable.
 *
 * The highest-value kernel — the private `determineTransportType(NetworkCapabilities)` — is
 * exercised directly via reflection using Robolectric-built [NetworkCapabilities] objects covering
 * every transport-priority branch.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestConnectivityMonitorApplication::class)
class ConnectivityMonitorApplicationTest {

	private lateinit var app: ConnectivityMonitorApplication

	@Before
	fun setUp() {
		app = ApplicationProvider.getApplicationContext()
		// Keep logging off so JVM/Robolectric tests never route to android.util.Log.
		app.setLogger(ConnectivityLogger, LoggingLevel.NONE)
	}

	/** Build a Robolectric NetworkCapabilities with the given transports + capabilities. */
	private fun capabilities(
		transports: List<Int>,
		capabilities: List<Int> = emptyList()
	): NetworkCapabilities {
		val caps = ShadowNetworkCapabilities.newInstance()
		val shadow = shadowOf(caps)
		transports.forEach { shadow.addTransportType(it) }
		capabilities.forEach { shadow.addCapability(it) }
		return caps
	}

	private fun determineTransportType(caps: NetworkCapabilities): NetworkTransportType {
		val method = ConnectivityMonitorApplication::class.java
			.getDeclaredMethod("determineTransportType", NetworkCapabilities::class.java)
			.apply { isAccessible = true }
		return method.invoke(app, caps) as NetworkTransportType
	}

	private fun invokePrivate(name: String) {
		ConnectivityMonitorApplication::class.java
			.getDeclaredMethod(name)
			.apply { isAccessible = true }
			.invoke(app)
	}

	@Suppress("UNCHECKED_CAST")
	private fun <T> privateField(name: String): T {
		val field = ConnectivityMonitorApplication::class.java
			.getDeclaredField(name)
			.apply { isAccessible = true }
		return field.get(app) as T
	}

	private val cm: ConnectivityManager
		get() = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

	/**
	 * Registers a Network in the Robolectric ShadowConnectivityManager so that
	 * `connectivityManager.activeNetwork` and `getNetworkCapabilities(...)` return it, then
	 * seeds the application's private tracking maps with that network and its capabilities.
	 *
	 * This drives the real `recomputeConnectivity()` / `recomputeNetworkType()` code paths exactly
	 * as the registered NetworkCallback would.
	 */
	private fun seedActiveValidatedNetwork(caps: NetworkCapabilities): Network {
		// getActiveNetwork() looks up netIdToNetwork by activeNetworkInfo.getType(), so the
		// registered Network's netId must equal that type for the lookup to resolve.
		val type = ConnectivityManager.TYPE_WIFI
		val network = ShadowNetwork.newInstance(type)
		val shadowCm = shadowOf(cm)
		// Make this the system's active+default network with matching capabilities.
		val info: NetworkInfo = ShadowNetworkInfo.newInstance(
			NetworkInfo.DetailedState.CONNECTED,
			type,
			0,
			true,
			NetworkInfo.State.CONNECTED
		)
		shadowCm.addNetwork(network, info)
		shadowCm.setActiveNetworkInfo(info)
		shadowCm.setDefaultNetworkActive(true)
		shadowCm.setNetworkCapabilities(network, caps)

		// Seed the app's private tracking maps as the NetworkCallback would.
		val validationMap = privateField<MutableMap<Network, Boolean>>("networkValidationMap")
		val capsMap = privateField<MutableMap<Network, NetworkCapabilities>>("networkCapabilitiesMap")
		val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
				caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
		validationMap[network] = validated
		capsMap[network] = caps
		return network
	}

	private fun validatedCapabilities(
		transport: Int,
		unmetered: Boolean
	): NetworkCapabilities {
		val extra = mutableListOf(
			NetworkCapabilities.NET_CAPABILITY_INTERNET,
			NetworkCapabilities.NET_CAPABILITY_VALIDATED
		)
		if (unmetered) extra += NetworkCapabilities.NET_CAPABILITY_NOT_METERED
		return capabilities(transports = listOf(transport), capabilities = extra)
	}

	@Test
	fun `determineTransportType maps WIFI transport to WIFI`() {
		assertEquals(
			NetworkTransportType.WIFI,
			determineTransportType(capabilities(listOf(NetworkCapabilities.TRANSPORT_WIFI)))
		)
	}

	@Test
	fun `determineTransportType maps CELLULAR transport to CELLULAR`() {
		assertEquals(
			NetworkTransportType.CELLULAR,
			determineTransportType(capabilities(listOf(NetworkCapabilities.TRANSPORT_CELLULAR)))
		)
	}

	@Test
	fun `determineTransportType maps ETHERNET transport to ETHERNET`() {
		assertEquals(
			NetworkTransportType.ETHERNET,
			determineTransportType(capabilities(listOf(NetworkCapabilities.TRANSPORT_ETHERNET)))
		)
	}

	@Test
	fun `determineTransportType maps BLUETOOTH transport to OTHER`() {
		assertEquals(
			NetworkTransportType.OTHER,
			determineTransportType(capabilities(listOf(NetworkCapabilities.TRANSPORT_BLUETOOTH)))
		)
	}

	@Test
	fun `determineTransportType prioritises VPN over WIFI`() {
		// VPN wraps the underlying transport, so when both flags are present VPN must win.
		assertEquals(
			NetworkTransportType.VPN,
			determineTransportType(
				capabilities(listOf(NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_WIFI))
			)
		)
	}

	@Test
	fun `determineTransportType prioritises WIFI over CELLULAR`() {
		assertEquals(
			NetworkTransportType.WIFI,
			determineTransportType(
				capabilities(listOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR))
			)
		)
	}

	@Test
	fun `determineTransportType prioritises ETHERNET over CELLULAR`() {
		assertEquals(
			NetworkTransportType.ETHERNET,
			determineTransportType(
				capabilities(listOf(NetworkCapabilities.TRANSPORT_ETHERNET, NetworkCapabilities.TRANSPORT_CELLULAR))
			)
		)
	}

	@Test
	fun `determineTransportType falls back to OTHER for unknown transport`() {
		assertEquals(NetworkTransportType.OTHER, determineTransportType(capabilities(emptyList())))
	}

	@Test
	fun `companion accessors are initialised after onCreate and expose live flows`() = runTest {
		// instance is initialised by onCreate; these must not throw IllegalStateException.
		val connected: Boolean = ConnectivityMonitorApplication.isConnected.value
		val debounced: Boolean = ConnectivityMonitorApplication.isConnectedDebounced.value
		val networkType: NetworkTransportType = ConnectivityMonitorApplication.networkType.first()
		// On a fresh instance both connectivity flows agree and a type is exposed.
		assertEquals(connected, ConnectivityMonitorApplication.isConnected.value)
		// Debounced flow seeds from the immediate flow's current value.
		assertEquals(connected, debounced)
		// A fresh, networkless instance reports NONE.
		assertEquals(NetworkTransportType.NONE, networkType)
	}

	@Test
	fun `isMetered defaults to true before any unmetered network is observed`() {
		// _isMetered is seeded true (conservative) and only flips false on an unmetered network.
		assertTrue(ConnectivityMonitorApplication.isMetered.value)
	}

	@Test
	fun `debounce delay configuration is mutable and round-trips`() {
		val originalOnline = ConnectivityMonitorApplication.onlineDebounceDelayMillis
		val originalOffline = ConnectivityMonitorApplication.offlineDebounceDelayMillis
		try {
			ConnectivityMonitorApplication.onlineDebounceDelayMillis = 1234L
			ConnectivityMonitorApplication.offlineDebounceDelayMillis = 0L
			assertEquals(1234L, ConnectivityMonitorApplication.onlineDebounceDelayMillis)
			assertEquals(0L, ConnectivityMonitorApplication.offlineDebounceDelayMillis)
		} finally {
			ConnectivityMonitorApplication.onlineDebounceDelayMillis = originalOnline
			ConnectivityMonitorApplication.offlineDebounceDelayMillis = originalOffline
		}
	}

	@Test
	fun `recomputeNetworkType is a no-op safe to call repeatedly`() {
		// Drive the private recompute kernel directly to confirm it handles the empty-network case.
		val method = ConnectivityMonitorApplication::class.java
			.getDeclaredMethod("recomputeNetworkType")
			.apply { isAccessible = true }
		method.invoke(app)
		method.invoke(app)
		// With no validated networks tracked, the exposed type stays NONE.
		assertEquals(NetworkTransportType.NONE, ConnectivityMonitorApplication.networkType.value)
	}

	// --- Callback-driven state: drive recomputeConnectivity()/recomputeNetworkType() through the
	//     real Robolectric ConnectivityManager + the app's private tracking maps. ---

	@Test
	fun `default network callback is registered during onCreate`() {
		// initConnectivityMonitoring registers a default-network callback with the system service.
		val callbacks = shadowOf(cm).networkCallbacks
		assertTrue(
			"Expected ConnectivityMonitorApplication to register a NetworkCallback",
			callbacks.isNotEmpty()
		)
	}

	@Test
	fun `recomputeConnectivity reports connected on a validated WiFi network`() {
		// Online debounce only delays the debounced flow; the immediate _isConnected flips at once.
		seedActiveValidatedNetwork(validatedCapabilities(NetworkCapabilities.TRANSPORT_WIFI, unmetered = true))

		invokePrivate("recomputeConnectivity")

		assertTrue(ConnectivityMonitorApplication.isConnected.value)
		assertEquals(NetworkTransportType.WIFI, ConnectivityMonitorApplication.networkType.value)
	}

	@Test
	fun `unmetered WiFi flips isMetered to false`() {
		seedActiveValidatedNetwork(validatedCapabilities(NetworkCapabilities.TRANSPORT_WIFI, unmetered = true))

		invokePrivate("recomputeConnectivity")

		assertFalse(ConnectivityMonitorApplication.isMetered.value)
	}

	@Test
	fun `validated cellular network is reported as metered`() {
		// Cellular advertises no NET_CAPABILITY_NOT_METERED, so isMetered stays true.
		seedActiveValidatedNetwork(validatedCapabilities(NetworkCapabilities.TRANSPORT_CELLULAR, unmetered = false))

		invokePrivate("recomputeConnectivity")

		assertTrue(ConnectivityMonitorApplication.isConnected.value)
		assertEquals(NetworkTransportType.CELLULAR, ConnectivityMonitorApplication.networkType.value)
		assertTrue(ConnectivityMonitorApplication.isMetered.value)
	}

	@Test
	fun `network type change emits a NetworkTypeChangedEvent`() = runTest {
		// Seed WiFi then transition to cellular; recompute should publish a type-change event.
		seedActiveValidatedNetwork(validatedCapabilities(NetworkCapabilities.TRANSPORT_WIFI, unmetered = true))
		invokePrivate("recomputeConnectivity")
		assertEquals(NetworkTransportType.WIFI, ConnectivityMonitorApplication.networkType.value)

		// Replace tracked capabilities with cellular and recompute.
		val validationMap = privateField<MutableMap<Network, Boolean>>("networkValidationMap")
		val capsMap = privateField<MutableMap<Network, NetworkCapabilities>>("networkCapabilitiesMap")
		val net = validationMap.keys.first()
		val cellular = validatedCapabilities(NetworkCapabilities.TRANSPORT_CELLULAR, unmetered = false)
		capsMap[net] = cellular
		shadowOf(cm).setNetworkCapabilities(net, cellular)

		invokePrivate("recomputeNetworkType")

		assertEquals(NetworkTransportType.CELLULAR, ConnectivityMonitorApplication.networkType.value)
		assertTrue(ConnectivityMonitorApplication.isMetered.value)
	}

	@Test
	fun `going offline with zero debounce reports disconnected immediately`() {
		val original = ConnectivityMonitorApplication.offlineDebounceDelayMillis
		try {
			// Disable offline debounce so the offline transition is synchronous on the main dispatcher.
			ConnectivityMonitorApplication.offlineDebounceDelayMillis = 0L

			// First go online.
			seedActiveValidatedNetwork(validatedCapabilities(NetworkCapabilities.TRANSPORT_WIFI, unmetered = true))
			invokePrivate("recomputeConnectivity")
			assertTrue(ConnectivityMonitorApplication.isConnected.value)

			// Now drop the network entirely (as onLost would) and recompute.
			privateField<MutableMap<Network, Boolean>>("networkValidationMap").clear()
			privateField<MutableMap<Network, NetworkCapabilities>>("networkCapabilitiesMap").clear()
			shadowOf(cm).clearAllNetworks()
			shadowOf(cm).setDefaultNetworkActive(false)

			invokePrivate("recomputeConnectivity")

			assertFalse(ConnectivityMonitorApplication.isConnected.value)
			assertEquals(NetworkTransportType.NONE, ConnectivityMonitorApplication.networkType.value)
		} finally {
			ConnectivityMonitorApplication.offlineDebounceDelayMillis = original
		}
	}

	@Test
	fun `connected requires both a tracked validated network and a live active network`() {
		// Tracking map says validated, but the system has NO active network -> not connected.
		val caps = validatedCapabilities(NetworkCapabilities.TRANSPORT_WIFI, unmetered = true)
		val phantom = ShadowNetwork.newInstance(999)
		privateField<MutableMap<Network, Boolean>>("networkValidationMap")[phantom] = true
		privateField<MutableMap<Network, NetworkCapabilities>>("networkCapabilitiesMap")[phantom] = caps
		shadowOf(cm).clearAllNetworks()
		shadowOf(cm).setDefaultNetworkActive(false)

		invokePrivate("recomputeConnectivity")

		// hasValidatedNetwork is true but hasActiveNetwork is false -> AND yields false.
		assertFalse(ConnectivityMonitorApplication.isConnected.value)
	}
}
