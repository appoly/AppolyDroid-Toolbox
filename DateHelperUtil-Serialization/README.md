# DateHelperUtil-Serialization

Extension module for DateHelperUtil that provides kotlinx.serialization integration for Java 8 date and time types.

## Features

- Serializers for LocalDate, LocalDateTime, and ZonedDateTime
- Support for both nullable and non-nullable date-time values
- Standardized date/time formatting using ISO-8601 formats
- Timezone preservation for ZonedDateTime values
- Automatic UTC conversion for consistent serialization
- Full compatibility with kotlinx.serialization

## Installation

```gradle.kts
// Requires base DateHelperUtil module
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:1.0.33")
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:1.0.33")

// Required kotlinx.serialization dependencies
implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
```

## Usage

### 1. Enable Kotlin Serialization Plugin

In your module's build.gradle.kts file:

```kotlin
plugins {
    id("kotlin-android")
    id("kotlinx-serialization")
}
```

### 2. Use Serializers in Data Classes

Add serializer annotations to date properties:

```kotlin
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

@Serializable
data class Event(
    val id: Int,
    val title: String,

    // Non-nullable LocalDate
    @Serializable(with = LocalDateSerializer::class)
    val eventDate: LocalDate,

    // Nullable LocalDateTime
    @Serializable(with = NullableDateTimeSerializer::class)
    val startTime: LocalDateTime?,

    // ZonedDateTime (with timezone preservation)
    @Serializable(with = ZonedDateTimeSerializer::class)
    val createdAt: ZonedDateTime
)
```

### 3. Serializing to JSON

```kotlin
import kotlinx.serialization.json.Json

val jsonFormat = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

val event = Event(
    id = 1,
    title = "Conference",
    eventDate = LocalDate.of(2025, 6, 15),
    startTime = LocalDateTime.of(2025, 6, 15, 9, 0),
    createdAt = ZonedDateTime.now()
)

// Serialize to JSON string
val jsonString = jsonFormat.encodeToString(Event.serializer(), event)

// Deserialize from JSON string
val parsedEvent = jsonFormat.decodeFromString(Event.serializer(), jsonString)
```

## Available Serializers

| Serializer | Type | Description |
|------------|------|-------------|
| `LocalDateSerializer` | `LocalDate` | Non-nullable date |
| `NullableLocalDateSerializer` | `LocalDate?` | Nullable date |
| `DateTimeSerializer` | `LocalDateTime` | Non-nullable date-time |
| `NullableDateTimeSerializer` | `LocalDateTime?` | Nullable date-time |
| `ZonedDateTimeSerializer` | `ZonedDateTime` | Non-nullable date-time with timezone |
| `NullableZonedDateTimeSerializer` | `ZonedDateTime?` | Nullable date-time with timezone |

## Serialization Format

The serializers use the standard date formats defined in DateHelper:

| Java Type | JSON Format | Example |
|-----------|-------------|---------|
| LocalDate | ISO-8601 date | `"2025-06-15"` |
| LocalDateTime | ISO-8601 datetime | `"2025-06-15T09:00:00.000000Z"` |
| ZonedDateTime | ISO-8601 datetime (UTC) | `"2025-06-15T13:00:00.000000Z"` |

Note: ZonedDateTime values are always converted to UTC before serialization for consistent storage and transmission.

## Timezone Handling

For ZonedDateTime values:

1. When serializing: The ZonedDateTime is converted to UTC timezone
2. When deserializing: The UTC time is parsed and then converted to the device's local timezone

This approach ensures consistent serialization while preserving timezone information.

## Example: Custom JSON Configuration

For more advanced use cases, you may want to configure the JSON serialization:

```kotlin
val json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

// Create events list
val events = listOf(
    Event(1, "Meeting", LocalDate.now(), LocalDateTime.now(), ZonedDateTime.now()),
    Event(2, "Conference", LocalDate.now().plusDays(7), null, ZonedDateTime.now())
)

// Serialize list to JSON
val jsonString = json.encodeToString(ListSerializer(Event.serializer()), events)

// Deserialize from JSON
val parsedEvents = json.decodeFromString(ListSerializer(Event.serializer()), jsonString)
```

## Dependencies

- [DateHelperUtil](../DateHelperUtil/README.md) - Base date/time utility module
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) - Kotlin serialization library

