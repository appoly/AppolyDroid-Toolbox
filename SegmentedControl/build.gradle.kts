import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlinKSP)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.vanniktech.publish)
}


configure<LibraryExtension> {
	namespace = "uk.co.appoly.droid.segmentedcontrol"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	defaultConfig {
		minSdk = BuildConfig.MinSdk.SEGMENTED_CONTROL

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

	//Compose
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.material3)

	testImplementation(libs.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.androidx.junit)
	testImplementation(platform(libs.androidx.compose.bom))
	testImplementation(libs.androidx.ui.test.junit4)
	testImplementation(libs.androidx.ui.test.manifest)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
}
mavenPublishing {
    pom {
        name.set("SegmentedControl")
        description.set("An iOS-style segmented control for Compose, with an animated thumb and drag-to-switch gestures.")
    }
}
