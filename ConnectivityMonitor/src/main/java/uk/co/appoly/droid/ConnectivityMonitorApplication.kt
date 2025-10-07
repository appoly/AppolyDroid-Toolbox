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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlin.collections.any
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.isInitialized

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

	private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
    }

	// Track each network's validated internet capability
	private val networkValidationMap = mutableMapOf<Network, Boolean>()

	private val networkCallback: ConnectivityManager.NetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
				ConnectivityLog.v("Network available: $network")
                networkValidationMap[network] = false
                recomputeConnectivity()
            }

            override fun onLost(network: Network) {
				ConnectivityLog.v("Network lost: $network")
                networkValidationMap.remove(network)
                recomputeConnectivity()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
				ConnectivityLog.v("Capabilities changed: $network validated=$validated")
                if (networkValidationMap[network] != validated) {
                    networkValidationMap[network] = validated
                    recomputeConnectivity()
                }
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
			ConnectivityLog.v(this, "Connectivity changed -> $connectedNow")
			_isConnected.value = connectedNow
		}
	}

	private fun initConnectivityMonitoring() {
		connectivityManager.registerDefaultNetworkCallback(networkCallback)
		// Seed initial state using currently known networks
		connectivityManager.allNetworks.forEach { n ->
			val caps = connectivityManager.getNetworkCapabilities(n)
			val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
					caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
			networkValidationMap[n] = validated
		}
		recomputeConnectivity()
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
		 * The delay in milliseconds before the [isConnectedDebounced] flow emits `true` after
		 * a connection is established. Defaults to 2000ms.
		 */
		var onlineDebounceDelayMillis: Long = 2000L
	}
}