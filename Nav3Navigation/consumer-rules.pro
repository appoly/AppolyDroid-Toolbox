# Consumer R8/ProGuard rules for Nav3Navigation.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# --- Nav3Screen back-stack restore -------------------------------------------------
# Navigation 3 persists the back stack through NavKeySerializer, which resolves each
# key's KSerializer reflectively by class. Under R8 full mode a consuming app's
# @Serializable screen classes have no direct reference to their generated serializers
# and can be stripped or renamed, breaking back-stack restore after process death.
#
# Keep, for every class implementing uk.co.appoly.droid.nav3.Nav3Screen:
# the class itself, its Companion, and its generated $$serializer (INSTANCE + serializer()).
# Scoped to Nav3Screen implementors only — no blanket -keep class ** { *; }.

# Class identity must survive shrinking/obfuscation (serial names default to FQCN).
-keep class * implements uk.co.appoly.droid.nav3.Nav3Screen

# Companion object that hosts serializer() for @Serializable data classes / objects.
-if class * implements uk.co.appoly.droid.nav3.Nav3Screen
-keepclassmembers class <1> {
    *** Companion;
}

# serializer() factory on Companion (and named companions).
-if class * implements uk.co.appoly.droid.nav3.Nav3Screen {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Generated $$serializer class, its INSTANCE field, and serializer() methods.
-if class * implements uk.co.appoly.droid.nav3.Nav3Screen
-keep,includedescriptorclasses class <1>$$serializer {
    *** INSTANCE;
    <methods>;
}

# data object / object screens also expose INSTANCE + serializer() on the class itself.
-if class * implements uk.co.appoly.droid.nav3.Nav3Screen
-keepclassmembers class <1> {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
