# DateHelperUtil-Serialization

Extension module for DateHelperUtil that provides kotlinx.serialization integration for Java 8 date and time types.

## Features

- Serializers for `Instant`, `LocalDate`, `LocalDateTime`, and `ZonedDateTime`
- **Zone-safe `Instant` serialization** — UTC enforced at the type level, no caller-side conversion required
- Strict and lenient serializer per type — fail loudly, or degrade bad values to `null`
- Standardized date/time formatting using ISO-8601 formats
- Timezone preservation for `ZonedDateTime` values
- Automatic UTC conversion for consistent serialization
- Full compatibility with kotlinx.serialization

## Installation

```gradle.kts
// Requires base DateHelperUtil module
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:1.8.3")
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Serialization:1.8.3")

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
import uk.co.appoly.droid.util.NullableSerializableDateTime
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

    // Nullable LocalDateTime, strict — a `null` is fine, a malformed value throws
    val startTime: SerializableDateTime?,

    // Nullable LocalDateTime, lenient — a malformed value becomes `null` instead of throwing
    val endTime: NullableSerializableDateTime,

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
> **Pick a serializer by how it should handle bad data, not by nullability.** Both families
> accept a `null` on the wire for a nullable property. They differ on a value that cannot be
> parsed: `XSerializer` **throws**, failing the whole decode, while `NullableXSerializer`
> **degrades it to `null`**. See [Strict vs lenient](#strict-vs-lenient).

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

    // Nullable LocalDateTime, strict — kotlinx wraps it to accept the `null` token
    @Serializable(with = DateTimeSerializer::class)
    val startTime: LocalDateTime?,

    // Nullable LocalDateTime, lenient — also degrades an unparseable value to `null`
    @Serializable(with = NullableDateTimeSerializer::class)
    val endTime: LocalDateTime?,

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

| Type | Strict | Lenient |
|------|--------|---------|
| `LocalDate` | `LocalDateSerializer` / `SerializableLocalDate` | `NullableLocalDateSerializer` / `NullableSerializableLocalDate` |
| `LocalDateTime` | `DateTimeSerializer` / `SerializableDateTime` | `NullableDateTimeSerializer` / `NullableSerializableDateTime` |
| `ZonedDateTime` | `ZonedDateTimeSerializer` / `SerializableZonedDateTime` | `NullableZonedDateTimeSerializer` / `NullableSerializableZonedDateTime` |
| `Instant` **(recommended type)** | `InstantSerializer` / `SerializableInstant` | `NullableInstantSerializer` / `NullableSerializableInstant` |

The strict serializers work on nullable properties too — write `SerializableInstant?` and kotlinx
wraps them to handle the `null` token. That gives you "null is allowed, but malformed is an error".

### Strict vs lenient

Both families emit identical JSON and both accept a literal `null` on the wire. They diverge on
**input that cannot be parsed**:

| Wire value | `XSerializer` (strict) | `NullableXSerializer` (lenient) |
|---|---|---|
| `"2026-06-05"` | the value | the value |
| `null` | `null` (nullable property) / throws (non-null property) | `null` |
| `"garbage"` | **throws `SerializationException`** | **`null`** |

That last row is the whole reason both exist. Given the wire value
`{"z":"2026-06-05T10:38:29.000000Z","nullable":"garbage"}`:

```kotlin
// lenient — one bad field becomes null, the rest of the response survives
@Serializable(with = NullableZonedDateTimeSerializer::class) val nullable: ZonedDateTime?  // -> null

// strict — one bad field fails the entire decode
@Serializable(with = ZonedDateTimeSerializer::class) val nullable: ZonedDateTime?          // -> throws
```

**Rule of thumb: strict on data you produce, lenient on data you parse.**

You write your own values moments before encoding them — local persistence, nav-key state, your
own round-trips — so a malformed value there is a bug in your code and should fail loudly. A
response model is parsing someone else's output, which you do not control and cannot fix at
read time, so one malformed timestamp should null one field rather than cost you the whole
payload.

> [!NOTE]
> **`1.8.1` deprecated the `Nullable*` family as "redundant". That was wrong, and `1.8.2` reverses
> it.** The deprecation was based on the two families being interchangeable, which holds for `null`
> tokens and valid values but *not* for unparseable input — exactly the row above. If you migrated
> away from the lenient serializers on `1.8.1`, review those fields: on response models you very
> likely want the lenient behaviour back. Apologies for the churn. See
> [issue #106](https://github.com/appoly/AppolyDroid-Toolbox/issues/106).

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
> Reading legacy data is fully backward-compatible: `DateTimeSerializer` and
> `NullableDateTimeSerializer` accept both the new no-Z format and the legacy `...Z` format
> (plus any explicit ISO-8601 offset and the short `yyyy-MM-dd HH:mm:ss` format).
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
