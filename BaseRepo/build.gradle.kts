import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlinKSP)
	alias(libs.plugins.kotlinxSerialization)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.vanniktech.publish)
}


configure<LibraryExtension> {
	namespace = "uk.co.appoly.droid.baserepo"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	defaultConfig {
		minSdk = BuildConfig.MinSdk.BASE_REPO

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
	buildFeatures {
		compose = true
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

	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)

	//FlexiLog
	api(libs.flexiLogger)
	api(libs.flexiLogger.okhttp)

	//kotlinx serialization
	api(libs.kotlinx.serialization.json)

	//sandwich
	api(platform(libs.sandwich.bom))
	api(libs.sandwich)
	api(libs.sandwich.retrofit)

	testImplementation(libs.junit)
	testImplementation(libs.retrofit)
	testImplementation(libs.retrofit.serializationConverter)
	testImplementation(libs.okhttp)
	testImplementation(libs.okhttp.mockwebserver)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.robolectric)
	testImplementation(libs.androidx.junit)
	testImplementation(libs.androidx.ui.test.junit4)
	testImplementation(libs.androidx.ui.test.manifest)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
}
mavenPublishing {
    pom {
        name.set("BaseRepo")
        description.set("Repository pattern for Retrofit and Sandwich, with APIResult and APIFlowState result types.")
    }
}
