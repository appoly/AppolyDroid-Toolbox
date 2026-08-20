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
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.8.1")
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
| `ServerUnreachableException` | Online, but the request couldn't reach the server — DNS/connect failures, SSL handshake failures, connection resets, and other transport-level `IOException`s | `"Couldn't reach the server"` |
| `ServerTimeoutException` | Online, but the server didn't respond in time — read/connect timeouts (`SocketTimeoutException`) and OkHttp call-level timeouts (`OkHttpClient.Builder.callTimeout`) | `"Server took too long to respond"` |

Classification is delegated to Sandwich's `RetrofitExceptionClassifier`, which encodes transport
quirks (e.g. OkHttp reports a call-level timeout as a plain `InterruptedIOException`, not a
`SocketTimeoutException`). A response that arrived but failed to parse (malformed/truncated body)
is deliberately **not** a network error — the server was reached — so it surfaces as a generic
`APIResult.Error` with `isNetworkError() == false`.

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

### `APIResult.Error.responseCode` values

| Code | Meaning |
|------|---------|
| HTTP status (`4xx`/`5xx`, or `2xx` for an "HTTP 200 but `success: false`" body) | The real status code of the response |
| `RESPONSE_EXCEPTION_CODE` (`-1`) | The call failed with an exception (connectivity, timeout, parsing, …) — no HTTP response is available |
| `RESPONSE_NON_HTTP_ERROR_CODE` (`-2`) | The error carries no HTTP response. Produced when Sandwich's `ApiEnvelopeMapper` (registered globally by default since Sandwich 2.4.0) demotes an HTTP 200 business failure reported by a response model implementing `ApiEnvelope`; the envelope's error surfaces as `message` |

### Testing repositories built on BaseRepo

Since repositories expose `APIResult`, plain fakes are usually enough: have a fake API return
hand-built `ApiResponse` values and assert on the `APIResult`. For tests that assert on
`ApiResponse` itself, Sandwich ships a dedicated
[`sandwich-test`](https://skydoves.github.io/sandwich/testing/) module with fake factories
(`ApiResponse.fakeSuccess/fakeError/fakeException` — deterministic, bypassing global operators)
and an assertion DSL. To verify behaviour through the real Retrofit call-adapter pipeline
(including globally registered mappers), use OkHttp's `MockWebServer` — see
`GenericBaseRepoMockWebServerTest` in this module for a worked example.
