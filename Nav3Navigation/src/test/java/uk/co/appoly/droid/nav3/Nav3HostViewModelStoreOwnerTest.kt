package uk.co.appoly.droid.nav3

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [LocalNav3HostViewModelStoreOwner] / [nav3HostViewModelStoreOwner]: the host captures the
 * pre-decorator [LocalViewModelStoreOwner] so screens can resolve navigator-scoped ViewModels
 * even after the per-entry ViewModelStore decorator shadows the ambient owner.
 */
@RunWith(AndroidJUnit4::class)
class Nav3HostViewModelStoreOwnerTest {

	@get:Rule
	val composeRule = createComposeRule()

	@Test
	fun `is null outside a host`() {
		var seen: ViewModelStoreOwner? = null
		composeRule.setContent {
			seen = LocalNav3HostViewModelStoreOwner.current
			Text("Outside")
		}
		composeRule.runOnIdle {
			assertNull(seen)
		}
	}

	@Test
	fun `host provides the owner it was composed under`() {
		var outsideHost: ViewModelStoreOwner? = null
		var insideScreen: ViewModelStoreOwner? = null
		val probe = object : Nav3Screen {
			@Composable
			override fun Content() {
				insideScreen = nav3HostViewModelStoreOwner()
				Text("Probe")
			}
		}
		val backStack = NavBackStack<NavKey>(probe)

		composeRule.setContent {
			outsideHost = LocalViewModelStoreOwner.current
			Nav3ScreenHost(
				backStack = backStack,
				entryDecorators = emptyList(),
			)
		}

		composeRule.runOnIdle {
			assertNotNull(outsideHost)
			assertSame(outsideHost, insideScreen)
		}
	}

	@Test
	fun `entry decorator shadows the ambient owner but the host owner stays reachable`() {
		var hostOwner: ViewModelStoreOwner? = null
		var entryOwner: ViewModelStoreOwner? = null
		var navigatorScoped: ViewModelStoreOwner? = null
		val probe = object : Nav3Screen {
			@Composable
			override fun Content() {
				entryOwner = LocalViewModelStoreOwner.current
				navigatorScoped = nav3HostViewModelStoreOwner()
				Text("Probe")
			}
		}
		val backStack = NavBackStack<NavKey>(probe)

		composeRule.setContent {
			hostOwner = LocalViewModelStoreOwner.current
			Nav3ScreenHost(
				backStack = backStack,
				// Default decorators: the ViewModelStore decorator gives the entry its own owner.
			)
		}

		composeRule.runOnIdle {
			assertNotNull(entryOwner)
			assertNotSame(hostOwner, entryOwner)
			assertSame(hostOwner, navigatorScoped)
		}
	}

	@Test
	fun `nested host scopes to the hosting screen's entry not the activity`() {
		var outerScreenEntryOwner: ViewModelStoreOwner? = null
		var innerNavigatorScoped: ViewModelStoreOwner? = null
		var activityLevelOwner: ViewModelStoreOwner? = null

		val innerScreen = object : Nav3Screen {
			@Composable
			override fun Content() {
				innerNavigatorScoped = nav3HostViewModelStoreOwner()
				Text("Inner")
			}
		}
		val outerScreen = object : Nav3Screen {
			@Composable
			override fun Content() {
				// The flow's scope: this screen's own entry-scoped owner.
				outerScreenEntryOwner = LocalViewModelStoreOwner.current
				val innerStack = NavBackStack<NavKey>(innerScreen)
				Nav3ScreenHost(
					backStack = innerStack,
					entryDecorators = emptyList(),
				)
			}
		}
		val outerStack = NavBackStack<NavKey>(outerScreen)

		composeRule.setContent {
			activityLevelOwner = LocalViewModelStoreOwner.current
			Nav3ScreenHost(
				backStack = outerStack,
				// Default decorators so the outer screen gets a real per-entry owner.
			)
		}

		composeRule.runOnIdle {
			assertNotNull(outerScreenEntryOwner)
			assertSame(outerScreenEntryOwner, innerNavigatorScoped)
			assertNotSame(activityLevelOwner, innerNavigatorScoped)
		}
	}
}
