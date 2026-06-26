# AppolyDroid Toolbox

Appoly's Android development toolbox - a collection of utilities and components to accelerate Android app development

[![Release](https://jitpack.io/v/appoly/AppolyDroid-Toolbox.svg)](https://jitpack.io/#appoly/AppolyDroid-Toolbox)

## Overview

AppolyDroid Toolbox is a comprehensive collection of Android utility modules that simplify common development tasks. The library provides ready-to-use solutions for:

- API data handling with `BaseRepo`
- AWS S3 file uploading
- Date/time operations
- UI state management
- Snackbar notifications
- Segmented controls
- Jetpack Compose pagination utilities
- And more!

## Installation

Add the JitPack repository to your project build file:

```gradle.kts
dependencyResolutionManagement {
    repositories {
        ...
        maven {
            url = uri("https://jitpack.io")
        }
    }
}
```

or in your `settings.gradle` with:

```gradle
allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
    }
}
```

### Using the BOM (Bill of Materials)

For easier dependency management, you can use the AppolyDroid BOM which provides version alignment for all modules and their shared dependencies:

#### Using Version Catalog

In your `libs.versions.toml` file:

```toml
[versions]
appolydroidToolbox = "1.6.0" # Replace with the latest version

[libraries]
appolydroid-toolbox-bom = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppolyDroid-Toolbox-bom", version.ref = "appolydroidToolbox" }
# AppolyDroid modules (versions managed by BOM)
appolydroid-toolbox-baseRepo = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo" }
appolydroid-toolbox-baseRepo-appolyJson = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-AppolyJson" }
appolydroid-toolbox-baseRepo-s3 = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-S3Uploader" }
appolydroid-toolbox-baseRepo-s3-multipart = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-S3Uploader-Multipart" }
appolydroid-toolbox-baseRepo-paging = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-Paging" }
appolydroid-toolbox-baseRepo-paging-AppolyJson = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-Paging-AppolyJson" }
appolydroid-toolbox-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "UiState" }
appolydroid-toolbox-appSnackBar = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar" }
appolydroid-toolbox-appSnackBar-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar-UiState" }
appolydroid-toolbox-dateHelper = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil" }
appolydroid-toolbox-dateHelper-room = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Room" }
appolydroid-toolbox-dateHelper-serialization = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Serialization" }
appolydroid-toolbox-compose-extensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "ComposeExtensions" }
appolydroid-toolbox-segmentedControl = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "SegmentedControl" }
appolydroid-toolbox-lazyListPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyListPagingExtensions" }
appolydroid-toolbox-lazyGridPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyGridPagingExtensions" }
appolydroid-toolbox-pagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "PagingExtensions" }
appolydroid-toolbox-s3Uploader = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "S3Uploader" }
appolydroid-toolbox-s3Uploader-multipart = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "S3Uploader-Multipart" }
appolydroid-toolbox-connectivityMonitor = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "ConnectivityMonitor" }
appolydroid-toolbox-mockInterceptor = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor" }
appolydroid-toolbox-mockInterceptor-serialization = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor-Serialization" }
appolydroid-toolbox-mockInterceptor-appolyjson = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor-AppolyJson" }
appolydroid-toolbox-mockInterceptor-retrofit = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor-Retrofit" }
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
    implementation(platform("com.github.appoly.AppolyDroid-Toolbox:AppolyDroid-Toolbox-bom:1.6.0"))

    // Now you can use AppolyDroid modules without specifying versions
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-AppolyJson")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader-Multipart")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging-AppolyJson")
    implementation("com.github.appoly.AppolyDroid-Toolbox:UiState")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization")
    implementation("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:SegmentedControl")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader")
    implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart")
    implementation("com.github.appoly.AppolyDroid-Toolbox:ConnectivityMonitor")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Serialization")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-AppolyJson")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Retrofit")
}
```

### Individual Module Installation

In your `libs.versions.toml` file:

```toml
[versions]
appolydroidToolbox = "1.6.0" # Replace with the latest version

[libraries]
#AppolyDroid-Toolbox
appolydroid-toolbox-baseRepo = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-appolyJson = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-AppolyJson", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-s3 = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-S3Uploader", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-s3-multipart = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-S3Uploader-Multipart", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-paging = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-Paging", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-paging-AppolyJson = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-Paging-AppolyJson", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "UiState", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-appSnackBar = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-appSnackBar-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar-UiState", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper-room = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Room", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper-serialization = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Serialization", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-compose-extensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "ComposeExtensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-segmentedControl = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "SegmentedControl", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-lazyListPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyListPagingExtensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-lazyGridPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyGridPagingExtensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-s3Uploader = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "S3Uploader", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-s3Uploader-multipart = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "S3Uploader-Multipart", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-connectivityMonitor = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "ConnectivityMonitor", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor-serialization = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor-Serialization", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor-appolyjson = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor-AppolyJson", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-mockInterceptor-retrofit = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "MockInterceptor-Retrofit", version.ref = "appolydroidToolbox" }
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
    val appolydroidToolbox = "1.6.0" // Replace with the latest version
    // Add only the modules you need
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-AppolyJson:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader-Multipart:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging-AppolyJson:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:UiState:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:SegmentedControl:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader-Multipart:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:ConnectivityMonitor:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Serialization:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-AppolyJson:$appolydroidToolbox")
    implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Retrofit:$appolydroidToolbox")
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

**Regression test.** These rules are guarded by the `verifyConsumerKeepRules` Gradle task in the
`app` module. The demo app depends on every module and is minified (`isMinifyEnabled = true`), so
R8 applies all of their `consumer-rules.pro`. The task reads R8's `seeds.txt` — the exact set of
classes its keep rules matched — and asserts every serializer / converter the consumer rules
protect is present. If a module's rule regresses, the class drops out of `seeds.txt` and the task
fails. It runs in CI (no device needed) and via the **"Verify Consumer R8 Rules"** IDE run config:

```bash
./gradlew :app:verifyConsumerKeepRules
```

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
