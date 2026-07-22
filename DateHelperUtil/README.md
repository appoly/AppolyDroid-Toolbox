# DateHelperUtil

A utility module for standardized date and time operations in Android applications.

## Features

- Easy date and time formatting
- Date parsing with error handling
- Time zone conversions
- Common date operations
- Localization support
- **Zone-safe server timestamps via `Instant` / `ZonedDateTime`** — UTC enforced at the type level, no more "did the caller remember to convert?"

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:1.6.3")
```

## 1.4.1 patch note

`parseServerInstant` and `parseServerZoneDateTime` (and therefore the
`ZonedDateTimeSerializer` / `NullableZonedDateTimeSerializer` they back) now fall back to
the short `yyyy-MM-dd HH:mm:ss` format, treating the digits as UTC. This restores the
pre-1.4 tolerance that 1.4.0 inadvertently lost — apps consuming Carbon/Laravel-style
backends were getting `DateTimeParseException` on every `created_at` / `updated_at` field
when bumping from 1.3.x straight to 1.4.0. 1.4.1 is the recommended baseline for the 1.4
line; 1.4.0 should be avoided against any backend that emits zone-naive server timestamps.

## Migrating from 1.3.x to 1.4

1.4.0 made timezone semantics explicit at the type level. The summary:

### What's new

- **`formatServerTimestamp(Instant)` / `formatServerTimestamp(ZonedDateTime)`** — recommended
  for any wire I/O. UTC enforced by the type system; the formatter is honest.
- **`parseServerInstant(text)`** — returns an `Instant` carrying UTC at the type level.
- **`parseServerZoneDateTime(text)`** — same, but returns a `ZonedDateTime` already in UTC.
- **`formatNaiveDateTime` / `parseNaiveDateTime`** — explicit naive helpers for genuinely
  zone-less values (date pickers, naive Room columns).
- **`SERVER_PATTERN_FULL_OFFSET`** — `yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX` (real ISO-8601 offset).
- **`NAIVE_PATTERN_FULL`** — `yyyy-MM-dd'T'HH:mm:ss.SSSSSS` (no zone marker).
- **`Instant`** Room TypeConverter and kotlinx `InstantSerializer` /
  `NullableInstantSerializer` (in the sibling modules) — see their READMEs.

### What's deprecated (with `WARNING` — code still compiles and runs unchanged)

- `formatLocalDateTime(LocalDateTime?)` — retains pre-1.4 byte-identical output (literal `Z`).
- `parseLocalDateTime(String?)` — retains pre-1.4 behaviour.
- `SERVER_PATTERN_FULL` constant — literal-`Z` pattern; replace direct usage with
  `SERVER_PATTERN_FULL_OFFSET`.

The deprecation messages don't carry a `ReplaceWith` quick-fix because the right
replacement depends on intent — see the migration steps below.

### ⚠ Wire format change to be aware of

The `LocalDateTime` serialization paths — `formatNaiveDateTime`, the
`LocalDateTime?.toJsonString()` extension, the `localDateTimeToJson` Room TypeConverter,
and the `DateTimeSerializer` / `NullableDateTimeSerializer` kotlinx serializers — now emit
`2025-05-29T10:38:29.000000` (no trailing `Z`) where they previously emitted
`2025-05-29T10:38:29.000000Z`.

Reads are **fully backward-compatible**: `parseNaiveDateTime` accepts the new no-Z format,
the legacy `...Z` format, any explicit ISO-8601 offset, and the short
`yyyy-MM-dd HH:mm:ss` format. Existing Room rows / cached JSON keep parsing correctly.
No Room migration required.

Laravel/Carbon and most other backends parse no-Z input transparently. If your backend
is stricter and requires `...Z`, migrate the field type to `Instant` —
`formatServerTimestamp(Instant)` produces byte-identical `...Z` output for UTC moments.

The recommended-path wire formats (`Instant`, `ZonedDateTime` via `formatServerTimestamp`)
are **byte-identical** to pre-1.4 for any UTC moment. No change for those.

### Step-by-step migration

1. Bump to `1.4.1`. Build the app.
2. Audit IDE warnings on `formatLocalDateTime`, `parseLocalDateTime`, and direct uses of
   `SERVER_PATTERN_FULL`. For each call site, decide:
   - **Server I/O** (the field represents a moment in time): migrate the field type to
     `Instant` and use `formatServerTimestamp(Instant)` / `parseServerInstant`. Wire bytes
     are unchanged for UTC values.
   - **Genuinely zone-naive value** (date picker, alarm time, display label): use
     `formatNaiveDateTime` / `parseNaiveDateTime`. Note the no-Z wire-format change.
3. If you have `LocalDateTime` Room columns: choose either to keep them as `LocalDateTime`
   (storage format becomes no-Z; reads handle both) or migrate the column to `Instant`.
4. If you have `LocalDateTime` fields in `@Serializable` data classes consumed by an
   external service: confirm the consumer accepts the no-Z format, or migrate the field
   to `Instant`.
5. Replace direct uses of `SERVER_PATTERN_FULL` with `SERVER_PATTERN_FULL_OFFSET` (or
   route through `formatServerTimestamp` / `parseServerInstant`).

If you want a no-op upgrade with zero behavioural change, leaving the deprecated
`formatLocalDateTime` / `parseLocalDateTime` calls in place is supported indefinitely —
they retain pre-1.4 byte-identical behaviour.

## Usage

### Server Timestamps (Recommended)

For any timestamp travelling to or from a server, use the `Instant`-based API. `Instant`
is UTC by definition, so the formatter is guaranteed to emit UTC digits regardless of the
device's timezone — and parsing returns an `Instant` that carries UTC at the type level,
so it can't be silently misinterpreted as device-local downstream.

```kotlin
import java.time.Instant

