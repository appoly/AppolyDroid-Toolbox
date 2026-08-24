import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlinKSP)
	alias(libs.plugins.kotlinxSerialization)
	alias(libs.plugins.vanniktech.publish)
}


configure<LibraryExtension> {
	namespace = "uk.co.appoly.droid.datehelper.serialization"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	defaultConfig {
		minSdk = BuildConfig.MinSdk.DATE_HELPER

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

	api(project(":DateHelperUtil"))

	//kotlinx serialization
	api(libs.kotlinx.serialization.json)

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
}
mavenPublishing {
    pom {
        name.set("DateHelperUtil-Serialization")
        description.set("kotlinx-serialization serializers for DateHelperUtil types.")
    }
}
