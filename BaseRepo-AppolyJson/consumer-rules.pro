# Consumer R8/ProGuard rules for BaseRepo-AppolyJson.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# Generic envelope response models deserialized by consumer code. GenericResponse<T> is
# generic, so Signature is required for runtime type reconstruction.
# (No *Annotation* — it isn't needed and disables annotation optimizations app-wide.)
-keepattributes Signature,InnerClasses
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.GenericResponse { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.GenericResponse$$serializer { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.BaseResponse { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.BaseResponse$$serializer { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.ErrorBody { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.ErrorBody$$serializer { *; }
