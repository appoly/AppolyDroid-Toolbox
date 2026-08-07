# Nav3Navigation

Voyager-style ergonomics on androidx Navigation 3: screens are self-rendering classes (the nav
key **is** the screen, cmd+B-navigable), with an ambient `LocalNav3Navigator` for push/pop — no
key ↔ entryProvider mapping and no host-threaded lambdas — while keeping Nav3's native predictive
back, caller-owned back stack, and opt-in per-entry state/ViewModel scoping.

Use this when you want Navigation 3's first-party Compose features (especially predictive back)
without giving up the fused-screen / ambient-navigator convenience that Voyager popularised.

## Features

| Piece                                  | What it does                                                                                            |
|----------------------------------------|---------------------------------------------------------------------------------------------------------|
| `Nav3Screen`                           | Fused key + UI: implement `Content()` on the key class itself                                           |
| `Nav3Navigator` + `LocalNav3Navigator` | Ambient navigation: `push` / `pop` / `replace` / … + optional `parent` / `root()` for nested hosts |
| Stack peek                             | `canPop`, `lastItem`, `previousItem`, `items` — bottom bar, BackHandler, deep-link reconcile            |
| `popWithResult` / `Nav3ResultReceiver` | Voyager-style screen-to-screen results (stable; preferred over the alpha result bus)                    |
| `BackStackNav3Navigator`               | Default navigator — navigation is list mutation on your `NavBackStack`                                  |
| `Nav3ScreenHost`                       | Full `NavDisplay` surface for `Nav3Screen` stacks + ambient navigator + default entry decorators        |
| `TabsNav3Navigator` + `LocalTabsNavigator` | Flattened per-tab stacks, exit-through-home, `navigateToTab`, tab-slide hints                        |
| `Nav3TabsHost`                         | Tabs host: provides both ambients, wires tab back stack + tab-aware transition defaults                 |
| `Nav3Transitions`                      | Optional spring-slide / full-slide / tab-slide `ContentTransform`s (used as `Nav3TabsHost` defaults)    |
| `rememberDefaultNav3EntryDecorators`   | Saveable state + ViewModelStore + result bus per entry                                                  |
| Native predictive back                 | Pop transition is scrubbed by the system gesture                                                        |
| Caller-owned back stack                | Deep links are just a seeded start stack                                                                |
| Per-screen transitions                 | Override via `Nav3Screen.metadata`                                                                      |

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:Nav3Navigation:1.7.0-beta01")
```

Or via the AppolyDroid BOM (version managed by the platform):

```gradle.kts
implementation(platform("com.github.appoly.AppolyDroid-Toolbox:AppolyDroid-Toolbox-bom:1.7.0-beta01"))
implementation("com.github.appoly.AppolyDroid-Toolbox:Nav3Navigation")
```

**Requirements**

- `minSdk` **23** (androidx.navigation3 requirement)
- Depends on `androidx.navigation3` **1.2.0-alpha07** (alpha result bus is optional; see [Results](#results))
- Screen classes need `kotlinx-serialization` (`@Serializable` + the serialization plugin)

## Usage

### Declaring screens

```kotlin
@Serializable
data object HomeScreen : Nav3Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNav3Navigator.current
        Button(onClick = { navigator?.push(DetailScreen(itemId = 42)) }) {
            Text("Open detail")
        }
    }
}

@Serializable
data class DetailScreen(val itemId: Int) : Nav3Screen {
    @Composable
    override fun Content() {
        // itemId is a real constructor arg — no Bundles, no route strings
    }
}
```

Rules the compiler won't enforce:

1. Concrete screens must be `@Serializable` — they ride the persisted back stack.
2. Non-serializable body properties must be `get() =` computed (no backing field) — including
   `metadata` overrides. kotlinx.serialization serializes stored body `val`s.
3. Two pushes of an equal key share saved state + ViewModel store; give multi-instance screens
   a distinguishing constructor arg (the same reason Voyager screens carry `uniqueScreenKey`).

### Hosting a stack

```kotlin
val backStack = rememberNavBackStack(HomeScreen)

