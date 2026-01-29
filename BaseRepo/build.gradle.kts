import com.android.build.api.dsl.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.android.library)
	alias(libs.plugins.kotlinKSP)
	alias(libs.plugins.kotlinxSerialization)
	alias(libs.plugins.kotlin.compose)
	`maven-publish`
}

group = "com.github.appoly"

configure<LibraryExtension> {
	namespace = "uk.co.appoly.droid.baserepo"
	compileSdk {
		version = release(BuildConfig.Sdk.COMPILE)
	}

	publishing {
		singleVariant("release") {
			withSourcesJar()
		}
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
			version = BuildConfig.TOOLBOX_VERSION
		}
	}
}
