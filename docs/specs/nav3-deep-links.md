# Nav3Navigation: deep-link support (plan)

Status: **in progress** (2026-10-05). Steps 1–4 of the work order are done: `navigateToTab`'s
whole-stack dedup, `Nav3DeepLink` / `navigateToDeepLink` (Append and Reconcile),
`Nav3DeepLinkRouter`, and entry-scoped continuations. The branch is up to date with `develop`.

Notes from implementing steps 1–4:

- `navigateToDeepLink` is a `Nav3Navigator` member **with a default body** (built on `items`,
  `pop`, `push` and `replaceAll`), so adding it breaks no custom navigator.
  `BackStackNav3Navigator` and `TabsNav3Navigator` override it.
- **Continuations (step 4).** Compatibility option (a) was taken: `continuations` is a required
  `Nav3Navigator` member, since an interface can't hold state and a no-op default would silently
  drop continuations. Call it out in the release notes. Other choices:
  - Payloads implement a marker interface, `Nav3Continuation`, rather than being `Any`. That
    makes the consumer R8 rules scopable (mirroring the `Nav3Screen` block) and `put` type-safe.
  - `Nav3DeepLink(stack, mode, continuation)`: the continuation goes **last**, so a positional
    `mode` can't be mistaken for it.
  - Writes go only through `navigateToDeepLink` and `push(screen, continuation)`; `put` is
    internal, so nothing can address a key that isn't being navigated to.
  - A payload that isn't `@Serializable` is rejected where it is sent, not at save time. An
    entry that can't be decoded on restore is dropped rather than crashing the restore.
  - **Step 6 must add `verifyConsumerKeepRules` sentinels** for a demo-app `Nav3Continuation`;
    until the demo uses one, the new keep rules are checked only for R8 syntax.
- A tab root inside a link (anywhere but first) throws in `navigateToDeepLink`. `navigateToTab`
  stays permissive about that, since it is released API.
- Nav3's `MatchResult.compareTo` isn't antisymmetric across result types (a URI result beats a
  plain one, but the plain one calls it a tie), so the router ranks by the difference of both
  directions and leaves ties in declaration order.

The consumer-app findings behind this plan are in `nav3-deep-links-consumer-survey.md`. That file
names client apps, and this repo is public, so **do not commit it**.

## Background

androidx Navigation 3 1.2.0 adds deep-link matching in `navigation3-runtime`
(`androidx.navigation3.runtime.deeplink`). Main types:

- `DeepLinkRequest`, built from a URI or from an `Intent` (keeps data, action, type and extras).
- `DeepLinkMatcher`: `UriDeepLinkMatcher(DeepLinkUri, KSerializer<T>)`,
  `StaticKeyDeepLinkMatcher`, and `.withBackStack { }`, which produces a `BackStackMatchResult`.
- Match results implement `Comparable`, so the most specific match wins (`maxOrNull()`).

Facts checked against the real sources:

- The `rc01` runtime on `main` has the same deep-link sources as 1.2.0, so this work does not
  depend on the 1.2.0 bump in PR #120.
- The runtime's minSdk is 24, the same as our `NAV3_NAVIGATION`.
- The deep-link classes are in `navigation3-runtime`, which we already expose as `api`, so no new
  dependency is needed.

## What already works (proven by the probe test)

- `Nav3Screen`s are valid match targets. `UriDeepLinkMatcher(..., serializer<DetailScreen>())`
  decodes `DetailScreen(42)` directly.
- `withBackStack { listOf(HomeScreen, ListScreen, it.key) }` returns a `List<Nav3Screen>`. Mixed
  matchers fit in one `List<DeepLinkMatcher<Nav3Screen, *>>`.
- `DeepLinkRequest(intent = …)` resolves, and the resolved stack can be passed to
  `BackStackNav3Navigator.replaceAll(...)`.

## Gaps

1. **Handling a link exactly once.** Every app surveyed reads the Intent in `onCreate` with no
   `savedInstanceState` check. Rotation, process-death restore and launching from recents all
   route the same link again.
2. **A pending holder.** A link has to survive until the navigator is composed and the user is
   logged in, survive process death, be cleared on logout, and be consumed exactly once.
3. **Tabs.**
   - Each destination needs to belong to a tab.
   - `navigateToTab` only dedups against the top of the stack. Equal keys deeper in the stack
     silently share saved state and ViewModels.
   - Apps switch the tab and push in separate effects, which is an ordering race.
