# DateHelperUtil-Serialization

Extension module for DateHelperUtil that provides kotlinx.serialization integration for Java 8 date and time types.

## Features

- Serializers for `Instant`, `LocalDate`, `LocalDateTime`, and `ZonedDateTime`
- **Zone-safe `Instant` serialization** — UTC enforced at the type level, no caller-side conversion required
- Works for both nullable and non-nullable properties — one serializer per type
- Standardized date/time formatting using ISO-8601 formats
- Timezone preservation for `ZonedDateTime` values
- Automatic UTC conversion for consistent serialization
- Full compatibility with kotlinx.serialization

## Installation

```gradle.kts
// Requires base DateHelperUtil module
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:1.8.1")
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:1.8.1")

// Required kotlinx.serialization dependencies
implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
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

Use the provided typealiases to simplify declarations without annotations:

```kotlin
import kotlinx.serialization.Serializable
import uk.co.appoly.droid.util.SerializableDateTime
import uk.co.appoly.droid.util.SerializableInstant
import uk.co.appoly.droid.util.SerializableLocalDate
import uk.co.appoly.droid.util.SerializableZonedDateTime

@Serializable
data class Event(
    val id: Int,
    val title: String,

    // Non-nullable LocalDate
    val eventDate: SerializableLocalDate,

    // Nullable LocalDateTime — just add `?`, no separate typealias needed
    val startTime: SerializableDateTime?,

    // ZonedDateTime (with timezone preservation)
    val createdAt: SerializableZonedDateTime,

    // Instant — recommended for any "moment in time" field. Always UTC.
    val timestamp: SerializableInstant
)
```

> [!TIP]
> For new fields representing a moment in time, prefer `SerializableInstant` over the
> `LocalDateTime` variants. `Instant` carries UTC at the type level so the serializer cannot
> accidentally emit device-local digits.

> [!IMPORTANT]
> **There is one serializer per type — use it for nullable properties too.** Write
> `SerializableInstant?` (or `@Serializable(with = InstantSerializer::class) val x: Instant?`)
> and kotlinx wraps the serializer to handle the null itself. The `Nullable*` family is
> deprecated as of 1.8.1; see [Deprecated: the `Nullable*` family](#deprecated-the-nullable-family).

> **Note:** These typealiases are equivalent to their underlying types (e.g., `SerializableLocalDate` is just `LocalDate` with built-in serialization). You can assign and use them interchangeably with standard `LocalDate`, `LocalDateTime`, or `ZonedDateTime` values without any conversion.

Alternatively, add serializer annotations to date properties:

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

    // Nullable LocalDateTime — the same serializer; kotlinx wraps it for the null
    @Serializable(with = DateTimeSerializer::class)
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

Each serializer covers both the non-nullable and the nullable form of its type — append `?`
to the typealias, or annotate the nullable property with the same serializer.

| Serializer | TypeAlias | Type | Description |
|------------|-----------|------|-------------|
| `LocalDateSerializer` | `SerializableLocalDate` | `LocalDate` / `LocalDate?` | Date |
| `DateTimeSerializer` | `SerializableDateTime` | `LocalDateTime` / `LocalDateTime?` | Date-time (zone-naive) |
| `ZonedDateTimeSerializer` | `SerializableZonedDateTime` | `ZonedDateTime` / `ZonedDateTime?` | Date-time with timezone |
| `InstantSerializer` | `SerializableInstant` | `Instant` / `Instant?` | **Recommended** — UTC moment in time |

### Deprecated: the `Nullable*` family

`NullableLocalDateSerializer`, `NullableDateTimeSerializer`, `NullableZonedDateTimeSerializer`,
`NullableInstantSerializer` and their `NullableSerializable*` typealiases are **deprecated as of
1.8.1** and will be removed in a future release.

They were always redundant: because they did not declare a nullable descriptor, kotlinx wrapped
them in its own `NullableSerializer` on the property path exactly as it wraps the non-nullable
serializers — so `@Serializable(with = InstantSerializer::class) val x: Instant?` and
`@Serializable(with = NullableInstantSerializer::class) val x: Instant?` produced byte-identical
JSON. Their own null branches were unreachable there, and **incorrect** off that path: used
directly (`Json.encodeToString(NullableInstantSerializer, null)`, or inside a `ListSerializer`)
`serialize` emitted nothing at all — yielding malformed JSON such as `["2026-06-05",,"2026-01-02"]`
— and `deserialize` threw on a `null` token. See
[issue #106](https://github.com/appoly/AppolyDroid-Toolbox/issues/106).

Migration is a one-line swap per field, with no wire-format change:

```kotlin
// Before
val deletedAt: NullableSerializableInstant
@Serializable(with = NullableInstantSerializer::class) val reminderAt: ZonedDateTime?

// After
val deletedAt: SerializableInstant?
@Serializable(with = ZonedDateTimeSerializer::class) val reminderAt: ZonedDateTime?
```

Until they are removed, the deprecated serializers now declare nullable descriptors and handle
`null` correctly, so direct use no longer corrupts output.

## Serialization Format

The serializers use the standard date formats defined in DateHelper:

| Java Type | JSON Format | Example |
|-----------|-------------|---------|
| LocalDate | ISO-8601 date | `"2025-06-15"` |
| LocalDateTime | ISO-8601 datetime (naive, no zone marker) | `"2025-06-15T09:00:00.000000"` |
| ZonedDateTime | ISO-8601 datetime (UTC) | `"2025-06-15T13:00:00.000000Z"` |
| Instant | ISO-8601 datetime (UTC) | `"2025-06-15T13:00:00.000000Z"` |

> [!IMPORTANT]
> **Wire format change in 1.4.0** for `LocalDateTime` serialization: emitted JSON no longer
> carries a trailing `Z`. The honest no-zone format reflects that `LocalDateTime` carries
> no zone information.
>
> Reading legacy data is fully backward-compatible: `DateTimeSerializer` accepts both the new
> no-Z format and the legacy `...Z` format (plus any explicit ISO-8601 offset and the short
> `yyyy-MM-dd HH:mm:ss` format).
>
> If you need byte-identical `...Z` output for backend compat, migrate the field type to
> `Instant` and use `InstantSerializer` — the wire bytes for UTC moments are unchanged.

Note: ZonedDateTime values are always converted to UTC before serialization for consistent storage and transmission.

## Timezone Handling

For ZonedDateTime values:

1. When serializing: The ZonedDateTime is converted to UTC timezone
2. When deserializing: The UTC time is parsed and then converted to the device's local timezone

This approach ensures consistent serialization while preserving timezone information.

For Instant values:

1. When serializing: Formatted via `DateHelper.formatServerTimestamp(Instant)`, which pins the
   formatter to UTC. The emitted digits are always UTC wall-clock regardless of device zone.
2. When deserializing: Parsed via `DateHelper.parseServerInstant`, which explicitly attaches
   `ZoneOffset.UTC`. The returned Instant carries UTC at the type level — it cannot be
   silently misinterpreted as device-local downstream.

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
