package uk.co.appoly.droid

/**
 * Represents the transport type of the current network connection.
 *
 * This enum provides a simplified view of the network transport, useful for
 * determining whether the device is on WiFi, cellular, or another connection type.
 */
enum class NetworkTransportType {
	/**
	 * No network connection available.
	 */
	NONE,

	/**
	 * Connected via WiFi.
	 */
	WIFI,

	/**
	 * Connected via cellular data (2G, 3G, 4G, 5G, etc.).
	 */
	CELLULAR,

	/**
	 * Connected via Ethernet.
	 */
	ETHERNET,

	/**
	 * Connected via VPN.
	 * Note: When connected via VPN, the underlying transport (WiFi/Cellular)
	 * may also be present. This indicates VPN is the primary transport.
	 */
	VPN,

	/**
	 * Connected via Bluetooth tethering or other transport types.
	 */
	OTHER;

	/**
	 * Returns true if this is a metered connection type (typically cellular).
	 *
	 * Note: This is a heuristic. WiFi connections can also be metered in some cases.
	 * For accurate metering information, use [android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED].
	 */
	val isTypicallyMetered: Boolean
		get() = this == CELLULAR

	/**
	 * Returns true if this represents an active network connection.
	 */
	val isConnected: Boolean
		get() = this != NONE
}
