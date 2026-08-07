package uk.co.appoly.droid.nav3

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable

/**
 * Lightweight [Nav3Screen] fixtures for unit/Compose tests.
 *
 * `@Serializable` so [TabsNav3Navigator.saver] / [NavKey] reflection restore works in tab tests.
 */
@Serializable
internal data object HomeScreen : Nav3Screen {
	@Composable
	override fun Content() {
		Text("Home")
	}
}

@Serializable
internal data class DetailScreen(val itemId: Int) : Nav3Screen {
	@Composable
	override fun Content() {
		Text("Detail $itemId")
	}
}

@Serializable
internal data object SettingsScreen : Nav3Screen {
	override val metadata: Map<String, Any>
		get() = mapOf("test_meta" to "settings")

	@Composable
	override fun Content() {
		Text("Settings")
	}
}

@Serializable
internal data object ListScreen : Nav3Screen {
	@Composable
	override fun Content() {
		Text("List")
	}
}

/** Not used as a tab root in [TabsNav3NavigatorTest] — for "unknown tab" rejection tests. */
@Serializable
internal data object OtherTabScreen : Nav3Screen {
	@Composable
	override fun Content() {
		Text("OtherTab")
	}
}
