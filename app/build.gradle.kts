import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlinxSerialization)
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
	kotlin {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_11)
		}
	}
	buildFeatures {
		compose = true
	}
}

dependencies {

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
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
	implementation(project(":PagingExtensions"))
	implementation(project(":LazyListPagingExtensions"))
	implementation(project(":LazyGridPagingExtensions"))
	implementation(project(":S3Uploader"))
	implementation(project(":S3Uploader-Multipart"))
	implementation(project(":ConnectivityMonitor"))

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