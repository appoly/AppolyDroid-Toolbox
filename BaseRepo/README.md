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
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.2.0-beta02")
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
