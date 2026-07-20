# BaseRepo

Foundation module for implementing the repository pattern with standardized API call handling. This module provides a generic framework that can be extended to support different JSON response formats.

## Features

- Standardized API call handling with error management
- Support for coroutines and Flow
- Integration with [FlexiLogger](https://github.com/projectdelta6/FlexiLogger)
- Built-in retry mechanisms
- Refreshable data flows
- Extensible design for custom JSON formats

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.6.2")
```

## Extensions

For specific JSON response formats, use the following extension modules:

- **BaseRepo-AppolyJson**: Provides support for Appoly's standard JSON response structure.

## Usage

### Basic Repository Setup

Create a base repository class that extends `GenericBaseRepo`:

```kotlin
abstract class BaseRepo : GenericBaseRepo(
    getRetrofitClient = { RetrofitClient },
    logger = Log, //Your Implementation of FlexiLogger
    loggingLevel = LoggingLevel.V// Set desired logging level
)
```

### Making API Calls

The base module provides generic methods for API calls. For specific JSON handling, refer to the extension modules.

### Network error handling

Failed calls are surfaced as `APIResult.Error`. Connectivity-related failures are mapped to a small
exception hierarchy so you can distinguish "the device is offline" from "the device is online but we
couldn't reach the server":

| Exception | When | `APIResult.Error.message` |
|-----------|------|---------------------------|
| `NoConnectivityException` | Device is genuinely offline (thrown pre-flight by `NetworkConnectionInterceptor`, `cause == null`) | `"No Internet Connection"` |
| `ServerUnreachableException` | Online, but the host couldn't be resolved/reached (`UnknownHostException`, `ConnectException`, `SocketException`) | `"Couldn't reach the server"` |
| `ServerTimeoutException` | Online, but the server didn't respond in time (`SocketTimeoutException`) | `"Server took too long to respond"` |

The hierarchy is `ServerTimeoutException` → `ServerUnreachableException` → `NoConnectivityException`,
so `isNetworkError()` (which checks `is NoConnectivityException`) returns `true` for all three. Use
`isServerUnreachable()` when you specifically want the "online but unreachable" case:

```kotlin
when (val result = repo.fetchItem(id)) {
    is APIResult.Success -> show(result.data)
    is APIResult.Error -> when {
        result.isServerUnreachable() -> showRetry("Couldn't reach the server")
        result.isNetworkError()      -> showOffline("You appear to be offline")
        else                         -> showError(result.message)
    }
}
```

Library code can't use Android string resources, so the default messages live on the exception types —
map the exception type (or `isServerUnreachable()` / `isNetworkError()`) to your own localized copy in
the consuming app rather than matching on the message strings.