Nav3ScreenHost(
    modifier = Modifier.fillMaxSize(),
    backStack = backStack,
)
```

`Nav3ScreenHost` mirrors the primary [NavDisplay](https://developer.android.com/jetpack/androidx/releases/navigation3)
parameter surface so you rarely need to drop down to a raw `NavDisplay` call:

| Parameter | Default | Notes |
|---|---|---|
| `navigator` | `BackStackNav3Navigator(backStack)` | Provided as `LocalNav3Navigator` |
| `onBack` | `{ navigator.pop() }` | Prefer this over raw list mutation |
| `entryDecorators` | `rememberDefaultNav3EntryDecorators()` | Saveable + ViewModelStore + result bus |
| `sceneStrategies` | `SinglePaneSceneStrategy` | List-pane / adaptive scenes |
| `sceneDecoratorStrategies` | `emptyList()` | Scene-level chrome / shared state |
| `sharedTransitionScope` | `null` | Pass a parent `SharedTransitionLayout` scope for shared elements |
| `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` | NavDisplay defaults | Host-level animations |
| `entryProvider` | `::nav3ScreenEntry` | Fused `Nav3Screen` rendering — override only for mixed stacks |

The back stack must be non-empty (`NavDisplay` requires it).

### Navigator API (Voyager parity)

| Voyager                | Nav3Navigation                               |
|------------------------|----------------------------------------------|
| `push(screen)`         | `push(screen)`                               |
| `push(a, b, c)` / list | `push(a, b, c)` or `push(list)`              |
| `pop()`                | `pop()` (no-op at root / when `!canPop`)     |
| `replace(screen)`      | `replace(screen)`                            |
| `replaceAll(screen)`   | `replaceAll(screen)` / `replaceAll(a, b, …)` |
| `popUntil { … }`       | `popUntil { … }` / `popUpTo(screen)`         |
| `popUntilRoot()`       | `popUntilRoot()`                             |
| `canPop`               | `canPop`                                     |
| `lastItem`             | `lastItem`                                   |
| `items`                | `items`                                      |
| `parent`               | `parent` (`null` at root)                    |
| *(root walk)*          | `navigator.root()`                           |

```kotlin
// Checkout → confirmation (must not leave checkout on the stack)
navigator.replace(OrderConfirmationScreen(order))

// Day-setup / auth handoff: wipe the tab stack
navigator.replaceAll(ScheduleScreen())

// Done with a flow
navigator.popUntilRoot()

// Bottom bar from top screen
val showBottomBar = (navigator.lastItem as? ShowsBottomBar)?.showBottomBar != false

// System back: pop tab stack, else switch tab / finish
BackHandler(enabled = navigator.canPop || currentTab != HomeTab) {
    if (navigator.canPop) navigator.pop() else selectTab(HomeTab)
}
```

### Deep links

A deep link is just a seeded start stack — no graph, no URI-pattern framework:

```kotlin
val backStack = rememberNavBackStack(
    HomeScreen,
    ListScreen,
    DetailScreen(itemId = deepLinkId),
)
```

Or at runtime (router already built a list):

```kotlin
navigator.push(screensToPush)          // Iterable overload
// or seed / replace the whole tab stack:
navigator.replaceAll(*desiredStack.toTypedArray())
```

Inspect `navigator.items` to skip screens already present when reconciling a deep-link stack.

### Tabs (`TabsNav3Navigator`)

Bottom-bar chrome stays **app-owned**. The library provides a navigator that:

- keeps **one stack per tab** and flattens `startTab + currentTab` into a single `backStack` for one `Nav3ScreenHost`
- implements `Nav3Navigator` so in-tab `LocalNav3Navigator.push/pop` stay tab-local
- **exit-through-home**: `pop` at a non-start tab root switches to the start tab
- **`navigateToTab(tab, vararg screens)`** for cross-tab deep links
- records **`pendingTabSlide`** so tab switches can animate directionally (see [Transitions](#transitions))

```kotlin
// Wires parent = LocalNav3Navigator.current; restores tab stacks across rotation / process death
val tabs = rememberTabsNav3Navigator(listOf(HomeTab, RoomsTab, SettingsTab))

