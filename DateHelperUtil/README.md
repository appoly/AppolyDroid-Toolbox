# DateHelperUtil

A utility module for standardized date and time operations in Android applications.

## Features

- Easy date and time formatting
- Date parsing with error handling
- Time zone conversions
- Common date operations
- Localization support

## Installation

```gradle.kts
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:1.0.33")
```

## Usage

### Basic Date Formatting

```kotlin
// Format local date time
val dateString = DateHelper.formatLocalDateTime(localDateTime)

// Format local date
val dateString = DateHelper.formatLocalDate(localDate)

// Format using extensions
val jsonString = localDateTime.toJsonString()
val fileString = localDateTime.toFileString()
```

### Parsing Date Strings

```kotlin
// Parse standard datetime format
val localDateTime = DateHelper.parseLocalDateTime("2025-05-29T10:38:29.000000Z")

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

// Parsing
fun parseLocalDateTime(dateTime: String?): LocalDateTime?
fun parseLocalDate(dateTime: String?): LocalDate?

// Formatting
fun formatLocalDateTime(dateTime: LocalDateTime?): String?
fun formatLocalDate(date: LocalDate?): String?

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
const val SERVER_PATTERN_FULL = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"
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

