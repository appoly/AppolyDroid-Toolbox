import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlinxSerialization)
	alias(libs.plugins.kover)
}

configure<ApplicationExtension> {
	namespace = "uk.co.appoly.droid.app"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	defaultConfig {
		applicationId = "uk.co.appoly.droid"
		minSdk = BuildConfig.MinSdk.max() // App uses highest minSdk of all modules
		targetSdk = BuildConfig.Sdk.TARGET
		versionCode = 1
		versionName = BuildConfig.TOOLBOX_VERSION

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

// Coverage report for the published library modules. The demo app is the aggregation point
// (an Android application), pulling in the library modules via the kover(project(...)) deps
// below and reporting on the "debug" variant:
//   ./gradlew :app:koverHtmlReportDebug → app/build/reports/kover/reportDebug/html/index.html
//   ./gradlew :app:koverXmlReportDebug  → XML report
//
// Pass 1 measures & reports coverage but does NOT gate on it: most modules are still
// untested (~1% aggregate), and Kover's minBound is integer-only so no meaningful nonzero
// floor is possible yet.
//
// KNOWN LIMITATION (Pass 2): the "debug" variant only includes the Android-library modules.
// The pure-Kotlin (java-library) modules — the MockInterceptor family — expose a "jvm"
// variant instead, and unifying both into one report requires either a custom variant
// declared in *every* module or the `org.jetbrains.kotlinx.kover.aggregation` settings
// plugin. Deferred to Pass 2, alongside introducing the coverage floor:
//   reports { verify { rule { minBound(N) } } }
kover {
	reports {
		// The demo app is scaffolding — measure the published libraries only.
		filters {
			excludes {
				classes("uk.co.appoly.droid.app.*")
			}
		}
	}
}

dependencies {

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime)
	implementation(libs.androidx.activity.compose)

	//Compose
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.ui.tooling.preview)
	implementation(libs.androidx.material3)
	implementation(libs.compose.material.icons.extended)

	//Navigation
	implementation(libs.androidx.navigation.compose)

	//AppolyDroidBaseRepo
	implementation(project(":BaseRepo"))
	implementation(project(":BaseRepo-S3Uploader"))
	implementation(project(":BaseRepo-Paging"))
	implementation(project(":DateHelperUtil"))
	implementation(project(":DateHelperUtil-Room"))
	implementation(project(":DateHelperUtil-Serialization"))
	implementation(project(":UiState"))
	implementation(project(":AppSnackBar"))
	implementation(project(":AppSnackBar-UiState"))
	implementation(project(":SegmentedControl"))
	implementation(project(":PagingExtensions"))
	implementation(project(":LazyListPagingExtensions"))
	implementation(project(":LazyGridPagingExtensions"))
	implementation(project(":S3Uploader"))
	implementation(project(":S3Uploader-Multipart"))
	implementation(project(":ConnectivityMonitor"))
	implementation(project(":MockInterceptor"))
	implementation(project(":MockInterceptor-Serialization"))
	implementation(project(":MockInterceptor-AppolyJson"))
	implementation(project(":MockInterceptor-Retrofit"))

	// Kover coverage aggregation — every published library module, including those the
	// demo app doesn't otherwise depend on, so the gate measures all of them.
	kover(project(":BaseRepo"))
	kover(project(":BaseRepo-S3Uploader"))
	kover(project(":BaseRepo-S3Uploader-Multipart"))
	kover(project(":BaseRepo-Paging"))
	kover(project(":BaseRepo-AppolyJson"))
	kover(project(":BaseRepo-Paging-AppolyJson"))
	kover(project(":UiState"))
	kover(project(":AppSnackBar"))
	kover(project(":AppSnackBar-UiState"))
	kover(project(":SegmentedControl"))
	kover(project(":DateHelperUtil"))
	kover(project(":DateHelperUtil-Room"))
	kover(project(":DateHelperUtil-Serialization"))
	kover(project(":PagingExtensions"))
	kover(project(":LazyListPagingExtensions"))
	kover(project(":LazyGridPagingExtensions"))
	kover(project(":ComposeExtensions"))
	kover(project(":S3Uploader"))
	kover(project(":S3Uploader-Multipart"))
	kover(project(":ConnectivityMonitor"))
	kover(project(":MockInterceptor"))
	kover(project(":MockInterceptor-Serialization"))
	kover(project(":MockInterceptor-AppolyJson"))
	kover(project(":MockInterceptor-Retrofit"))

	// For test backend API
	implementation(libs.retrofit)
	implementation(libs.retrofit.serializationConverter)
	implementation(libs.okhttp)
	implementation(libs.okhttp.logging)
	implementation(libs.kotlinx.serialization)
	implementation(libs.sandwich)
	implementation(libs.sandwich.retrofit)

	//Paging
	implementation(libs.paging.runtime)
	implementation(libs.paging.compose)
	testImplementation(libs.paging.common)

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
	androidTestImplementation(platform(libs.androidx.compose.bom))
	androidTestImplementation(libs.androidx.ui.test.junit4)
	debugImplementation(libs.androidx.ui.tooling)
	debugImplementation(libs.androidx.ui.test.manifest)
}