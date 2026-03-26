# MockInterceptor-Serialization

Extension for [MockInterceptor](../MockInterceptor/) that adds type-safe JSON response bodies and pagination helpers using kotlinx-serialization.

## Features

- `jsonBody<T>()` — serialize any `@Serializable` object as the response body
- `paginate()` — slice a list into pages based on query parameters
- `PageSlice<T>` — format-agnostic pagination data class for use with custom envelopes

## Installation

```gradle.kts
// MockInterceptor is included transitively
implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Serialization:1.3.1")
```

## Usage

### Typed JSON Responses

Use `jsonBody<T>()` instead of manually writing JSON strings:

```kotlin
@Serializable
data class User(val id: Int, val name: String)

MockApiInterceptor(tag = "Mock") {
    get("api/users") {
        jsonBody(listOf(User(1, "Alice"), User(2, "Bob")))
    }

    get("api/users/{id}") { request ->
        val id = request.pathParamInt("id")
        jsonBody(User(id, "User $id"))
    }
}
```

### Custom Json Instance

Pass a custom `Json` instance if needed:

```kotlin
val json = Json {
    encodeDefaults = true
    prettyPrint = true
}

get("api/users") {
    jsonBody(listOf(User(1, "Alice")), json)
}
```

### Pagination

Use `paginate()` to automatically slice a list based on `page` and `per_page` query parameters:

```kotlin
val allItems = (1..50).map { Item(it, "Item $it") }

get("api/items") { request ->
    val slice = paginate(allItems, request)
    // slice.items = current page items
    // slice.page, slice.perPage, slice.total, slice.lastPage, etc.
    jsonBody(slice.items) // or wrap in your own envelope
}
```

The `paginate()` function supports custom parameter names and defaults:

```kotlin
val slice = paginate(
    allItems = items,
    request = request,
    pageParam = "page",       // query param name for page number
    perPageParam = "per_page", // query param name for page size
    defaultPerPage = 10,       // default if per_page not in request
)
```

### PageSlice

`PageSlice<T>` is a plain data class containing the sliced items and pagination metadata. It does not impose any JSON format — wrap it in whatever envelope your API uses:

```kotlin
data class PageSlice<T>(
    val items: List<T>,
    val page: Int,
    val perPage: Int,
    val total: Int,
    val lastPage: Int,
    val from: Int,  // 1-indexed position of first item (0 if empty)
    val to: Int,    // 1-indexed position of last item (0 if empty)
)
```
