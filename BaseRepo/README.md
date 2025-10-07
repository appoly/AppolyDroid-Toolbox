# BaseRepo

Foundation module for implementing the repository pattern with standardized API call handling.

## Features

- Standardized API call handling with error management
- Support for coroutines and Flow
- Integration with [FlexiLogger](https://github.com/projectdelta6/FlexiLogger)
- Built-in retry mechanisms
- Refreshable data flows

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.0.34")
```

## API Response Structure

The BaseRepo module expects a specific JSON structure for all API responses. The API handling code requires all responses to use this structure as the root level of the JSON response, with the API-specific data in the `data` field of the `GenericResponse`.

### Response Format

All API responses should follow one of these formats.

**Note:** The `message` field is optional in all responses, and is only checked in cases where `success` is `false`.

#### Basic Response

```json
{
  "success": true,
  "message": "Operation completed successfully"
}
```

#### Success Response with Data Payload

```json
{
  "success": true,
  "data": {
    "id": 123,
    "name": "John Doe",
    "email": "john.doe@example.com"
  }
}
```

#### Success Response with Array Data

```json
{
  "success": true,
  "data": [
    { "id": 1, "name": "Item 1" },
    { "id": 2, "name": "Item 2" }
  ]
}
```

#### Error Response

```json
{
  "success": false,
  "message": "Resource not found"
}
```

#### Validation Error Response

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "email": ["Email is required", "Email format is invalid"],
    "password": ["Password must be at least 8 characters long"]
  }
}
```

The standardized error handling in BaseRepo automatically processes these response formats and converts them to the appropriate `APIResult` or `APIFlowState` types.

## Usage

### Basic Repository Setup

Create a base repository class that extends `AppolyBaseRepo`:

```kotlin
abstract class BaseRepo : AppolyBaseRepo(
    getRetrofitClient = { RetrofitClient },
    logger = Log, //Your Implementation of FlexiLogger
    loggingLevel = LoggingLevel.V// Set desired logging level
)
```

### Making API Calls

Define your API service interface using Sandwich for response handling:

```kotlin
interface UserAPI : BaseService.API {
    @GET("/api/user")
    suspend fun fetchUser(): ApiResponse<GenericResponse<UserData>>
}
```

Use the `doAPICall` method for standardized API request handling:

```kotlin
abstract class UserRepo: BaseRepo() {
    private val userService by lazyService<UserAPI>()

    suspend fun fetchUser(userId: Int): APIResult<UserData> = doAPICall("fetchUser") {
        userService.api.getUser(userId)
    }
}
```

### Using with Flow

Convert API calls to Flow:

```kotlin
fun getUserFlow(userId: Int): Flow<APIFlowState<UserData>> = flow {
    emit(APIFlowState.Loading)
    emit(fetchUser(userId).asApiFlowState())
}
```

or

```kotlin
fun getUserFlow(userId: Int): Flow<APIFlowState<UserData>> = callApiAsFlow {
    fetchUser(userId).asApiFlowState()
}
```

### Creating Refreshable API Flows

Create a refreshable data flow that can be manually refreshed:

```kotlin
val userDataRefreshFlow = RefreshableAPIFlow(
    apiCall = { fetchUser(userId) },
    scope = viewModelScope
)

val userDataState: StateFlow<APIFlowState<UserData>> = userDataRefreshFlow.stateIn(viewModelScope)

// To refresh:
userDataRefreshFlow.refresh()
```

### Caching Success Data

Maintain stable data during refresh operations to prevent UI flicker:

```kotlin
// Cache the entire user object, preserving data during loading states
val cachedUserFlow = userDataRefreshFlow.cacheSuccessData(null)

// Cache only specific fields with transformation
val userNameFlow = userDataRefreshFlow.cacheSuccessData("Unknown User") { user -> user.name }

// Use in Compose
@Composable
fun UserScreen() {
    val cachedUser by cachedUserFlow.collectAsState()
    val userName by userNameFlow.collectAsState()

    // cachedUser retains the last successful value even during refresh
    // userName shows "Unknown User" initially, then the actual name
}
```

### Processing API Results

Process API results with extension functions:

```kotlin
fetchUser(userId).onSuccess { user ->
    // Handle success
    println("User: ${user.name}")
}.onError { error ->
    // Handle error
    println("Error: ${error.message}")
}
```

Or using when expression:

```kotlin
when (val result = fetchUser(userId)) {
    is APIResult.Success -> {
        val user = result.data
        // Use user data
    }
    is APIResult.Error -> {
        val errorMessage = result.message
        // Handle error
    }
}
```

### Using the Result Extensions

```kotlin
// Get success data or null
val userData = fetchUser(userId).successOrNull()

// Transform success data
val userName = fetchUser(userId).mapSuccess { it.name }

// Check state
val isLoading = userDataState.isLoading()
val hasError = userDataState.isError()
val errorMsg = userDataState.errorMessage()
```

## API Reference

### Core Classes

#### `AppolyBaseRepo`
Base class for repositories with API handling capabilities.

#### `APIResult<T>`
Sealed class representing an API result:

- `APIResult.Success<T>`: Contains successful data
- `APIResult.Error`: Contains error information

#### `APIFlowState<T>`
Sealed class representing a Flow API state:

- `APIFlowState.Loading`: Loading state
- `APIFlowState.Success<T>`: Success state with data
- `APIFlowState.Error`: Error state with message

#### `RefreshableAPIFlow<T>`
A Flow wrapper that can be refreshed on demand.

### Key Methods

#### `doAPICall`

```kotlin
suspend fun <T> doAPICall(tag: String, apiCall: suspend () -> ApiResponse<GenericResponse<T>>): APIResult<T>
```

#### `doAPICallWithBaseResponse`

```kotlin
suspend fun doAPICallWithBaseResponse(tag: String, apiCall: suspend () -> ApiResponse<BaseResponse>): APIResult<BaseResponse>
```

## Dependencies

- [FlexiLogger](https://github.com/projectdelta6/FlexiLogger) - For logging
- OkHttp for HTTP communication
- [Retrofit](https://square.github.io/retrofit/) - For network requests
- [Sandwich](https://github.com/skydoves/sandwich) - For API response handling
- Kotlin Coroutines and Flow

## See Also

- [BaseRepo-Paging](../BaseRepo-Paging/README.md) - Adds Jetpack Paging support
- [BaseRepo-S3Uploader](../BaseRepo-S3Uploader/README.md) - Adds S3 upload capabilities