// Format an Instant for the wire (always UTC)
val payload = DateHelper.formatServerTimestamp(Instant.now())
// → e.g. "2025-05-29T10:38:29.000000Z"

// Parse a server response
val instant: Instant? = DateHelper.parseServerInstant("2025-05-29T10:38:29.000000Z")

// ZonedDateTime overload — collapses to an Instant before formatting
val payload2 = DateHelper.formatServerTimestamp(ZonedDateTime.now())
```

The wire format is `yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX` (microsecond precision plus the real
ISO-8601 offset designator). For UTC moments — which is what `formatServerTimestamp` and
`parseServerInstant` always produce on the wire — this renders as `2025-05-29T10:38:29.000000Z`,
byte-identical to the legacy literal-`Z` format. Existing servers (including Laravel/Carbon)
accept this transparently, and `parseServerInstant` now also accepts non-UTC offsets like
`+00:00` or `+01:00` for forward compatibility.

> [!IMPORTANT]
> **`SERVER_PATTERN_FULL` is deprecated.** Its trailing `'Z'` is a literal character, not the
> UTC offset designator — it does not enforce UTC and will format any `LocalDateTime` regardless
> of zone. The deprecated `formatLocalDateTime` / `parseLocalDateTime` helpers retain it for
> backward compat. For honest formatter access use `SERVER_PATTERN_FULL_OFFSET`. For full
> type-safe server I/O use `formatServerTimestamp(Instant)` / `parseServerInstant` — these
> route through `SERVER_PATTERN_FULL_OFFSET` internally.

### Naive Date Formatting

For genuinely zone-naive use cases (date pickers, display labels, naive Room columns)
where the digits do **not** represent UTC, use the explicitly-named naive helpers:

```kotlin
// Format a zone-naive LocalDateTime — output carries no zone marker.
// e.g. "2025-05-29T10:38:29.000000" (NAIVE_PATTERN_FULL — no 'Z')
val text = DateHelper.formatNaiveDateTime(localDateTime)

// Parse a string into a zone-naive LocalDateTime. Tolerates the new no-Z format,
// the legacy '...Z' format, any explicit ISO-8601 offset (e.g. '+01:00' — offset
// is dropped, returning the wall-clock at that offset), and 'yyyy-MM-dd HH:mm:ss'.
val ldt = DateHelper.parseNaiveDateTime("2025-05-29T10:38:29.000000")

// Format a date (zone-irrelevant — a date is just a date)
val dateString = DateHelper.formatLocalDate(localDate)

// Format using extensions (delegate to formatNaiveDateTime / parseNaiveDateTime)
val jsonString = localDateTime.toJsonString()
val fileString = localDateTime.toFileString()
```

> [!IMPORTANT]
> **Wire format change in 1.4.0.** The naive format helpers (and any path routing through
> them — `LocalDateTime.toJsonString()`, `DateTimeSerializer`, `localDateTimeToJson` Room
> converter) now emit `2025-05-29T10:38:29.000000` (no `Z`) where pre-1.4 they emitted
> `2025-05-29T10:38:29.000000Z`. The honest no-zone format reflects what `LocalDateTime`
> actually is — a value with no zone information.
>
> **Reading legacy data is fully backward-compatible**: `parseNaiveDateTime` and the
> `parseJsonDateTime` extension accept both the new no-Z format and the legacy `...Z` format
> (plus arbitrary offsets and the short format). Existing Room rows / cached JSON keep
> reading correctly.
>
> **Servers (Laravel/Carbon) parse both** — ISO-8601 with or without zone marker is valid
> and Laravel infers UTC when no offset is present. If your server is stricter, route the
> field through `Instant` + `formatServerTimestamp` instead, which still emits `...Z` for
> UTC moments byte-identically to the legacy format.

> [!NOTE]
> `formatLocalDateTime` / `parseLocalDateTime` are deprecated with `WARNING` level but
> retain their **pre-1.4 byte-identical behaviour** — `formatLocalDateTime` still emits
> `...Z`, `parseLocalDateTime` still expects it. They no longer carry a `ReplaceWith`
> hint because the right replacement depends on intent: server I/O wants
> `formatServerTimestamp(Instant)`; genuinely-naive use wants `formatNaiveDateTime` (and
> the wire-format change above is part of that migration).

### Parsing Date Strings

```kotlin
// Parse standard date format
val localDate = DateHelper.parseLocalDate("2025-05-29")

