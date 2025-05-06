package uk.co.appoly.droid.util

import kotlinx.datetime.*
import kotlin.time.Duration
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Converts a LocalDateTime from its current TimeZone to UTC.
 * Assumes the receiver LocalDateTime is in the system's default TimeZone if not otherwise specified.
 */
fun LocalDateTime.toUTC(sourceTimeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime {
	return this.toInstant(sourceTimeZone).toLocalDateTime(TimeZone.UTC)
}

/**
 * Converts a LocalDateTime from UTC to the system's default TimeZone.
 * Assumes the receiver LocalDateTime is in UTC.
 */
fun LocalDateTime.toDeviceZone(sourceTimeZone: TimeZone = TimeZone.UTC): LocalDateTime {
	return this.toInstant(sourceTimeZone).toLocalDateTime(TimeZone.currentSystemDefault())
}


@OptIn(ExperimentalContracts::class)
fun LocalDateTime?.isFuture(clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
	contract { returns(true) implies (this@isFuture != null) }
	return this?.let { it > clock.now().toLocalDateTime(timeZone) } == true
}

@OptIn(ExperimentalContracts::class)
fun LocalDateTime?.isPast(clock: Clock = Clock.System, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
	contract { returns(true) implies (this@isPast != null) }
	return this?.let { it < clock.now().toLocalDateTime(timeZone) } == true
}

/**
 * Truncates this duration to the specified unit.
 *
 * @throws IllegalArgumentException if the unit is zero or negative.
 *
 * @param unit the unit to truncate to.
 * @return the truncated duration.
 */
fun Duration.truncatedTo(unit: Duration): Duration {
	if (unit == Duration.ZERO) throw IllegalArgumentException("unit cannot be zero")
	if (unit.isNegative()) throw IllegalArgumentException("unit cannot be negative")
	val ratio = this.inWholeMilliseconds / unit.inWholeMilliseconds
	return unit * ratio.toDouble()
}

fun LocalDateTime?.toEpochMillis(timeZone: TimeZone = TimeZone.UTC): Long? {
	return this?.toInstant(timeZone)?.toEpochMilliseconds()
}

fun LocalDate?.toEpochMillisAtStartOfDay(timeZone: TimeZone = TimeZone.UTC): Long? {
	return this?.atStartOfDayIn(timeZone)?.toEpochMilliseconds()
}

fun Long.millisToLocalDateTime(timeZone: TimeZone = TimeZone.UTC): LocalDateTime {
	return Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone)
}

fun Long.millisToLocalDate(timeZone: TimeZone = TimeZone.UTC): LocalDate {
	return Instant.fromEpochMilliseconds(this).toLocalDateTime(timeZone).date
}