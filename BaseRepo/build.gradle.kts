plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlin.android)
	alias(libs.plugins.kotlinKSP)
	alias(libs.plugins.kotlinxSerialization)
	alias(libs.plugins.kotlin.compose)
	`maven-publish`
}

group = "com.github.appoly"

android {
	namespace = "uk.co.appoly.droid.baserepo"
	compileSdk = libs.versions.compileSdk.get().toInt()

	publishing {
		singleVariant("release") {
			withSourcesJar()
			withJavadocJar()
		}
	}

	defaultConfig {
		minSdk = libs.versions.baseRepoMinSdk.get().toInt()

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
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
	buildFeatures {
		compose = true
	}
}

dependencies {
	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.appcompat)

	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.ui)

	//FlexiLog
	api(libs.flexiLogger)
	api(libs.flexiLogger.httpLogger)

	//kotlinx serialization
	api(libs.kotlinx.serialization)

	//sandwich
	api(libs.sandwich)
	api(libs.sandwich.retrofit)

	testImplementation(libs.junit)
	androidTestImplementation(libs.androidx.junit)
	androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
	publications {
		create<MavenPublication>("release") {
			afterEvaluate {
				from(components["release"])
			}
			groupId = "com.github.appoly"
			artifactId = project.name
			version = libs.versions.toolboxVersion.get()
		}
	}
}