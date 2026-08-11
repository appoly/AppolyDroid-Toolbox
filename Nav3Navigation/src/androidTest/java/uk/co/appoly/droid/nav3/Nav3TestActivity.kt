package uk.co.appoly.droid.nav3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.serialization.Serializable

/**
 * Host Activity for the on-device suite.
 *
 * The composition is established in [onCreate] rather than by a Compose test rule, because
 * `recreate()` is one of the things under test — a rule-driven `setContent` does not survive it,
 * and faking recreation would defeat the purpose of running on a device at all.
 *
 * [tabs] and [retentionScope] are published back to the test as the composition resolves them,
 * so assertions can reach the live instances after a recreation.
 */
class Nav3TestActivity : ComponentActivity() {

	/** The navigator the current composition is driving. Reassigned on every recreation. */
	@Volatile
	var tabs: TabsNav3Navigator? = null
		private set

	/** The retention scope the current composition resolved. Survives recreation (Activity-scoped). */
	@Volatile
	var retentionScope: Nav3RetentionScope? = null
		private set

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			val scope = rememberNav3RetentionScope()
			val navigator = rememberTabsNav3Navigator(deviceTabOrder)
			retentionScope = scope
			tabs = navigator
			Nav3TabsHost(
				modifier = Modifier.testTag(HOST_TAG),
				tabsNavigator = navigator,
				retentionScope = scope,
			)
		}
	}

	companion object {
		const val HOST_TAG: String = "nav3_tabs_host"
	}
}

// --- Fixtures ---------------------------------------------------------------------------------

internal val deviceTabOrder: List<Nav3Screen> =
	listOf(DeviceHomeTab, DeviceRoomsTab, DeviceSettingsTab)

/**
 * Records what each device probe screen resolved on its latest composition, so assertions can
 * compare ViewModel identity and saveable state across recreation.
 */
internal object DeviceProbes {
	private val viewModels = mutableMapOf<String, DeviceProbeViewModel>()
	private val counters = mutableMapOf<String, MutableState<Int>>()
	private val compositions = mutableMapOf<String, Int>()
	private val navigators = mutableMapOf<String, Nav3Navigator?>()
	private val hosts = mutableMapOf<String, Int>()

	fun record(
		tab: String,
		viewModel: DeviceProbeViewModel,
		counter: MutableState<Int>,
		navigator: Nav3Navigator?,
		hostIdentity: Int,
	) {
		synchronized(this) {
			hosts[tab] = hostIdentity
			viewModels[tab] = viewModel
			counters[tab] = counter
			navigators[tab] = navigator
			compositions[tab] = (compositions[tab] ?: 0) + 1
		}
	}

	fun viewModelFor(tab: String): DeviceProbeViewModel? = synchronized(this) { viewModels[tab] }

	fun counterFor(tab: String): MutableState<Int>? = synchronized(this) { counters[tab] }

	/**
	 * The ambient navigator in force when [tab] last composed.
	 *
	 * Recreation installs a new [TabsNav3Navigator], so this identifies *which* composition
	 * produced the current recording. Waiting on a plain composition counter is not enough: the
	 * outgoing Activity can compose once more on its way out, and the incoming navigator is
	 * published before its tab content has rendered.
	 */
	fun navigatorFor(tab: String): Nav3Navigator? = synchronized(this) { navigators[tab] }

	/** identityHashCode of the Activity that hosted [tab]'s last composition. */
	fun hostFor(tab: String): Int = synchronized(this) { hosts[tab] ?: 0 }

	/** How many times [tab] has entered composition since the last [reset]. */
	fun compositionCount(tab: String): Int = synchronized(this) { compositions[tab] ?: 0 }

	fun reset() {
		synchronized(this) {
			viewModels.clear()
			counters.clear()
			navigators.clear()
			hosts.clear()
			compositions.clear()
		}
	}
}

internal class DeviceProbeViewModel : ViewModel() {
	var cleared: Boolean = false
		private set

	override fun onCleared() {
		cleared = true
	}
}

@Composable
private fun DeviceProbeContent(tab: String) {
	val viewModel: DeviceProbeViewModel = viewModel()
	val counter = rememberSaveable { mutableStateOf(0) }
	val host = LocalContext.current
	DeviceProbes.record(tab, viewModel, counter, LocalNav3Navigator.current, System.identityHashCode(host))
	Text("Tab $tab • ${counter.value}", modifier = Modifier.testTag("tab_$tab"))
}

@Serializable
internal data object DeviceHomeTab : Nav3Screen {
	@Composable
	override fun Content() = DeviceProbeContent("Home")
}

@Serializable
internal data object DeviceRoomsTab : Nav3Screen {
	@Composable
	override fun Content() = DeviceProbeContent("Rooms")
}

@Serializable
internal data object DeviceSettingsTab : Nav3Screen {
	@Composable
	override fun Content() = DeviceProbeContent("Settings")
}

@Serializable
internal data class DeviceDetailScreen(val itemId: Int) : Nav3Screen {
	@Composable
	override fun Content() {
		Text("Detail $itemId", modifier = Modifier.testTag("detail_$itemId"))
	}
}
