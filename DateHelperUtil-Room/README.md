# DateHelperUtil-Room

Extension module for DateHelperUtil that provides Room database integration for Java 8 date and time types.

## Features

- Room TypeConverters for LocalDateTime, LocalDate, and ZonedDateTime
- Standardized date/time formatting using ISO-8601 formats
- Timezone preservation for ZonedDateTime values
- Automatic UTC conversion for consistent storage
- Null-safety for all conversions
- Seamless integration with Room database entities

## Installation

```gradle.kts
// Requires base DateHelperUtil module
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil:1.0.34")
implementation("com.github.appoly.AppolyDroid-Toolbox:DateHelperUtil-Room:1.0.34")

// Required Room dependencies
implementation("androidx.room:room-runtime:2.8.1")
implementation("androidx.room:room-ktx:2.8.1")
ksp("androidx.room:room-compiler:2.8.1")
```

## Usage

### Setting Up Room Type Converters

Add the converters to your Room database by annotating your database class with `@TypeConverters`:

```kotlin
@Database(
    entities = [UserEntity::class, PostEntity::class],
    version = 1
)
@TypeConverters(
    DBDateConverters::class // Include the DateHelperUtil-Room converters
)
abstract class AppDatabase : RoomDatabase() {
    abstract val userDao: UserDao
    abstract val postDao: PostDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

### Using Date Types in Entities

Once the converters are registered with Room, you can use Java 8 date and time types directly in your entity classes:

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: Long,
    val username: String,
    val email: String,
    // LocalDate will be stored as "YYYY-MM-DD" in the database
    val birthDate: LocalDate?,
    // LocalDateTime will be stored as "YYYY-MM-DDThh:mm:ss.SSSSSSZ" in the database
    val registrationDate: LocalDateTime,
    // ZonedDateTime will be converted to UTC before storage
    val lastLoginDate: ZonedDateTime?
)
```

### Working with Queries

Room will automatically handle the conversion between your Java 8 date-time types and their string representations in the database:

```kotlin
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE registrationDate >= :startDate")
    fun getUsersRegisteredAfter(startDate: LocalDateTime): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE birthDate BETWEEN :startDate AND :endDate")
    suspend fun getUsersBornBetween(startDate: LocalDate, endDate: LocalDate): List<UserEntity>

    @Insert
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET lastLoginDate = :loginTime WHERE id = :userId")
    suspend fun updateLastLogin(userId: Long, loginTime: ZonedDateTime)
}
```

### Repository Implementation Example

Here's how you might implement a repository that uses these date-time types:

```kotlin
class UserRepository(private val userDao: UserDao) {
    // Get all users registered in the past week
    fun getRecentUsers(): Flow<List<UserEntity>> {
        val oneWeekAgo = LocalDateTime.now().minusWeeks(1)
        return userDao.getUsersRegisteredAfter(oneWeekAgo)
    }

    // Get users born in a specific year
    suspend fun getUsersBornInYear(year: Int): List<UserEntity> {
        val startDate = LocalDate.of(year, 1, 1)
        val endDate = LocalDate.of(year, 12, 31)
        return userDao.getUsersBornBetween(startDate, endDate)
    }

    // Create a new user with current registration time
    suspend fun createUser(username: String, email: String, birthDate: LocalDate?): Long {
        val user = UserEntity(
            id = 0, // Room will assign the actual ID
            username = username,
            email = email,
            birthDate = birthDate,
            registrationDate = LocalDateTime.now(),
            lastLoginDate = ZonedDateTime.now() // Current time with timezone info
        )
        return userDao.insertUser(user)
    }

    // Update a user's last login time
    suspend fun recordUserLogin(userId: Long) {
        userDao.updateLastLogin(userId, ZonedDateTime.now())
    }
}
```

## Storage Format Details

The DateHelperUtil-Room module stores date-time values in the following formats:

| Java Type | Database Storage Format | Example |
|-----------|-------------------------|---------|
| LocalDateTime | ISO-8601 extended format | "2023-05-30T15:45:30.000000Z" |
| LocalDate | Simple date format | "2023-05-30" |
| ZonedDateTime | ISO-8601 extended format (UTC) | "2023-05-30T15:45:30.000000Z" |

For ZonedDateTime values:

1. When storing: The ZonedDateTime is converted to UTC timezone before storage
2. When retrieving: The UTC time is parsed and then converted to the device's local timezone

This approach ensures consistent storage while preserving timezone information.

## API Reference

### DBDateConverters

The main class containing type converters for Room:

```kotlin
class DBDateConverters {
    @TypeConverter
    fun localDateTimeToJson(date: LocalDateTime?): String?

    @TypeConverter
    fun jsonToLocalDateTime(json: String?): LocalDateTime?

    @TypeConverter
    fun localDateToJson(date: LocalDate?): String?

    @TypeConverter
    fun jsonToLocalDate(json: String?): LocalDate?

    @TypeConverter
    fun zonedDateTimeToJson(date: ZonedDateTime?): String?

    @TypeConverter
    fun jsonToZonedDateTime(json: String?): ZonedDateTime?
}
```

## Dependencies

- [DateHelperUtil](../DateHelperUtil/README.md) - Base date/time utility module
- [Room persistence library](https://developer.android.com/jetpack/androidx/releases/room) - Android database library

## Notes

- All converters handle null values gracefully
- The converters leverage DateHelper's parsing and formatting methods for consistency
- The module automatically uses DateHelperUtil's standardized date formats
- For troubleshooting, enable logging in DateHelper: `DateHelper.setLogger(yourLogger, LoggingLevel.D)`

