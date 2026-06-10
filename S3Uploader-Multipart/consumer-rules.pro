# Consumer R8/ProGuard rules for S3Uploader-Multipart.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# --- Room entities & WorkManager workers (instantiated reflectively) --------------
-keep class uk.co.appoly.droid.s3upload.multipart.database.entity.** { *; }
-keep class uk.co.appoly.droid.s3upload.multipart.worker.** { *; }

# --- kotlinx-serialization network models -----------------------------------------
# ~10 @Serializable request/response models serialized by the multipart upload flow.
# These models are non-generic and non-polymorphic, so no -keepattributes is needed.
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.multipart.network.model.** { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.multipart.config.UploadConstraints$$serializer { *; }

# Custom serializer object referenced via @Serializable(with = ...); keep its INSTANCE.
-keep class uk.co.appoly.droid.s3upload.multipart.network.model.EmptyArrayAsEmptyMapSerializer { *; }
-keepclassmembers class uk.co.appoly.droid.s3upload.multipart.network.model.EmptyArrayAsEmptyMapSerializer {
    public static ** INSTANCE;
}

# Room TypeConverter carrier (UploadSessionStatus / PartUploadStatus name-string mapping).
-keep class uk.co.appoly.droid.s3upload.multipart.database.converter.UploadStatusConverters { *; }
