package uk.co.appoly.droid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.nav3.LocalNav3Navigator
import uk.co.appoly.droid.nav3.LocalTabsNavigator
import uk.co.appoly.droid.nav3.Nav3Screen
import uk.co.appoly.droid.nav3.Nav3TabsHost
import uk.co.appoly.droid.nav3.rememberNav3RetentionScope
import uk.co.appoly.droid.nav3.rememberTabsNav3Navigator

/**
 * Demo of [TabsNav3Navigator] + [Nav3TabsHost]: per-tab stacks, directional tab-slide
 * animations, spring-slide for in-tab push/pop, and cross-tab [TabsNav3Navigator.navigateToTab].
 *
 * Bottom bar chrome is **app code** — the host wires both ambient navigators and tab-aware transitions.
 *
 * Also shows the required [rememberNav3RetentionScope]: tabs keep visited entries on the back
 * stack so state survives switches, and that retention needs an explicit owner that ends with
 * the identity behind the UI (sign-out / account switch). This demo has no auth boundary —
 * popping the shell is enough — but production tabs hosts must call
 * [uk.co.appoly.droid.nav3.Nav3RetentionScope.clear] when the member ends.
 */
@Serializable
data object TabsDemoScreen : Nav3Screen {

	@OptIn(ExperimentalMaterial3Api::class)
	@Composable
	override fun Content() {
		// Outer navigator (root showcase stack) for exiting this demo.
		val rootNavigator = LocalNav3Navigator.current

		val tabItems = remember {
			listOf(
				TabItem("Home", Icons.Default.Home, TabsHomeScreen),
				TabItem("Rooms", Icons.Default.Star, TabsRoomsScreen),
				TabItem("Settings", Icons.Default.Settings, TabsSettingsScreen),
			)
		}
		// parent = ambient root showcase navigator (for LocalNav3Navigator.current?.parent?.pop())
		val tabsNavigator = rememberTabsNav3Navigator(tabItems.map { it.screen })
		// Owns ViewModel stores of retained tab entries. Survives config change; call clear()
		// on sign-out so the next session cannot reattach to this one's stores.
		val retentionScope = rememberNav3RetentionScope()

		Scaffold(
			topBar = {
				TopAppBar(
					title = { Text("Tabs + transitions") },
					navigationIcon = {
						// Same as tabsNavigator.parent?.pop() once inside Nav3TabsHost.
						IconButton(onClick = { rootNavigator?.pop() }) {
							Text("←")
						}
					},
				)
			},
			bottomBar = {
				NavigationBar {
					tabItems.forEach { tab ->
						NavigationBarItem(
							selected = tabsNavigator.currentTab == tab.screen,
							onClick = { tabsNavigator.switchTab(tab.screen) },
							icon = {
								Icon(imageVector = tab.icon, contentDescription = tab.label)
							},
							label = { Text(tab.label) },
						)
					}
				}
			},
		) { padding ->
			Nav3TabsHost(
				modifier = Modifier
					.fillMaxSize()
					.padding(padding),
				tabsNavigator = tabsNavigator,
				retentionScope = retentionScope,
			)
		}
	}
}

private data class TabItem(
	val label: String,
	val icon: ImageVector,
	val screen: Nav3Screen,
)

@Serializable
data object TabsHomeScreen : Nav3Screen {
	@Composable
	override fun Content() {
		val tabsNavigator = LocalTabsNavigator.current
		var tapCount by rememberSaveable { mutableIntStateOf(0) }

		TabPage {
			Text(
				text = "Home tab. Bump the counter, switch tabs, come back — saveable entry state is retained.",
				style = MaterialTheme.typography.bodyMedium,
			)
			Button(onClick = { tapCount++ }) {
				Text("Tapped $tapCount times")
			}
			Card(
				modifier = Modifier
					.fillMaxWidth()
					.clickable {
						tabsNavigator?.navigateToTab(
							TabsRoomsScreen,
							TabsRoomDetailScreen(roomName = "Bedroom"),
						)
					},
				colors = CardDefaults.cardColors(
					containerColor = MaterialTheme.colorScheme.tertiaryContainer,
				),
			) {
				Text(
					modifier = Modifier.padding(16.dp),
					text = "Bedroom is heating — view it",
					style = MaterialTheme.typography.titleMedium,
				)
				Text(
					modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
					text = "navigateToTab(Rooms, Bedroom detail) — cross-tab jump with tab-slide animation.",
					style = MaterialTheme.typography.bodySmall,
				)
			}
		}
	}
}

@Serializable
data object TabsRoomsScreen : Nav3Screen {
	@Composable
	override fun Content() {
		val navigator = LocalNav3Navigator.current
		val rooms = listOf("Living room", "Bedroom", "Kitchen")

		TabPage {
			Text(
				text = "Rooms tab — push a detail, switch tabs, return: the detail stays on this tab's stack. In-tab transitions use spring-slide + parallax.",
				style = MaterialTheme.typography.bodyMedium,
			)
			rooms.forEach { name ->
				Card(
					modifier = Modifier
						.fillMaxWidth()
						.clickable { navigator?.push(TabsRoomDetailScreen(roomName = name)) },
				) {
					Text(
						modifier = Modifier.padding(16.dp),
						text = name,
						style = MaterialTheme.typography.titleMedium,
					)
				}
			}
		}
	}
}

@Serializable
data class TabsRoomDetailScreen(val roomName: String) : Nav3Screen {
	@Composable
	override fun Content() {
		TabPage {
			Text(text = roomName, style = MaterialTheme.typography.headlineMedium)
			Text(
				text = "Sub-screen on the Rooms tab stack. System back pops this first; on the tab root it falls back to Home (exit-through-home), then the demo top bar exits the shell.",
				style = MaterialTheme.typography.bodyMedium,
			)
		}
	}
}

@Serializable
data object TabsSettingsScreen : Nav3Screen {
	@Composable
	override fun Content() {
		TabPage {
			Text(
				text = "Settings tab — another independent stack root. Tab switches use full directional slides.",
				style = MaterialTheme.typography.bodyMedium,
			)
		}
	}
}

@Composable
private fun TabPage(content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
		content = content,
	)
}