// Parse using extensions
val localDateTime = "2025-05-29T10:38:29.000000Z".parseJsonDateTime()
val localDate = "2025-05-29".parseJsonDate()
```

### Time Zone Handling

```kotlin
// Get current time in UTC
val nowUtc = DateHelper.nowAsUTC()

// Convert to UTC
val utcDateTime = zonedDateTime.toUTC()

// Convert local datetime to UTC
val utcDateTime = localDateTime.deviceToUTC()

// Convert to device time zone
val deviceTimeZone = zonedDateTime.toDeviceZone()
```

### Date Calculations and Checks

```kotlin
// Check if date is in the future
val isFuture = localDateTime.isFuture()
val isFuture = zonedDateTime.isFuture()

// Check if date is in the past
val isPassed = localDateTime.isPassed()
val isPassed = zonedDateTime.isPassed()

// Convert to/from milliseconds
val millis = localDateTime.toMillis()
val localDateTime = millis.millisToLocalDateTime()
val localDate = millis.millisToLocalDate()
```

## API Reference

### Core Methods in DateHelper

```kotlin
// Configuration
fun setLogger(logger: FlexiLog, loggingLevel: LoggingLevel = LoggingLevel.NONE)

// Server timestamps (recommended for any wire I/O — outputs '...Z' for UTC moments)
fun formatServerTimestamp(instant: Instant?): String?
fun formatServerTimestamp(zoned: ZonedDateTime?): String?
fun parseServerInstant(text: String?): Instant?

// Zone-naive parsing / formatting (use for genuinely naive values — outputs no zone marker)
fun formatNaiveDateTime(dateTime: LocalDateTime?): String?
fun parseNaiveDateTime(dateTime: String?): LocalDateTime?
fun parseLocalDate(dateTime: String?): LocalDate?
fun formatLocalDate(date: LocalDate?): String?

// Deprecated — pre-1.4 byte-identical behaviour preserved (literal-Z output / parse).
// Migrate to formatServerTimestamp / formatNaiveDateTime as appropriate.
@Deprecated(...) fun parseLocalDateTime(dateTime: String?): LocalDateTime?
@Deprecated(...) fun formatLocalDateTime(dateTime: LocalDateTime?): String?

// Utilities
fun nowAsUTC(): ZonedDateTime
```

### Extension Methods

```kotlin
// Formatting extensions
fun LocalDateTime?.toJsonString(): String?
fun LocalDate?.toJsonString(): String?
fun LocalDateTime.toFileString(): String

// Parsing extensions
fun String?.parseJsonDateTime(): LocalDateTime?
fun String?.parseJsonDate(): LocalDate?

// Time zone handling
fun ZonedDateTime.toUTC(): ZonedDateTime
fun ZonedDateTime.toDeviceZone(): ZonedDateTime
fun LocalDateTime.deviceToUTC(): LocalDateTime

// Status checks
fun LocalDateTime?.isFuture(): Boolean
fun ZonedDateTime?.isFuture(): Boolean
fun LocalDateTime?.isPassed(): Boolean
fun ZonedDateTime?.isPassed(): Boolean

// Time conversions
fun LocalDateTime?.toMillis(zoneOffset: ZoneOffset? = ZoneOffset.UTC): Long?
fun LocalDate?.toMillis(zoneOffset: ZoneOffset? = ZoneOffset.UTC): Long?
fun Long.millisToLocalDateTime(zoneOffset: ZoneOffset? = ZoneOffset.UTC): LocalDateTime
fun Long.millisToLocalDate(zoneOffset: ZoneOffset? = ZoneOffset.UTC): LocalDate
```

### Constants

```kotlin
// Real-offset pattern (recommended for server I/O). Renders Z for UTC and +01:00
// etc. otherwise. Refuses to format a bare LocalDateTime — the formatter polices
// its own input. Used internally by formatServerTimestamp / parseServerInstant.
const val SERVER_PATTERN_FULL_OFFSET = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"

// Honest naive pattern (recommended for zone-naive values). No zone marker.
// Used internally by formatNaiveDateTime / parseNaiveDateTime.
const val NAIVE_PATTERN_FULL = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"

// Deprecated. Literal-Z pattern — does NOT enforce UTC, will format any LocalDateTime.
// Retained only by the deprecated formatLocalDateTime / parseLocalDateTime entry
// points for byte-identical pre-1.4 backward compat.
@Deprecated(...) const val SERVER_PATTERN_FULL = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"

const val SERVER_PATTERN_SHORT = "yyyy-MM-dd HH:mm:ss"
const val SERVER_PATTERN_DATE = "yyyy-MM-dd"
```

## Dependencies

- Java 8 Time API
- [FlexiLogger](https://github.com/projectdelta6/FlexiLogger) for logging capabilities
- Android Core KTX (for extension functions)

## See Also

- [DateHelperUtil-Room](../DateHelperUtil-Room/README.md) - Room database integration
- [DateHelperUtil-Serialization](../DateHelperUtil-Serialization/README.md) - Kotlinx Serialization support
