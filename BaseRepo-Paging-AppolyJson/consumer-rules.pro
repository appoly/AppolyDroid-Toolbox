# Consumer R8/ProGuard rules for BaseRepo-Paging-AppolyJson.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# Generic nested paged response models deserialized by consumer code. These are generic,
# so Signature is required for runtime type reconstruction.
# (No *Annotation* — it isn't needed and disables annotation optimizations app-wide.)
-keepattributes Signature,InnerClasses
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.GenericNestedPagedResponse { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.GenericNestedPagedResponse$$serializer { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.NestedPageData { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.data.remote.model.response.NestedPageData$$serializer { *; }
