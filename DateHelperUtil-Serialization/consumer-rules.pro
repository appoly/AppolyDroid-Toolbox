# Consumer R8/ProGuard rules for DateHelperUtil-Serialization.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# These KSerializer singletons are referenced via @Serializable(with = ...) in *consumer*
# models, so the consuming app's R8 must keep each object and its INSTANCE field.
# They are non-generic and non-polymorphic, so no -keepattributes is needed.
-keep class uk.co.appoly.droid.util.LocalDateSerializer { *; }
-keep class uk.co.appoly.droid.util.NullableLocalDateSerializer { *; }
-keep class uk.co.appoly.droid.util.DateTimeSerializer { *; }
-keep class uk.co.appoly.droid.util.NullableDateTimeSerializer { *; }
-keep class uk.co.appoly.droid.util.ZonedDateTimeSerializer { *; }
-keep class uk.co.appoly.droid.util.NullableZonedDateTimeSerializer { *; }
-keep class uk.co.appoly.droid.util.InstantSerializer { *; }
-keep class uk.co.appoly.droid.util.NullableInstantSerializer { *; }
-keepclassmembers class uk.co.appoly.droid.util.*Serializer {
    public static ** INSTANCE;
}
