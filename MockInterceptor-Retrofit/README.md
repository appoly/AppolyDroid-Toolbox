# MockInterceptor-Retrofit

Extension for [MockInterceptor](../MockInterceptor/) that reads Retrofit HTTP annotations via reflection to auto-register mock routes from interface methods.

## Features

- `mockApi<T>()` DSL reads `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH` annotations at runtime
- Reference methods with `KFunction` for compile-time safety — renaming a Retrofit method triggers a compile error
- Query strings in annotation paths are automatically stripped
- Only methods with both a Retrofit annotation and a registered handler produce routes

## Installation

```gradle.kts
// MockInterceptor is included transitively
implementation("com.github.appoly.AppolyDroid-Toolbox:MockInterceptor-Retrofit:1.3.3")
```

> **Note:** Retrofit is a `compileOnly` dependency — your project must already depend on Retrofit.

## Usage

### Basic Setup

Given a Retrofit API interface:

```kotlin
interface UsersApi {
    @GET("api/users")
    suspend fun getUsers(): List<User>

    @GET("api/users/{id}")
    suspend fun getUser(@Path("id") id: Int): User

    @POST("api/users")
    suspend fun createUser(@Body user: User): User

    @DELETE("api/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int)
}
```

Register mock handlers using `KFunction` references:

```kotlin
MockApiInterceptor(tag = "Mock") {
    defaultDelay(250L)

    mockApi(UsersApi::class) {
        mock(UsersApi::getUsers) {
            jsonBody("""[{"id": 1, "name": "Alice"}]""")
        }

        mock(UsersApi::getUser) { request ->
            val id = request.pathParamInt("id")
            jsonBody("""{"id": $id, "name": "User $id"}""")
        }

        mock(UsersApi::createUser) {
            status(201, "Created")
            jsonBody("""{"id": 3, "name": "New User"}""")
        }

        mock(UsersApi::deleteUser) {
            emptyBody()
        }
    }
}
```

### Combining with Other Extensions

Use with MockInterceptor-AppolyJson for Appoly envelope responses:

```kotlin
mockApi(UsersApi::class) {
    mock(UsersApi::getUsers) {
        successBody(listOf(User(1, "Alice"), User(2, "Bob")))
    }

    mock(UsersApi::getUser) { request ->
        val id = request.pathParamInt("id")
        successBody(User(id, "User $id"))
    }
}
```

### Selective Mocking

Only methods with a registered `mock()` handler produce routes. Unhandled methods pass through to the real network:

```kotlin
mockApi(UsersApi::class) {
    // Only getUsers is mocked; getUser, createUser, deleteUser hit the real API
    mock(UsersApi::getUsers) {
        jsonBody("...")
    }
}
```
