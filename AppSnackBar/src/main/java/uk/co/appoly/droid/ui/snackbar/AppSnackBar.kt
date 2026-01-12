package uk.co.appoly.droid.ui.snackbar

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.ConsistentCopyVisibility

/**
 * A Snackbar that can be used to display messages to the user.
 *
 * Provide a [LocalAppSnackBarColors] composition local to set the colors for the snackbar.
 *
 * @param snackbarData The data to display in the snackbar.
 *
 * @throws IllegalArgumentException if [SnackbarData.visuals] is not an instance of [SnackBarVisualsWithType]
 */
@Composable
fun AppSnackBar(
	snackbarData: SnackbarData
) {
	val visuals = try {
		snackbarData.visuals as SnackBarVisualsWithType
	} catch (e: Exception) {
		throw IllegalArgumentException("SnackbarData.visuals must be an instance of SnackBarVisualsWithType\nYou should be using SnackbarHostState.showSnackbar extension with type parameter", e)
	}
	AppSnackBar(
		type = visuals.type,
		snackbarData = snackbarData
	)
}

/**
 * A Snackbar that can be used to display messages to the user.
 *
 * Provide a [LocalAppSnackBarColors] composition local to set the colors for the snackbar.
 *
 * @param type The type of snackbar to display, to set the the Color.
 * @param snackbarData The data to display in the snackbar.
 */
@Composable
private fun AppSnackBar(
	type: SnackBarType = SnackBarType.Error,
	snackbarData: SnackbarData
) {
	val colors = LocalAppSnackBarColors.current
	Snackbar(
		snackbarData = snackbarData,
		containerColor = colors.get(type),
		contentColor = Color.White
	)
}

/**
 * Enumeration of available snackbar types, each with its own visual styling.
 *
 * Used to differentiate between different types of messages shown to users:
 * - [Info]: For general information messages
 * - [Success]: For successful operation notifications
 * - [Error]: For error messages and warnings
 */
enum class SnackBarType {
	Info,
	Success,
	Error
}

/**
 * A data class to hold the colors for the snackbar.
 *
 * @param info The color for the [info][SnackBarType.Info] snackbar.
 * @param success The color for the [success][SnackBarType.Success] snackbar.
 * @param error The color for the [error][SnackBarType.Error] snackbar.
 */
data class AppSnackBarColors(
	val info: Color,
	val success: Color,
	val error: Color
) {
	/**
	 * Returns the appropriate color for the specified snackbar type.
	 *
	 * @param type The snackbar type to get the color for
	 * @return The color associated with the specified type
	 */
	fun get(type: SnackBarType): Color {
		return when (type) {
			SnackBarType.Info -> info
			SnackBarType.Success -> success
			SnackBarType.Error -> error
		}
	}
}

/**
 * Default colors for AppSnackBar.
 *
 * Provides a standard set of colors for different snackbar types:
 * - Info: Blue
 * - Success: Green
 * - Error: Red
 */
object AppSnackBarDefaults {
	val colors = AppSnackBarColors(
		info = Color.Blue,
		success = Color.Green,
		error = Color.Red
	)
}

/**
 * CompositionLocal to provide AppSnackBar colors throughout the composition hierarchy.
 *
 * Default value is provided by [AppSnackBarDefaults.colors].
 */
val LocalAppSnackBarColors = compositionLocalOf<AppSnackBarColors> { AppSnackBarDefaults.colors }

/**
 * Extension of [SnackbarVisuals] that includes a [type] property to specify the visual style of the snackbar.
 *
 * This is used internally by [AppSnackBar] to determine the styling of the snackbar.
 *
 * @property actionLabel Optional label for the snackbar action button
 * @property duration How long to display the snackbar
 * @property message The message to display in the snackbar
 * @property withDismissAction Whether to show a dismiss action
 * @property type The type of snackbar to display, determining its color
 */
@ConsistentCopyVisibility
data class SnackBarVisualsWithType internal constructor(
	override val actionLabel: String?,
	override val duration: SnackbarDuration,
	override val message: String,
	override val withDismissAction: Boolean,
	val type: SnackBarType
) : SnackbarVisuals

/**
 * Shows or queues to be shown a [Snackbar] at the bottom of the [Scaffold] to which this state
 * is attached and suspends until the snackbar has disappeared.
 *
 * [SnackbarHostState] guarantees to show at most one snackbar at a time. If this function is
 * called while another snackbar is already visible, it will be suspended until this snackbar is
 * shown and subsequently addressed. If the caller is cancelled, the snackbar will be removed
 * from display and/or the queue to be displayed.
 *
 * This is for use with the [AppSnackBar] composable to make use of the [type] param.
 *
 * @see SnackbarHostState.showSnackbar
 *
 * To change the Snackbar appearance, change it in 'snackbarHost' on the [Scaffold].
 *
 * @param message text to be shown in the Snackbar
 * @param actionLabel optional action label to show as button in the Snackbar
 * @param withDismissAction a boolean to show a dismiss action in the Snackbar. This is
 *   recommended to be set to true for better accessibility when a Snackbar is set with a
 *   [SnackbarDuration.Indefinite]
 * @param duration duration to control how long snackbar will be shown in [SnackbarHost], either
 *   [SnackbarDuration.Short], [SnackbarDuration.Long] or [SnackbarDuration.Indefinite].
 * @param type [SnackBarType] The type of snackbar to display, this will set the Color of the snackbar.
 * @return [SnackbarResult.ActionPerformed] if option action has been clicked or
 *   [SnackbarResult.Dismissed] if snackbar has been dismissed via timeout or by the user
 */
suspend fun SnackbarHostState.showSnackbar(
	message: String,
	actionLabel: String? = null,
	withDismissAction: Boolean = false,
	duration: SnackbarDuration =
		if (actionLabel == null) SnackbarDuration.Short else SnackbarDuration.Indefinite,
	type: SnackBarType = SnackBarType.Info
): SnackbarResult {
	return showSnackbar(
		SnackBarVisualsWithType(
			actionLabel = actionLabel,
			duration = duration,
			message = message,
			withDismissAction = withDismissAction,
			type = type
		)
	)
}
