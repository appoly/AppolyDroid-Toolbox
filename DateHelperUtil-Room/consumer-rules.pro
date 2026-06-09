# Consumer R8/ProGuard rules for DateHelperUtil-Room.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# Room invokes the @TypeConverter methods reflectively from generated _Impl code in the
# consuming app's database, so the converter carrier and its methods must be kept.
-keep class uk.co.appoly.droid.util.DBDateConverters { *; }
-keepclassmembers class uk.co.appoly.droid.util.DBDateConverters {
    @androidx.room.TypeConverter <methods>;
}
