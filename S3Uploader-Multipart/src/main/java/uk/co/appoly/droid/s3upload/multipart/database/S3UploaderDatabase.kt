package uk.co.appoly.droid.s3upload.multipart.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import uk.co.appoly.droid.s3upload.multipart.database.converter.UploadStatusConverters
import uk.co.appoly.droid.s3upload.multipart.database.dao.MultipartUploadDao
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadPartEntity
import uk.co.appoly.droid.s3upload.multipart.database.entity.UploadSessionEntity

/**
 * Room database for storing multipart upload state.
 *
 * This database enables pause/resume/recovery functionality by persisting
 * upload session and part information.
 */
@Database(
	entities = [
		UploadSessionEntity::class,
		UploadPartEntity::class
	],
	version = 1,
	exportSchema = true
)
@TypeConverters(UploadStatusConverters::class)
abstract class S3UploaderDatabase : RoomDatabase() {

	abstract fun multipartUploadDao(): MultipartUploadDao

	companion object {
		private const val DATABASE_NAME = "s3_uploader_multipart.db"

		@Volatile
		private var INSTANCE: S3UploaderDatabase? = null

		/**
		 * Gets the singleton database instance, creating it if necessary.
		 *
		 * @param context Application context
		 * @return The database instance
		 */
		fun getInstance(context: Context): S3UploaderDatabase {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
			}
		}

		private fun buildDatabase(context: Context): S3UploaderDatabase {
			return Room.databaseBuilder(
				context.applicationContext,
				S3UploaderDatabase::class.java,
				DATABASE_NAME
			)
				.fallbackToDestructiveMigration(dropAllTables = true)
				.build()
		}

		/**
		 * Clears the singleton instance. Used for testing.
		 */
		internal fun clearInstance() {
			INSTANCE?.close()
			INSTANCE = null
		}
	}
}
