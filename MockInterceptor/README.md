# MockInterceptor

An OkHttp interceptor with a route-matching DSL for mocking API responses during development and testing. Define mock routes with path parameters, grouped prefixes, and configurable delays — no mock server required.

## Features

- Route-matching DSL with `get`, `post`, `put`, `delete`, `patch` methods
- Path parameter extraction (`/users/{id}`)
- Route grouping with shared path prefixes and delay overrides
- Per-route, per-group, and global default delays
- JSON response bodies from strings or classpath resource files
- Status code helpers (`notFound()`, `unauthorized()`, `serverError()`, etc.)
- Unmatched requests pass through to the real network
- Logging via [FlexiLogger](https://github.com/projectdelta6/FlexiLogger)

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor:1.3.0")
```

## Usage

### Basic Setup

Add the interceptor to your OkHttp client:

```kotlin
val mockInterceptor = MockApiInterceptor(tag = "Mock") {
    defaultDelay(300L) // simulate network latency

    get("api/users") {
        jsonBody("""[{"id": 1, "name": "Alice"}, {"id": 2, "name": "Bob"}]""")
    }

    get("api/users/{id}") { request ->
        val id = request.pathParam("id")
        jsonBody("""{"id": $id, "name": "User $id"}""")
    }

    post("api/users") {
        status(201, "Created")
        jsonBody("""{"id": 3, "name": "New User"}""")
    }

    delete("api/users/{id}") {
        emptyBody()
    }
}

val client = OkHttpClient.Builder()
    .addInterceptor(mockInterceptor)
    .build()
```

### Route Grouping

Group routes under a common path prefix:

```kotlin
MockApiInterceptor(tag = "Mock") {
    group("api/v2") {
        get("users") { jsonBody("...") }
        get("users/{id}") { request -> jsonBody("...") }

        group("admin", delay = 500L) {
            get("stats") { jsonBody("...") }
        }
    }
}
```

### Loading JSON from Files

Place JSON files in `src/debug/resources/` and load them:

```kotlin
get("api/categories") {
    jsonFile("mock/categories.json")
}
```

### Reading Request Data

The `MockRequestContext` provides access to path parameters, query parameters, and the request body:

```kotlin
get("api/items/{id}") { request ->
    val id = request.pathParamInt("id")
    val page = request.queryParamInt("page", 1)
    jsonBody("...")
}

post("api/items") { request ->
    val body = request.bodyString()
    jsonBody("...")
}
```

### Status Code Helpers

```kotlin
get("api/protected") { unauthorized() }
get("api/missing") { notFound() }
get("api/broken") { serverError() }
get("api/invalid") { badRequest() }
```

## Extension Modules

- **[MockInterceptor-Serialization](../MockInterceptor-Serialization/)** — `jsonBody<T>()` and `paginate()` via kotlinx-serialization
- **[MockInterceptor-AppolyJson](../MockInterceptor-AppolyJson/)** — Appoly JSON envelope helpers
- **[MockInterceptor-Retrofit](../MockInterceptor-Retrofit/)** — Auto-register routes from Retrofit interface annotations
