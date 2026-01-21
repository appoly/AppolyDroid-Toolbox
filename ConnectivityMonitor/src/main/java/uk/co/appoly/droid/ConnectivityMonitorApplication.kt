package uk.co.appoly.droid

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import androidx.annotation.CallSuper
import com.duck.flexilogger.FlexiLog
import com.duck.flexilogger.LoggingLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import uk.co.appoly.droid.ConnectivityMonitorApplication.Companion.isConnected
import uk.co.appoly.droid.ConnectivityMonitorApplication.Companion.isConnectedDebounced
import uk.co.appoly.droid.ConnectivityMonitorApplication.Companion.onlineDebounceDelayMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An [Application] subclass that provides lifecycle-aware connectivity monitoring.
 * It offers two [StateFlow]s to observe the network state:
 * - [isConnected]: An immediate reflection of the device's connectivity status.
 * - [isConnectedDebounced]: A debounced version that delays online status changes to prevent
 *   flickering UI during transient network fluctuations.
 *
 * To use, either extend your Application class with this class or declare it directly in your
 * AndroidManifest.xml.
 *
 * Example of extending your Application class:
 * ```kotlin
 * class MyApp : ConnectivityMonitorApplication() {
 *     override fun onCreate() {
 *         super.onCreate()
 *         // Your other application setup
 *     }
 * }
 * ```
 *
 * Then, from any part of your app (e.g., a ViewModel or Composable):
 * ```kotlin
 * val isOnline by ConnectivityMonitorApplication.isConnectedDebounced.collectAsState()
 * // Use isOnline to show/hide UI elements
 * ```
 */
open class ConnectivityMonitorApplication : Application() {

	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

	// Internal raw connectivity (immediate)
	private val _isConnected = MutableStateFlow(false)

	// Internal network type tracking
	private val _networkType = MutableStateFlow(NetworkTransportType.NONE)

	// Network type change events
	private val _networkTypeChanges = MutableSharedFlow<NetworkTypeChangedEvent>(
		replay = 0,
		extraBufferCapacity = 10
	)

	// Track metered status
	private val _isMetered = MutableStateFlow(true)

	// Reconnection events
	private val _connectivityEvents = MutableSharedFlow<ConnectivityRestoredEvent>(
		replay = 0,
		extraBufferCapacity = 10
	)

	// Reconnection tracking
	private var previouslyConnected = true
	private var offlineSince: Long? = null
	private var reconnectionMonitorJob: Job? = null
	private var networkTypeMonitorJob: Job? = null
	private var previousNetworkType = NetworkTransportType.NONE

	private val connectivityManager: ConnectivityManager by lazy {
		getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
	}

	// Track each network's validated internet capability
	private val networkValidationMap = mutableMapOf<Network, Boolean>()

	// Track each network's capabilities for transport type determination
	private val networkCapabilitiesMap = mutableMapOf<Network, NetworkCapabilities>()

	private val networkCallback: ConnectivityManager.NetworkCallback by lazy {
		object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(network: Network) {
				ConnectivityLog.v(this@ConnectivityMonitorApplication, "Network available: $network")
				networkValidationMap[network] = false
				// Request capabilities immediately to get validation state
				connectivityManager.getNetworkCapabilities(network)?.let { caps ->
					val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
							caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
					networkValidationMap[network] = validated
					networkCapabilitiesMap[network] = caps
				}
				recomputeConnectivity()
			}

			override fun onLost(network: Network) {
				ConnectivityLog.v(this@ConnectivityMonitorApplication, "Network lost: $network")
				networkValidationMap.remove(network)
				networkCapabilitiesMap.remove(network)
				recomputeConnectivity()
			}

			override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
				val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
						capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
				ConnectivityLog.v(this@ConnectivityMonitorApplication, "Capabilities changed: $network validated=$validated")

				// Always update capabilities map for transport type tracking
				networkCapabilitiesMap[network] = capabilities

				if (networkValidationMap[network] != validated) {
					networkValidationMap[network] = validated
					recomputeConnectivity()
				} else {
					// Still recompute network type even if validation didn't change
					recomputeNetworkType()
				}
			}

