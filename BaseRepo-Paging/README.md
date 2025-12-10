# BaseRepo-Paging

An extension module for BaseRepo that adds Jetpack Paging 3 support for efficient data loading in RecyclerViews and Jetpack Compose. This module provides generic paging functionality that can be
extended for specific JSON formats.

## Features

- Seamless integration with BaseRepo and APIResult pattern
- Support for Jetpack Paging 3 library
- Standardized paging source implementation
- Thread-safe invalidation capabilities for refreshing data
- Support for jumping to specific pages
- Integration with both traditional RecyclerView and Jetpack Compose
- Extensible design for custom JSON paging formats

## Installation

```gradle.kts
// Requires the base BaseRepo module
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.1.9")
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-Paging:1.1.9")

// For Compose UI integration
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyListPagingExtensions:1.1.9") // For LazyColumn
implementation("com.github.appoly.AppolyDroid-Toolbox:LazyGridPagingExtensions:1.1.9") // For LazyGrid
```

## Extensions

For specific JSON paging response formats, use the following extension modules:

- **BaseRepo-Paging-AppolyJson**: Provides support for Appoly's nested JSON paging response structure.

## Usage

### Step 1: Create API Service Interface

First, create your API service interface that returns paginated responses. The exact structure depends on the JSON format used.

### Step 2: Add Repository Method to Fetch Pages

In your repository, create a method that fetches a page. The implementation will vary based on the JSON format.

### Step 3: Create a PagingSource Factory

Create a factory that will generate paging sources on demand. Refer to extension modules for specific implementations.

## Key Components

### GenericNestedPagedResponse

Models the nested paged response from your API.

### PageData

A flattened, non-nullable representation of page data with computed properties like `itemsBefore` and `itemsAfter`.

### GenericPagingSource

Implements Android's `PagingSource` for seamless integration with Paging 3.

### GenericInvalidatingPagingSourceFactory

Thread-safe factory that creates and tracks paging sources, allowing for invalidation.

## Dependencies

- [BaseRepo](../BaseRepo/README.md) - Core repository pattern implementation
- [Jetpack Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview) - Android paging library
