# AppolyDroid Toolbox

Appoly's Android development toolbox - a collection of utilities and components to accelerate Android app development

[![Maven Central](https://img.shields.io/maven-central/v/uk.co.appoly.droid/baserepo)](https://central.sonatype.com/namespace/uk.co.appoly.droid)

## Overview

AppolyDroid Toolbox is a comprehensive collection of Android utility modules that simplify common development tasks. The library provides ready-to-use solutions for:

- API data handling with `BaseRepo`
- AWS S3 file uploading
- Date/time operations
- UI state management
- Snackbar notifications
- Segmented controls
- Jetpack Compose pagination utilities
- Voyager-style Navigation 3 screens (`Nav3Navigation`)
- And more!

## Installation

The toolbox is published to Maven Central, so no custom repository is required — if
`mavenCentral()` is already in your repositories list, there is nothing to add:

```gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

> **Migrating from 1.8.3 or earlier?** Those versions were published on JitPack under
> `com.github.appoly.AppolyDroid-Toolbox` with PascalCase artifact names. From 1.9.0 the coordinates
> are `uk.co.appoly.droid` with lowercase names — for example
> `com.github.appoly.AppolyDroid-Toolbox:BaseRepo` becomes `uk.co.appoly.droid:baserepo`, and
> `AppolyDroid-Toolbox-bom` becomes `bom`. Keep your `jitpack.io` entry unless you have checked
> that nothing else needs it — the toolbox no longer does, but other dependencies may, and a group
> like `com.github.projectdelta6` looks deceptively like the toolbox's old coordinates. The
> [1.9.0 release notes](https://github.com/appoly/AppolyDroid-Toolbox/releases/tag/1.9.0) cover the
> full move, including a transitive FlexiLogger bump that arrives with it.

Need to test a change before it is released, or release the toolbox yourself? See
[CONTRIBUTING.md](CONTRIBUTING.md).

### Using the BOM (Bill of Materials)

For easier dependency management, you can use the AppolyDroid BOM which provides version alignment for all modules and their shared dependencies:

#### Using Version Catalog

In your `libs.versions.toml` file:

```toml
[versions]
appolydroidToolbox = "1.9.0" # Replace with the latest version

[libraries]
appolydroid-toolbox-bom = { group = "uk.co.appoly.droid", name = "bom", version.ref = "appolydroidToolbox" }
# AppolyDroid modules (versions managed by BOM)
appolydroid-toolbox-baseRepo = { group = "uk.co.appoly.droid", name = "baserepo" }
appolydroid-toolbox-baseRepo-appolyJson = { group = "uk.co.appoly.droid", name = "baserepo-appolyjson" }
appolydroid-toolbox-baseRepo-s3 = { group = "uk.co.appoly.droid", name = "baserepo-s3uploader" }
appolydroid-toolbox-baseRepo-s3-multipart = { group = "uk.co.appoly.droid", name = "baserepo-s3uploader-multipart" }
appolydroid-toolbox-baseRepo-paging = { group = "uk.co.appoly.droid", name = "baserepo-paging" }
appolydroid-toolbox-baseRepo-paging-AppolyJson = { group = "uk.co.appoly.droid", name = "baserepo-paging-appolyjson" }
appolydroid-toolbox-uiState = { group = "uk.co.appoly.droid", name = "uistate" }
appolydroid-toolbox-appSnackBar = { group = "uk.co.appoly.droid", name = "appsnackbar" }
appolydroid-toolbox-appSnackBar-uiState = { group = "uk.co.appoly.droid", name = "appsnackbar-uistate" }
appolydroid-toolbox-dateHelper = { group = "uk.co.appoly.droid", name = "datehelperutil" }
appolydroid-toolbox-dateHelper-room = { group = "uk.co.appoly.droid", name = "datehelperutil-room" }
appolydroid-toolbox-dateHelper-serialization = { group = "uk.co.appoly.droid", name = "datehelperutil-serialization" }
appolydroid-toolbox-compose-extensions = { group = "uk.co.appoly.droid", name = "composeextensions" }
appolydroid-toolbox-segmentedControl = { group = "uk.co.appoly.droid", name = "segmentedcontrol" }
appolydroid-toolbox-lazyListPagingExtensions = { group = "uk.co.appoly.droid", name = "lazylistpagingextensions" }
appolydroid-toolbox-lazyGridPagingExtensions = { group = "uk.co.appoly.droid", name = "lazygridpagingextensions" }
appolydroid-toolbox-pagingExtensions = { group = "uk.co.appoly.droid", name = "pagingextensions" }
appolydroid-toolbox-s3Uploader = { group = "uk.co.appoly.droid", name = "s3uploader" }
appolydroid-toolbox-s3Uploader-multipart = { group = "uk.co.appoly.droid", name = "s3uploader-multipart" }
appolydroid-toolbox-connectivityMonitor = { group = "uk.co.appoly.droid", name = "connectivitymonitor" }
appolydroid-toolbox-nav3Navigation = { group = "uk.co.appoly.droid", name = "nav3navigation" }
appolydroid-toolbox-mockInterceptor = { group = "uk.co.appoly.droid", name = "mockinterceptor" }
appolydroid-toolbox-mockInterceptor-serialization = { group = "uk.co.appoly.droid", name = "mockinterceptor-serialization" }
appolydroid-toolbox-mockInterceptor-appolyjson = { group = "uk.co.appoly.droid", name = "mockinterceptor-appolyjson" }
appolydroid-toolbox-mockInterceptor-retrofit = { group = "uk.co.appoly.droid", name = "mockinterceptor-retrofit" }
```

Then in your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    // Import the BOM
    implementation(platform(libs.appolydroid.toolbox.bom))

    // Now you can use AppolyDroid modules without specifying versions
    implementation(libs.appolydroid.toolbox.baseRepo)
    implementation(libs.appolydroid.toolbox.baseRepo.appolyJson)
    implementation(libs.appolydroid.toolbox.baseRepo.s3)
    implementation(libs.appolydroid.toolbox.baseRepo.s3.multipart)
    implementation(libs.appolydroid.toolbox.baseRepo.paging)
    implementation(libs.appolydroid.toolbox.baseRepo.pagingAppolyJson)
    implementation(libs.appolydroid.toolbox.uiState)
    implementation(libs.appolydroid.toolbox.appSnackBar)
    implementation(libs.appolydroid.toolbox.appSnackBar.uiState)
    implementation(libs.appolydroid.toolbox.dateHelper)
    implementation(libs.appolydroid.toolbox.dateHelper.room)
    implementation(libs.appolydroid.toolbox.dateHelper.serialization)
    implementation(libs.appolydroid.toolbox.compose.extensions)
    implementation(libs.appolydroid.toolbox.segmentedControl)
    implementation(libs.appolydroid.toolbox.lazyListPagingExtensions)
    implementation(libs.appolydroid.toolbox.lazyGridPagingExtensions)
    implementation(libs.appolydroid.toolbox.pagingExtensions)
    implementation(libs.appolydroid.toolbox.s3Uploader)
    implementation(libs.appolydroid.toolbox.s3Uploader.multipart)
    implementation(libs.appolydroid.toolbox.connectivityMonitor)
    implementation(libs.appolydroid.toolbox.nav3Navigation)
    implementation(libs.appolydroid.toolbox.mockInterceptor)
    implementation(libs.appolydroid.toolbox.mockInterceptor.serialization)
    implementation(libs.appolydroid.toolbox.mockInterceptor.appolyjson)
    implementation(libs.appolydroid.toolbox.mockInterceptor.retrofit)
}
```

#### Without Version Catalog (BOM)

In your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    // Import the BOM
    implementation(platform("uk.co.appoly.droid:bom:1.9.0"))

    // Now you can use AppolyDroid modules without specifying versions
    implementation("uk.co.appoly.droid:baserepo")
    implementation("uk.co.appoly.droid:baserepo-appolyjson")
    implementation("uk.co.appoly.droid:baserepo-s3uploader")
    implementation("uk.co.appoly.droid:baserepo-s3uploader-multipart")
    implementation("uk.co.appoly.droid:baserepo-paging")
    implementation("uk.co.appoly.droid:baserepo-paging-appolyjson")
    implementation("uk.co.appoly.droid:uistate")
    implementation("uk.co.appoly.droid:appsnackbar")
    implementation("uk.co.appoly.droid:appsnackbar-uistate")
    implementation("uk.co.appoly.droid:datehelperutil")
    implementation("uk.co.appoly.droid:datehelperutil-room")
    implementation("uk.co.appoly.droid:datehelperutil-serialization")
    implementation("uk.co.appoly.droid:composeextensions")
    implementation("uk.co.appoly.droid:segmentedcontrol")
    implementation("uk.co.appoly.droid:lazylistpagingextensions")
    implementation("uk.co.appoly.droid:lazygridpagingextensions")
    implementation("uk.co.appoly.droid:pagingextensions")
    implementation("uk.co.appoly.droid:s3uploader")
    implementation("uk.co.appoly.droid:s3uploader-multipart")
    implementation("uk.co.appoly.droid:connectivitymonitor")
    implementation("uk.co.appoly.droid:nav3navigation")
    implementation("uk.co.appoly.droid:mockinterceptor")
    implementation("uk.co.appoly.droid:mockinterceptor-serialization")
    implementation("uk.co.appoly.droid:mockinterceptor-appolyjson")
    implementation("uk.co.appoly.droid:mockinterceptor-retrofit")
}
```

### Individual Module Installation

In your `libs.versions.toml` file:

```toml
[versions]
appolydroidToolbox = "1.9.0" # Replace with the latest version

[libraries]
#AppolyDroid-Toolbox
appolydroid-toolbox-baseRepo = { group = "uk.co.appoly.droid", name = "baserepo", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-appolyJson = { group = "uk.co.appoly.droid", name = "baserepo-appolyjson", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-s3 = { group = "uk.co.appoly.droid", name = "baserepo-s3uploader", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-s3-multipart = { group = "uk.co.appoly.droid", name = "baserepo-s3uploader-multipart", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-paging = { group = "uk.co.appoly.droid", name = "baserepo-paging", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-paging-AppolyJson = { group = "uk.co.appoly.droid", name = "baserepo-paging-appolyjson", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-uiState = { group = "uk.co.appoly.droid", name = "uistate", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-appSnackBar = { group = "uk.co.appoly.droid", name = "appsnackbar", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-appSnackBar-uiState = { group = "uk.co.appoly.droid", name = "appsnackbar-uistate", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper = { group = "uk.co.appoly.droid", name = "datehelperutil", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper-room = { group = "uk.co.appoly.droid", name = "datehelperutil-room", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper-serialization = { group = "uk.co.appoly.droid", name = "datehelperutil-serialization", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-compose-extensions = { group = "uk.co.appoly.droid", name = "composeextensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-segmentedControl = { group = "uk.co.appoly.droid", name = "segmentedcontrol", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-lazyListPagingExtensions = { group = "uk.co.appoly.droid", name = "lazylistpagingextensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-lazyGridPagingExtensions = { group = "uk.co.appoly.droid", name = "lazygridpagingextensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-s3Uploader = { group = "uk.co.appoly.droid", name = "s3uploader", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-s3Uploader-multipart = { group = "uk.co.appoly.droid", name = "s3uploader-multipart", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-connectivityMonitor = { group = "uk.co.appoly.droid", name = "connectivitymonitor", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-nav3Navigation = { group = "uk.co.appoly.droid", name = "nav3navigation", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor = { group = "uk.co.appoly.droid", name = "mockinterceptor", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor-serialization = { group = "uk.co.appoly.droid", name = "mockinterceptor-serialization", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor-appolyjson = { group = "uk.co.appoly.droid", name = "mockinterceptor-appolyjson", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor-retrofit = { group = "uk.co.appoly.droid", name = "mockinterceptor-retrofit", version.ref = "appolydroidToolbox" }
```

Then in your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    // Add only the modules you need
    implementation(libs.appolydroid.toolbox.baseRepo)
    implementation(libs.appolydroid.toolbox.baseRepo.appolyJson)
    implementation(libs.appolydroid.toolbox.baseRepo.s3)
    implementation(libs.appolydroid.toolbox.baseRepo.s3.multipart)
    implementation(libs.appolydroid.toolbox.baseRepo.paging)
    implementation(libs.appolydroid.toolbox.baseRepo.pagingAppolyJson)
    implementation(libs.appolydroid.toolbox.uiState)
    implementation(libs.appolydroid.toolbox.appSnackBar)
    implementation(libs.appolydroid.toolbox.appSnackBar.uiState)
    implementation(libs.appolydroid.toolbox.dateHelper)
    implementation(libs.appolydroid.toolbox.dateHelper.room)
    implementation(libs.appolydroid.toolbox.dateHelper.serialization)
    implementation(libs.appolydroid.toolbox.compose.extensions)
    implementation(libs.appolydroid.toolbox.segmentedControl)
    implementation(libs.appolydroid.toolbox.lazyListPagingExtensions)
    implementation(libs.appolydroid.toolbox.lazyGridPagingExtensions)
    implementation(libs.appolydroid.toolbox.s3Uploader)
    implementation(libs.appolydroid.toolbox.s3Uploader.multipart)
    implementation(libs.appolydroid.toolbox.connectivityMonitor)
    implementation(libs.appolydroid.toolbox.nav3Navigation)
    implementation(libs.appolydroid.toolbox.mockInterceptor)
    implementation(libs.appolydroid.toolbox.mockInterceptor.serialization)
    implementation(libs.appolydroid.toolbox.mockInterceptor.appolyjson)
    implementation(libs.appolydroid.toolbox.mockInterceptor.retrofit)
}
```

### Without Version Catalog

In your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    val appolydroidToolbox = "1.9.0" // Replace with the latest version
    // Add only the modules you need
    implementation("uk.co.appoly.droid:baserepo:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:baserepo-appolyjson:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:baserepo-s3uploader:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:baserepo-s3uploader-multipart:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:baserepo-paging:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:baserepo-paging-appolyjson:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:uistate:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:appsnackbar:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:appsnackbar-uistate:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:datehelperutil:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:datehelperutil-room:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:datehelperutil-serialization:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:composeextensions:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:segmentedcontrol:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:lazylistpagingextensions:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:lazygridpagingextensions:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:s3uploader:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:s3uploader-multipart:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:connectivitymonitor:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:nav3navigation:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:mockinterceptor:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:mockinterceptor-serialization:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:mockinterceptor-appolyjson:$appolydroidToolbox")
    implementation("uk.co.appoly.droid:mockinterceptor-retrofit:$appolydroidToolbox")
}
```

## Modules
### BaseRepo
Foundation for repository pattern implementation with API call handling.
[Learn more](BaseRepo/README.md)

### BaseRepo-AppolyJson

Extension to BaseRepo providing Appoly's JSON response structure support.
[Learn more](BaseRepo-AppolyJson/README.md)
### BaseRepo-S3Uploader
Extension to BaseRepo adding S3 upload capabilities.
[Learn more](BaseRepo-S3Uploader/README.md)
### BaseRepo-Paging
Extends BaseRepo with Jetpack Paging capabilities.
[Learn more](BaseRepo-Paging/README.md)

### BaseRepo-Paging-AppolyJson

Extension to BaseRepo-Paging providing Appoly's nested JSON paging response structure support.
[Learn more](BaseRepo-Paging-AppolyJson/README.md)
### DateHelperUtil
Utilities for date and time operations.
[Learn more](DateHelperUtil/README.md)
### DateHelperUtil-Room
Room database integration for DateHelperUtil.
[Learn more](DateHelperUtil-Room/README.md)
### DateHelperUtil-Serialization
Kotlinx Serialization support for DateHelperUtil.
[Learn more](DateHelperUtil-Serialization/README.md)
### UiState
Simplified UI state management.
[Learn more](UiState/README.md)
### AppSnackBar
Enhanced Snackbar implementation.
[Learn more](AppSnackBar/README.md)
### AppSnackBar-UiState
Integration of AppSnackBar with UiState.
[Learn more](AppSnackBar-UiState/README.md)
### SegmentedControl
iOS-style segmented control with smooth animations and customizable styling.
[Learn more](SegmentedControl/README.md)
### ComposeExtensions
Compose utilities: insets/IME padding, padding arithmetic, serialization-safe `MutableState` holders for Voyager Screens, and a clipboard copier.
[Learn more](ComposeExtensions/README.md)
### PagingExtensions
Core Jetpack Paging 3 utilities: `LoadState` predicates, paging-stream de-duplication, and shared loading/error/empty state components.
[Learn more](PagingExtensions/README.md)
### LazyListPagingExtensions
Extensions for Jetpack Compose LazyList with paging support.
[Learn more](LazyListPagingExtensions/README.md)
### LazyGridPagingExtensions
Extensions for Jetpack Compose LazyGrid with paging support.
[Learn more](LazyGridPagingExtensions/README.md)
### S3Uploader
Standalone S3 file upload utility.
[Learn more](S3Uploader/README.md)

### S3Uploader-Multipart
Advanced S3 uploads with pause, resume, and recovery support using AWS S3 Multipart Upload API.
[Learn more](S3Uploader-Multipart/README.md)

### BaseRepo-S3Uploader-Multipart
Extension bridging BaseRepo and S3Uploader-Multipart for pausable, resumable uploads within the repository pattern.
[Learn more](BaseRepo-S3Uploader-Multipart/README.md)

### ConnectivityMonitor
Connectivity monitoring flows
[Learn more](ConnectivityMonitor/README.md)

### Nav3Navigation
Voyager-style screens on androidx Navigation 3: fused key+UI (`Nav3Screen`), ambient
`LocalNav3Navigator` push/pop, and a `Nav3ScreenHost` that preserves native predictive back
and per-entry ViewModel/saveable/result decorators.
[Learn more](Nav3Navigation/README.md)

### MockInterceptor
OkHttp interceptor with a route-matching DSL for mocking API responses during development and testing.
[Learn more](MockInterceptor/README.md)

### MockInterceptor-Serialization
Adds `jsonBody<T>()` and `paginate()` helpers using kotlinx-serialization for type-safe mock responses.
[Learn more](MockInterceptor-Serialization/README.md)

### MockInterceptor-AppolyJson
Appoly JSON envelope helpers (`successBody`, `errorBody`, `pagedBody`) for mocking Appoly-standard API responses.
[Learn more](MockInterceptor-AppolyJson/README.md)

### MockInterceptor-Retrofit
`mockApi<T>()` DSL that reads Retrofit annotations via reflection to auto-register mock routes from interface methods.
[Learn more](MockInterceptor-Retrofit/README.md)

## Dependencies

Some modules depend on [FlexiLogger](https://github.com/projectdelta6/FlexiLogger) for logging capabilities.

## R8 / ProGuard

**You don't need to author any keep rules for AppolyDroid internals.** Every module ships its
own [consumer ProGuard rules](https://developer.android.com/build/shrink-code#configuration-files)
inside its AAR, so the keeps for serializable response models, custom `KSerializer`s, Room
converters/entities, and WorkManager workers are merged into your app's R8 configuration
automatically when you depend on the module. This holds in R8 full mode.

This includes the `-keepattributes Signature,InnerClasses` that the generic response models
(`PageData<T>`, `GenericResponse<T>`, `GenericNestedPagedResponse<T>`) need for runtime type
reconstruction — each is shipped by the module that declares those models, so you don't add it
yourself. The rules deliberately **do not** keep `*Annotation*`: the library uses no polymorphic
serialization, so no runtime-annotation attributes are required, and keeping `*Annotation*` in a
library's consumer rules would disable annotation-related optimizations across the whole consuming
app (R8 warns about exactly this).

**Enum serializer base classes.** If you subclass any of these (shipped by `BaseRepo`) for your
own `@Serializable` enums, your generated subclasses are kept automatically — the rule keeps
`* extends` each base:

- `uk.co.appoly.droid.util.EnumAsStringSerializer`
- `uk.co.appoly.droid.util.NullableEnumAsStringSerializer`
- `uk.co.appoly.droid.util.EnumAsIntSerializer`
- `uk.co.appoly.droid.util.NullableEnumAsIntSerializer`

**`Nav3Screen` implementors (your classes, not ours).** `Nav3Navigation` is the one module whose
consumer rules keep classes *you* wrote. Navigation 3 persists its back stack by resolving each
key's `KSerializer` reflectively, so under R8 full mode your `@Serializable` screen classes — which
nothing references directly — could be stripped or renamed, breaking back-stack restore after
process death. The module therefore keeps every implementor of
`uk.co.appoly.droid.nav3.Nav3Screen`, along with its `Companion` and generated `$$serializer`. The
rules are scoped to that interface; there is no blanket `-keep class ** { *; }`.

**Regression test.** These rules are guarded by the `verifyConsumerKeepRules` Gradle task in the
`app` module. The demo app depends on every module and is minified (`isMinifyEnabled = true`), so
R8 applies all of their `consumer-rules.pro`. The task reads R8's `seeds.txt` — the exact set of
classes its keep rules matched — and asserts every serializer / converter the consumer rules
protect is present. If a module's rule regresses, the class drops out of `seeds.txt` and the task
fails. It runs in CI (no device needed) and via the **"Verify Consumer R8 Rules"** IDE run config:

> **What this does and does not prove.** `seeds.txt` records *that* something was kept, never
> *which* rule kept it, and a class listed there may still have been renamed. So the task also
> asserts, against `mapping.txt`, that `Nav3Screen` implementors keep their exact fully-qualified
> names — Nav3 resolves back-stack keys by serial name, which defaults to the FQCN, and
> kotlinx-serialization's own rules keep `@Serializable` classes with `allowobfuscation`. Keeps
> that merely duplicate another library's shipped rules (Nav3's `Companion` / `serializer()` /
> `INSTANCE` branches mirror `kotlinx-serialization-common.pro`) cannot be attributed here; they
> are retained as insurance against that upstream file changing, not because this task verifies
> them. The task header in `app/build.gradle.kts` records how to re-measure this.

```bash
./gradlew :app:verifyConsumerKeepRules
```

**On-device suite (Nav3Navigation).** `Nav3Navigation` additionally ships a small instrumented
suite covering what Robolectric cannot reach — real predictive-back gestures and real Activity
recreation. It is **deliberately excluded from CI** (it needs a device) and is a **pre-release
gate**: run it before tagging, alongside `verifyConsumerKeepRules`.

```bash
./gradlew :Nav3Navigation:connectedDebugAndroidTest
```

See the [Nav3Navigation README](Nav3Navigation/README.md#the-modules-own-suites) for what it
covers, plus a manual cold-restore checklist for the two things no instrumented test can reach
(true process death and minified back-stack restore — killing the process kills the instrumentation
with it). Both were verified by hand against `1.7.0-beta03` on a physical device, through a real
minified consuming app, including a parameterised `NavKey` round-tripping its arguments under R8.

> An earlier version used an instrumented test (`testBuildType = "release"`) that round-tripped the
> serializers on a device. It was dropped: minifying the *test* APK strips the test runner's own
> transitive dependencies (`androidx.tracing`, Kotlin stdlib facades, …), which is plumbing
> unrelated to the library. The static `seeds.txt` check gives the same regression guarantee with
> no device and no test-harness R8 fight.

## License

```text
MIT License

Copyright (c) 2025 Appoly Ltd

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
