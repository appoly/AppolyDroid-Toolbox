package uk.co.appoly.droid.nav3

import android.content.Intent
import android.net.Uri
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.BackStackMatchResult
import androidx.navigation3.runtime.deeplink.DeepLinkMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.invoke
import androidx.navigation3.runtime.deeplink.withBackStack
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Probe: do Nav3 1.2.0's deep-link matchers work with [Nav3Screen] keys and our navigators
 * as-is, with no glue from this module?
 */
@RunWith(AndroidJUnit4::class)
class Nav3DeepLinkCompatTest {

	private val matchers: List<DeepLinkMatcher<Nav3Screen, *>> = listOf(
		UriDeepLinkMatcher(DeepLinkUri("www.example.com/home"), serializer<HomeScreen>()),
		UriDeepLinkMatcher(DeepLinkUri("www.example.com/items"), serializer<ListScreen>())
			.withBackStack { listOf(HomeScreen, it.key) },
		UriDeepLinkMatcher(DeepLinkUri("www.example.com/items/{itemId}"), serializer<DetailScreen>())
			.withBackStack { listOf(HomeScreen, ListScreen, it.key) },
	)

	private fun resolve(request: DeepLinkRequest): List<Nav3Screen>? =
		when (val result = matchers.mapNotNull { it.match(request) }.maxOrNull()) {
			null -> null
			is BackStackMatchResult<*, *> -> result.backStack.map { it as Nav3Screen }
			else -> listOf(result.key)
		}

	@Test
	fun `uri matcher decodes a Nav3Screen with a path argument`() {
		val stack = resolve(DeepLinkRequest(uri = "https://www.example.com/items/42"))
		assertEquals(listOf(HomeScreen, ListScreen, DetailScreen(42)), stack)
	}

	@Test
	fun `request built from an Intent resolves`() {
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/items"))
		assertEquals(listOf(HomeScreen, ListScreen), resolve(DeepLinkRequest(intent = intent)))
	}

	@Test
	fun `resolved stack seeds a plain navigator via replaceAll`() {
		val backStack = NavBackStack<NavKey>(SettingsScreen)
		val navigator = BackStackNav3Navigator(backStack)
		navigator.replaceAll(*resolve(DeepLinkRequest(uri = "https://www.example.com/items/7"))!!.toTypedArray())
		assertEquals(listOf(HomeScreen, ListScreen, DetailScreen(7)), navigator.items)
	}

	/**
	 * The original gap: navigateToTab could only append to whatever the tab already held.
	 * navigateToDeepLink now lands the resolved stack exactly when asked to (Reconcile), while
	 * Append keeps the user's place.
	 */
	@Test
	fun `tabs - a resolved stack lands on its tab in either mode`() {
		val tabs = TabsNav3Navigator(listOf(HomeScreen, ListScreen, SettingsScreen))
		tabs.navigateToTab(ListScreen, DetailScreen(3))
		tabs.switchTab(HomeScreen)

		// Deep link resolves to [ListScreen, DetailScreen(7)]: tab root + target.
		tabs.navigateToDeepLink(Nav3DeepLink(listOf(ListScreen, DetailScreen(7)), Nav3DeepLinkMode.Reconcile))

		assertEquals(ListScreen, tabs.currentTab)
		assertEquals(listOf(ListScreen, DetailScreen(7)), tabs.items)

		tabs.navigateToDeepLink(Nav3DeepLink(listOf(ListScreen, DetailScreen(8))))
		assertEquals(listOf(ListScreen, DetailScreen(7), DetailScreen(8)), tabs.items)
	}
}
