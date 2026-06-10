package uk.co.appoly.droid.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import uk.co.appoly.droid.util.DBDateConverters
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A row whose `java.time` columns are persisted via [DBDateConverters] from the
 * **DateHelperUtil-Room** module — Room has no built-in support for these types, so the
 * `@TypeConverter`s on [DemoDatabase] do the String <-> date marshalling.
 */
@Entity(tableName = "date_notes")
data class DateNoteEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val label: String,
	val createdAt: LocalDateTime,
	val dueDate: LocalDate?
)

@Dao
interface DateNoteDao {
	@Insert
	suspend fun insert(note: DateNoteEntity): Long

	@Query("SELECT * FROM date_notes ORDER BY id DESC")
	suspend fun getAll(): List<DateNoteEntity>

	@Query("DELETE FROM date_notes")
	suspend fun clear()
}

/**
 * Demo database wiring the **DateHelperUtil-Room** [DBDateConverters] in via [TypeConverters],
 * so [DateNoteEntity]'s `LocalDateTime` / `LocalDate` columns round-trip through SQLite.
 */
@Database(entities = [DateNoteEntity::class], version = 1, exportSchema = false)
@TypeConverters(DBDateConverters::class)
abstract class DemoDatabase : RoomDatabase() {
	abstract fun dateNoteDao(): DateNoteDao
}