Scaffold(
    bottomBar = {
        NavigationBar {
            // selected = tabs.currentTab == …; onClick = { tabs.switchTab(…) }
        }
    },
) { padding ->
    // Provides LocalNav3Navigator + LocalTabsNavigator; tab-aware spring/tab-slide defaults
    Nav3TabsHost(
        modifier = Modifier.padding(padding),
        tabsNavigator = tabs,
    )
}
```

Cross-tab from a page:

```kotlin
LocalTabsNavigator.current?.navigateToTab(RoomsTab, RoomDetailScreen(id))
```

Nested / outer stack (escape hatch — prefer for dismiss-shell / logout, not everyday nav):

```kotlin
LocalNav3Navigator.current?.parent?.pop()   // pop outer host
LocalNav3Navigator.current?.root()?.replaceAll(LoginScreen)
```

Tab roots are never popped/replaced (they key each tab's stack). `replaceAll` is **tab-local**:
keeps the root, then appends the new screens.

**Persistence:** `rememberTabsNav3Navigator` saves `currentTab` and every per-tab stack (via the
same reflection-based `NavKey` serialization as `rememberNavBackStack`). Screens must be
`@Serializable`. `parent` is re-wired from composition on restore.

**Equal keys across tabs:** the display stack is `startTabStack + currentTabStack`. The same
equal key on Home and on Rooms shares saveable state / ViewModelStore — use distinguishing
constructor args when a destination can appear under more than one tab.

`Nav3TabsHost` is only a convenience wrapper over `Nav3ScreenHost` — you can still wire
`CompositionLocalProvider(LocalTabsNavigator provides tabs) { Nav3ScreenHost(...) }` yourself
if you need a custom layout.

#### Multi-stack alternative

If you prefer independent `rememberNavBackStack` per tab (no flatten / no built-in tab-slide),
swap which stack you pass to `Nav3ScreenHost` and re-provide `LocalNav3Navigator` — same idea as
nested Voyager navigators. Cross-tab then means mutating the target tab's list yourself.

### Transitions

`Nav3Transitions` is **optional** — not applied unless you pass the specs into the host.

| Builder | Use |
|---|---|
| `springSlidePush/Pop(stackSize)` | In-tab / single-stack spring + parallax |
| `slidePush/Pop()` | Full-width spring slide |
| `tabSlide(Forward/Backward)` | Directional full slide for tab switches |
| `tabs.transitionSpec()` / `popTransitionSpec()` / `predictivePopTransitionSpec()` | Tab-aware: tab-slide when `pendingTabSlide` is set, else spring-slide |

Single-stack host with spring-slide:

```kotlin
Nav3ScreenHost(
    backStack = backStack,
    transitionSpec = { Nav3Transitions.springSlidePush(backStack.size) },
    popTransitionSpec = { Nav3Transitions.springSlidePop(backStack.size) },
    predictivePopTransitionSpec = { Nav3Transitions.springSlidePop(backStack.size) },
)
```

### Custom / per-screen transitions

Host-level defaults (and optional shared-element scope):

```kotlin
SharedTransitionLayout {
    Nav3ScreenHost(
        modifier = Modifier.fillMaxSize(),
        backStack = backStack,
        sharedTransitionScope = this,
        transitionSpec = { myPushTransform() },
        popTransitionSpec = { myPopTransform() },
        predictivePopTransitionSpec = { myPopTransform() },
    )
}
```

Per-screen overrides go through `Nav3Screen.metadata` using `NavDisplay.transitionSpec(...)` /
`NavDisplay.popTransitionSpec(...)` keys (computed `get() =`, never a stored `val`):

```kotlin
@Serializable
data object ModalPickerScreen : Nav3Screen {
    override val metadata: Map<String, Any>
        get() = NavDisplay.transitionSpec {
            slideInVertically { it } togetherWith ExitTransition.None
        }

    @Composable
    override fun Content() { /* ... */ }
}
```

### Results

Two options — pick one per app, don't mix freely:

#### A. `Nav3ResultReceiver` + `popWithResult` (recommended, stable)

Voyager-style “pop and deliver a value to the previous screen” without depending on the alpha
result bus:

```kotlin
@Serializable
data object ListScreen : Nav3Screen, Nav3ResultReceiver {
    // Prefer a ViewModel as the real result sink — don't store mutable state on the key
    override fun onResult(result: Any?) { /* cast/check as needed */ }

    @Composable
    override fun Content() { /* push PickerScreen */ }
}

// On the picker:
navigator.popWithResult(selectedId)