4. **Multi-step flows.** A link to a clip opens the detail screen. That screen loads its data,
   checks permissions, then either stops or continues to the media screen, which may open the
   clip. Today this needs a global focus-router `StateFlow`, an `action` value in the key, and
   `hasHandled*` flags in saved state. Known bugs follow from that: a request can sit unclaimed
   and fire later, a buried entry can claim it, and keys that carry `action` can't be deduped.
5. **Boilerplate.** Picking the best match and casting it to screens.

## Decisions made

- **Tab collision default is Append, with Reconcile as a mode** (agreed 2026-10-01). Keep the
  user's place in the tab by default.
- **Continuations are in v1.** They replace the app-side focus routers, including those that
  exist only because a permanent tab root cannot take an argument.
- **Out of scope for v1:**
  - helpers for building notification PendingIntents
  - a navigator query to replace static "is this screen open" flags (used to suppress
    notifications while that screen is showing)
  - deep links that replace the whole UI tree (e.g. magic-link login, which stays app-owned)
  - seeding the cold-start stack without animation (a cold-start link animates in, which is
    what apps do today)

## Proposed API

### 1. `Nav3DeepLinks`: a ViewModel holding the pending link (`SavedStateHandle`)

```kotlin
private val deepLinks: Nav3DeepLinks by viewModels()

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    deepLinks.handle(intent, savedInstanceState)  // ignored on recreate/restore
}
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    deepLinks.handle(intent)
}
// on logout: deepLinks.clear()
// in-app sources: deepLinks.submit(uri / intent)
```

- The pending item is stored as the **`Intent`** (Parcelable), so it survives process death. It
  becomes a `DeepLinkRequest` when it is consumed.
- `handle(intent, savedInstanceState)` ignores the Intent when `savedInstanceState != null` or
  when `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` is set (launching from recents re-delivers the old
  link).
- It also ignores Intents with no data and no extras, i.e. a plain launcher start.
- Consumption is a compare-and-clear on a `MutableStateFlow`, so two consumers can't both
  deliver a link. No "single consumer" check is needed, which matters because Crossfade and
  AnimatedContent briefly compose two subtrees.
- The link is consumed **before** it is dispatched (at most once). An async handler that is
  cancelled drops the link instead of re-firing it.

### 2. `Nav3DeepLinkRouter`: matchers and mapping

```kotlin
val router = nav3DeepLinkRouter {
    // The key is already a screen; BackStackMatchResult becomes the stack
    screen(UriDeepLinkMatcher(DeepLinkUri("example.com/items/{itemId}"), serializer<DetailScreen>())
        .withBackStack { listOf(ItemsTab, it.key) })
    // The key is a link model that maps to a stack plus a continuation; return null to reject
    route(UriDeepLinkMatcher(DeepLinkUri("example.com/items/media?itemId={id}&clipId={clipId}"),
        serializer<MediaLink>())) { link ->
        Nav3DeepLink(listOf(HomeTab, DetailScreen(link.id)), continuation = OpenMedia(link.clipId))
    }
}
router.resolve(request): Nav3DeepLink?
```

- `resolve` ranks every match and tries them best-first. A mapping that returns `null` falls
  through to the next match, so the router never routes on a guess.
- Notification extras (FCM data) are handled by a custom `DeepLinkMatcher` subclass reading
  `DeepLinkRequest.IntentExtrasKey`. Document it with an example. A built-in extras matcher is a
  later nicety.
- Document that explicit-component Intents skip manifest filters, so URI matchers should pin the
  scheme and host.

```kotlin
class Nav3DeepLink(
    val stack: List<Nav3Screen>,          // non-empty
    val continuation: Any? = null,        // @Serializable; delivered to stack.last()
    val mode: Nav3DeepLinkMode = Append,  // Append | Reconcile
)
```

### 3. `HandleNav3DeepLinks`: the consumer composable

```kotlin
HandleNav3DeepLinks(
    deepLinks = deepLinks,
    router = router,
    navigator = tabs,
    onUnmatched = { /* log */ },
    onDeepLink = { link -> tabs.navigateToDeepLink(link) },  // default; suspend
)
```

- Place it **inside** the logged-in / unlocked subtree. That placement is the gate, matching what
  every app already relies on.
- Override `onDeepLink` for outcomes that aren't navigation (open a sheet, mark a notification
  read) or for async gates (fetch something, then push).

### 4. `Nav3Navigator.navigateToDeepLink(link)`

The target is a stack `S`; the link's stack is `L`.

