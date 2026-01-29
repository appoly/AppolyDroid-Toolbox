import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlinKSP)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.room)
    `maven-publish`
}

group = "com.github.appoly"

configure<LibraryExtension> {
    namespace = "uk.co.appoly.droid.s3upload.multipart"
    compileSdk {
        version = release(BuildConfig.Sdk.COMPILE)
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
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

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    sourceSets {
        // Adds exported schema location as test app assets for migration testing
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

dependencies {
    // Base S3Uploader module
    api(project(":S3Uploader"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Coroutines
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // kotlinx serialization (for JSON storage)
    implementation(libs.kotlinx.serialization)

    // Retrofit kotlinx serialization converter
    implementation(libs.retrofit.serializationConverter)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }
            groupId = "com.github.appoly"
            artifactId = project.name
            version = BuildConfig.TOOLBOX_VERSION
        }
    }
}
