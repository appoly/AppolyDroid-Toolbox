# MockInterceptor-AppolyJson

Extension for [MockInterceptor-Serialization](../MockInterceptor-Serialization/) that provides helpers for mocking API responses in Appoly's standard JSON envelope format.

## Features

- `successBody<T>()` — wrap a typed payload in `{ "success": true, "data": ... }`
- `successMessage()` — success response with only a message
- `errorBody()` — error response with `{ "success": false, "message": "..." }`
- `pagedBody<T>()` — paginated response in Appoly's nested pagination format
- `emptyPage()` — empty paginated response

## Installation

```gradle.kts
// MockInterceptor and MockInterceptor-Serialization are included transitively
implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-AppolyJson:1.3.3")
```

## Usage

### Success Responses

```kotlin
@Serializable
data class User(val id: Int, val name: String)

MockApiInterceptor(tag = "Mock") {
    // { "success": true, "data": [{"id": 1, "name": "Alice"}, ...] }
    get("api/users") {
        successBody(listOf(User(1, "Alice"), User(2, "Bob")))
    }

    // { "success": true, "data": {"id": 1, "name": "Alice"} }
    get("api/users/{id}") { request ->
        successBody(User(request.pathParamInt("id"), "Alice"))
    }

    // { "success": true, "message": "Item deleted" }
    delete("api/items/{id}") {
        successMessage("Item deleted")
    }
}
```

### Error Responses

```kotlin
// { "success": false, "message": "Item not found" } with HTTP 404
get("api/items/{id}") { request ->
    errorBody("Item not found", code = 404)
}

// { "success": false, "message": "Validation failed" } with HTTP 400 (default)
post("api/items") {
    errorBody("Validation failed")
}
```

### Paginated Responses

Automatically slices a list using `page` and `per_page` query parameters and wraps it in the Appoly nested pagination envelope:

```kotlin
val allItems = (1..50).map { Item(it, "Item $it") }

// Produces:
// {
//   "success": true,
//   "data": {
//     "data": [...items...],
//     "current_page": 1,
//     "last_page": 5,
//     "per_page": 10,
//     "from": 1,
//     "to": 10,
//     "total": 50
//   }
// }
get("api/items") { request ->
    pagedBody(allItems, request)
}
```

For an empty paginated response:

```kotlin
get("api/items") {
    emptyPage()
}
```

You can also build a paginated response from a pre-computed `PageSlice` if you need custom pagination logic:

```kotlin
get("api/items") { request ->
    val slice = paginate(allItems, request, defaultPerPage = 20)
    pagedBody(slice)
}
```