- **Tabs:**
  - If `L[0]` is a tab root, select that tab, then switch and push in one atomic call.
  - Otherwise use the current tab, with its root implicitly prepended to `L`.
- **Append** (default):
  1. Drop the common prefix of `S` and `L`.
  2. Find the last remaining element of `L` that already exists anywhere in `S`. If there is one,
     pop `S` down to it ("surface existing").
  3. Push the rest of `L`.

  No equal key can then appear twice on a stack. Examples:
  - `S=[Root,A,B]`, `L=[Root,C]` gives `[Root,A,B,C]`
  - `S=[Root,A,D1,B]`, `L=[Root,D1]` gives `[Root,A,D1]`
  - `S=[K,SB,X]`, `L=[K,SB,FD]` gives `[K,SB,FD]`
- **Reconcile:** keep the longest common prefix, pop the rest, push the remainder of `L`.
  - Tabs: the root is never popped.
  - Plain stack with an empty common prefix: `replaceAll(L)`.
  - Example: `S=[Root,A,B]`, `L=[Root,C]` gives `[Root,C]`.
- The continuation is attached to `L.last()`. If that key already exists, the existing entry
  receives it, which covers the "already open, focus this clip" case.
- Also fix `TabsNav3Navigator.navigateToTab` to use the same whole-stack dedup.

### 5. Entry-scoped continuations

A one-shot payload handed to a specific back-stack entry. Each screen performs its own step:

```kotlin
// in DetailScreen.Content()
val step = rememberNav3Continuation<DetailStep>()  // null unless addressed to THIS entry
LaunchedEffect(step, data, user) {
    val s = step ?: return@LaunchedEffect
    if (data == null || user == null) return@LaunchedEffect  // wait for data to load
    s.consume()
    if (canShowMedia) navigator.push(MediaScreen(id), continuation = MediaStep.OpenClip(s.value.clipId))
}
```

- **Store:** `Nav3Continuations`, a snapshot-state map from `NavKey` to payload, owned by the
  navigator. Keys are unique per stack because of the dedup rule above.
  - Persisted in saved state. Keys are encoded with `NavKeySerializer`. Payloads are encoded with
    a small reflective serializer (class name plus `serializer(Class)`), the same approach
    `NavKeySerializer` uses.
  - `TabsNav3Navigator.saver` adds the store to its Bundle.
  - `rememberBackStackNav3Navigator` and the default navigator in `Nav3ScreenHost` keep the store
    with `rememberSaveable`.
- **Lifetime:** entries are pruned synchronously when a navigator operation removes their key,
  and also by a host `snapshotFlow` over the back stack for direct list mutation. A popped entry
  therefore can't leave a request to fire later, and a consumed one never fires again after
  restore.
- **Lookup:** `nav3ScreenEntry` provides a new `LocalNav3Screen`, so
  `rememberNav3Continuation<T>()` knows which entry it is in.
  - It ignores payloads of a different type and leaves them in place.
  - There is also a plain, non-composable API, `navigator.continuations.peek(key)` /
    `consume(key)`, for consuming from a ViewModel.
- **`push(screen, continuation)`** lets one step hand off to the next.

## Compatibility risk to resolve first

`Nav3Navigation` shipped in **1.10.0**, so adding `val continuations` to the `Nav3Navigator`
interface breaks any external implementer. None of the four Nav3 consumers implements it (checked
2026-10-01). Options:

- (a) Add it as an interface member and call it out in the release notes. Simplest; this is the
  recommendation.
- (b) Give it a default that returns a no-op store, so custom navigators silently drop
  continuations.

## Work order

1. The `TabsNav3Navigator` whole-stack dedup fix in `navigateToTab`. It is independently useful.
2. `Nav3DeepLink`, `navigateToDeepLink` (Append / Reconcile), and tests. Flip the probe test's
   tabs case to assert the fixed behaviour.
3. `Nav3DeepLinkRouter` and `resolve`.
4. `Nav3Continuations`: the store, saving, pruning, `LocalNav3Screen`,
   `rememberNav3Continuation`, and `push(..., continuation)`.
5. `Nav3DeepLinks` holder and `HandleNav3DeepLinks`. Robolectric tests for recreate, restore,
   launch from recents and compare-and-clear.
6. Demo app wiring and a rewrite of the README "Deep links" section, which currently says "no
   URI-pattern framework".
7. Afterwards: migrate one consumer as the canary. Pick the one that uses tabs, multi-step flows and focus routers; the survey file names it.
