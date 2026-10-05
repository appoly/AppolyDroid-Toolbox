package uk.co.appoly.droid.nav3

import androidx.navigation3.runtime.deeplink.BackStackMatchResult
import androidx.navigation3.runtime.deeplink.DeepLinkMatcher
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import kotlin.math.sign

/**
 * Resolves a [DeepLinkRequest] to a [Nav3DeepLink] using Navigation 3's [DeepLinkMatcher]s.
 *
 * Build one with [nav3DeepLinkRouter]:
 *
 * ```kotlin
 * val router = nav3DeepLinkRouter {
 *     // The matched key is already a screen. withBackStack { } supplies the screens beneath it.
 *     screen(
 *         UriDeepLinkMatcher(DeepLinkUri("https://example.com/items/{itemId}"), serializer<DetailScreen>())
 *             .withBackStack { listOf(ItemsTab, it.key) },
 *     )
 *     // The matched key is a link model you map yourself. Return null to reject the match.
 *     route(UriDeepLinkMatcher(DeepLinkUri("https://example.com/share/{code}"), serializer<ShareLink>())) { link ->
 *         if (link.code.isBlank()) null else Nav3DeepLink(listOf(HomeTab, SharedItemScreen(link.code)))
 *     }
 * }
 *
 * router.resolve(DeepLinkRequest(intent = intent))?.let(navigator::navigateToDeepLink)
 * ```
 *
 * ## Matching
 *
 * [resolve] collects every match, ranks them with Navigation 3's own [DeepLinkMatcher.MatchResult]
 * ordering (for URIs: an exact path beats arguments, more path arguments beat fewer, and so on)
 * and tries them best first. Equally ranked matches are tried in declaration order. If a
 * [Builder.route] mapping returns `null`, the next match is tried, so the router never routes on
 * a guess; when nothing is left, [resolve] returns `null`.
 *
 * ## Notes
 *
 * - Pin the scheme and host in URI patterns. An Intent with an explicit component skips your
 *   manifest's intent filters, so the router may see URIs the manifest would never have routed.
 * - Data that isn't in the URI (an FCM notification's extras, say) can be matched with a custom
 *   [DeepLinkMatcher] subclass that reads [DeepLinkRequest.extras], passed to [Builder.route].
 */
class Nav3DeepLinkRouter private constructor(private val routes: List<Route<*>>) {

	/**
	 * Resolves [request] to the best-ranked match whose mapping accepts it, or `null` when no
	 * declared matcher matches (or every mapping rejected its match).
	 */
	fun resolve(request: DeepLinkRequest): Nav3DeepLink? =
		routes
			.mapNotNull { route -> route.match(request) }
			.sortedWith(BEST_FIRST)
			.firstNotNullOfOrNull { it.resolve() }

	/** Declares the matchers of a [Nav3DeepLinkRouter]. Use via [nav3DeepLinkRouter]. */
	class Builder internal constructor() {
		private val routes = mutableListOf<Route<*>>()

		/**
		 * A [matcher] whose key is the destination screen itself.
		 *
		 * - A plain match lands the key alone, so the navigator supplies what's beneath it (the
		 *   current tab's root, for a [TabsNav3Navigator]).
		 * - A `withBackStack { }` match lands its whole back stack, which must contain only
		 *   [Nav3Screen]s.
		 *
		 * @param mode how the resolved link lands; see [Nav3DeepLinkMode].
		 */
		fun screen(
			matcher: DeepLinkMatcher<Nav3Screen, *>,
			mode: Nav3DeepLinkMode = Nav3DeepLinkMode.Append,
		) {
			routes += Route(matcher) { result ->
				Nav3DeepLink(stackOf(result, matcher), mode)
			}
		}

		/**
		 * A [matcher] whose key is a link model, mapped to a destination by [map].
		 *
		 * [map] receives the matched key (for a `withBackStack { }` matcher, the key it wraps; its
		 * back stack is ignored here, so build the stack in [map]). Return `null` to reject the
		 * match, and [Nav3DeepLinkRouter.resolve] falls through to the next-best one.
		 */
		fun <T : Any> route(matcher: DeepLinkMatcher<T, *>, map: (key: T) -> Nav3DeepLink?) {
			routes += Route(matcher) { result -> map(result.key) }
		}

		internal fun build() = Nav3DeepLinkRouter(routes.toList())
	}

	/** One declared matcher and how its match becomes a [Nav3DeepLink]. */
	private class Route<T : Any>(
		private val matcher: DeepLinkMatcher<T, *>,
		private val toLink: (DeepLinkMatcher.MatchResult<T>) -> Nav3DeepLink?,
	) {
		fun match(request: DeepLinkRequest): Match<T>? =
			matcher.match(request)?.let { Match(it, toLink) }
	}

	private class Match<T : Any>(
		val result: DeepLinkMatcher.MatchResult<T>,
		private val toLink: (DeepLinkMatcher.MatchResult<T>) -> Nav3DeepLink?,
	) {
		fun resolve(): Nav3DeepLink? = toLink(result)
	}

	private companion object {
		/**
		 * Best match first. Navigation 3's `compareTo` isn't antisymmetric across result types (a
		 * URI result calls itself greater than a plain one, while the plain one calls them equal),
		 * so take the difference of both directions: that is consistent whichever way round the
		 * sort compares, and leaves genuine ties to the stable sort (declaration order).
		 */
		val BEST_FIRST = Comparator<Match<*>> { a, b ->
			b.result.rank(a.result) - a.result.rank(b.result)
		}

		@Suppress("UNCHECKED_CAST")
		private fun DeepLinkMatcher.MatchResult<*>.rank(other: DeepLinkMatcher.MatchResult<*>): Int =
			(this as DeepLinkMatcher.MatchResult<Any>).compareTo(other as DeepLinkMatcher.MatchResult<Any>).sign

		private fun stackOf(
			result: DeepLinkMatcher.MatchResult<Nav3Screen>,
			matcher: DeepLinkMatcher<*, *>,
		): List<Nav3Screen> {
			if (result !is BackStackMatchResult<*, *>) return listOf(result.key)
			return result.backStack.map { key ->
				key as? Nav3Screen ?: throw IllegalStateException(
					"withBackStack for $matcher produced $key, which is not a Nav3Screen. " +
						"Every key in a screen() back stack must be a Nav3Screen; use route() to map " +
						"other keys."
				)
			}
		}
	}
}

/**
 * Builds a [Nav3DeepLinkRouter]. Declare each matcher with [Nav3DeepLinkRouter.Builder.screen]
 * (the key is a screen) or [Nav3DeepLinkRouter.Builder.route] (the key is mapped to a link).
 */
fun nav3DeepLinkRouter(block: Nav3DeepLinkRouter.Builder.() -> Unit): Nav3DeepLinkRouter =
	Nav3DeepLinkRouter.Builder().apply(block).build()
