package uk.co.appoly.droid

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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

	// Reconnection events
	private val _connectivityEvents = MutableSharedFlow<ConnectivityRestoredEvent>(
		replay = 0,
		extraBufferCapacity = 10
	)

	// Reconnection tracking
	private var previouslyConnected = true
	private var offlineSince: Long? = null
	private var reconnectionMonitorJob: Job? = null

	private val connectivityManager: ConnectivityManager by lazy {
		getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
	}

	// Track each network's validated internet capability
	private val networkValidationMap = mutableMapOf<Network, Boolean>()

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
				}
				recomputeConnectivity()
			}

			override fun onLost(network: Network) {
				ConnectivityLog.v(this@ConnectivityMonitorApplication, "Network lost: $network")
				networkValidationMap.remove(network)
				recomputeConnectivity()
			}

			override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
				val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
						capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
				ConnectivityLog.v(this@ConnectivityMonitorApplication, "Capabilities changed: $network validated=$validated")
				if (networkValidationMap[network] != validated) {
					networkValidationMap[network] = validated
					recomputeConnectivity()
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
		val connectedNow = networkValidationMap.values.any { it }
		if (_isConnected.value != connectedNow) {
			ConnectivityLog.v(this@ConnectivityMonitorApplication, "Connectivity changed -> $connectedNow")
			_isConnected.value = connectedNow
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