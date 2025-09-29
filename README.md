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
appolydroidToolbox = "1.0.33" # Replace with the latest version

[libraries]
appolydroid-toolbox-bom = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppolyDroid-Toolbox-bom", version.ref = "appolydroidToolbox" }
# AppolyDroid modules (versions managed by BOM)
appolydroid-toolbox-baseRepo = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo" }
appolydroid-toolbox-baseRepo-s3 = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-S3Uploader" }
appolydroid-toolbox-baseRepo-paging = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-Paging" }
appolydroid-toolbox-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "UiState" }
appolydroid-toolbox-appSnackBar = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar" }
appolydroid-toolbox-appSnackBar-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar-UiState" }
appolydroid-toolbox-dateHelper = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil" }
appolydroid-toolbox-dateHelper-room = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Room" }
appolydroid-toolbox-dateHelper-serialization = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Serialization" }
appolydroid-toolbox-compose-extensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "ComposeExtensions" }
appolydroid-toolbox-lazyListPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyListPagingExtensions" }
appolydroid-toolbox-lazyGridPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyGridPagingExtensions" }
appolydroid-toolbox-pagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "PagingExtensions" }
appolydroid-toolbox-s3Uploader = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "S3Uploader" }
```

Then in your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    // Import the BOM
    implementation(platform(libs.appolydroid.toolbox.bom))

    // Now you can use AppolyDroid modules without specifying versions
    implementation(libs.appolydroid.toolbox.baseRepo)
    implementation(libs.appolydroid.toolbox.baseRepo.s3)
    implementation(libs.appolydroid.toolbox.baseRepo.paging)
    implementation(libs.appolydroid.toolbox.uiState)
    implementation(libs.appolydroid.toolbox.appSnackBar)
    implementation(libs.appolydroid.toolbox.appSnackBar.uiState)
    implementation(libs.appolydroid.toolbox.dateHelper)
    implementation(libs.appolydroid.toolbox.dateHelper.room)
    implementation(libs.appolydroid.toolbox.dateHelper.serialization)
    implementation(libs.appolydroid.toolbox.compose.extensions)
    implementation(libs.appolydroid.toolbox.lazyListPagingExtensions)
    implementation(libs.appolydroid.toolbox.lazyGridPagingExtensions)
    implementation(libs.appolydroid.toolbox.pagingExtensions)
    implementation(libs.appolydroid.toolbox.s3Uploader)
}
```

#### Without Version Catalog (BOM)

In your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    // Import the BOM
    implementation(platform("com.github.appoly.AppolyDroid-Toolbox:AppolyDroid-Toolbox-bom:1.0.33"))

    // Now you can use AppolyDroid modules without specifying versions
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging")
    implementation("com.github.appoly.AppolyDroid-Toolbox:UiState")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization")
    implementation("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:PagingExtensions")
    implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader")
}
```

**Benefits of using the BOM:**

- Ensures all AppolyDroid modules use compatible versions
- Provides version management for shared 3rd party dependencies
- Simplifies dependency declarations
- Reduces version conflicts

### Individual Module Installation

In your `libs.versions.toml` file:

```toml
[versions]
appolydroidToolbox = "1.0.33" # Replace with the latest version

[libraries]
#AppolyDroid-Toolbox
appolydroid-toolbox-baseRepo = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-s3 = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-S3Uploader", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-baseRepo-paging = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "BaseRepo-Paging", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper-room = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Room", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-dateHelper-serialization = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "DateHelperUtil-Serialization", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "UiState", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-appSnackBar = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-appSnackBar-uiState = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "AppSnackBar-UiState", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-lazyListPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyListPagingExtensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-lazyGridPagingExtensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "LazyGridPagingExtensions", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-s3Uploader = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "S3Uploader", version.ref = "appolydroidToolbox" }
appolydroid-toolbox-compose-extensions = { group = "com.github.appoly.AppolyDroid-Toolbox", name = "ComposeExtensions", version.ref = "appolydroidToolbox" }
```

Then in your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    // Add only the modules you need
    implementation(libs.appolydroid.toolbox.baseRepo)
    implementation(libs.appolydroid.toolbox.baseRepo.s3)
    implementation(libs.appolydroid.toolbox.baseRepo.paging)
    implementation(libs.appolydroid.toolbox.dateHelper)
    implementation(libs.appolydroid.toolbox.dateHelper.room)
    implementation(libs.appolydroid.toolbox.dateHelper.serialization)
    implementation(libs.appolydroid.toolbox.uiState)
    implementation(libs.appolydroid.toolbox.appSnackBar)
    implementation(libs.appolydroid.toolbox.appSnackBar.uiState)
    implementation(libs.appolydroid.toolbox.lazyListPagingExtensions)
    implementation(libs.appolydroid.toolbox.lazyGridPagingExtensions)
    implementation(libs.appolydroid.toolbox.s3Uploader)
    implementation(libs.appolydroid.toolbox.compose.extensions)
}
```
### Without Version Catalog

In your module's `build.gradle.kts`:

```gradle.kts
dependencies {
    // Add only the modules you need
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-S3Uploader:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:UiState:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:AppSnackBar-UiState:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:S3Uploader:1.0.32_rc01_bom")
    implementation("com.github.appoly.AppolyDroid-Toolbox:ComposeExtensions:1.0.32_rc01_bom")
}
```

## Modules
### BaseRepo
Foundation for repository pattern implementation with API call handling.
[Learn more](BaseRepo/README.md)
### BaseRepo-S3Uploader
Extension to BaseRepo adding S3 upload capabilities.
[Learn more](BaseRepo-S3Uploader/README.md)
### BaseRepo-Paging
Extends BaseRepo with Jetpack Paging capabilities.
[Learn more](BaseRepo-Paging/README.md)
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
### LazyListPagingExtensions
Extensions for Jetpack Compose LazyList with paging support.
[Learn more](LazyListPagingExtensions/README.md)
### LazyGridPagingExtensions
Extensions for Jetpack Compose LazyGrid with paging support.
[Learn more](LazyGridPagingExtensions/README.md)
### S3Uploader
Standalone S3 file upload utility.
[Learn more](S3Uploader/README.md)

## Dependencies

Some modules depend on [FlexiLogger](https://github.com/projectdelta6/FlexiLogger) for logging capabilities.

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

