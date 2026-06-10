# Consumer R8/ProGuard rules for BaseRepo.
# These are merged into the consuming app's R8 config automatically (AAR consumer rules),
# so downstream AppolyDroid modules and consumer code inherit them transitively.

# NOTE: no -keepattributes here. BaseRepo has no generic @Serializable models, and the
# library uses no polymorphic serialization, so no runtime-annotation attributes are needed.
# The Signature attribute that generic response models require is kept by the modules that
# actually declare them (BaseRepo-Paging, BaseRepo-AppolyJson, BaseRepo-Paging-AppolyJson).
# Never keep *Annotation* in a consumer rule — it disables annotation optimizations app-wide.

# --- Enum serializer base classes -------------------------------------------------
# Consumer apps subclass these for their own @Serializable enums; the generated
# subclasses (and the base reflection) must survive shrinking.
-keep class uk.co.appoly.droid.util.EnumAsStringSerializer { *; }
-keep class uk.co.appoly.droid.util.NullableEnumAsStringSerializer { *; }
-keep class uk.co.appoly.droid.util.EnumAsIntSerializer { *; }
-keep class uk.co.appoly.droid.util.NullableEnumAsIntSerializer { *; }
-keepclassmembers class * extends uk.co.appoly.droid.util.EnumAsStringSerializer { *; }
-keepclassmembers class * extends uk.co.appoly.droid.util.NullableEnumAsStringSerializer { *; }
-keepclassmembers class * extends uk.co.appoly.droid.util.EnumAsIntSerializer { *; }
-keepclassmembers class * extends uk.co.appoly.droid.util.NullableEnumAsIntSerializer { *; }