// Multi-step flow: dismiss intermediates and deliver to a specific ancestor
navigator.popUntilWithResult(result = selectedId) { it is ListScreen }
```

#### B. Nav3 1.2 `ResultEventBus` (alpha — optional)

Default decorators include the result-bus decorator. A picker can `sendResult(...)`; the
caller observes via `ResultEffect<T>`. **Treat as alpha** — event vs state variants differ on
process-death behaviour. Prefer (A) or a shared ViewModel until this hits beta/stable.

### Screen-scoped ViewModels (ScreenModel → ViewModel)

Voyager `ScreenModel` + `koinScreenModel()` maps cleanly onto real `ViewModel`s:

| Voyager                                         | Nav3Navigation + Koin                                   |
|-------------------------------------------------|---------------------------------------------------------|
| `class FooScreenModel : ScreenModel`            | `class FooViewModel : ViewModel()`                      |
| `screenModelScope`                              | `viewModelScope`                                        |
| `koinScreenModel()`                             | `koinViewModel()`                                       |
| `koinScreenModel { parametersOf(id) }`          | `koinViewModel { parametersOf(id) }`                    |
| `koinNavigatorScreenModel()` (navigator-scoped) | Host-/activity-scoped ViewModel shared across the stack |

With the default ViewModelStore decorator, `viewModel()` / `koinViewModel()` resolve against the
entry's `LocalViewModelStoreOwner` — **no Nav3-specific Koin artifact**. Scope is per entry:
survives rotation, `onCleared()` on pop, one instance per stacked equal-key entry.

```kotlin
@Serializable
data class DetailScreen(val itemId: Int) : Nav3Screen {
    @Composable
    override fun Content() {
        val vm: DetailViewModel = koinViewModel { parametersOf(itemId) }
        // …
    }
}
```

Drop `uniqueScreenKey` — multi-instance identity is the constructor args (and equality) of the
`@Serializable` key itself.

## API surface

| Symbol                                 | Kind             | Role                                            |
|----------------------------------------|------------------|-------------------------------------------------|
| `Nav3Screen`                           | interface        | `NavKey` + `Content()` + optional `metadata`    |
| `nav3ScreenEntry(key)`                 | function         | Universal entryProvider for `Nav3Screen` stacks |
| `Nav3Navigator`                        | interface        | Full Voyager-parity stack ops + peek + `parent` |
| `LocalNav3Navigator`                   | CompositionLocal | Ambient navigator (`null` outside a host)       |
| `root()`                               | extension        | Walk `parent` to the outermost navigator        |
| `BackStackNav3Navigator`               | class            | List-mutating default implementation            |
| `rememberBackStackNav3Navigator`       | composable       | Remembers navigator with ambient `parent`       |
| `rememberTabsNav3Navigator`            | composable       | Remembers tabs navigator with ambient `parent`  |
| `Nav3ResultReceiver`                   | interface        | `onResult` target for `popWithResult`           |
| `popWithResult` / `popUntilWithResult` | extensions       | Deliver result + pop                            |
| `Nav3ScreenHost`                       | composable       | Full `NavDisplay` host + ambient navigator      |
| `TabsNav3Navigator`                    | class            | Per-tab stacks + flatten + `navigateToTab`      |
| `LocalTabsNavigator`                   | CompositionLocal | Ambient tabs API (`null` outside a tab host)    |
| `Nav3TabsHost`                         | composable       | Tabs host: both ambients + tab transition defaults |
| `TabSlide`                             | enum             | Forward / Backward tab-switch direction         |
| `Nav3Transitions`                      | object           | Optional slide / spring-slide / tab-slide specs |
| `rememberDefaultNav3EntryDecorators`   | composable       | Saveable + VM store + result bus                |

## Dependencies

| Artifact                                             | Version       | Notes                                                        |
|------------------------------------------------------|---------------|--------------------------------------------------------------|
| `androidx.navigation3:navigation3-runtime`           | 1.2.0-alpha07 | `api` — `NavKey` / `NavBackStack` / `NavEntry` in public API |
| `androidx.navigation3:navigation3-ui`                | 1.2.0-alpha07 | `api` — `NavDisplay` / scene types in public API             |
| `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0        | Per-entry ViewModelStore decorator                           |

Pin Navigation 3 deliberately — decorator/API names have churned across 1.0 → 1.1 → 1.2 alphas.

## Why not plain Navigation 3 / Voyager?

| Concern              | Voyager             | Nav3 alone            | This module               |
|----------------------|---------------------|-----------------------|---------------------------|
| cmd+B to screen code | ✅ fused `Screen`    | ❌ key ↔ entryProvider | ✅ fused `Nav3Screen`      |
| Ambient push/pop     | ✅ `LocalNavigator`  | ❌ host lambdas        | ✅ `LocalNav3Navigator`    |
| Predictive back      | ❌ after-commit only | ✅ native              | ✅ native                  |
| Stack is a list      | partial             | ✅                     | ✅                         |
| Screen-scoped VMs    | ScreenModel         | decorator             | decorator (default on)    |
| Maintenance          | beta, slowing       | first-party           | thin layer on first-party |

## Testing

Unit-test navigation as ordinary list code — no device needed:

```kotlin
val backStack = NavBackStack<NavKey>(HomeScreen)
val navigator = BackStackNav3Navigator(backStack)

navigator.push(DetailScreen(1))
assertEquals(2, backStack.size)
assertTrue(navigator.canPop)

navigator.replace(DetailScreen(2))
navigator.popUntilRoot()
assertEquals(listOf(HomeScreen), navigator.items)
```
