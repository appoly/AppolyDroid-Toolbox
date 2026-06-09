# Consumer R8/ProGuard rules for BaseRepo-Paging.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# PageData<T> is the generic paged-response wrapper deserialized by consumer code.
# Under R8 full mode its synthetic $$serializer and Companion serializer(...) factory
# must be kept; Signature preserves the generic type argument at runtime.
# (No *Annotation* — it isn't needed and disables annotation optimizations app-wide.)
-keepattributes Signature,InnerClasses
-if @kotlinx.serialization.Serializable class uk.co.appoly.droid.data.remote.model.response.PageData
-keepclassmembers class uk.co.appoly.droid.data.remote.model.response.PageData {
    *** Companion;
}
-keepclasseswithmembers class uk.co.appoly.droid.data.remote.model.response.PageData {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.PageData$$serializer { *; }
