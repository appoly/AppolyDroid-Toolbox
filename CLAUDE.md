# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AppolyDroid Toolbox is a multi-module Android library published via JitPack. It provides utilities for API handling, UI state management, Compose pagination, S3 uploads, and date utilities.

## Build Commands

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :BaseRepo:build

# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Publish to local Maven (for testing)
./gradlew publishToMavenLocal

# Clean build
./gradlew clean build
```

## Module Architecture

The library uses a layered module structure:

**Core Foundation:**
- `BaseRepo` - Repository pattern with Retrofit/Sandwich integration, `APIResult<T>` and `APIFlowState<T>` sealed classes
- `BOM` - Bill of Materials providing version constraints (current: 1.2.5)

**BaseRepo Extensions:**
- `BaseRepo-AppolyJson` - Appoly's standard JSON response format
- `BaseRepo-Paging` - Jetpack Paging 3 integration
- `BaseRepo-Paging-AppolyJson` - Combines paging with Appoly JSON
- `BaseRepo-S3Uploader` - S3 upload integration with BaseRepo

**Compose Pagination (builds on PagingExtensions):**
- `PagingExtensions` - Core paging utilities, LoadState extensions
- `LazyListPagingExtensions` - LazyColumn/LazyRow helpers
- `LazyGridPagingExtensions` - LazyGrid helpers

**Standalone Utilities:**
- `UiState` - Sealed class for UI state (Idle/Loading/Success/Error)
- `S3Uploader` - Direct S3 uploads with progress tracking
- `ConnectivityMonitor` - Network state monitoring
- `DateHelperUtil` - Date/time operations (with Room and Serialization variants)
- `AppSnackBar` / `AppSnackBar-UiState` - Enhanced Snackbar

**Demo App:**
- `app` - Test application implementing all modules

## Key Patterns

**API Result Handling:**
```kotlin
// APIResult<T> sealed class for single responses
when (result) {
    is APIResult.Success -> result.data
    is APIResult.Error -> result.message
}

// APIFlowState<T> for observable flows
flow.collect { state ->
    when (state) {
        is APIFlowState.Loading -> ...
        is APIFlowState.Success -> state.data
        is APIFlowState.Error -> state.message
    }
}
```

**Kotlin Contracts:** Extension functions use contracts for smart casting (e.g., `isSuccess()` implies `this is Success`).

**Namespace Convention:** `uk.co.appoly.droid.<module>` (e.g., `uk.co.appoly.droid.baserepo`)

## Version Management

- Versions are centralized in `gradle/libs.versions.toml`
- The `UpdateReadmeVersions` Gradle task (in `buildSrc`) automatically syncs version numbers in README files during Gradle sync
- Library version is defined in `buildSrc/src/main/kotlin/BuildConfig.kt` as `TOOLBOX_VERSION`
- SDK levels (compile/target) are also in `BuildConfig.kt` under `BuildConfig.Sdk`

## Tech Stack

- Kotlin 2.3.10, AGP 9.0.1, Gradle 9.2.1
- Target/Compile SDK 36, Java 11
- Jetpack Compose BOM 2026.02.00
- OkHttp 5.3.2, Retrofit 3.0.0
- Sandwich 2.2.1 (API response handling)
- Jetpack Paging 3.4.1, Room 2.8.4
- kotlinx-serialization 1.10.0

## Publishing

Published to JitPack via `com.github.appoly`. Each module has:
- Maven publication configuration
- Sources JAR
- Consumer ProGuard rules (`consumer-rules.pro`)
