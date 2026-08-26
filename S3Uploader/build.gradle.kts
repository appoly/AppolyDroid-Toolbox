import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinKSP)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.vanniktech.publish)
}

configure<LibraryExtension> {
    namespace = "uk.co.appoly.droid.s3upload"
    compileSdk {
        version = release(BuildConfig.Sdk.COMPILE)
    }

    defaultConfig {
        minSdk = BuildConfig.MinSdk.S3_UPLOADER
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.okhttp.logging)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.serializationConverter)

    // Sandwich
    api(platform(libs.sandwich.bom))
    api(libs.sandwich)
    api(libs.sandwich.retrofit)

    //kotlinx serialization
    api(libs.kotlinx.serialization.json)

    // FlexiLogger
    api(libs.flexiLogger)
    api(libs.flexiLogger.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Upload-flow tests drive the real Retrofit/OkHttp stack against MockWebServer.
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    pom {
        name.set("S3Uploader")
        description.set("Direct S3 uploads from Android with pre-signed URLs and progress tracking.")
    }
}
