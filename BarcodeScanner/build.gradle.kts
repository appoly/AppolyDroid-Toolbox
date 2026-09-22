import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.vanniktech.publish)
}


configure<LibraryExtension> {
	namespace = "uk.co.appoly.droid.barcodescanner"
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
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.JVM_11)
	}
}

dependencies {

	implementation(libs.androidx.core.ktx)

	// api: Barcode.FORMAT_* constants back BarcodeFormat.mlKitFormat, and Barcode is the receiver
	// of the public toScannedBarcode() extension. ~50KB of constants and interfaces — no model.
	api(libs.mlkit.barcode.scanning.common)

	// The hosted scanner UI. Play services ships the implementation; this is the thin client.
	implementation(libs.playServices.codeScanner)
	// ModuleInstallClient, for OneShotBarcodeScanner.warmUp()
	implementation(libs.playServices.base)
	// Task.await()
	implementation(libs.kotlinx.coroutines.playServices)

	testImplementation(libs.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.androidx.junit)
	testImplementation(libs.kotlinx.coroutines.test)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
}
mavenPublishing {
	pom {
		name.set("BarcodeScanner")
		description.set("Barcode model and a one-shot scanner backed by the Google Play services code scanner, with no camera permission or CameraX dependency.")
	}
}
