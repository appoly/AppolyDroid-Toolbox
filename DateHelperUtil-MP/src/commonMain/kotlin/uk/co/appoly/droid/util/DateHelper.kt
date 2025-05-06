package uk.co.appoly.droid.util

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.*
import kotlinx.datetime.toLocalDateTime

object DateHelper {
	/**
	 * format to LocalDateTime from "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"
	 */
	val serverDateFormatFull: DateTimeFormat<LocalDateTime>
		get() = LocalDateTime.Format {
			year()
			char('-')
			monthNumber()
			char('-')
			dayOfMonth()
			char('T')
			hour()
			char(':')
			minute()
			char(':')
			second()
			char('.')
			secondFraction(6)
			char('Z')
		}
	/**
	 * format to LocalDateTime from "yyyy-MM-dd HH:mm:ss"
	 */
	val serverDateFormatShort: DateTimeFormat<LocalDateTime>
		get() = LocalDateTime.Format {
			year()
			char('-')
			monthNumber()
			char('-')
			dayOfMonth()
			char(' ')
			hour()
			char(':')
			minute()
			char(':')
			second()
		}
	/**
	 * format to LocalDateTime from "yyyy-MM-dd"
	 */
	val serverDateFormatDate: DateTimeFormat<LocalDate>
		get() = LocalDate.Format {
			year()
			char('-')
			monthNumber()
			char('-')
			dayOfMonth()
		}
	/**
	 * format to LocalDateTime from "yyyy-MM-dd_HH-mm-ss.SSS"
	 */
	val fileDateFormat: DateTimeFormat<LocalDateTime>
		get() = LocalDateTime.Format {
			year()
			char('-')
			monthNumber()
			char('-')
			dayOfMonth()
			char('_')
			hour()
			char('-')
			minute()
			char('-')
			second()
			char('.')
			secondFraction(3)
		}

	/**
	 * parse to LocalDateTime from "2023-12-01T10:38:29.000000Z"
	 */
	fun parseLocalDateTime(date: String?): LocalDateTime? {
		return if(date.isNullOrBlank()) {
			null
		} else {
			serverDateFormatFull.parseOrNull(date)
				?: serverDateFormatShort.parseOrNull(date)
		}
	}

	fun formatLocalDateTime(dateTime: LocalDateTime?): String? {
		return if (dateTime == null) {
			null
		} else {
			serverDateFormatFull.format(dateTime)
		}
	}

	fun parseLocalDate(dateTime: String?): LocalDate? {
		return if (dateTime.isNullOrBlank()) {
			null
		} else {
			//attempt to pars from pattern SERVER_PATTERN_DATE
			serverDateFormatDate.parseOrNull(dateTime) ?: parseLocalDateTime(dateTime)?.date
		}
	}

	fun formatLocalDate(date: LocalDate?): String? {
		return if (date == null) {
			null
		} else {
			serverDateFormatDate.format(date)
		}
	}

	fun LocalDateTime?.toJsonString(): String? {
		return formatLocalDateTime(this)
	}

	fun String?.parseJsonDateTime(): LocalDateTime? {
		return parseLocalDateTime(this)
	}

	fun LocalDate?.toJsonString(): String? {
		return formatLocalDate(this)
	}

	fun String?.parseJsonDate(): LocalDate? {
		return parseLocalDate(this)
	}

	fun LocalDateTime.toFileString(): String {
		return fileDateFormat.format(this)
	}

	/**
	 * Gets the current moment as a LocalDateTime in UTC.
	 */
	fun nowAsUTC(): LocalDateTime {
		return Clock.System.now().toLocalDateTime(TimeZone.UTC)
	}
}