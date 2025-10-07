import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
}

android {
	namespace = "uk.co.appoly.droid.app"
	compileSdk = libs.versions.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "uk.co.appoly.droid"
		minSdk = 26
		targetSdk = libs.versions.targetSdk.get().toInt()
		versionCode = 1
		versionName = libs.versions.toolboxVersion.get()

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlin {
		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_17)
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
	implementation(project(":ConnectivityMonitor"))

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