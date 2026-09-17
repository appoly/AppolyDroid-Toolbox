import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.vanniktech.publish)
}


configure<LibraryExtension> {
	namespace = "uk.co.appoly.droid.barcodescanner.camera"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	defaultConfig {
		minSdk = BuildConfig.MinSdk.BARCODE_SCANNER

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

	// api: ScannedBarcode/BarcodeFormat are this module's callback and parameter types, and a
	// consumer of the camera gets the one-shot scanner for free.
	api(project(":BarcodeScanner"))

	//Compose
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.compose.foundation)
	// LocalLifecycleOwner — the lifecycle the camera use cases bind to
	implementation(libs.androidx.lifecycle.runtime.compose)

	//CameraX. Deliberately no camera-view, camera-video or camera-mlkit-vision: CameraXViewfinder
	//replaces PreviewView, and MlKitAnalyzer would drag in the other two for a fifteen-line class.
	implementation(libs.androidx.camera.core)
	implementation(libs.androidx.camera.camera2)
	implementation(libs.androidx.camera.lifecycle)
	implementation(libs.androidx.camera.compose)

	//ML Kit barcode detection, model served by Play services rather than bundled in the APK
	implementation(libs.playServices.mlkit.barcode.scanning)

	testImplementation(libs.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.androidx.junit)
	testImplementation(platform(libs.androidx.compose.bom))
	testImplementation(libs.androidx.ui.test.junit4)
	testImplementation(libs.androidx.ui.test.manifest)
	// On-device suite (see README "On-device test suite"). Deliberately NOT run in CI: it needs a
	// real camera, which no CI runner has. Run it before tagging a release.
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.ui.test.junit4)
	androidTestImplementation(libs.androidx.activity.compose)
	debugImplementation(libs.androidx.ui.test.manifest)
}
mavenPublishing {
	pom {
		name.set("BarcodeScanner-Camera")
		description.set("Continuous in-app barcode scanning for Compose: a CameraX preview and ML Kit analyzer that decode 1D and 2D symbologies reliably.")
	}
}
