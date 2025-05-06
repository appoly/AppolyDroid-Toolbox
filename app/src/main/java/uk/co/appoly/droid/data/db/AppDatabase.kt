package uk.co.appoly.droid.data.db

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import uk.co.appoly.droid.data.db.dao.UserDao
import uk.co.appoly.droid.data.db.entity.UserEntity
import uk.co.appoly.droid.util.DBDateConverters

@Database(
	entities = [
		UserEntity::class,
	],
	version = AppDatabase.DATABASE_VERSION,
//	autoMigrations = [
//		AutoMigration(from = 1, to = 2),
//	]
)
@TypeConverters(
	DBDateConverters::class,
)
@RewriteQueriesToDropUnusedColumns
abstract class AppDatabase: RoomDatabase() {
	abstract val userDao: UserDao

	companion object {
		const val DATABASE_VERSION = 1

		@Volatile
		private var INSTANCE: AppDatabase? = null

		@VisibleForTesting
		private val DATABASE_NAME = "eqpd-db"

		fun getInstance(context: Context): AppDatabase =
			INSTANCE ?: synchronized(this) {
				INSTANCE ?: buildDatabase(context.applicationContext).also {
					INSTANCE = it
				}
			}

		/**
		 * Set up the database configuration.
		 * The SQLite database is only created when it's accessed for the first time.
		 */
		private fun buildDatabase(appContext: Context): AppDatabase {
			return Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
//				.fallbackToDestructiveMigration(false)
				.build()
		}
	}
}