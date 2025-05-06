plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlinKSP)
	alias(libs.plugins.kotlinxSerialization)
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

		//Room settings
		ksp {
			//Room settings
			arg("room.schemaLocation", "$projectDir/schemas")
			arg("room.incremental", "true")
		}
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
	kotlinOptions {
		jvmTarget = "17"
	}
	buildFeatures {
		compose = true
	}

	// used by Room, to test migrations
	sourceSets {
		getByName("androidTest").assets.srcDirs("$projectDir/schemas")
	}
}

dependencies {

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.lifecycle.runtime.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)
	implementation(libs.androidx.ui.graphics)
	implementation(libs.androidx.ui.tooling.preview)
	implementation(libs.androidx.material3)

	//AppolyDroidBaseRepo
	implementation(project(":BaseRepo"))
	implementation(project(":BaseRepo-S3Uploader"))
	implementation(project(":BaseRepo-Paging"))
	implementation(project(":UiState"))
	implementation(project(":DateHelperUtil-MP"))
	implementation(project(":DateHelperUtil-MP-Room"))
	implementation(project(":DateHelperUtil-MP-Serialization"))

	implementation(libs.androidx.room.runtime)
	ksp(libs.androidx.room.compiler)
	implementation(libs.androidx.room.ktx)
	androidTestImplementation(libs.androidx.room.testing)

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