package uk.co.appoly.droid.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import uk.co.appoly.droid.data.db.DateNoteEntity
import uk.co.appoly.droid.data.db.DemoDatabase
import uk.co.appoly.droid.util.DateTimeSerializer
import uk.co.appoly.droid.util.InstantSerializer
import uk.co.appoly.droid.util.LocalDateSerializer
import uk.co.appoly.droid.util.ZonedDateTimeSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Demonstrates the two `java.time`-bridging DateHelperUtil modules:
 *
 * - **DateHelperUtil-Serialization** — `@Serializable(with = ...)` KSerializers round-trip
 *   `LocalDate` / `LocalDateTime` / `ZonedDateTime` / `Instant` through kotlinx JSON.
 * - **DateHelperUtil-Room** — `DBDateConverters` persists the same types through Room/SQLite.
 */
class DateSerializationRoomDemoViewModel(application: Application) : AndroidViewModel(application) {

	@Serializable
	data class EventDto(
		val title: String,
		@Serializable(with = LocalDateSerializer::class) val day: LocalDate,
		@Serializable(with = DateTimeSerializer::class) val startsAt: LocalDateTime,
		@Serializable(with = ZonedDateTimeSerializer::class) val zoned: ZonedDateTime,
		@Serializable(with = InstantSerializer::class) val createdAt: Instant
	)

	private val json = Json { prettyPrint = true }

	private val db = Room.databaseBuilder(
		application,
		DemoDatabase::class.java,
		"demo-dates.db"
	).build()
	private val dao = db.dateNoteDao()

	// --- Serialization round-trip ---
	private val _serializedJson = MutableStateFlow("")
	val serializedJson: StateFlow<String> = _serializedJson.asStateFlow()

	private val _decoded = MutableStateFlow<EventDto?>(null)
	val decoded: StateFlow<EventDto?> = _decoded.asStateFlow()

	// --- Room persistence ---
	private val _notes = MutableStateFlow<List<DateNoteEntity>>(emptyList())
	val notes: StateFlow<List<DateNoteEntity>> = _notes.asStateFlow()

	init {
		refreshNotes()
	}

	fun runSerializationRoundTrip() {
		val event = EventDto(
			title = "Launch",
			day = LocalDate.of(2026, 6, 9),
			startsAt = LocalDateTime.of(2026, 6, 9, 10, 30),
			zoned = ZonedDateTime.now(),
			createdAt = Instant.now()
		)
		val encoded = json.encodeToString(event)
		_serializedJson.value = encoded
		_decoded.value = json.decodeFromString<EventDto>(encoded)
	}

	fun addNote() {
		viewModelScope.launch {
			withContext(Dispatchers.IO) {
				dao.insert(
					DateNoteEntity(
						label = "Note saved at ${LocalDateTime.now()}",
						createdAt = LocalDateTime.now(),
						dueDate = LocalDate.now().plusDays(7)
					)
				)
			}
			refreshNotes()
		}
	}

	fun clearNotes() {
		viewModelScope.launch {
			withContext(Dispatchers.IO) { dao.clear() }
			refreshNotes()
		}
	}

	private fun refreshNotes() {
		viewModelScope.launch {
			_notes.value = withContext(Dispatchers.IO) { dao.getAll() }
		}
	}

	override fun onCleared() {
		super.onCleared()
		db.close()
	}
}
