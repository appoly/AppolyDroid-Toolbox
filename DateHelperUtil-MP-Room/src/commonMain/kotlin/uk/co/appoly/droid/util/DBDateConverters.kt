package uk.co.appoly.droid.util

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import uk.co.appoly.droid.util.DateHelper.parseJsonDate
import uk.co.appoly.droid.util.DateHelper.parseJsonDateTime
import uk.co.appoly.droid.util.DateHelper.toJsonString

class DBDateConverters {
	@TypeConverter
	fun localDateTimeToJson(date: LocalDateTime?): String? = date.toJsonString()

	@TypeConverter
	fun jsonToLocalDateTime(json: String?): LocalDateTime? = json.parseJsonDateTime()

	@TypeConverter
	fun localDateToJson(date: LocalDate?): String? = date.toJsonString()

	@TypeConverter
	fun jsonToLocalDate(json: String?): LocalDate? = json.parseJsonDate()
}