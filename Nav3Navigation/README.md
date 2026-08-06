# Nav3Navigation

Voyager-style ergonomics on androidx Navigation 3: screens are self-rendering classes (the nav key IS the screen, cmd+B-navigable), with an ambient `LocalNav3Navigator` for push/pop — no key ↔ entryProvider mapping and no host-threaded lambdas — while keeping Nav3's native predictive back, caller-owned back stack, and opt-in per-entry state/ViewModel scoping.

## Features

- `Nav3Screen` — fused key + UI: implement `Content()` on the key class itself
- `Nav3Navigator` + `LocalNav3Navigator` — Voyager-style ambient navigation (`push`, `pop`, `popUpTo`), re-providable over nested stacks (tabs)
- `Nav3ScreenHost` — a `NavDisplay` wrapper wiring the entry provider, navigator, and default entry decorators (saveable state, ViewModelStore, result bus)
- Native predictive back — the pop transition is scrubbed by the gesture
- The back stack stays your list — deep links are just a seeded start stack
- Per-screen transition overrides via `Nav3Screen.metadata`

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:Nav3Navigation:1.6.4")
```

Note: depends on `androidx.navigation3` **1.2.0-alpha07** (the navigation result API is not yet stable) — consumers inherit the alpha dependency.

## Usage

### Declaring screens

```kotlin
@Serializable
data object HomeScreen : Nav3Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNav3Navigator.current
        // ...
        Button(onClick = { navigator?.push(DetailScreen(itemId = 42)) }) {
            Text("Open detail")
        }
    }
}

@Serializable
data class DetailScreen(val itemId: Int) : Nav3Screen {
    @Composable
    override fun Content() {
        // ...
    }
}
```

Rules the compiler won't enforce:

- Concrete screens must be `@Serializable` — they ride the persisted back stack.
- Non-serializable body properties must be `get() =` computed (no backing field).
- Two pushes of an equal key share saved state + ViewModel store; give multi-instance screens a distinguishing constructor arg.

### Hosting a stack

```kotlin
val backStack = rememberNavBackStack(HomeScreen)

Nav3ScreenHost(
    modifier = Modifier.fillMaxSize(),
    backStack = backStack,
)
```

### Deep links

A deep link is just a seeded start stack — no graph, no URI-pattern framework:

```kotlin
val backStack = rememberNavBackStack(HomeScreen, ListScreen, DetailScreen(itemId = deepLinkId))
```

### Custom transitions

```kotlin
Nav3ScreenHost(
    modifier = Modifier.fillMaxSize(),
    backStack = backStack,
    transitionSpec = { myPushTransform() },
    popTransitionSpec = { myPopTransform() },
    predictivePopTransitionSpec = { myPopTransform() },
)
```

Per-screen overrides go through `Nav3Screen.metadata` using `NavDisplay.transitionSpec(...)` / `NavDisplay.popTransitionSpec(...)` keys.

## Dependencies

- `androidx.navigation3:navigation3-runtime` / `navigation3-ui` 1.2.0-alpha07
- `androidx.lifecycle:lifecycle-viewmodel-navigation3` 2.11.0
