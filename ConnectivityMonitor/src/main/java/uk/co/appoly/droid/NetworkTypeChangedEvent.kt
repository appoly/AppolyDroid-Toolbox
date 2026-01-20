package uk.co.appoly.droid

/**
 * Event emitted when the network transport type changes.
 *
 * This event provides information about network type transitions, such as
 * switching from WiFi to cellular or vice versa.
 *
 * @property previousType The transport type before the change
 * @property currentType The transport type after the change
 * @property isMetered Whether the current network is metered.
 *                     This is the authoritative metering status from the system,
 *                     not just based on transport type.
 */
data class NetworkTypeChangedEvent(
	val previousType: NetworkTransportType,
	val currentType: NetworkTransportType,
	val isMetered: Boolean
) {
	/**
	 * Returns true if this represents a transition from an unmetered to a metered network.
	 *
	 * Useful for scenarios like pausing large uploads when switching from WiFi to cellular.
	 */
	val becameMetered: Boolean
		get() = !previousType.isTypicallyMetered && currentType.isTypicallyMetered

	/**
	 * Returns true if this represents a transition from a metered to an unmetered network.
	 *
	 * Useful for scenarios like resuming paused uploads when WiFi becomes available.
	 */
	val becameUnmetered: Boolean
		get() = previousType.isTypicallyMetered && !currentType.isTypicallyMetered

	/**
	 * Returns true if the device lost all network connectivity.
	 */
	val wentOffline: Boolean
		get() = previousType != NetworkTransportType.NONE && currentType == NetworkTransportType.NONE

	/**
	 * Returns true if the device gained network connectivity from being offline.
	 */
	val cameOnline: Boolean
		get() = previousType == NetworkTransportType.NONE && currentType != NetworkTransportType.NONE
}
