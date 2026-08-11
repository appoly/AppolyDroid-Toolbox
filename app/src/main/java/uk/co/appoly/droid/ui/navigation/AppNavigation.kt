package uk.co.appoly.droid.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.rememberNavBackStack
import uk.co.appoly.droid.nav3.Nav3ScreenHost
import uk.co.appoly.droid.ui.screens.HomeScreen

/**
 * Root navigation for the toolbox showcase app.
 *
 * Uses [Nav3ScreenHost] so the demo is also living documentation for the Nav3Navigation
 * module: destinations are self-rendering [uk.co.appoly.droid.nav3.Nav3Screen]s and screens
 * navigate via [uk.co.appoly.droid.nav3.LocalNav3Navigator].
 */
@Composable
fun AppNavigation() {
	val backStack = rememberNavBackStack(HomeScreen)

	Nav3ScreenHost(
		modifier = Modifier.fillMaxSize(),
		backStack = backStack,
	)
}