			override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
				ConnectivityLog.v(this@ConnectivityMonitorApplication, "Blocked status changed: $network blocked=$blocked")
				if (blocked) {
					networkValidationMap[network] = false
				} else {
					// Re-check capabilities when unblocked
					connectivityManager.getNetworkCapabilities(network)?.let { caps ->
						val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
								caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
						networkValidationMap[network] = validated
						networkCapabilitiesMap[network] = caps
					}
				}
				recomputeConnectivity()
			}
		}
	}

	/**
	 * Set the logger for this class
	 * @param logger [FlexiLog] the logger to use
	 * @param loggingLevel [LoggingLevel] the logging level to use
	 */
	fun setLogger(
		logger: FlexiLog,
		loggingLevel: LoggingLevel = LoggingLevel.NONE
	) {
		ConnectivityLog.updateLogger(logger, loggingLevel)
	}

	private fun recomputeConnectivity() {
		// Primary check: any validated network in our tracking map
		val hasValidatedNetwork = networkValidationMap.values.any { it }

		// Fallback check: verify activeNetwork actually exists and is validated
		// This catches edge cases where onLost() doesn't fire (e.g., manual radio disable on some devices)
		val activeNetwork = connectivityManager.activeNetwork
		val hasActiveNetwork = activeNetwork != null &&
				connectivityManager.getNetworkCapabilities(activeNetwork)?.let { caps ->
					caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
							caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
				} == true

		// Consider connected only if BOTH our tracking map AND the system agree
		// This provides resilience against stale networkValidationMap entries
		val connectedNow = hasValidatedNetwork && hasActiveNetwork

		if (_isConnected.value != connectedNow) {
			ConnectivityLog.v(
				this@ConnectivityMonitorApplication,
				"Connectivity changed -> $connectedNow (tracked=$hasValidatedNetwork, active=$hasActiveNetwork)"
			)
			_isConnected.value = connectedNow
		}
		recomputeNetworkType()
	}

	/**
	 * Recomputes the current network transport type based on available validated networks.
	 */
	private fun recomputeNetworkType() {
		// Find the primary validated network's capabilities
		val validatedNetworks = networkValidationMap.filter { it.value }
		val primaryCapabilities =
            validatedNetworks.keys.firstNotNullOfOrNull { networkCapabilitiesMap[it] }

        val newNetworkType = if (primaryCapabilities != null) {
			determineTransportType(primaryCapabilities)
		} else {
			NetworkTransportType.NONE
		}

		// Check metered status
		val isNowMetered = primaryCapabilities?.let {
			!it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
		} ?: true

		if (_isMetered.value != isNowMetered) {
			_isMetered.value = isNowMetered
		}

		if (_networkType.value != newNetworkType) {
			ConnectivityLog.v(this@ConnectivityMonitorApplication, "Network type changed: ${_networkType.value} -> $newNetworkType (metered=$isNowMetered)")
			_networkType.value = newNetworkType
		}
	}

	/**
	 * Determines the primary transport type from network capabilities.
	 * Priority: VPN > WiFi > Ethernet > Cellular > Bluetooth > Other
	 */
	private fun determineTransportType(capabilities: NetworkCapabilities): NetworkTransportType {
		return when {
			// VPN takes priority as it wraps other transports
			capabilities.hasTransport(TRANSPORT_VPN) -> NetworkTransportType.VPN
			// Then check common transports in order of preference
			capabilities.hasTransport(TRANSPORT_WIFI) -> NetworkTransportType.WIFI
			capabilities.hasTransport(TRANSPORT_ETHERNET) -> NetworkTransportType.ETHERNET
			capabilities.hasTransport(TRANSPORT_CELLULAR) -> NetworkTransportType.CELLULAR
			capabilities.hasTransport(TRANSPORT_BLUETOOTH) -> NetworkTransportType.OTHER
			else -> NetworkTransportType.OTHER
		}
	}

	private fun initConnectivityMonitoring() {
		// Check active network first for immediate state
		val activeNetwork = connectivityManager.activeNetwork
		val activeCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
		val isActiveValidated = activeCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
				activeCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

		if (activeNetwork != null && isActiveValidated) {
			networkValidationMap[activeNetwork] = true
		}

		// Register callback
		connectivityManager.registerDefaultNetworkCallback(networkCallback)

		// Recompute based on initial state
		recomputeConnectivity()

		// Start monitoring for reconnection events
		startReconnectionMonitoring()

		// Start monitoring for network type changes
		startNetworkTypeMonitoring()
	}

	private fun startNetworkTypeMonitoring() {
		networkTypeMonitorJob = applicationScope.launch {
			_networkType.collect { currentType ->
				handleNetworkTypeChange(currentType)
			}
		}
	}

	private fun handleNetworkTypeChange(currentType: NetworkTransportType) {
		if (previousNetworkType != currentType) {
			ConnectivityLog.d(this, "Network type transition: $previousNetworkType -> $currentType")

			// Emit network type change event
			applicationScope.launch {
				_networkTypeChanges.emit(
					NetworkTypeChangedEvent(
						previousType = previousNetworkType,
						currentType = currentType,
						isMetered = _isMetered.value
					)
				)
			}

			onNetworkTypeChanged(previousNetworkType, currentType)
			previousNetworkType = currentType
		}
	}

	/**
	 * Called when the network transport type changes (e.g., WiFi to Cellular).
	 * Override in subclasses to implement custom behavior.
	 *
	 * @param previousType The previous network transport type
	 * @param currentType The new network transport type
	 */
	protected open fun onNetworkTypeChanged(
		previousType: NetworkTransportType,
		currentType: NetworkTransportType
	) {
		// Override in subclasses for custom behavior
	}

	private fun startReconnectionMonitoring() {
		reconnectionMonitorJob = applicationScope.launch {
			_isConnected.collect { isConnected ->
				handleConnectivityChange(isConnected)
			}
		}
	}


	private fun handleConnectivityChange(isConnected: Boolean) {
		when {
			// Device went offline
			!isConnected && previouslyConnected -> {
				offlineSince = System.currentTimeMillis()
				ConnectivityLog.d(this, "Device went offline")
				onDeviceWentOffline()
			}
			// Device came back online
			isConnected && !previouslyConnected -> {
				val wasOfflineSince = offlineSince
				if (wasOfflineSince != null) {
					val offlineDuration = (System.currentTimeMillis() - wasOfflineSince).milliseconds
					ConnectivityLog.d(this, "Device reconnected after $offlineDuration offline")

					// Emit reconnection event
					applicationScope.launch {
						_connectivityEvents.emit(ConnectivityRestoredEvent(offlineDuration))
					}

					onDeviceReconnected(offlineDuration)
				}
				offlineSince = null
			}
		}
		previouslyConnected = isConnected
	}

	/**
	 * Called when device loses internet connectivity.
	 * Override in subclasses to implement custom offline behavior.
	 */
	protected open fun onDeviceWentOffline() {
		// Override in subclasses for custom behavior
	}

	/**
	 * Called when device regains internet connectivity.
	 * Override in subclasses to implement custom reconnection behavior.
	 *
	 * @param offlineDuration How long the device was offline
	 */
	protected open fun onDeviceReconnected(offlineDuration: Duration) {
		// Override in subclasses for custom behavior
	}

	@CallSuper
	override fun onCreate() {
		super.onCreate()
		instance = this
		initConnectivityMonitoring()
	}

	companion object {
		private const val NOT_INITIALIZED_ERROR =
			"ConnectivityMonitorApplication is not initialized!\nEnsure your Application class extends ConnectivityMonitorApplication and " +
					"is properly set in the AndroidManifest.xml or set ConnectivityMonitorApplication directly in your AndroidManifest.xml " +
					"if you don't have a custom Application class."
		private lateinit var instance: ConnectivityMonitorApplication

		/**
		 * Provides immediate access to the device's connectivity state.
		 * `true` if connected, `false` otherwise.
		 *
		 * @throws IllegalStateException if [ConnectivityMonitorApplication] has not been initialized.
		 */
		val isConnected: StateFlow<Boolean>
			get() {
				if (!::instance.isInitialized) throw kotlin.IllegalStateException(NOT_INITIALIZED_ERROR)
				return instance._isConnected.asStateFlow()
			}

		/**
		 * A debounced connectivity state flow.
		 *
		 * When the device goes offline, the value is updated to `false` immediately.
		 * When the device comes online, the value is updated to `true` after a delay, which helps
		 * prevent UI flickering from transient network changes.
		 *
		 * The debounce delay can be configured via [onlineDebounceDelayMillis].
		 *
		 * @throws IllegalStateException if [ConnectivityMonitorApplication] has not been initialized.
		 */
		@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
		val isConnectedDebounced: StateFlow<Boolean> by lazy {
			if (!::instance.isInitialized) throw kotlin.IllegalStateException(NOT_INITIALIZED_ERROR)
			instance._isConnected
				.transformLatest { value ->
					if (value) {
						// Delay only transitions to online
						delay(onlineDebounceDelayMillis)
						emit(true)
					} else {
						// Emit offline immediately
						emit(false)
					}
				}
				.stateIn(
					scope = instance.applicationScope,
					started = SharingStarted.Eagerly,
					initialValue = instance._isConnected.value
				)
		}

		/**
		 * Flow that emits events when device regains internet connectivity.
		 * Each event contains the duration the device was offline.
		 *
		 * @throws IllegalStateException if [ConnectivityMonitorApplication] has not been initialized.
		 */
		val connectivityEvents: SharedFlow<ConnectivityRestoredEvent>
			get() {
				if (!::instance.isInitialized) throw IllegalStateException(NOT_INITIALIZED_ERROR)
				return instance._connectivityEvents.asSharedFlow()
			}

		/**
		 * Provides immediate access to the current network transport type.
		 *
		 * Returns [NetworkTransportType.NONE] when offline, or the primary transport type
		 * (WiFi, Cellular, etc.) when connected.
		 *
		 * @throws IllegalStateException if [ConnectivityMonitorApplication] has not been initialized.
		 */
		val networkType: StateFlow<NetworkTransportType>
			get() {
				if (!::instance.isInitialized) throw IllegalStateException(NOT_INITIALIZED_ERROR)
				return instance._networkType.asStateFlow()
			}

		/**
		 * Flow that emits events when the network transport type changes.
		 *
		 * This is useful for detecting transitions between WiFi and cellular networks,
		 * for example to pause uploads when switching to a metered connection.
		 *
		 * @throws IllegalStateException if [ConnectivityMonitorApplication] has not been initialized.
		 */
		val networkTypeChanges: SharedFlow<NetworkTypeChangedEvent>
			get() {
				if (!::instance.isInitialized) throw IllegalStateException(NOT_INITIALIZED_ERROR)
				return instance._networkTypeChanges.asSharedFlow()
			}

		/**
		 * Provides immediate access to whether the current network connection is metered.
		 *
		 * This is more accurate than checking [networkType] alone, as WiFi connections
		 * can also be metered in some configurations.
		 *
		 * @throws IllegalStateException if [ConnectivityMonitorApplication] has not been initialized.
		 */
		val isMetered: StateFlow<Boolean>
			get() {
				if (!::instance.isInitialized) throw IllegalStateException(NOT_INITIALIZED_ERROR)
				return instance._isMetered.asStateFlow()
			}

		/**
		 * The delay in milliseconds before the [isConnectedDebounced] flow emits `true` after
		 * a connection is established. Defaults to 2000ms.
		 */
		var onlineDebounceDelayMillis: Long = 2000L
	}
}

/**
 * Connectivity event emitted when device regains internet connectivity
 * @property offlineDuration How long the device was offline
 */
data class ConnectivityRestoredEvent(
	val offlineDuration: Duration
)