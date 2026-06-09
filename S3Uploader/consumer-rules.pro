# Consumer R8/ProGuard rules for S3Uploader.
# Merged automatically into the consuming app's R8 config (AAR consumer rules).

# Pre-signed-URL request/response models are serialized by consumer code at runtime.
# These models are non-generic and non-polymorphic, so no -keepattributes is needed.
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.GetPreSignedUrlResponse { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.GetPreSignedUrlResponse$$serializer { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.PreSignedURLData { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.PreSignedURLData$$serializer { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.GetPreSignedUrlBody { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.GetPreSignedUrlBody$$serializer { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.ErrorBody { *; }
-keep,includedescriptorclasses class uk.co.appoly.droid.s3upload.network.ErrorBody$$serializer { *; }

# Custom serializer object referenced via @Serializable(with = ...); keep its INSTANCE.
-keep class uk.co.appoly.droid.s3upload.utils.StringOrListSerialiser { *; }
-keepclassmembers class uk.co.appoly.droid.s3upload.utils.StringOrListSerialiser {
    public static ** INSTANCE;
}
