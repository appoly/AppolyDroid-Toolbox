package uk.co.appoly.droid.nav3

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.navigation3.runtime.deeplink.DeepLinkUri
import androidx.navigation3.runtime.deeplink.StaticKeyDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher
import androidx.navigation3.runtime.deeplink.invoke
import androidx.navigation3.runtime.deeplink.withBackStack
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@Serializable
internal data class TagScreen(val tag: String) : Nav3Screen {
	@Composable
	override fun Content() {
		Text("Tag $tag")
	}
}

/** A link model, not a screen: [Nav3DeepLinkRouter.Builder.route] maps it to a destination. */
@Serializable
internal data class ShareLink(val code: String)

/** A plain [NavKey] that isn't a [Nav3Screen], for the misconfigured-back-stack case. */
@Serializable
internal data object PlainKey : NavKey

@RunWith(AndroidJUnit4::class)
class Nav3DeepLinkRouterTest {

	private fun request(uri: String) = DeepLinkRequest(uri = uri)

	@Test
	fun `a plain screen match lands the key alone`() {
		val router = nav3DeepLinkRouter {
			screen(UriDeepLinkMatcher(DeepLinkUri("https://example.com/items/{itemId}"), serializer<DetailScreen>()))
		}

		assertEquals(Nav3DeepLink(DetailScreen(42)), router.resolve(request("https://example.com/items/42")))
	}

	@Test
	fun `a withBackStack screen match lands its back stack`() {
		val router = nav3DeepLinkRouter {
			screen(
				UriDeepLinkMatcher(DeepLinkUri("https://example.com/items/{itemId}"), serializer<DetailScreen>())
					.withBackStack { listOf(HomeScreen, ListScreen, it.key) },
			)
		}

		assertEquals(
			Nav3DeepLink(listOf(HomeScreen, ListScreen, DetailScreen(7))),
			router.resolve(request("https://example.com/items/7")),
		)
	}

	@Test
	fun `the mode is carried onto the resolved link`() {
		val router = nav3DeepLinkRouter {
			screen(
				UriDeepLinkMatcher(DeepLinkUri("https://example.com/settings"), serializer<SettingsScreen>()),
				mode = Nav3DeepLinkMode.Reconcile,
			)
		}

		assertEquals(Nav3DeepLinkMode.Reconcile, router.resolve(request("https://example.com/settings"))?.mode)
	}

	@Test
	fun `route maps a link model to a destination`() {
		val router = nav3DeepLinkRouter {
			route(UriDeepLinkMatcher(DeepLinkUri("https://example.com/share/{code}"), serializer<ShareLink>())) { link ->
				Nav3DeepLink(listOf(HomeScreen, TagScreen(link.code)))
			}
		}

		assertEquals(
			Nav3DeepLink(listOf(HomeScreen, TagScreen("abc"))),
			router.resolve(request("https://example.com/share/abc")),
		)
	}

	@Test
	fun `an exact path beats a path argument regardless of declaration order`() {
		val router = nav3DeepLinkRouter {
			screen(UriDeepLinkMatcher(DeepLinkUri("https://example.com/tags/{tag}"), serializer<TagScreen>()))
			screen(UriDeepLinkMatcher(DeepLinkUri("https://example.com/tags/new"), serializer<SettingsScreen>()))
		}

		assertEquals(Nav3DeepLink(SettingsScreen), router.resolve(request("https://example.com/tags/new")))
		assertEquals(Nav3DeepLink(TagScreen("red")), router.resolve(request("https://example.com/tags/red")))
	}

	@Test
	fun `a uri match beats a static-key match declared before it`() {
		// Nav3's compareTo is asymmetric across result types; the ranking must not depend on order.
		val router = nav3DeepLinkRouter {
			screen(StaticKeyDeepLinkMatcher(HomeScreen, emptyList()))
			screen(UriDeepLinkMatcher(DeepLinkUri("https://example.com/items/{itemId}"), serializer<DetailScreen>()))
		}

		assertEquals(Nav3DeepLink(DetailScreen(5)), router.resolve(request("https://example.com/items/5")))
	}

	@Test
	fun `a rejected mapping falls through to the next-best match`() {
		val router = nav3DeepLinkRouter {
			route(UriDeepLinkMatcher(DeepLinkUri("https://example.com/share/special"), serializer<SettingsScreen>())) {
				null // e.g. a feature flag is off
			}
			route(UriDeepLinkMatcher(DeepLinkUri("https://example.com/share/{code}"), serializer<ShareLink>())) { link ->
				Nav3DeepLink(TagScreen(link.code))
			}
		}

		assertEquals(Nav3DeepLink(TagScreen("special")), router.resolve(request("https://example.com/share/special")))
	}

	@Test
	fun `equally ranked matches are tried in declaration order`() {
		val router = nav3DeepLinkRouter {
			screen(StaticKeyDeepLinkMatcher(ListScreen, emptyList()))
			screen(StaticKeyDeepLinkMatcher(SettingsScreen, emptyList()))
		}

		assertEquals(Nav3DeepLink(ListScreen), router.resolve(request("https://example.com/anything")))
	}

	@Test
	fun `nothing matching resolves to null`() {
		val router = nav3DeepLinkRouter {
			screen(UriDeepLinkMatcher(DeepLinkUri("https://example.com/items/{itemId}"), serializer<DetailScreen>()))
		}

		assertNull(router.resolve(request("https://other.example.com/items/1")))
		assertNull("an argument that can't decode is no match", router.resolve(request("https://example.com/items/abc")))
	}

	@Test
	fun `every mapping rejecting resolves to null`() {
		val router = nav3DeepLinkRouter {
			route(UriDeepLinkMatcher(DeepLinkUri("https://example.com/share/{code}"), serializer<ShareLink>())) { null }
		}

		assertNull(router.resolve(request("https://example.com/share/abc")))
	}

	@Test
	fun `a request built from an Intent resolves`() {
		val router = nav3DeepLinkRouter {
			screen(UriDeepLinkMatcher(DeepLinkUri("https://example.com/items/{itemId}"), serializer<DetailScreen>()))
		}
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/items/9"))

		assertEquals(Nav3DeepLink(DetailScreen(9)), router.resolve(DeepLinkRequest(intent = intent)))
	}

	@Test
	fun `a screen back stack containing a non-screen key fails loudly`() {
		val router = nav3DeepLinkRouter {
			screen(
				UriDeepLinkMatcher(DeepLinkUri("https://example.com/items/{itemId}"), serializer<DetailScreen>())
					.withBackStack<Nav3Screen, NavKey> { listOf(PlainKey, it.key) },
			)
		}

		assertThrows(IllegalStateException::class.java) { router.resolve(request("https://example.com/items/1")) }
	}
}
