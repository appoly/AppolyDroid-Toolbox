package uk.co.appoly.droid.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDateTime

@Entity
data class UserEntity(
	@PrimaryKey(autoGenerate = true)
	val id: Long,
	val name: String,
	val email: String,
	val createdAt: LocalDateTime,
	val updatedAt: LocalDateTime?,
)