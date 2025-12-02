# BaseRepo-AppolyJson

An extension module for BaseRepo that provides support for Appoly's standard JSON response structure. This module implements the specific JSON parsing and response handling for APIs that follow
Appoly's JSON format.

## Features

- Implementation of Appoly's JSON response format
- Automatic parsing of standardized API responses
- Error handling for Appoly-specific response structures
- Seamless integration with the generic BaseRepo module

## Installation

```gradle.kts
// Requires the base BaseRepo module
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo:1.1.7")
implementation("com.github.appoly.AppolyDroid-Toolbox:BaseRepo-AppolyJson:1.1.7")
```

## API Response Structure

This module expects all API responses to follow Appoly's specific JSON structure. The API handling code requires all responses to use this structure as the root level of the JSON response, with the
API-specific data in the `data` field.

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
    {
      "id": 1,
      "name": "Item 1"
    },
    {
      "id": 2,
      "name": "Item 2"
    }
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
    "email": [
      "Email is required",
      "Email format is invalid"
    ],
    "password": [
      "Password must be at least 8 characters long"
    ]
  }
}
```

The standardized error handling automatically processes these response formats and converts them to the appropriate `APIResult` or `APIFlowState` types.

## Usage

### Basic Repository Setup

Create a repository class that extends `AppolyBaseRepo` (provided by this module):

```kotlin
abstract class AppolyRepo : AppolyBaseRepo(
	getRetrofitClient = { RetrofitClient },
	logger = Log, //Your Implementation of FlexiLogger
	loggingLevel = LoggingLevel.V// Set desired logging level
)
```

### Making API Calls

Use the standard `doAPICall` and `doAPICallWithBaseResponse` methods, which now handle the Appoly JSON format automatically.
