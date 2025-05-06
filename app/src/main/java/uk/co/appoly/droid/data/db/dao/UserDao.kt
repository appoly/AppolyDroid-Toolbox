package uk.co.appoly.droid.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import uk.co.appoly.droid.data.db.entity.UserEntity

@Dao
interface UserDao {
	@Upsert
	suspend fun upsert(user: UserEntity)

	@Query("SELECT * FROM UserEntity")
	suspend fun getUsers(): List<UserEntity>
}